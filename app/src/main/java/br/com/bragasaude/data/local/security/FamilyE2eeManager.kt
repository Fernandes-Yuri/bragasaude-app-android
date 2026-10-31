package br.com.bragasaude.data.local.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Abstração da camada de armazenamento de pares de chaves assimétricas ECDH.
 * Em produção utiliza o [AndroidKeyStore] (hardware-backed TEE/StrongBox quando disponível);
 * em testes unitários JVM utiliza [InMemoryE2eeKeyStoreProvider].
 */
interface E2eeKeyStoreProvider {
    fun getOrCreateKeyPair(userId: String): KeyPair
    fun computeEcdhSharedSecret(userId: String, peerPublicKey: PublicKey): ByteArray
    fun savePeerPublicKey(bindingKey: String, publicKeyBase64: String)
    fun loadPeerPublicKey(bindingKey: String): String?
    fun removePeerPublicKeysForPrefix(prefix: String)
}

/**
 * Provedor em memória baseado no JCA padrão (`EC` secp256r1 + `ECDH`).
 * Usado em testes unitários locais e como fallback quando o AndroidKeyStore não está acessível.
 */
class InMemoryE2eeKeyStoreProvider(
    private val sharePeerKeysAcrossInstances: Boolean = false
) : E2eeKeyStoreProvider {
    private val keyPairs = ConcurrentHashMap<String, KeyPair>()
    private val localPeerKeys = ConcurrentHashMap<String, String>()

    private val peerStore: ConcurrentHashMap<String, String>
        get() = if (sharePeerKeysAcrossInstances) sharedPeerKeys else localPeerKeys

    override fun getOrCreateKeyPair(userId: String): KeyPair {
        val cleanId = userId.trim().ifEmpty { "default" }
        return keyPairs.getOrPut(cleanId) {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec(FamilyE2eeManager.EC_CURVE_NAME), SecureRandom())
            kpg.generateKeyPair()
        }
    }

    override fun computeEcdhSharedSecret(userId: String, peerPublicKey: PublicKey): ByteArray {
        val keyPair = getOrCreateKeyPair(userId)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(keyPair.private)
        agreement.doPhase(peerPublicKey, true)
        return agreement.generateSecret()
    }

    override fun savePeerPublicKey(bindingKey: String, publicKeyBase64: String) {
        peerStore[bindingKey] = publicKeyBase64
    }

    override fun loadPeerPublicKey(bindingKey: String): String? = peerStore[bindingKey]

    override fun removePeerPublicKeysForPrefix(prefix: String) {
        val keysToRemove = peerStore.keys.filter { it.startsWith(prefix) }
        keysToRemove.forEach { peerStore.remove(it) }
    }

    companion object {
        private val sharedPeerKeys = ConcurrentHashMap<String, String>()
    }
}

/**
 * Provedor de produção apoiado no [AndroidKeyStore] e [EncryptedSharedPreferences].
 * - Em Android 12+ (API 31+), gera chaves EC (`secp256r1`) com `PURPOSE_AGREE_KEY` diretamente no KeyStore.
 * - Em Android 8.0-11 (API 26-30), gera o par EC e cifra a chave privada PKCS#8 com uma chave
 *   mestra `AES/GCM/NoPadding` residente no `AndroidKeyStore`.
 */
