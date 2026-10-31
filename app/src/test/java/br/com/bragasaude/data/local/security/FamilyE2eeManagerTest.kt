package br.com.bragasaude.data.local.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.GeneralSecurityException
import java.util.Base64
import javax.crypto.AEADBadTagException

/**
 * Suíte de testes unitários da Criptografia Ponta a Ponta (E2EE) do Chat Familiar (SPEC-13).
 *
 * Cobre:
 * - Geração de chaves assimétricas EC (NIST P-256) e acordo de chaves ECDH entre paciente e cuidador
 * - Derivação HKDF-SHA256 (RFC 5869) vinculada ao par (patient_id, caregiver_id)
 * - Cifragem e decifragem bidirecional AES-256-GCM com IV de 12 bytes e tag de 128 bits
 * - Rejeição de adulteração de ciphertext/IV e tentativa de decifragem com chave incorreta
 * - Retrocompatibilidade com mensagens legadas em texto plano (SPEC-13 §3)
 * - Purga LGPD de chaves públicas ao revogar vínculo
 */
class FamilyE2eeManagerTest {

    private lateinit var patientE2ee: FamilyE2eeManager
    private lateinit var caregiverE2ee: FamilyE2eeManager
    private lateinit var intruderE2ee: FamilyE2eeManager

    private val patientId = "patient-carlos-uid"
    private val caregiverId = "caregiver-maria-uid"
    private val intruderId = "intruder-mallory-uid"

    @Before
    fun setUp() {
        patientE2ee = FamilyE2eeManager(InMemoryE2eeKeyStoreProvider())
        caregiverE2ee = FamilyE2eeManager(InMemoryE2eeKeyStoreProvider())
        intruderE2ee = FamilyE2eeManager(InMemoryE2eeKeyStoreProvider())
    }

    @Test
    fun `ecdh key agreement and hkdf-sha256 derive identical 256-bit session key on both devices`() {
        val patientPubB64 = patientE2ee.getOrCreatePublicKeyBase64(patientId)
        val caregiverPubB64 = caregiverE2ee.getOrCreatePublicKeyBase64(caregiverId)

        assertTrue(patientPubB64.isNotBlank())
        assertTrue(caregiverPubB64.isNotBlank())
        assertNotEquals(patientPubB64, caregiverPubB64)

        val patientSecret = patientE2ee.computeSharedSecret(patientId, caregiverPubB64)
        val caregiverSecret = caregiverE2ee.computeSharedSecret(caregiverId, patientPubB64)
        assertArrayEquals("Segredo ECDH deve coincidir exatamente nos dois lados", patientSecret, caregiverSecret)

        val patientSessionKey = patientE2ee.deriveSessionKey(patientSecret, patientId, caregiverId)
        val caregiverSessionKey = caregiverE2ee.deriveSessionKey(caregiverSecret, patientId, caregiverId)

        assertEquals(32, patientSessionKey.encoded.size)
        assertArrayEquals(
            "Chaves simétricas de 256 bits derivadas via HKDF-SHA256 devem coincidir",
            patientSessionKey.encoded,
            caregiverSessionKey.encoded
        )

        val otherPairKey = patientE2ee.deriveSessionKey(patientSecret, patientId, "other-caregiver-uid")
        assertFalse(
            "Pares (patient_id, caregiver_id) distintos devem derivar chaves diferentes",
            patientSessionKey.encoded.contentEquals(otherPairKey.encoded)
        )
    }

