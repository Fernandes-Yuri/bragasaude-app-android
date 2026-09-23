package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.FamilyMessageEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Regressão do polling resiliente da ponte familiar (task "Ponte familiar
 * resiliente"): o fingerprint do payload de mensagens é a base do skip de
 * ciclo idêntico — sem ele, a conversa parada gerava tráfego repetido a cada
 * 15s no gateway.
 */
class ChatCycleSnapshotTest {

    private fun msg(id: String, sentAt: Long, deletedAt: Long? = null) =
        FamilyMessageEntity(
            id = id,
            patientUserId = "p1",
            senderName = "Familiar",
            messageText = "Olá",
            iconType = "LOVE",
            isRead = false,
            sentAt = sentAt,
            senderUserId = "c1",
            remoteId = null,
            pendingSync = false,
            expiresAt = System.currentTimeMillis() + 86_400_000L,
            deletedAt = deletedAt
        )

    @Test fun nullFingerprint_meansCycleFailed() {
        // Ciclo em falha: snapshot sem fingerprint, a UI mostra aviso.
        val failed = ChatCycleSnapshot(messagesFingerprint = null)
        assertNull(failed.messagesFingerprint)
    }

    @Test fun samePayload_producesSameFingerprint() {
        val a = listOf(msg("m1", 1000L), msg("m2", 2000L))
        val b = listOf(msg("m1", 1000L), msg("m2", 2000L))
        val snapshotA = ChatCycleSnapshot(messagesFingerprint = fingerprintOf(a))
        val snapshotB = ChatCycleSnapshot(messagesFingerprint = fingerprintOf(b))
        assertEquals("Payload idêntico precisa ter o mesmo fingerprint", snapshotA.messagesFingerprint, snapshotB.messagesFingerprint)
    }

    @Test fun newMessage_changesFingerprint() {
        val before = listOf(msg("m1", 1000L))
        val after = listOf(msg("m1", 1000L), msg("m2", 2000L))
        val f1 = fingerprintOf(before)
        val f2 = fingerprintOf(after)
        assertNotEquals("Mensagem nova precisa mudar o fingerprint", f1, f2)
    }

    @Test fun deletedMessage_changesFingerprint() {
        // D47: tombstone também é variação de estado e precisa ser detectada.
        val before = listOf(msg("m1", 1000L))
        val after = listOf(msg("m1", 1000L, deletedAt = 1_700_000_000_000L))
        assertNotEquals(fingerprintOf(before), fingerprintOf(after))
    }

    private fun fingerprintOf(messages: List<FamilyMessageEntity>): String =
        messages.joinToString(separator = "|") { "${it.id}:${it.sentAt}:${it.deletedAt}" }
            .hashCode()
            .toString()
}
