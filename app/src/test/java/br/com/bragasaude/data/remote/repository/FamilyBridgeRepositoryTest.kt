package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.DailyMetricsDao
import br.com.bragasaude.data.local.FamilyBindingEntity
import br.com.bragasaude.data.local.FamilyDao
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.SocialFeedDao
import br.com.bragasaude.data.local.VitalSignDao
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.sync.SyncScheduler
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID
import br.com.bragasaude.util.BragaConstants

/**
 * Suíte de testes do Módulo 2/3/4 — Ponte Familiar & Modo Cuidador (PostgreSQL / BragaApiClient).
 *
 * Cobertura:
 *   - Formato: 8 caracteres alfanuméricos [A-Z0-9], sem prefixo
 *   - Unicidade: colisão força novo sorteio
 *   - Uso único: aceite ativa vínculo
 *   - Sync REST/PostgreSQL: remoto com fallback local e pendingSync
 *   - Purga LGPD de dados médicos ao revogar
 */
class FamilyBridgeRepositoryTest {

    private lateinit var familyDao: FamilyDao
    private lateinit var vitalSignDao: VitalSignDao
    private lateinit var profileDao: ProfileDao
    private lateinit var dailyMetricsDao: DailyMetricsDao
    private lateinit var socialFeedDao: SocialFeedDao
    private lateinit var apiClient: BragaApiClient
    private lateinit var auth: FirebaseAuth
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var appContext: android.content.Context

    private lateinit var repository: FamilyBridgeRepository

    private val sevenDaysMs = 7 * 24 * 60 * 60 * 1000L

    @Before
    fun setUp() {
        familyDao = mockk(relaxed = true)
        vitalSignDao = mockk(relaxed = true)
        profileDao = mockk(relaxed = true)
        dailyMetricsDao = mockk(relaxed = true)
        socialFeedDao = mockk(relaxed = true)
        apiClient = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        syncScheduler = mockk(relaxed = true)
        appContext = mockk(relaxed = true)
        every { auth.currentUser?.uid } returns "caregiver-1"

        repository = FamilyBridgeRepository(
            familyDao = familyDao,
            vitalSignDao = vitalSignDao,
            profileDao = profileDao,
            dailyMetricsDao = dailyMetricsDao,
            socialFeedDao = socialFeedDao,
            apiClient = apiClient,
            auth = auth,
            syncScheduler = syncScheduler,
            appContext = appContext
        )
    }

    @Test
    fun `patient download failure is not reported as fresh data`() = runBlocking {
        coEvery { apiClient.getProfile("patient-1") } returns null
        coEvery { apiClient.getVitalSigns("patient-1") } returns null
        coEvery { apiClient.getDailyMetrics("patient-1") } returns emptyList()
        assertTrue(repository.syncPatientDataForCaregiver("patient-1").isFailure)
        coVerify(exactly = 0) { vitalSignDao.insert(any()) }
    }

    @Test
    fun `cancellation of patient download is propagated`() = runBlocking {
        coEvery { apiClient.getProfile("patient-1") } throws kotlinx.coroutines.CancellationException("session changed")
        try {
            repository.syncPatientDataForCaregiver("patient-1")
            fail("O cancelamento deve interromper o worker e o polling")
        } catch (_: kotlinx.coroutines.CancellationException) {}
        coVerify(exactly = 0) { apiClient.getVitalSigns(any()) }
    }

    private fun pendingBinding(
        code: String = "A1B2C3D4",
        expiresAt: Long = System.currentTimeMillis() + sevenDaysMs
    ) = FamilyBindingEntity(
        id = "binding-id",
        patientUserId = "patient-1",
        caregiverUserId = "",
        caregiverName = "",
        caregiverRelation = "",
        connectionCode = code,
        status = "PENDING",
        createdAt = System.currentTimeMillis(),
        expiresAt = expiresAt
    )

    // ==================================================================
    // [1] FORMATO DO CÓDIGO
    // ==================================================================
    @Test
    fun `generateConnectionCode returns exactly 8 alphanumeric characters`() {
        repeat(200) {
            val code = repository.generateConnectionCode()
            assertEquals(8, code.length)
            assertTrue("Código inválido: $code", code.all { it in 'A'..'Z' || it in '0'..'9' })
            assertFalse("Código não deve conter prefixo/hífen", code.contains("-"))
        }
    }