    @Test
    fun `bidirectional encryption and decryption between patient and caregiver succeeds with zero plaintext leakage`() {
        val patientPubB64 = patientE2ee.getOrCreatePublicKeyBase64(patientId)
        val caregiverPubB64 = caregiverE2ee.getOrCreatePublicKeyBase64(caregiverId)

        patientE2ee.registerBindingKeys(patientId, caregiverId, patientPubB64, caregiverPubB64)
        caregiverE2ee.registerBindingKeys(patientId, caregiverId, patientPubB64, caregiverPubB64)

        val originalFromPatient = "Filha, já tomei a losartana das 08h e medi a pressão: 120x80."
        val encryptedByPatient = patientE2ee.encryptForPair(
            patientId = patientId,
            caregiverId = caregiverId,
            ownUserId = patientId,
            plaintext = originalFromPatient
        )

        assertTrue(FamilyE2eeManager.isEncryptedEnvelope(encryptedByPatient))
        assertFalse("Blob cifrado nunca deve expor o texto plano", encryptedByPatient.contains("losartana"))
        assertFalse("Blob cifrado nunca deve expor números sensíveis", encryptedByPatient.contains("120x80"))

        val envelope = FamilyE2eeManager.E2eeEnvelope.fromJsonStringOrNull(encryptedByPatient)
        assertNotNull(envelope)
        assertEquals(1, envelope!!.v)
        assertEquals(12, Base64.getDecoder().decode(envelope.iv).size)
        assertTrue(Base64.getDecoder().decode(envelope.ciphertext).size > 16)

        val decryptedByCaregiver = caregiverE2ee.decryptForPair(
            patientId = patientId,
            caregiverId = caregiverId,
            ownUserId = caregiverId,
            payload = encryptedByPatient
        )
        assertEquals(originalFromPatient, decryptedByCaregiver)

        val replyFromCaregiver = "Que ótimo, pai! Lembre de beber água antes do almoço."
        val encryptedByCaregiver = caregiverE2ee.encryptForPair(
            patientId = patientId,
            caregiverId = caregiverId,
            ownUserId = caregiverId,
            plaintext = replyFromCaregiver
        )
        val decryptedByPatient = patientE2ee.decryptForPair(
            patientId = patientId,
            caregiverId = caregiverId,
            ownUserId = patientId,
            payload = encryptedByCaregiver
        )
        assertEquals(replyFromCaregiver, decryptedByPatient)
    }

    @Test
    fun `encrypting identical plaintext twice produces distinct ivs and ciphertexts`() {
        val patientPubB64 = patientE2ee.getOrCreatePublicKeyBase64(patientId)
        val caregiverPubB64 = caregiverE2ee.getOrCreatePublicKeyBase64(caregiverId)
        val sessionKey = patientE2ee.deriveSessionKeyFromPeer(patientId, caregiverPubB64, patientId, caregiverId)
        val caregiverKey = caregiverE2ee.deriveSessionKeyFromPeer(caregiverId, patientPubB64, patientId, caregiverId)

        val msg = "Bom dia, família!"
        val firstBlob = patientE2ee.encryptMessage(msg, sessionKey)
        val secondBlob = patientE2ee.encryptMessage(msg, sessionKey)

        assertNotEquals("Cada cifragem deve usar IV aleatório de 12 bytes único", firstBlob, secondBlob)
        assertEquals(msg, caregiverE2ee.decryptMessage(firstBlob, caregiverKey))
        assertEquals(msg, caregiverE2ee.decryptMessage(secondBlob, caregiverKey))
    }

    @Test
    fun `decryption with wrong key or unauthorized third party fails with security exception`() {
        val patientPubB64 = patientE2ee.getOrCreatePublicKeyBase64(patientId)
        val caregiverPubB64 = caregiverE2ee.getOrCreatePublicKeyBase64(caregiverId)
        val intruderPubB64 = intruderE2ee.getOrCreatePublicKeyBase64(intruderId)

        val validSessionKey = patientE2ee.deriveSessionKeyFromPeer(patientId, caregiverPubB64, patientId, caregiverId)
        val intruderKey = intruderE2ee.deriveSessionKeyFromPeer(intruderId, patientPubB64, patientId, caregiverId)
        assertNotNull(intruderPubB64)

        val encrypted = patientE2ee.encryptMessage("Segredo familiar protegido", validSessionKey)

        val error = runCatching {
            intruderE2ee.decryptMessage(encrypted, intruderKey)
        }.exceptionOrNull()

        assertTrue("Decifragem com chave incorreta deve falhar com GeneralSecurityException", error is GeneralSecurityException)
        assertTrue("Deve falhar na verificação da tag GCM (AEADBadTagException)", error is AEADBadTagException)
    }

