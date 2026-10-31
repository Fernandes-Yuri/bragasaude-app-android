package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.FamilyMessageEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/** O fingerprint descreve o payload de uma consulta bem-sucedida, mesmo sem novidades. */
class ChatCycleSnapshotTest {
    private lateinit var apiClient: BragaApiClient
    private lateinit var repository: FamilyBridgeRepository

    @Before fun setUp() {
        apiClient = mockk(relaxed = true)
        val auth = mockk<FirebaseAuth>(relaxed = true)
        every { auth.currentUser?.uid } returns "p1"
        repository = FamilyBridgeRepository(
            familyDao = mockk(relaxed = true),
            vitalSignDao = mockk(relaxed = true),
            profileDao = mockk(relaxed = true),
            dailyMetricsDao = mockk(relaxed = true),
            socialFeedDao = mockk(relaxed = true),
            apiClient = apiClient,
            auth = auth,
            syncScheduler = mockk(relaxed = true),
            appContext = mockk(relaxed = true)
        )
    }

    private fun msg(id: String, sentAt: Long, deletedAt: Long? = null) =
        FamilyMessageEntity(
            id = id,
            patientUserId = "p1",
            senderName = "Familiar",
            messageText = "Olá",
            iconType = "LOVE",
            isRead = false,
            sentAt = sentAt,
            senderUserId = "p1",
            remoteId = null,
            pendingSync = false,
            expiresAt = System.currentTimeMillis() + 86_400_000L,
            deletedAt = deletedAt
        )

    @Test fun emptyPayloadStillProducesSuccessfulSnapshot() = runBlocking {
        coEvery { apiClient.getFamilyMessages("p1") } returns emptyList()
        val first = repository.chatCycleSnapshot("p1", "p1")
        val repeated = repository.chatCycleSnapshot("p1", "p1")
        assertEquals(first, repeated)
        coVerify(exactly=2) { apiClient.getFamilyMessages("p1") }
    }

    @Test fun samePayload_producesSameFingerprint() = runBlocking {
        coEvery { apiClient.getFamilyMessages("p1") } returns listOf(msg("m1", 1000L), msg("m2", 2000L))
        val snapshotA = repository.chatCycleSnapshot("p1", "p1")
        val snapshotB = repository.chatCycleSnapshot("p1", "p1")
        assertEquals("Payload idêntico precisa ter o mesmo fingerprint", snapshotA.messagesFingerprint, snapshotB.messagesFingerprint)
    }

    @Test fun newMessage_changesFingerprint() = runBlocking {
        coEvery { apiClient.getFamilyMessages("p1") } returnsMany listOf(
            listOf(msg("m1", 1000L)),
            listOf(msg("m1", 1000L), msg("m2", 2000L))
        )
        val before = repository.chatCycleSnapshot("p1", "p1")
        val after = repository.chatCycleSnapshot("p1", "p1")
        assertNotEquals("Mensagem nova precisa mudar o fingerprint", before.messagesFingerprint, after.messagesFingerprint)
    }

    @Test fun deletedMessage_changesFingerprint() = runBlocking {
        coEvery { apiClient.getFamilyMessages("p1") } returnsMany listOf(
            listOf(msg("m1", 1000L)),
            listOf(msg("m1", 1000L, deletedAt = 1_700_000_000_000L))
        )
        val before = repository.chatCycleSnapshot("p1", "p1")
        val after = repository.chatCycleSnapshot("p1", "p1")
        assertNotEquals(before.messagesFingerprint, after.messagesFingerprint)
    }
}