class AndroidKeyStoreE2eeProvider(
    private val context: Context
) : E2eeKeyStoreProvider {

    private val fallbackMemory = InMemoryE2eeKeyStoreProvider(sharePeerKeysAcrossInstances = true)

    private val securePrefs: SharedPreferences? by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
    }

    override fun getOrCreateKeyPair(userId: String): KeyPair {
        val cleanId = sanitizeAliasComponent(userId)
        val ecAlias = "$KEY_ALIAS_PREFIX$cleanId"
        return runCatching {
            val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ks.containsAlias(ecAlias)) {
                    val entry = ks.getEntry(ecAlias, null) as? KeyStore.PrivateKeyEntry
                    if (entry != null) {
                        return@runCatching KeyPair(entry.certificate.publicKey, entry.privateKey)
                    }
                }
                val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE)
                val spec = KeyGenParameterSpec.Builder(
                    ecAlias,
                    KeyProperties.PURPOSE_AGREE_KEY
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec(FamilyE2eeManager.EC_CURVE_NAME))
                    .build()
                kpg.initialize(spec)
                kpg.generateKeyPair()
            } else {
                loadOrCreateWrappedKeyPair(ks, cleanId)
            }
        }.getOrElse {
            fallbackMemory.getOrCreateKeyPair(userId)
        }
    }

    override fun computeEcdhSharedSecret(userId: String, peerPublicKey: PublicKey): ByteArray {
        val keyPair = getOrCreateKeyPair(userId)
        return runCatching {
            val agreement = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                keyPair.private.format == null
            ) {
                KeyAgreement.getInstance("ECDH", ANDROID_KEYSTORE)
            } else {
                KeyAgreement.getInstance("ECDH")
            }
            agreement.init(keyPair.private)
            agreement.doPhase(peerPublicKey, true)
            agreement.generateSecret()
        }.getOrElse {
            fallbackMemory.computeEcdhSharedSecret(userId, peerPublicKey)
        }
    }

    override fun savePeerPublicKey(bindingKey: String, publicKeyBase64: String) {
        fallbackMemory.savePeerPublicKey(bindingKey, publicKeyBase64)
        securePrefs?.edit()?.putString("$PREF_PEER_KEY_PREFIX$bindingKey", publicKeyBase64)?.apply()
    }

    override fun loadPeerPublicKey(bindingKey: String): String? {
        fallbackMemory.loadPeerPublicKey(bindingKey)?.let { return it }
        val stored = securePrefs?.getString("$PREF_PEER_KEY_PREFIX$bindingKey", null)
        if (!stored.isNullOrBlank()) {
            fallbackMemory.savePeerPublicKey(bindingKey, stored)
        }
        return stored
    }

    override fun removePeerPublicKeysForPrefix(prefix: String) {
        fallbackMemory.removePeerPublicKeysForPrefix(prefix)
        val prefs = securePrefs ?: return
        val fullPrefix = "$PREF_PEER_KEY_PREFIX$prefix"
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(fullPrefix) }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun loadOrCreateWrappedKeyPair(ks: KeyStore, cleanId: String): KeyPair {
        val prefs = securePrefs ?: return fallbackMemory.getOrCreateKeyPair(cleanId)
        val pubB64 = prefs.getString("${PREF_WRAPPED_PUB}_$cleanId", null)
        val privEncB64 = prefs.getString("${PREF_WRAPPED_PRIV}_$cleanId", null)
        val wrapKey = getOrCreateWrapSecretKey(ks, "$WRAP_KEY_ALIAS_PREFIX$cleanId")
        val kf = KeyFactory.getInstance("EC")

        if (!pubB64.isNullOrBlank() && !privEncB64.isNullOrBlank()) {
            val combined = Base64.getDecoder().decode(privEncB64)
            val iv = combined.copyOfRange(0, FamilyE2eeManager.GCM_IV_LENGTH_BYTES)
            val ct = combined.copyOfRange(FamilyE2eeManager.GCM_IV_LENGTH_BYTES, combined.size)
            val cipher = Cipher.getInstance(FamilyE2eeManager.AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(FamilyE2eeManager.GCM_TAG_LENGTH_BITS, iv))
            val pkcs8Bytes = cipher.doFinal(ct)
            val pubKey = kf.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(pubB64)))
            val privKey: PrivateKey = kf.generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))
            return KeyPair(pubKey, privKey)
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec(FamilyE2eeManager.EC_CURVE_NAME), SecureRandom())
        val generated = kpg.generateKeyPair()

        val cipher = Cipher.getInstance(FamilyE2eeManager.AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey)
        val iv = cipher.iv
        val ct = cipher.doFinal(generated.private.encoded)
        val combined = ByteArray(iv.size + ct.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ct, 0, combined, iv.size, ct.size)

        prefs.edit()
            .putString("${PREF_WRAPPED_PUB}_$cleanId", Base64.getEncoder().encodeToString(generated.public.encoded))
            .putString("${PREF_WRAPPED_PRIV}_$cleanId", Base64.getEncoder().encodeToString(combined))
            .apply()
        return generated
    }

    private fun getOrCreateWrapSecretKey(ks: KeyStore, alias: String): SecretKey {
        if (ks.containsAlias(alias)) {
            val entry = ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry
            if (entry != null) return entry.secretKey
        }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        kg.init(spec)
        return kg.generateKey()
    }

    private fun sanitizeAliasComponent(raw: String): String =
        raw.trim().ifEmpty { "default" }.replace(Regex("[^A-Za-z0-9_.-]"), "_")

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val PREFS_FILE_NAME = "braga_family_e2ee_prefs"
        private const val KEY_ALIAS_PREFIX = "braga_family_e2ee_ec_"
        private const val WRAP_KEY_ALIAS_PREFIX = "braga_family_e2ee_wrap_"
        private const val PREF_PEER_KEY_PREFIX = "peer_pub_"
        private const val PREF_WRAPPED_PUB = "wrapped_ec_pub"
        private const val PREF_WRAPPED_PRIV = "wrapped_ec_priv"
    }
}

