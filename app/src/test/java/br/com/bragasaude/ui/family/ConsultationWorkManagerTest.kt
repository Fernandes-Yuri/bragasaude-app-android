package br.com.bragasaude.ui.family

import android.content.Context
import androidx.work.Data
import androidx.work.WorkerParameters
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import br.com.bragasaude.data.remote.sync.ConsultationReminderWorker
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.ui.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.util.Date

class ConsultationWorkManagerTest {
    private val dao = mockk<FamilyDao>(relaxed = true)
    private val api = mockk<BragaApiClient>(relaxed = true)
    private val auth = mockk<FirebaseAuth>(relaxed = true)
    private val context = mockk<Context>(relaxed = true)
    private val scheduler = mockk<SyncScheduler>(relaxed = true)
    private val profile = mockk<ProfileDao>(relaxed = true)
    private lateinit var repository: FamilyBridgeRepository
    private var saved: ConsultationEntity? = null

    @Before fun setup() {
        mockkObject(NotificationHelper)
        every { NotificationHelper.scheduleConsultationReminders(any(), any(), any(), any(), any(), any()) } just Runs
        every { NotificationHelper.cancelConsultationReminders(any(), any(), any()) } just Runs
        every { NotificationHelper.sendConsultationReminderNotification(any(), any(), any(), any(), any(), any(), any()) } just Runs
        every { auth.currentUser?.uid } returns "caregiver"
        coEvery { dao.insertConsultation(any()) } coAnswers { saved = firstArg() }
        coEvery { dao.getConsultationById(any()) } coAnswers { saved }
        coEvery { dao.getPendingConsultations() } coAnswers { listOfNotNull(saved).filter { it.pendingSync } }
        repository = FamilyBridgeRepository(dao, mockk(relaxed = true), profile, mockk(relaxed = true),
            mockk(relaxed = true), api, auth, scheduler, context)
    }
    @After fun cleanup() { unmockkObject(NotificationHelper) }

    @Test fun failedCreationKeepsStableIdAndPendingCommandForPatient() = runBlocking {
        coEvery { api.createConsultationRecord(any()) } returns null
        val c = repository.createConsultation("patient", "Cardiologia", System.currentTimeMillis()+172800000,
            "caregiver", "Ana", "Filha")!!
        assertEquals("patient", c.userId)
        assertEquals("caregiver", c.createdBy)
        assertTrue(c.pendingSync)
        assertEquals("CREATE", c.pendingAction)
        coVerify { api.createConsultationRecord(match { it.id == c.id && it.userId == "patient" }) }
        verify { scheduler.scheduleSync(any()) }
    }

    @Test fun remoteConfirmationUsesSameIdAndCanonicalStatus() = runBlocking {
        coEvery { api.createConsultationRecord(any()) } coAnswers {
            firstArg<ConsultationEntity>().copy(pendingSync = false, pendingAction = null, remoteConfirmed = true)
        }
        repository.createConsultation("patient", "Consulta", System.currentTimeMillis()+172800000,
            "caregiver", "Ana", "Filha")
        coVerify { dao.acknowledgeConsultation(any(), match { it.id == saved!!.id && it.remoteConfirmed }) }
    }

    private fun workerInput(owner: String, at: Long) = Data.Builder().putString("ownerId", owner)
        .putString("patientId", "patient").putString("consultationId", "id")
        .putLong("scheduledAt", at).putString("reminderTag", "1h").build()

    private fun worker(data: Data): ConsultationReminderWorker {
        val parameters = mockk<WorkerParameters>(relaxed = true)
        every { parameters.inputData } returns data
        return ConsultationReminderWorker(context, parameters, profile, dao, auth)
    }

    @Test fun reminderDoesNotNotifyAfterAccountChanges() = runBlocking {
        worker(workerInput("previous-account", 100)).doWork()
        verify(exactly = 0) { NotificationHelper.sendConsultationReminderNotification(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun cancelledConsultationDoesNotNotify() = runBlocking {
        saved = ConsultationEntity("id", "patient", "caregiver", "Ana", "Filha", "Consulta", scheduledDate = Date(System.currentTimeMillis()+3600000), status = "CANCELLED")
        worker(workerInput("caregiver", saved!!.scheduledDate.time)).doWork()
        verify(exactly = 0) { NotificationHelper.sendConsultationReminderNotification(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun reminderUsesCurrentConsultationAndCarriesNavigationIdentifiers() = runBlocking {
        saved = ConsultationEntity("id", "patient", "caregiver", "Ana", "Filha", "Cardiologia", scheduledDate = Date(System.currentTimeMillis()+3600000), status = "ACCEPTED")
        coEvery { dao.existsActiveLink("patient", "caregiver") } returns mockk(relaxed = true)
        worker(workerInput("caregiver", saved!!.scheduledDate.time)).doWork()
        verify { NotificationHelper.sendConsultationReminderNotification(context, "Cardiologia", any(), any(), "1h", "id", "patient") }
    }
}