    @Test
    fun `generateConnectionCode produces varied codes`() {
        val codes = (1..50).map { repository.generateConnectionCode() }.toSet()
        assertTrue("Esperava-se variedade, obtive ${codes.size}", codes.size >= 49)
    }

    // ==================================================================
    // [2] CREATE PENDING BINDING (local + REST remoto)
    // ==================================================================
    @Test
    fun `createPendingBinding saves a PENDING binding with remoteId when sync succeeds`() = runBlocking {
        coEvery { familyDao.getActiveOrPendingBindingByCode(any()) } returns null
        val expectedRemoteId = UUID.randomUUID().toString()
        coEvery { apiClient.syncFamilyBinding(any()) } returns expectedRemoteId

        val binding = repository.createPendingBinding("patient-1", "Familiar", "Filho")

        assertEquals("PENDING", binding.status)
        assertEquals(8, binding.connectionCode.length)
        assertEquals("patient-1", binding.patientUserId)
        assertEquals(expectedRemoteId, binding.remoteId)
        assertFalse("Após sync ok, pendingSync=false", binding.pendingSync)
        assertTrue("expiresAt futuro", binding.expiresAt > binding.createdAt)
        coVerify(exactly = 1) { familyDao.insertBinding(any()) }
    }

    @Test
    fun `createPendingBinding retries when generated code collides locally`() = runBlocking {
        coEvery { familyDao.getActiveOrPendingBindingByCode(any()) } returnsMany listOf(pendingBinding(), null)
        coEvery { apiClient.syncFamilyBinding(any()) } returns UUID.randomUUID().toString()

        repository.createPendingBinding("patient-1", "Familiar", "Filho")

        coVerify(atLeast = 2) { familyDao.getActiveOrPendingBindingByCode(any()) }
    }

    @Test
    fun `createPendingBinding rejects provided code already in use`() = runBlocking {
        coEvery { familyDao.getActiveOrPendingBindingByCode("CODIGO12") } returns pendingBinding("CODIGO12")

        val ex = runCatching {
            repository.createPendingBinding("patient-1", "Familiar", "Filho", connectionCode = "CODIGO12")
        }.exceptionOrNull()

        assertTrue(ex is IllegalArgumentException)
        coVerify(exactly = 0) { familyDao.insertBinding(any()) }
        coVerify(exactly = 0) { apiClient.syncFamilyBinding(any()) }
    }

    @Test
    fun `createPendingBinding marks pendingSync when remote fails`() = runBlocking {
        coEvery { familyDao.getActiveOrPendingBindingByCode(any()) } returns null
        coEvery { apiClient.syncFamilyBinding(any()) } throws RuntimeException("Sem conexão")

        val binding = repository.createPendingBinding("patient-1", "Familiar", "Filho")

        assertTrue("pendingSync deve estar true em falha remota", binding.pendingSync)
        coVerify(exactly = 1) { familyDao.insertBinding(binding) }
        verify(atLeast = 1) { syncScheduler.scheduleSync() }
    }

    // ==================================================================
    // [3] FIND AND ACCEPT (REST)
    // ==================================================================
    @Test
    fun `findAndAcceptBinding saves only server confirmed patient and identity`() = runBlocking {
        val confirmed = pendingBinding().copy(status = "ACTIVE", caregiverUserId = "caregiver-1",
            caregiverName = "Maria", caregiverRelation = "Filha", pendingSync = false)
        coEvery { apiClient.acceptFamilyInvitation("A1B2C3D4", "Maria", "Filha") } returns confirmed
        val result = repository.findAndAcceptBinding("A1B2C3D4", "caregiver-1", "Maria", "Filha")
        assertEquals(confirmed, result)
        assertEquals("patient-1", result.patientUserId)
        coVerify(exactly = 1) { familyDao.insertBinding(confirmed) }
        coVerify(exactly = 0) { apiClient.syncFamilyBinding(any()) }
    }