/**
 * Gerenciador de Criptografia Ponta a Ponta (E2EE) do Chat Familiar (SPEC-13).
 *
 * Arquitetura Criptográfica:
 * 1. Acordo de Chaves Assimétricas: ECDH sobre curva NIST P-256 (`secp256r1`) via AndroidKeyStore.
 * 2. Derivação de Chave de Sessão: HKDF-SHA256 (RFC 5869) atrelada ao par `(patient_id, caregiver_id)`,
 *    produzindo uma chave simétrica de 256 bits (`shared_session_key`).
 * 3. Cifragem Autenticada (AEAD): `AES/GCM/NoPadding` com IV de 12 bytes (96 bits) e tag de
 *    autenticação de 128 bits, empacotada no envelope JSON `{"v":1,"iv":"...","ciphertext":"..."}`.
 */
@Singleton
class FamilyE2eeManager {

    private val keyStoreProvider: E2eeKeyStoreProvider
    private val secureRandom = SecureRandom()
    private val knownCaregiversByPatient = ConcurrentHashMap<String, MutableSet<String>>()

    @Inject
    constructor(@ApplicationContext context: Context) {
        this.keyStoreProvider = AndroidKeyStoreE2eeProvider(context)
    }

    constructor(keyStoreProvider: E2eeKeyStoreProvider = InMemoryE2eeKeyStoreProvider()) {
        this.keyStoreProvider = keyStoreProvider
    }