    @Test
    fun `tampered ciphertext or iv is rejected by aes-gcm 128-bit authentication tag`() {
        val patientPubB64 = patientE2ee.getOrCreatePublicKeyBase64(patientId)
        val caregiverPubB64 = caregiverE2ee.getOrCreatePublicKeyBase64(caregiverId)
        val sessionKey = patientE2ee.deriveSessionKeyFromPeer(patientId, caregiverPubB64, patientId, caregiverId)

        val validPayload = patientE2ee.encryptMessage("Dado íntegro", sessionKey)
        val envelope = FamilyE2eeManager.E2eeEnvelope.fromJsonStringOrNull(validPayload)!!

        // 1. Adulterar 1 bit no ciphertext
        val ctBytes = Base64.getDecoder().decode(envelope.ciphertext)
        ctBytes[0] = (ctBytes[0].toInt() xor 0x01).toByte()
        val tamperedCtPayload = envelope.copy(ciphertext = Base64.getEncoder().encodeToString(ctBytes)).toJsonString()

        val ctError = runCatching { caregiverE2ee.decryptMessage(tamperedCtPayload, sessionKey) }.exceptionOrNull()
        assertTrue(ctError is AEADBadTagException)

        // 2. Adulterar 1 bit no IV
        val ivBytes = Base64.getDecoder().decode(envelope.iv)
        ivBytes[0] = (ivBytes[0].toInt() xor 0x01).toByte()
        val tamperedIvPayload = envelope.copy(iv = Base64.getEncoder().encodeToString(ivBytes)).toJsonString()

        val ivError = runCatching { caregiverE2ee.decryptMessage(tamperedIvPayload, sessionKey) }.exceptionOrNull()
        assertTrue(ivError is AEADBadTagException)

        // 3. Versão de protocolo desconhecida
        val wrongVersionPayload = envelope.copy(v = 99).toJsonString()
        val versionError = runCatching { caregiverE2ee.decryptMessage(wrongVersionPayload, sessionKey) }.exceptionOrNull()
        assertTrue(versionError is GeneralSecurityException)
    }

    @Test
    fun `legacy plaintext messages remain readable with legacy notice while v1 messages decrypt`() {
        val patientPubB64 = patientE2ee.getOrCreatePublicKeyBase64(patientId)
        val caregiverPubB64 = caregiverE2ee.getOrCreatePublicKeyBase64(caregiverId)
        val sessionKey = patientE2ee.deriveSessionKeyFromPeer(patientId, caregiverPubB64, patientId, caregiverId)

        val legacyDecoded = caregiverE2ee.decryptOrFallbackLegacy(
            rawText = "Recado legado anterior à SPEC-13",
            bodyEncrypted = null,
            encryptionVersion = 0,
            sessionKey = sessionKey
        )
        assertEquals("Recado legado anterior à SPEC-13", legacyDecoded.text)
        assertFalse(legacyDecoded.isEncrypted)
        assertTrue(legacyDecoded.isLegacyPlaintext)
        assertEquals(FamilyE2eeManager.LEGACY_PLAINTEXT_NOTICE, legacyDecoded.legacyNotice)

        val encryptedBlob = patientE2ee.encryptMessage("Recado E2EE v1", sessionKey)
        val e2eeDecoded = caregiverE2ee.decryptOrFallbackLegacy(
            rawText = "",
            bodyEncrypted = encryptedBlob,
            encryptionVersion = 1,
            sessionKey = sessionKey
        )
        assertEquals("Recado E2EE v1", e2eeDecoded.text)
        assertTrue(e2eeDecoded.isEncrypted)
        assertFalse(e2eeDecoded.isLegacyPlaintext)
        assertNull(e2eeDecoded.legacyNotice)
    }

    @Test
    fun `clearKeysForPatient purges registered peer public keys on revocation`() {
        val patientPubB64 = patientE2ee.getOrCreatePublicKeyBase64(patientId)
        val caregiverPubB64 = caregiverE2ee.getOrCreatePublicKeyBase64(caregiverId)

        caregiverE2ee.registerBindingKeys(patientId, caregiverId, patientPubB64, caregiverPubB64)
        assertEquals(patientPubB64, caregiverE2ee.getRegisteredPeerPublicKey(patientId, caregiverId, caregiverId))

        caregiverE2ee.clearKeysForPatient(patientId)
        assertNull(caregiverE2ee.getRegisteredPeerPublicKey(patientId, caregiverId, caregiverId))
    }
}
