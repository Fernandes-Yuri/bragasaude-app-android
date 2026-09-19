package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.DailyMetricsDao
import br.com.bragasaude.data.local.FamilyBindingEntity
import br.com.bragasaude.data.local.FamilyDao
import br.com.bragasaude.data.local.GroceryListDao
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
    private lateinit var groceryListDao: GroceryListDao
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
        groceryListDao = mockk(relaxed = true)
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
            groceryListDao = groceryListDao,
            socialFeedDao = socialFeedDao,
            apiClient = apiClient,
            auth = auth,
            syncScheduler = syncScheduler,
            appContext = appContext
        )
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
        repository.syncBindingsForPatient("00000000-0000-0000-0000-000000000000")

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
    // [5] REVOKE E PURGA LGPD
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
        coVerify(exactly = 1) { groceryListDao.clearGroceryList("patient-secret-uid") }
    }

    @Test
    fun deletedLocalMessageIsNotResurrectedByStaleDownload() = runBlocking {
        val message = br.com.bragasaude.data.local.FamilyMessageEntity(
            id="message", patientUserId="patient", senderName="", messageText="",
            iconType="LOVE", senderUserId="caregiver-1", deletedAt=1L
        )
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
}