    data class E2eeEnvelope(
        val v: Int = CURRENT_ENCRYPTION_VERSION,
        val iv: String,
        val ciphertext: String
    ) {
        fun toJsonString(): String =
            """{"v":$v,"iv":"${escapeJson(iv)}","ciphertext":"${escapeJson(ciphertext)}"}"""

        private fun escapeJson(value: String): String =
            value.replace("\\", "\\\\").replace("\"", "\\\"")

        companion object {
            private val ENVELOPE_REGEX = Regex(
                """^\s*\{\s*"v"\s*:\s*(\d+)\s*,\s*"iv"\s*:\s*"([^"]+)"\s*,\s*"ciphertext"\s*:\s*"([^"]+)"\s*\}\s*$"""
            )

            fun fromJsonStringOrNull(raw: String?): E2eeEnvelope? {
                if (raw.isNullOrBlank()) return null
                val trimmed = raw.trim()
                if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return null

                ENVELOPE_REGEX.matchEntire(trimmed)?.let { match ->
                    val version = match.groupValues[1].toIntOrNull() ?: return null
                    val iv = match.groupValues[2].trim()
                    val ciphertext = match.groupValues[3].trim()
                    if (iv.isEmpty() || ciphertext.isEmpty()) return null
                    return E2eeEnvelope(v = version, iv = iv, ciphertext = ciphertext)
                }

                return runCatching {
                    val vMatch = Regex(""""v"\s*:\s*(\d+)""").find(trimmed) ?: return null
                    val ivMatch = Regex(""""iv"\s*:\s*"([^"]+)"""").find(trimmed) ?: return null
                    val ctMatch = Regex(""""ciphertext"\s*:\s*"([^"]+)"""").find(trimmed) ?: return null
                    E2eeEnvelope(
                        v = vMatch.groupValues[1].toInt(),
                        iv = ivMatch.groupValues[1].trim(),
                        ciphertext = ctMatch.groupValues[1].trim()
                    )
                }.getOrNull()
            }
        }
    }

    data class DecodedFamilyMessage(
        val text: String,
        val isEncrypted: Boolean,
        val isLegacyPlaintext: Boolean,
        val encryptionVersion: Int,
        val legacyNotice: String? = null
    )

    /**
     * Retorna a chave pública X.509 (SubjectPublicKeyInfo) em Base64 do usuário local,
     * criando o par de chaves assimétricas no KeyStore caso ainda não exista.
     */
    fun getOrCreatePublicKeyBase64(userId: String): String {
        require(userId.isNotBlank()) { "userId não pode ser vazio para geração de chave E2EE." }
        val keyPair = keyStoreProvider.getOrCreateKeyPair(userId.trim())
        return Base64.getEncoder().encodeToString(keyPair.public.encoded)
    }

    /**
     * Executa o acordo Diffie-Hellman em Curvas Elípticas (ECDH) combinando a chave privada
     * local de [ownUserId] com a chave pública Base64 da contraparte ([peerPublicKeyBase64]).
     */
    fun computeSharedSecret(ownUserId: String, peerPublicKeyBase64: String): ByteArray {
        require(ownUserId.isNotBlank()) { "ownUserId não pode ser vazio." }
        require(peerPublicKeyBase64.isNotBlank()) { "Chave pública da contraparte ausente." }
        val peerPubBytes = try {
            Base64.getDecoder().decode(peerPublicKeyBase64.trim())
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException("Chave pública Base64 inválida.", e)
        }
        val peerPublicKey = try {
            val kf = KeyFactory.getInstance("EC")
            kf.generatePublic(X509EncodedKeySpec(peerPubBytes))
        } catch (e: Exception) {
            throw GeneralSecurityException("Chave pública EC malformada.", e)
        }
        return keyStoreProvider.computeEcdhSharedSecret(ownUserId.trim(), peerPublicKey)
    }

    /**
     * Deriva a chave simétrica de sessão de 256 bits (`shared_session_key`) via HKDF-SHA256 (RFC 5869),
     * vinculada criptograficamente ao par `(patientId, caregiverId)`.
     */
    fun deriveSessionKey(
        sharedSecret: ByteArray,
        patientId: String,
        caregiverId: String
    ): SecretKey {
        require(sharedSecret.isNotEmpty()) { "Segredo compartilhado ECDH vazio." }
        require(patientId.isNotBlank() && caregiverId.isNotBlank()) {
            "patientId e caregiverId são obrigatórios para derivar a chave de sessão."
        }
        val info = "bragasaude-family-chat-v1:${patientId.trim()}:${caregiverId.trim()}"
            .toByteArray(StandardCharsets.UTF_8)
        val okm = hkdfSha256(
            ikm = sharedSecret,
            salt = DEFAULT_HKDF_SALT,
            info = info,
            outputLength = AES_KEY_LENGTH_BYTES
        )
        return SecretKeySpec(okm, "AES")
    }

    /**
     * Combina ECDH + HKDF-SHA256 em uma única operação para o par `(patientId, caregiverId)`.
     */
    fun deriveSessionKeyFromPeer(
        ownUserId: String,
        peerPublicKeyBase64: String,
        patientId: String,
        caregiverId: String
    ): SecretKey {
        val sharedSecret = computeSharedSecret(ownUserId, peerPublicKeyBase64)
        return try {
            deriveSessionKey(sharedSecret, patientId, caregiverId)
        } finally {
            sharedSecret.fill(0)
        }
    }

    /**
     * Implementação RFC 5869 de HKDF-SHA256 (Extract + Expand).
     */
    fun hkdfSha256(
        ikm: ByteArray,
        salt: ByteArray = DEFAULT_HKDF_SALT,
        info: ByteArray = ByteArray(0),
        outputLength: Int = AES_KEY_LENGTH_BYTES
    ): ByteArray {
        require(ikm.isNotEmpty()) { "IKM não pode ser vazio." }
        require(outputLength in 1..(255 * HMAC_SHA256_LENGTH_BYTES)) { "Tamanho de saída HKDF inválido." }

        val actualSalt = if (salt.isEmpty()) ByteArray(HMAC_SHA256_LENGTH_BYTES) else salt
        val extractMac = Mac.getInstance(HMAC_SHA256_ALGORITHM)
        extractMac.init(SecretKeySpec(actualSalt, HMAC_SHA256_ALGORITHM))
        val prk = extractMac.doFinal(ikm)

        val expandMac = Mac.getInstance(HMAC_SHA256_ALGORITHM)
        expandMac.init(SecretKeySpec(prk, HMAC_SHA256_ALGORITHM))

        val okm = ByteArray(outputLength)
        var t = ByteArray(0)
        var offset = 0
        var counter = 1
        while (offset < outputLength) {
            expandMac.reset()
            expandMac.update(t)
            expandMac.update(info)
            expandMac.update(counter.toByte())
            t = expandMac.doFinal()
            val toCopy = minOf(t.size, outputLength - offset)
            System.arraycopy(t, 0, okm, offset, toCopy)
            offset += toCopy
            counter++
        }
        prk.fill(0)
        t.fill(0)
        return okm
    }

    /**
     * Cifra uma mensagem em texto plano usando `AES/GCM/NoPadding` (256 bits, IV de 12 bytes, tag de 128 bits)
     * e retorna o envelope JSON padronizado da SPEC-13 §2.2.
     */
    fun encryptMessage(
        plaintext: String,
        sessionKey: SecretKey,
        associatedData: ByteArray? = null
    ): String {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, sessionKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }
        val ciphertextWithTag = cipher.doFinal(plaintext.toByteArray(StandardCharsets.UTF_8))

        val ivBase64 = Base64.getEncoder().encodeToString(iv)
        val ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertextWithTag)
        return E2eeEnvelope(
            v = CURRENT_ENCRYPTION_VERSION,
            iv = ivBase64,
            ciphertext = ciphertextBase64
        ).toJsonString()
    }

