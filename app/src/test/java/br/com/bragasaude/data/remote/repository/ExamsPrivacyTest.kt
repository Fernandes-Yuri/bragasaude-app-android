package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.local.security.ExamFileStore
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.RemoteExam
import br.com.bragasaude.data.remote.model.RemoteExamItem
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.domain.ExamCloudState
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Date

class ExamsPrivacyTest {
    private val api = mockk<BragaApiClient>(relaxed = true)
    private val dao = mockk<ExamDao>(relaxed = true)
    private val itemsDao = mockk<ExamItemDao>(relaxed = true)
    private val references = mockk<ClinicalReferenceDao>(relaxed = true)
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val files = mockk<ExamFileStore>(relaxed = true)
    private val auth = mockk<FirebaseAuth>()
    private val user = mockk<FirebaseUser>()
    private lateinit var repo: ExamsRepository
    private var stored: ExamEntity? = null

    @Before fun setup() {
        every { auth.currentUser } returns user
        every { user.uid } returns "paciente"
        coEvery { dao.getLatestByExamId(any()) } answers { stored }
        coEvery { dao.insert(any()) } answers { stored = firstArg(); 1L }
        coEvery { dao.saveWithItems(any(), any()) } answers { stored = firstArg() }
        repo = ExamsRepository(api, dao, itemsDao, references, scheduler, files, auth)
    }

    @Test fun `paciente logado salva exame manual sem rede e sem fila`() = runTest {
        repo.saveExam(RemoteExam(id = "exame", userId = "paciente", title = "Glicemia", examDate = "2026-10-05"),
            listOf(RemoteExamItem(examId = "exame", userId = "paciente", itemKey = "glucose", itemName = "Glicose", valueNumeric = 98.0)))
        assertEquals(ExamCloudState.LOCAL_ONLY, stored!!.cloudState)
        assertFalse(stored!!.pendingSync)
        assertFalse(stored!!.cloudConsentAccepted)
        coVerify { dao.saveWithItems(any(), match { it.size == 1 && !it[0].pendingSync }) }
        coVerify(exactly = 0) { api.syncManualExam(any(), any(), any(), any(), any()) }
        verify(exactly = 0) { scheduler.scheduleSync() }
    }

    @Test fun `foto local conserva original sem upload`() = runTest {
        repo.saveExam(RemoteExam(id = "exame", userId = "paciente", title = "Foto", examDate = "2026-10-05", fileUrl = "/privado/exame.pdf.enc"))
        assertEquals("/privado/exame.pdf.enc", stored!!.localFilePath)
        assertNull(stored!!.fileUrl)
        coVerify(exactly = 0) { api.uploadExamContract(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun `fila antiga sem consentimento nao transmite`() = runTest {
        stored = ExamEntity(remoteId = "exame", userId = "paciente", title = "Antigo", examDate = Date(), pendingSync = true)
        repo.syncAuthorizedExam("exame")
        coVerify(exactly = 0) { api.supportsExamSnapshots() }
        coVerify(exactly = 0) { api.syncManualExam(any(), any(), any(), any(), any()) }
    }

    @Test fun `consentimento posterior cria fila sem apagar copia local`() = runTest {
        stored = ExamEntity(localId = 1, remoteId = "exame", userId = "paciente", title = "Foto", examDate = Date(), localFilePath = "/privado/exame.enc", cloudState = ExamCloudState.LOCAL_ONLY)
        repo.authorizeCloud("exame", "paciente")
        assertTrue(stored!!.cloudConsentAccepted)
        assertTrue(stored!!.pendingSync)
        assertEquals("/privado/exame.enc", stored!!.localFilePath)
        assertNotNull(stored!!.cloudConsentAt)
        verify { scheduler.scheduleSync() }
    }

    @Test fun `falha da nuvem conserva dados e pendencia`() = runTest {
        stored = ExamEntity(localId = 1, remoteId = "exame", userId = "paciente", title = "Manual", examDate = Date(), cloudConsentAccepted = true, cloudState = ExamCloudState.PENDING, pendingSync = true)
        coEvery { api.supportsExamSnapshots() } returns true
        coEvery { itemsDao.getByExamLocal("exame") } returns emptyList()
        coEvery { api.syncManualExam(any(), any(), any(), any(), any()) } returns false
        try { repo.syncAuthorizedExam("exame"); fail("Deveria manter erro pendente") } catch (_: IllegalStateException) { }
        assertEquals(ExamCloudState.ERROR, stored!!.cloudState)
        assertTrue(stored!!.pendingSync)
        assertEquals("Manual", stored!!.title)
    }

    @Test fun `worker nao envia dados de outra conta`() = runTest {
        stored = ExamEntity(remoteId = "exame", userId = "outra-conta", title = "Privado", examDate = Date(), cloudConsentAccepted = true, pendingSync = true)
        repo.syncAuthorizedExam("exame")
        coVerify(exactly = 0) { api.supportsExamSnapshots() }
    }

    @Test fun `exclusao de exame exclusivamente local dispensa rede`() = runTest {
        stored = ExamEntity(remoteId = "exame", userId = "paciente", title = "Local", examDate = Date(), cloudState = ExamCloudState.LOCAL_ONLY)
        assertTrue(repo.deleteExamAtomically("exame", "paciente"))
        coVerify { dao.deleteWithItems("exame") }
        coVerify(exactly = 0) { api.deleteExam(any()) }
    }
}
