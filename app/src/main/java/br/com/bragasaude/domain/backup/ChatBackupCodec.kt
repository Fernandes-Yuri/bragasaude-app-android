package br.com.bragasaude.domain.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupFamilyMessage(
    val id: String,
    val patientUserId: String,
    val senderName: String,
    val messageText: String,
    val iconType: String,
    val isRead: Boolean = false,
    val sentAt: Long,
    val senderUserId: String? = null,
    val remoteId: String? = null,
    val expiresAt: Long,
    val awaitingDecryption: Boolean = false
)

@Serializable
data class BackupOrbConversation(
    val id: String,
    val userId: String,
    val title: String,
    val updatedAt: Long,
    val messagesJson: String
)

@Serializable
data class BackupB2BDirectMessage(
    val programId: String,
    val programName: String = "",
    val primaryColor: String? = null,
    val patientUserId: String,
    val patientName: String? = null,
    val messageId: String,
    val senderUserId: String,
    val senderRole: String,
    val body: String,
    val createdAt: String,
    val createdAtMillis: Long,
    val readAt: String? = null,
    val isClosedProgram: Boolean = false
)

@Serializable
data class ChatBackupPayload(
    val schemaVersion: Int = 1,
    val exportedAtMillis: Long,
    val ownerUserId: String,
    val familyMessages: List<BackupFamilyMessage> = emptyList(),
    val orbConversations: List<BackupOrbConversation> = emptyList(),
    val b2bDirectMessages: List<BackupB2BDirectMessage> = emptyList()
)

@Serializable
data class EncryptedChatBackupEnvelope(
    val format: String = FORMAT_NAME,
    val version: Int = CURRENT_VERSION,
    val kdf: String = KDF_ALGORITHM,
    val iterations: Int = DEFAULT_PBKDF2_ITERATIONS,
    val saltB64: String,
    val ivB64: String,
    val ciphertextB64: String
) {
    companion object {
        const val FORMAT_NAME = "BragaChatBackup"
        const val CURRENT_VERSION = 1
        const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
        const val DEFAULT_PBKDF2_ITERATIONS = 120_000
    }
}

data class ChatBackupStats(
    val familyCount: Int,
    val orbConversationCount: Int,
    val b2bMessageCount: Int,
    val timestampMillis: Long = System.currentTimeMillis()
) {
    val totalCount: Int
        get() = familyCount + orbConversationCount + b2bMessageCount
}

/**
 * Serializer e criptografia E2E portavel (`BragaChatBackup v1`) em Kotlin/JVM puro (CHAT-02).
 *
 * Compacta com GZIP o JSON contendo conversas familiares, IA Orb e chats diretos B2B
 * (identificados por `programId`/`programName`) e cifra com AES-256-GCM derivado
 * exclusivamente da senha de recuperacao escolhida pelo usuario (funciona em outro aparelho).
 */
object ChatBackupCodec {
    const val MIN_PASSPHRASE_LENGTH = 8
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 1_000_000
    private const val MAX_DECOMPRESSED_BYTES = 32 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun validatePassphrase(passphrase: String): String? {
        val clean = passphrase.trim()
        if (clean.length < MIN_PASSPHRASE_LENGTH) {
            return "Use uma senha de recuperação com pelo menos $MIN_PASSPHRASE_LENGTH caracteres."
        }
        return null
    }

    fun encrypt(
        payload: ChatBackupPayload,
        passphrase: String,
        iterations: Int = EncryptedChatBackupEnvelope.DEFAULT_PBKDF2_ITERATIONS
    ): ByteArray {
        validatePassphrase(passphrase)?.let { throw IllegalArgumentException(it) }
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) {
            "Número de iterações PBKDF2 inválido."
        }

        val jsonBytes = json.encodeToString(payload).toByteArray(Charsets.UTF_8)
        val compressed = gzip(jsonBytes)

        val random = SecureRandom()
        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }

        val secretKey = deriveAesKey(passphrase.trim(), salt, iterations)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(compressed)

        val encoder = Base64.getEncoder()
        val envelope = EncryptedChatBackupEnvelope(
            format = EncryptedChatBackupEnvelope.FORMAT_NAME,
            version = EncryptedChatBackupEnvelope.CURRENT_VERSION,
            kdf = EncryptedChatBackupEnvelope.KDF_ALGORITHM,
            iterations = iterations,
            saltB64 = encoder.encodeToString(salt),
            ivB64 = encoder.encodeToString(iv),
            ciphertextB64 = encoder.encodeToString(ciphertext)
        )
        return json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
    }

    fun decrypt(rawBytes: ByteArray, passphrase: String): ChatBackupPayload {
        if (rawBytes.isEmpty()) {
            throw IllegalArgumentException("Arquivo de backup vazio ou inválido.")
        }
        val cleanPassphrase = passphrase.trim()
        if (cleanPassphrase.isEmpty()) {
            throw IllegalArgumentException("Informe a senha de recuperação para restaurar.")
        }

        val envelope = runCatching {
            json.decodeFromString<EncryptedChatBackupEnvelope>(rawBytes.toString(Charsets.UTF_8))
        }.getOrElse {
            throw IllegalArgumentException("Formato de arquivo de backup não reconhecido.")
        }

        if (envelope.format != EncryptedChatBackupEnvelope.FORMAT_NAME || envelope.version != 1) {
            throw IllegalArgumentException("Versão do arquivo de backup não suportada.")
        }
        if (envelope.kdf != EncryptedChatBackupEnvelope.KDF_ALGORITHM ||
            envelope.iterations !in MIN_ITERATIONS..MAX_ITERATIONS
        ) {
            throw IllegalArgumentException("Parâmetros criptográficos do backup são inválidos.")
        }

        val decoder = Base64.getDecoder()
        val salt = runCatching { decoder.decode(envelope.saltB64) }.getOrNull()
            ?.takeIf { it.size == SALT_BYTES }
            ?: throw IllegalArgumentException("Salt do arquivo de backup inválido.")
        val iv = runCatching { decoder.decode(envelope.ivB64) }.getOrNull()
            ?.takeIf { it.size == IV_BYTES }
            ?: throw IllegalArgumentException("Vetor de inicialização inválido.")
        val ciphertext = runCatching { decoder.decode(envelope.ciphertextB64) }.getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException("Conteúdo cifrado do backup inválido.")

        val secretKey = deriveAesKey(cleanPassphrase, salt, envelope.iterations)
        val plainCompressed = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: AEADBadTagException) {
            throw IllegalArgumentException("Senha de recuperação incorreta ou backup corrompido.")
        } catch (_: Exception) {
            throw IllegalArgumentException("Não foi possível decifrar o backup informado.")
        }

        val decompressed = gunzip(plainCompressed)
        return runCatching {
            json.decodeFromString<ChatBackupPayload>(decompressed.toString(Charsets.UTF_8))
        }.getOrElse {
            throw IllegalArgumentException("Backup decifrado com estrutura incompatível.")
        }
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            val factory = SecretKeyFactory.getInstance(EncryptedChatBackupEnvelope.KDF_ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            SecretKeySpec(keyBytes, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(input) }
        return out.toByteArray()
    }

    private fun gunzip(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPInputStream(ByteArrayInputStream(input)).use { gz ->
            val buf = ByteArray(8192)
            var total = 0
            while (true) {
                val n = gz.read(buf)
                if (n < 0) break
                total += n
                if (total > MAX_DECOMPRESSED_BYTES) {
                    throw IllegalArgumentException("Backup excede o limite máximo permitido.")
                }
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }
}