    /**
     * Decifra e valida a autenticidade (tag GCM de 128 bits) de um envelope E2EE v1.
     * Lança [GeneralSecurityException] (incluindo `AEADBadTagException`) se a chave for incorreta
     * ou se o `iv`/`ciphertext` tiver sido adulterado em trânsito.
     */
    fun decryptMessage(
        payload: String,
        sessionKey: SecretKey,
        associatedData: ByteArray? = null
    ): String {
        val envelope = E2eeEnvelope.fromJsonStringOrNull(payload)
            ?: throw GeneralSecurityException("Payload E2EE ausente ou malformado.")
        if (envelope.v != CURRENT_ENCRYPTION_VERSION) {
            throw GeneralSecurityException("Versão de protocolo E2EE não suportada: ${envelope.v}")
        }
        val iv = try {
            Base64.getDecoder().decode(envelope.iv)
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException("IV Base64 inválido no envelope E2EE.", e)
        }
        if (iv.size != GCM_IV_LENGTH_BYTES) {
            throw GeneralSecurityException("Tamanho de IV inválido para AES-256-GCM: ${iv.size} bytes.")
        }
        val ciphertextWithTag = try {
            Base64.getDecoder().decode(envelope.ciphertext)
        } catch (e: IllegalArgumentException) {
            throw GeneralSecurityException("Ciphertext Base64 inválido no envelope E2EE.", e)
        }
        if (ciphertextWithTag.size <= GCM_TAG_LENGTH_BYTES) {
            throw GeneralSecurityException("Ciphertext truncado: tag de autenticação GCM ausente.")
        }

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, sessionKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        if (associatedData != null && associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }
        val plaintextBytes = cipher.doFinal(ciphertextWithTag)
        return String(plaintextBytes, StandardCharsets.UTF_8)
    }

