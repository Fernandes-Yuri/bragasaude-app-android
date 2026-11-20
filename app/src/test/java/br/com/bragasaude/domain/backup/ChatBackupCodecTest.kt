package br.com.bragasaude.domain.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatBackupCodecTest {

    @Test
    fun `encrypt and decrypt round-trip preserves family, orb and multi-clinic closed B2B threads`() {
        val payload = ChatBackupPayload(
            schemaVersion = 1,
            exportedAtMillis = 1_763_600_000_000L,
            ownerUserId = "uid-original",
            familyMessages = listOf(
                BackupFamilyMessage(
                    id = "fam-1",
                    patientUserId = "patient-1",
                    senderName = "Maria",
                    messageText = "Bom dia, tudo bem?",
                    iconType = "LOVE",
                    isRead = true,
                    sentAt = 1_763_500_000_000L,
                    senderUserId = "caregiver-1",
                    remoteId = "fam-1",
                    expiresAt = 1_763_586_400_000L
                )
            ),
            orbConversations = listOf(
                BackupOrbConversation(
                    id = "orb-22",
                    userId = "uid-original",
                    title = "Rotina de água",
                    updatedAt = 1_763_590_000_000L,
                    messagesJson = "[{\"role\":\"user\",\"text\":\"Oi\"}]"
                )
            ),
            b2bDirectMessages = listOf(
                BackupB2BDirectMessage(
                    programId = "prog-cardio",
                    programName = "Clinica Cardio",
                    primaryColor = "#1A6B5C",
                    patientUserId = "uid-original",
                    messageId = "dm-1",
                    senderUserId = "owner-a",
                    senderRole = "PARTNER",
                    body = "Agendamento confirmado",
                    createdAt = "2026-11-10T10:00:00Z",
                    createdAtMillis = 1_762_768_800_000L,
                    isClosedProgram = false
                ),
                BackupB2BDirectMessage(
                    programId = "prog-encerrado",
                    programName = "Nutricao Antiga",
                    primaryColor = "#0F4C81",
                    patientUserId = "uid-original",
                    messageId = "dm-2",
                    senderUserId = "owner-b",
                    senderRole = "PARTNER",
                    body = "Historico encerrado em modo leitura",
                    createdAt = "2026-11-01T09:00:00Z",
                    createdAtMillis = 1_761_987_600_000L,
                    isClosedProgram = true
                )
            )
        )

        val encrypted = ChatBackupCodec.encrypt(payload, "MinhaSenhaForte!2026", iterations = 15_000)
        val rawEnvelope = encrypted.toString(Charsets.UTF_8)
        assertTrue(rawEnvelope.contains("\"format\":\"BragaChatBackup\""))
        assertTrue(!rawEnvelope.contains("Agendamento confirmado"))
        assertTrue(!rawEnvelope.contains("Bom dia, tudo bem?"))

        val decrypted = ChatBackupCodec.decrypt(encrypted, "MinhaSenhaForte!2026")
        assertEquals(payload, decrypted)
        assertEquals(2, decrypted.b2bDirectMessages.size)
        assertEquals("prog-cardio", decrypted.b2bDirectMessages[0].programId)
        assertTrue(decrypted.b2bDirectMessages[1].isClosedProgram)
        assertNotEquals("wrong-pass", "MinhaSenhaForte!2026")
    }

    @Test
    fun `decrypt rejects wrong passphrase or tampered bytes cleanly`() {
        val payload = ChatBackupPayload(
            exportedAtMillis = 1_763_600_000_000L,
            ownerUserId = "uid-1",
            orbConversations = listOf(
                BackupOrbConversation("c1", "uid-1", "Duvida", 1_763_600_000_000L, "[]")
            )
        )
        val encrypted = ChatBackupCodec.encrypt(payload, "SenhaValida99", iterations = 15_000)

        val wrongErr = runCatching {
            ChatBackupCodec.decrypt(encrypted, "SenhaIncorreta99")
        }.exceptionOrNull()
        assertNotNull(wrongErr)

        val tampered = encrypted.copyOf()
        tampered[tampered.size - 15] = (tampered[tampered.size - 15].toInt() xor 0x0F).toByte()
        val tamperErr = runCatching {
            ChatBackupCodec.decrypt(tampered, "SenhaValida99")
        }.exceptionOrNull()
        assertNotNull(tamperErr)
    }

    @Test
    fun `validatePassphrase enforces minimum length`() {
        assertNotNull(ChatBackupCodec.validatePassphrase("1234567"))
        assertNotNull(ChatBackupCodec.validatePassphrase("   abc   "))
        assertNull(ChatBackupCodec.validatePassphrase("segredo8"))
    }
}