    @Test
    fun `findAndAcceptBinding never activates a local binding after network failure`() = runBlocking {
        coEvery { apiClient.acceptFamilyInvitation(any(), any(), any()) } throws java.io.IOException("Sem conexão")
        val error = runCatching { repository.findAndAcceptBinding("A1B2C3D4", "caregiver-1", "Maria", "Filha") }.exceptionOrNull()
        assertTrue(error is java.io.IOException)
        coVerify(exactly = 0) { familyDao.insertBinding(any()) }
    }

    @Test
    fun `findAndAcceptBinding rejects mismatched session`() = runBlocking {
        val error = runCatching { repository.findAndAcceptBinding("A1B2C3D4", "other-uid", "Maria", "Filha") }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        coVerify(exactly = 0) { apiClient.acceptFamilyInvitation(any(), any(), any()) }
    }

    // ==================================================================
    // [4] SYNC DE DOWNLOAD
    // ==================================================================
    @Test
    fun `syncBindingsForPatient downloads and upserts each binding locally`() = runBlocking {
        val remoteItem = FamilyBindingEntity(
            id = UUID.randomUUID().toString(),
            patientUserId = "patient-1",
            caregiverUserId = "caregiver-1",
            caregiverName = "Carlos",
            caregiverRelation = "Filho",
            connectionCode = "A1B2C3D4",
            status = "ACTIVE",
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + sevenDaysMs,
            remoteId = "remote-123",
            pendingSync = false
        )
        coEvery { apiClient.getFamilyBindings("patient-1") } returns listOf(remoteItem)

        repository.syncBindingsForPatient("patient-1")

        coVerify(exactly = 1) {
            familyDao.insertBinding(withArg {
                assertEquals("remote-123", it.remoteId)
                assertEquals("A1B2C3D4", it.connectionCode)
                assertEquals("caregiver-1", it.caregiverUserId)
                assertEquals("ACTIVE", it.status)
                assertFalse(it.pendingSync)
            })
        }
    }

    @Test
    fun `syncBindingsForPatient skips when userId is blank or guest`() = runBlocking {
        repository.syncBindingsForPatient("")
        repository.syncBindingsForPatient(BragaConstants.GUEST_UID)

        coVerify(exactly = 0) { apiClient.getFamilyBindings(any()) }
    }

    @Test
    fun `syncBindingsForCaregiver downloads and upserts each binding locally`() = runBlocking {
        val remoteItem = FamilyBindingEntity(
            id = UUID.randomUUID().toString(),
            patientUserId = "patient-1",
            caregiverUserId = "caregiver-1",
            caregiverName = "Carlos",
            caregiverRelation = "Filho",
            connectionCode = "A1B2C3D4",
            status = "ACTIVE",
            createdAt = System.currentTimeMillis(),
            expiresAt = System.currentTimeMillis() + sevenDaysMs,
            remoteId = "remote-123",
            pendingSync = false
        )
        coEvery { apiClient.getFamilyBindings("caregiver-1") } returns listOf(remoteItem)

        repository.syncBindingsForCaregiver("caregiver-1")

        coVerify(exactly = 1) {
            familyDao.insertBinding(withArg {
                assertEquals("patient-1", it.patientUserId)
                assertEquals("caregiver-1", it.caregiverUserId)
                assertEquals("remote-123", it.remoteId)
            })
        }
    }

    // ==================================================================
    // [5] POLLING DO CHAT (SPEC-16)
    // ==================================================================
    @Test
    fun emptyChatResponseIsSuccessfulAfterRefreshingKeys() = runBlocking {
        every { auth.currentUser?.uid } returns "patient"
        coEvery { apiClient.getFamilyMessages("patient") } returns emptyList()

        val first = repository.chatCycleSnapshot("patient", "patient")
        val repeated = repository.chatCycleSnapshot("patient", "patient")

        assertEquals(first, repeated)
        coVerify(exactly=2) { apiClient.getFamilyMessages("patient") }
        coVerify(exactly=2) { apiClient.getFamilyBindings("patient") }
    }

    @Test
    fun chatCyclePropagatesRealNetworkFailure() = runBlocking {
        every { auth.currentUser?.uid } returns "patient"
        val failure = java.io.IOException("offline")
        coEvery { apiClient.getFamilyMessages("patient") } throws failure

        assertSame(failure, runCatching { repository.chatCycleSnapshot("patient", "patient") }.exceptionOrNull())
        coVerify(exactly=0) { familyDao.purgeExpiredMessages(any()) }
    }