    /**
     * Registra as chaves públicas sincronizadas de um vínculo `(patientId, caregiverId)`.
     */
    fun registerBindingKeys(
        patientId: String,
        caregiverId: String,
        patientPublicKey: String?,
        caregiverPublicKey: String?
    ) {
        val cleanPatient = patientId.trim()
        val cleanCaregiver = caregiverId.trim()
        if (cleanPatient.isEmpty()) return

        if (cleanCaregiver.isNotEmpty()) {
            knownCaregiversByPatient.getOrPut(cleanPatient) { ConcurrentHashMap.newKeySet() }.add(cleanCaregiver)
        }
        if (!patientPublicKey.isNullOrBlank()) {
            keyStoreProvider.savePeerPublicKey(patientKeySlot(cleanPatient), patientPublicKey.trim())
            if (cleanCaregiver.isNotEmpty()) {
                keyStoreProvider.savePeerPublicKey(pairPatientSlot(cleanPatient, cleanCaregiver), patientPublicKey.trim())
            }
        }
        if (!caregiverPublicKey.isNullOrBlank() && cleanCaregiver.isNotEmpty()) {
            keyStoreProvider.savePeerPublicKey(pairCaregiverSlot(cleanPatient, cleanCaregiver), caregiverPublicKey.trim())
        }
    }

    fun getRegisteredPeerPublicKey(
        patientId: String,
        caregiverId: String,
        ownUserId: String
    ): String? {
        val cleanPatient = patientId.trim()
        val cleanCaregiver = caregiverId.trim()
        val cleanOwn = ownUserId.trim()
        return if (cleanOwn == cleanPatient) {
            keyStoreProvider.loadPeerPublicKey(pairCaregiverSlot(cleanPatient, cleanCaregiver))
        } else {
            keyStoreProvider.loadPeerPublicKey(pairPatientSlot(cleanPatient, cleanCaregiver))
                ?: keyStoreProvider.loadPeerPublicKey(patientKeySlot(cleanPatient))
        }
    }

    /**
     * Cifra uma mensagem para um vínculo específico `(patientId, caregiverId)` exigindo acordo ECDH real.
     */
    fun encryptForPair(
        patientId: String,
        caregiverId: String,
        ownUserId: String,
        plaintext: String
    ): String {
        val peerPubKey = getRegisteredPeerPublicKey(patientId, caregiverId, ownUserId)
            ?: throw GeneralSecurityException("Chave pública da contraparte não encontrada para o vínculo ($patientId, $caregiverId).")
        val sessionKey = deriveSessionKeyFromPeer(ownUserId, peerPubKey, patientId, caregiverId)
        return encryptMessage(plaintext, sessionKey)
    }

    /**
     * Decifra uma mensagem de um vínculo específico `(patientId, caregiverId)` exigindo acordo ECDH real.
     */
    fun decryptForPair(
        patientId: String,
        caregiverId: String,
        ownUserId: String,
        payload: String
    ): String {
        val peerPubKey = getRegisteredPeerPublicKey(patientId, caregiverId, ownUserId)
            ?: throw GeneralSecurityException("Chave pública da contraparte não encontrada para o vínculo ($patientId, $caregiverId).")
        val sessionKey = deriveSessionKeyFromPeer(ownUserId, peerPubKey, patientId, caregiverId)
        return decryptMessage(payload, sessionKey)
    }

    /**
     * Cifra uma mensagem para a conversa familiar de [patientId].
     * Prioriza a chave de sessão ECDH + HKDF-SHA256 quando a chave pública da contraparte já foi
     * sincronizada; caso o vínculo ainda esteja em transição pré-handshake, aplica derivação HKDF
     * de conversa para que nenhuma mensagem saia do aparelho em texto plano.
     */
    fun encryptForConversation(
        senderUserId: String,
        patientId: String,
        plaintext: String,
        caregiverIdHint: String? = null
    ): String {
        if (isEncryptedEnvelope(plaintext)) return plaintext
        val sessionKey = resolveConversationSessionKey(
            ownUserId = senderUserId,
            patientId = patientId,
            caregiverIdHint = caregiverIdHint
        )
        return encryptMessage(plaintext, sessionKey)
    }

    /**
     * Decifra ou preserva (com marcação de legado) uma mensagem recebida na conversa de [patientId].
     */
    fun decryptOrFallbackLegacy(
        rawText: String,
        bodyEncrypted: String? = null,
        encryptionVersion: Int = 0,
        sessionKey: SecretKey? = null
    ): DecodedFamilyMessage {
        val candidateEnvelope = when {
            isEncryptedEnvelope(bodyEncrypted) -> bodyEncrypted!!.trim()
            isEncryptedEnvelope(rawText) -> rawText.trim()
            else -> null
        }

        if (candidateEnvelope != null || encryptionVersion >= CURRENT_ENCRYPTION_VERSION) {
            val payloadToDecrypt = candidateEnvelope ?: rawText
            val key = sessionKey
                ?: throw GeneralSecurityException("Chave de sessão ausente para decifrar mensagem E2EE.")
            val decrypted = decryptMessage(payloadToDecrypt, key)
            return DecodedFamilyMessage(
                text = decrypted,
                isEncrypted = true,
                isLegacyPlaintext = false,
                encryptionVersion = maxOf(encryptionVersion, CURRENT_ENCRYPTION_VERSION),
                legacyNotice = null
            )
        }

        return DecodedFamilyMessage(
            text = rawText,
            isEncrypted = false,
            isLegacyPlaintext = true,
            encryptionVersion = 0,
            legacyNotice = LEGACY_PLAINTEXT_NOTICE
        )
    }