    @Test
    fun messageDownloadDoesNotSwallowCancellation() = runBlocking {
        every { auth.currentUser?.uid } returns "patient"
        val cancellation = kotlinx.coroutines.CancellationException("chat hidden")
        coEvery { apiClient.getFamilyMessages("patient") } throws cancellation

        assertSame(cancellation, runCatching { repository.syncFamilyMessages("patient") }.exceptionOrNull())
        coVerify(exactly=0) { familyDao.purgeExpiredMessages(any()) }
    }

    @Test
    fun chatCycleRejectsPreviousSessionBeforeDownloading() = runBlocking {
        assertTrue(runCatching { repository.chatCycleSnapshot("previous-user", "patient") }.isFailure)
        coVerify(exactly=0) { apiClient.getFamilyMessages(any()) }
    }

    // ==================================================================
    // [6] REVOKE E PURGA LGPD
    // ==================================================================
    @Test
    fun `revokeBinding persists a revocation and sends it to server`() = runBlocking {
        val local = pendingBinding().copy(id = "local-id")
        coEvery { familyDao.getBindingById("local-id") } returns local

        repository.revokeBinding("local-id")

        coVerify(exactly = 1) { familyDao.insertBinding(match { it.id == "local-id" && it.status == "REVOKED" && it.pendingSync }) }
        coVerify(exactly = 1) { apiClient.syncFamilyBinding(match { it.status == "REVOKED" }) }
    }

    @Test
    fun `revokeBinding purges patient medical data from cache for LGPD compliance when caregiver revokes`() = runBlocking {
        val local = pendingBinding().copy(id = "local-id", patientUserId = "patient-secret-uid")
        coEvery { familyDao.getBindingById("local-id") } returns local
        val mockUser = mockk<com.google.firebase.auth.FirebaseUser>()
        every { mockUser.uid } returns "caregiver-uid"
        every { auth.currentUser } returns mockUser

        repository.revokeBinding("local-id")

        // Valida purga LGPD no cache local
        coVerify(exactly = 1) { vitalSignDao.deleteByUserId("patient-secret-uid") }
        coVerify(exactly = 1) { dailyMetricsDao.deleteByUserId("patient-secret-uid") }
        coVerify(exactly = 1) { familyDao.deleteMessagesForPatient("patient-secret-uid") }
    }

    @Test
    fun deletedLocalMessageIsNotResurrectedByStaleDownload() = runBlocking {
        val message = br.com.bragasaude.data.local.FamilyMessageEntity(
            id="message", patientUserId="patient", senderName="", messageText="",
            iconType="LOVE", senderUserId="caregiver-1", deletedAt=1L
        )
        coEvery { familyDao.existsActiveLink("patient", "caregiver-1") } returns pendingBinding().copy(
            patientUserId = "patient", caregiverUserId = "caregiver-1", status = "ACTIVE")
        coEvery { familyDao.getMessageById("message") } returns message
        coEvery { apiClient.getFamilyMessages("patient") } returns listOf(message.copy(deletedAt=null, messageText="old"))
        assertTrue(repository.syncFamilyMessages("patient").isSuccess)
        coVerify(exactly=0) { familyDao.insertMessage(any()) }
    }

    @Test
    fun deletionWithoutRemoteAckStillPropagatesStableId() = runBlocking {
        val message = br.com.bragasaude.data.local.FamilyMessageEntity(
            id="message", patientUserId="patient", senderName="Me", messageText="Hi",
            iconType="LOVE", senderUserId="caregiver-1", pendingSync=true
        )
        coEvery { familyDao.getMessageById("message") } returns message
        repository.deleteMessage("message")
        coVerify { familyDao.softDeleteMessage("message", any()) }
        coVerify { apiClient.deleteFamilyMessage("message", "patient", message.sentAt) }
        coVerify(exactly=0) { familyDao.deleteMessage(any()) }
        verify { syncScheduler.scheduleSync(any()) }
    }