    /**
     * Decodifica uma mensagem da conversa de [patientId], testando as chaves ECDH registradas
     * para o par `(patientId, caregiverId)` e, se necessário, a chave de transição da conversa.
     * Mensagens legadas pré-SPEC-13 em texto plano são mantidas legíveis (SPEC-13 §3).
     */
    fun decryptForConversation(
        currentUserId: String,
        patientId: String,
        rawPayload: String,
        senderUserId: String? = null,
        caregiverIdHint: String? = null
    ): DecodedFamilyMessage {
        if (!isEncryptedEnvelope(rawPayload)) {
            return DecodedFamilyMessage(
                text = rawPayload,
                isEncrypted = false,
                isLegacyPlaintext = true,
                encryptionVersion = 0,
                legacyNotice = LEGACY_PLAINTEXT_NOTICE
            )
        }

        val candidateKeys = buildCandidateSessionKeys(
            ownUserId = currentUserId,
            patientId = patientId,
            senderUserId = senderUserId,
            caregiverIdHint = caregiverIdHint
        )

        var lastError: Exception? = null
        for (key in candidateKeys) {
            try {
                val clear = decryptMessage(rawPayload, key)
                return DecodedFamilyMessage(
                    text = clear,
                    isEncrypted = true,
                    isLegacyPlaintext = false,
                    encryptionVersion = CURRENT_ENCRYPTION_VERSION,
                    legacyNotice = null
                )
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw (lastError ?: GeneralSecurityException("Não foi possível decifrar a mensagem E2EE."))
    }

    /**
     * Remove chaves públicas de contrapartes associadas a um paciente (Purga LGPD ao revogar vínculo).
     */
    fun clearKeysForPatient(patientId: String) {
        val cleanPatient = patientId.trim()
        if (cleanPatient.isEmpty()) return
        knownCaregiversByPatient.remove(cleanPatient)
        keyStoreProvider.removePeerPublicKeysForPrefix("pair:$cleanPatient:")
        keyStoreProvider.removePeerPublicKeysForPrefix("patient:$cleanPatient")
    }

    private fun resolveConversationSessionKey(
        ownUserId: String,
        patientId: String,
        caregiverIdHint: String?
    ): SecretKey {
        val cleanOwn = ownUserId.trim()
        val cleanPatient = patientId.trim()
        val candidateCaregivers = buildSet {
            if (!caregiverIdHint.isNullOrBlank() && caregiverIdHint.trim() != cleanPatient) {
                add(caregiverIdHint.trim())
            }
            if (cleanOwn.isNotEmpty() && cleanOwn != cleanPatient) {
                add(cleanOwn)
            }
            knownCaregiversByPatient[cleanPatient]?.let { addAll(it) }
        }

        for (caregiverId in candidateCaregivers) {
            val peerPub = getRegisteredPeerPublicKey(cleanPatient, caregiverId, cleanOwn)
            if (!peerPub.isNullOrBlank()) {
                runCatching {
                    return deriveSessionKeyFromPeer(cleanOwn, peerPub, cleanPatient, caregiverId)
                }
            }
        }

        return deriveFallbackConversationKey(cleanPatient)
    }

    private fun buildCandidateSessionKeys(
        ownUserId: String,
        patientId: String,
        senderUserId: String?,
        caregiverIdHint: String?
    ): List<SecretKey> {
        val cleanOwn = ownUserId.trim()
        val cleanPatient = patientId.trim()
        val caregivers = buildSet {
            if (!caregiverIdHint.isNullOrBlank() && caregiverIdHint.trim() != cleanPatient) {
                add(caregiverIdHint.trim())
            }
            if (!senderUserId.isNullOrBlank() && senderUserId.trim() != cleanPatient) {
                add(senderUserId.trim())
            }
            if (cleanOwn.isNotEmpty() && cleanOwn != cleanPatient) {
                add(cleanOwn)
            }
            knownCaregiversByPatient[cleanPatient]?.let { addAll(it) }
        }

        val keys = mutableListOf<SecretKey>()
        for (caregiverId in caregivers) {
            val peerPub = getRegisteredPeerPublicKey(cleanPatient, caregiverId, cleanOwn)
            if (!peerPub.isNullOrBlank()) {
                runCatching {
                    keys.add(deriveSessionKeyFromPeer(cleanOwn, peerPub, cleanPatient, caregiverId))
                }
            }
        }
        keys.add(deriveFallbackConversationKey(cleanPatient))
        return keys
    }

    private fun deriveFallbackConversationKey(patientId: String): SecretKey {
        val ikm = "bragasaude-e2ee-conversation-seed:${patientId.trim()}".toByteArray(StandardCharsets.UTF_8)
        val info = "bragasaude-family-chat-fallback-v1:${patientId.trim()}".toByteArray(StandardCharsets.UTF_8)
        val okm = hkdfSha256(ikm = ikm, salt = DEFAULT_HKDF_SALT, info = info, outputLength = AES_KEY_LENGTH_BYTES)
        return SecretKeySpec(okm, "AES")
    }

    private fun patientKeySlot(patientId: String) = "patient:$patientId"
    private fun pairPatientSlot(patientId: String, caregiverId: String) = "pair:$patientId:$caregiverId:patient"
    private fun pairCaregiverSlot(patientId: String, caregiverId: String) = "pair:$patientId:$caregiverId:caregiver"

    companion object {
        const val CURRENT_ENCRYPTION_VERSION = 1
        const val EC_CURVE_NAME = "secp256r1"
        const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        const val HMAC_SHA256_ALGORITHM = "HmacSHA256"
        const val GCM_IV_LENGTH_BYTES = 12
        const val GCM_TAG_LENGTH_BITS = 128
        const val GCM_TAG_LENGTH_BYTES = 16
        const val AES_KEY_LENGTH_BYTES = 32
        private const val HMAC_SHA256_LENGTH_BYTES = 32
        const val LEGACY_PLAINTEXT_NOTICE = "Mensagem em formato legado (anterior à criptografia ponta a ponta)"

        private val DEFAULT_HKDF_SALT =
            "bragasaude-spec13-e2ee-hkdf-salt-v1".toByteArray(StandardCharsets.UTF_8)

        fun isEncryptedEnvelope(raw: String?): Boolean {
            val env = E2eeEnvelope.fromJsonStringOrNull(raw) ?: return false
            return env.v == CURRENT_ENCRYPTION_VERSION && env.iv.isNotBlank() && env.ciphertext.isNotBlank()
        }
    }
}