    @Test
    fun entityExpiryUsesOriginalSendTime() {
        val message=br.com.bragasaude.data.local.FamilyMessageEntity(
            id="message", patientUserId="patient", senderName="Me", messageText="Hi", iconType="LOVE", sentAt=123L)
        assertEquals(86400123L, message.expiresAt)
    }
    @Test fun blocksSendingWithoutActiveLinkBeforeWritingOrCallingServer() = runBlocking {
        coEvery { familyDao.existsActiveLink("patient", "caregiver-1") } returns null
        val result = runCatching { repository.createFamilyMessage("patient", "Nome", "Olá") }
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { familyDao.insertMessage(any()) }
        coVerify(exactly = 0) { apiClient.syncFamilyMessage(any()) }
    }

    @Test fun refusesMessagesFromAnotherGroupBeforeCaching() = runBlocking {
        coEvery { familyDao.existsActiveLink("patient", "caregiver-1") } returns pendingBinding().copy(status="ACTIVE")
        coEvery { apiClient.getFamilyMessages("patient") } returns listOf(
            br.com.bragasaude.data.local.FamilyMessageEntity(id="wrong", patientUserId="another-patient",
                senderName="Outro", messageText="Privado", iconType="CUSTOM"))
        assertTrue(repository.syncFamilyMessages("patient").isFailure)
        coVerify(exactly=0) { familyDao.insertMessage(any()) }
    }

    @Test fun patientReplySchedulesRetryWhenOffline() = runBlocking {
        every { auth.currentUser?.uid } returns "patient-1"
        coEvery { familyDao.getBindingById("binding-id") } returns pendingBinding().copy(
            caregiverUserId="caregiver-1", status="ACTIVE")
        coEvery { apiClient.syncFamilyMessage(any()) } returns null
        val result = repository.sendPatientReplyMessage("binding-id", " Olá ")
        assertTrue(result.pendingSync)
        assertEquals("Olá", result.messageText)
        verify { syncScheduler.scheduleSync(any()) }
    }

    @Test fun revokedBindingCannotSendThroughBidirectionalShortcut() = runBlocking {
        coEvery { familyDao.getBindingById("binding-id") } returns pendingBinding().copy(
            caregiverUserId="caregiver-1", status="REVOKED")
        assertTrue(runCatching { repository.sendMessageBidirectional("binding-id", "patient-1", "Nome", "Olá") }.isFailure)
        coVerify(exactly=0) { familyDao.insertMessage(any()) }
    }

    @Test fun accountSwitchDuringDownloadCannotRepopulatePreviousConversation() = runBlocking {
        coEvery { familyDao.existsActiveLink("patient", "caregiver-1") } returns pendingBinding().copy(status="ACTIVE")
        coEvery { apiClient.getFamilyMessages("patient") } coAnswers {
            every { auth.currentUser?.uid } returns "other-account"
            emptyList()
        }
        coEvery { familyDao.existsActiveLink("patient", "other-account") } returns null
        assertTrue(repository.syncFamilyMessages("patient").isFailure)
        coVerify(exactly=0) { familyDao.insertMessage(any()) }
    }

    @Test fun syncBindingsRethrowsCancellationException() {
        runBlocking {
            every { auth.currentUser?.uid } returns "patient-1"
            coEvery { apiClient.getFamilyBindings("patient-1") } throws kotlinx.coroutines.CancellationException("Job cancelled")
            var threw = false
            try {
                repository.syncBindingsForPatient("patient-1")
            } catch (_: kotlinx.coroutines.CancellationException) {
                threw = true
            }
            assertTrue("Deveria ter relançado CancellationException", threw)
        }
    }

    @Test fun e2eeEncryptsMessageBeforeWireAndDecryptsOnDownload() = runBlocking {
        val e2ee = br.com.bragasaude.data.local.security.FamilyE2eeManager()
        val patientPub = e2ee.getOrCreatePublicKeyBase64("patient-1")
        val caregiverPub = e2ee.getOrCreatePublicKeyBase64("caregiver-1")
        e2ee.registerBindingKeys("patient-1", "caregiver-1", patientPub, caregiverPub)

        val e2eeRepo = FamilyBridgeRepository(
            familyDao = familyDao,
            vitalSignDao = vitalSignDao,
            profileDao = profileDao,
            dailyMetricsDao = dailyMetricsDao,
            socialFeedDao = socialFeedDao,
            apiClient = apiClient,
            auth = auth,
            syncScheduler = syncScheduler,
            appContext = appContext,
            e2eeManager = e2ee
        )
        coEvery { familyDao.existsActiveLink("patient-1", "caregiver-1") } returns pendingBinding().copy(
            patientUserId = "patient-1",
            caregiverUserId = "caregiver-1",
            status = "ACTIVE"
        )
        val wireSlot = slot<br.com.bragasaude.data.local.FamilyMessageEntity>()
        coEvery { apiClient.syncFamilyMessage(capture(wireSlot)) } returns "remote-e2ee-id"

        val plaintext = "Tomei o medicamento da pressão às 09h"
        val created = e2eeRepo.createFamilyMessage("patient-1", "Maria", plaintext, "MED")

        assertEquals(plaintext, created.messageText)
        assertTrue(
            "Payload enviado para a nuvem deve ser um envelope E2EE v1",
            br.com.bragasaude.data.local.security.FamilyE2eeManager.isEncryptedEnvelope(wireSlot.captured.messageText)
        )
        assertFalse(
            "Payload de rede nunca deve conter texto plano",
            wireSlot.captured.messageText.contains("medicamento")
        )

        coEvery { familyDao.getMessageById(any()) } returns null
        coEvery { apiClient.getFamilyMessages("patient-1") } returns listOf(
            wireSlot.captured.copy(id = "remote-e2ee-id", remoteId = "remote-e2ee-id", pendingSync = false)
        )
        val insertedSlot = slot<br.com.bragasaude.data.local.FamilyMessageEntity>()
        coEvery { familyDao.insertMessage(capture(insertedSlot)) } just Runs

        val syncRes = e2eeRepo.syncFamilyMessages("patient-1")
        assertTrue(syncRes.isSuccess)
        assertEquals(plaintext, insertedSlot.captured.messageText)
    }

    @Test
    fun `syncFamilyMessages preserves local isRead status when remote message arrives with isRead false`() = runBlocking {
        val patientId = "patient-1"
        val msgId = "msg-preserve-read-1"
        coEvery { familyDao.existsActiveLink(patientId, "caregiver-1") } returns pendingBinding().copy(
            patientUserId = patientId, caregiverUserId = "caregiver-1", status = "ACTIVE"
        )
        val existingLocal = br.com.bragasaude.data.local.FamilyMessageEntity(
            id = msgId,
            remoteId = msgId,
            patientUserId = patientId,
            senderUserId = "caregiver-1",
            senderName = "Filho",
            messageText = "Recado importante",
            iconType = "LOVE",
            isRead = true,
            sentAt = System.currentTimeMillis() - 1000,
            expiresAt = System.currentTimeMillis() + 86400000,
            pendingSync = false
        )
        coEvery { familyDao.getMessageById(msgId) } returns existingLocal

        val remoteMessage = existingLocal.copy(isRead = false)
        coEvery { apiClient.getFamilyMessages(patientId) } returns listOf(remoteMessage)

        val insertedSlot = slot<br.com.bragasaude.data.local.FamilyMessageEntity>()
        coEvery { familyDao.insertMessage(capture(insertedSlot)) } just Runs

        val res = repository.syncFamilyMessages(patientId)
        assertTrue(res.isSuccess)
        assertTrue("O status isRead local (true) deve ser preservado contra a sobrescrita do servidor (false)", insertedSlot.captured.isRead)
    }

    @Test
    fun `receiveRealtimeMessage preserves local isRead status when event arrives with isRead false`() = runBlocking {
        val patientId = "patient-1"
        val msgId = "msg-rt-preserve-read-2"
        val existingLocal = br.com.bragasaude.data.local.FamilyMessageEntity(
            id = msgId,
            remoteId = msgId,
            patientUserId = patientId,
            senderUserId = "caregiver-1",
            senderName = "Filho",
            messageText = "Recado em tempo real",
            iconType = "LOVE",
            isRead = true,
            sentAt = System.currentTimeMillis() - 1000,
            expiresAt = System.currentTimeMillis() + 86400000,
            pendingSync = false
        )
        coEvery { familyDao.getMessageById(msgId) } returns existingLocal

        val incomingWsMessage = existingLocal.copy(isRead = false)
        val insertedSlot = slot<br.com.bragasaude.data.local.FamilyMessageEntity>()
        coEvery { familyDao.insertMessage(capture(insertedSlot)) } just Runs

        repository.receiveRealtimeMessage(incomingWsMessage)

        assertTrue("receiveRealtimeMessage deve manter isRead = true se a mensagem já foi lida no dispositivo", insertedSlot.captured.isRead)
    }


    @Test fun `read receipts exclude own unreadable and unseen messages`() = runBlocking {
        every { auth.currentUser?.uid } returns "patient"
        fun msg(id: String, sender: String = "caregiver", locked: Boolean = false) =
            br.com.bragasaude.data.local.FamilyMessageEntity(id, "patient", "Familiar", "Olá", "CUSTOM",
                senderUserId = sender, remoteId = id, awaitingDecryption = locked)
        coEvery { familyDao.getMessageById("visible") } returns msg("visible")
        coEvery { familyDao.getMessageById("locked") } returns msg("locked", locked = true)
        coEvery { familyDao.getMessageById("own") } returns msg("own", sender = "patient")
        coEvery { apiClient.markFamilyMessagesRead("patient", listOf("visible")) } returns true
        repository.markVisibleMessagesAsRead("patient", listOf("visible", "locked", "own"))
        coVerify(exactly=1) { apiClient.markFamilyMessagesRead("patient", listOf("visible")) }
        coVerify(exactly=1) { familyDao.markMessageAsRead("visible") }
        coVerify(exactly=0) { familyDao.markMessageAsRead("locked") }
        coVerify(exactly=0) { familyDao.markMessageAsRead("own") }
        coVerify(exactly=0) { familyDao.markAllMessagesAsRead(any()) }
    }

    @Test fun `own read receipt echo cannot mark sent messages read`() = runBlocking {
        every { auth.currentUser?.uid } returns "patient"
        repository.receiveRealtimeMessagesRead("patient", "patient", listOf("sent"))
        coVerify(exactly=0) { familyDao.markMessageAsRead(any()) }
    }

    @Test fun `peer receipt marks only IDs sent by this user in this room`() = runBlocking {
        every { auth.currentUser?.uid } returns "patient"
        val sent = br.com.bragasaude.data.local.FamilyMessageEntity("sent", "patient", "Paciente", "Olá", "CUSTOM", senderUserId = "patient")
        coEvery { familyDao.getMessageById("sent") } returns sent
        coEvery { familyDao.getMessageById("foreign") } returns sent.copy(id="foreign", patientUserId="other")
        repository.receiveRealtimeMessagesRead("patient", "caregiver", listOf("sent", "foreign"))
        coVerify(exactly=1) { familyDao.markMessageAsRead("sent") }
        coVerify(exactly=0) { familyDao.markMessageAsRead("foreign") }
    }

    @Test fun `decryption failure stays visible and preserves readable sender copy`() = runBlocking {
        every { auth.currentUser?.uid } returns "patient"
        val envelope = "{\"v\":1,\"iv\":\"AAAAAAAAAAAAAAAA\",\"ciphertext\":\"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA\"}"
        val incoming = br.com.bragasaude.data.local.FamilyMessageEntity("encrypted", "patient", "Familiar", envelope, "CUSTOM", senderUserId="caregiver")
        coEvery { familyDao.getMessageById("encrypted") } returns null
        val saved = slot<br.com.bragasaude.data.local.FamilyMessageEntity>()
        coEvery { familyDao.insertMessage(capture(saved)) } just Runs
        repository.receiveRealtimeMessage(incoming)
        assertTrue(saved.captured.awaitingDecryption)
        assertEquals("", saved.captured.messageText)
        coEvery { familyDao.getMessageById("encrypted") } returns incoming.copy(messageText="Cópia local legível")
        repository.receiveRealtimeMessage(incoming)
        assertEquals("Cópia local legível", saved.captured.messageText)
        assertFalse(saved.captured.awaitingDecryption)
    }
}
