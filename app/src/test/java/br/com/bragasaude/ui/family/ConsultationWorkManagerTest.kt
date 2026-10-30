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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class ConsultationWorkManagerTest {

    private lateinit var familyDao: FamilyDao
    private lateinit var vitalSignDao: VitalSignDao
    private lateinit var profileDao: ProfileDao
    private lateinit var dailyMetricsDao: DailyMetricsDao
    private lateinit var socialFeedDao: SocialFeedDao
    private lateinit var apiClient: BragaApiClient
    private lateinit var auth: FirebaseAuth
    private lateinit var syncScheduler: SyncScheduler
    private lateinit var appContext: Context

    private lateinit var repository: FamilyBridgeRepository

    @Before
    fun setUp() {
        mockkObject(NotificationHelper)
        every { NotificationHelper.scheduleConsultationReminders(any(), any(), any(), any()) } just Runs
        every { NotificationHelper.cancelConsultationReminders(any(), any()) } just Runs
        every { NotificationHelper.sendConsultationReminderNotification(any(), any(), any(), any(), any()) } just Runs

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

    @After
    fun tearDown() {
        unmockkObject(NotificationHelper)
    }

    @Test
    fun `createConsultation persists in Room and schedules 24h and 1h reminders`() = runBlocking {
        val scheduledDateMillis = System.currentTimeMillis() + 86400000L * 2
        val consultation = repository.createConsultation(
            userId = "patient-1",
            title = "Cardiologista",
            scheduledDate = scheduledDateMillis,
            caregiverUserId = "caregiver-1",
            caregiverName = "Ana",
            caregiverRelation = "Filha"
        )

        assertNotNull(consultation)
        assertEquals("Cardiologista", consultation?.title)
        assertEquals("patient-1", consultation?.userId)
        assertEquals("SUGGESTED", consultation?.status)

        // Verifica inserção no banco Room
        coVerify(exactly = 1) {
            familyDao.insertConsultation(match {
                it.userId == "patient-1" &&
                it.title == "Cardiologista" &&
                it.scheduledDate.time == scheduledDateMillis
            })
        }

        // Verifica que o agendador de lembretes duráveis (WorkManager) foi disparado
        verify(exactly = 1) {
            NotificationHelper.scheduleConsultationReminders(
                appContext,
                consultation!!.id,
                "Cardiologista",
                scheduledDateMillis
            )
        }
    }

    @Test
    fun `cancelConsultation marks as CANCELLED and cancels WorkManager reminders`() = runBlocking {
        val success = repository.cancelConsultation("consult-42", "caregiver-1")
        assertTrue(success)

        coVerify(exactly = 1) {
            familyDao.updateConsultationStatus("consult-42", "CANCELLED", any())
        }

        verify(exactly = 1) {
            NotificationHelper.cancelConsultationReminders(appContext, "consult-42")
        }
    }

    @Test
    fun `deleteConsultation removes from Room and cancels WorkManager reminders`() = runBlocking {
        val success = repository.deleteConsultation("consult-99")
        assertTrue(success)

        coVerify(exactly = 1) {
            familyDao.deleteConsultation("consult-99")
        }

        verify(exactly = 1) {
            NotificationHelper.cancelConsultationReminders(appContext, "consult-99")
        }
    }

    @Test
    fun `getActiveConsultationsForUser delegates to familyDao`() = runBlocking {
        val entity = ConsultationEntity(
            id = "c1",
            userId = "patient-1",
            caregiverUserId = "caregiver-1",
            caregiverName = "Ana",
            caregiverRelation = "Filha",
            title = "Consulta Geral",
            scheduledDate = Date(),
            status = "SUGGESTED"
        )
        every { familyDao.getActiveConsultationsForUser("patient-1") } returns flowOf(listOf(entity))

        val result = repository.getActiveConsultationsForUser("patient-1")
        var collected: List<ConsultationEntity>? = null
        result.collect { collected = it }

        assertNotNull(collected)
        assertEquals(1, collected?.size)
        assertEquals("Consulta Geral", collected?.first()?.title)
    }

    @Test
    fun `ConsultationReminderWorker sends 1h notification with specific tag text`() = runBlocking {
        val scheduledAt = System.currentTimeMillis() + 3600_000L
        val data = Data.Builder()
            .putString("title", "Geriatra")
            .putLong("scheduledAt", scheduledAt)
            .putString("reminderTag", "1h")
            .build()

        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.inputData } returns data

        every { auth.currentUser?.uid } returns "user-123"
        coEvery { profileDao.getProfileOneShot("user-123") } returns ProfileEntity(
            userId = "user-123",
            fullName = "Seu José",
            notificationsEnabled = true
        )

        val worker = ConsultationReminderWorker(appContext, workerParams, profileDao, auth)
        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        verify(exactly = 1) {
            NotificationHelper.sendConsultationReminderNotification(
                appContext,
                "Geriatra",
                any(),
                any(),
                "1h"
            )
        }
    }

    @Test
    fun `ConsultationReminderWorker respects notificationsEnabled false`() = runBlocking {
        val scheduledAt = System.currentTimeMillis() + 3600_000L
        val data = Data.Builder()
            .putString("title", "Geriatra")
            .putLong("scheduledAt", scheduledAt)
            .putString("reminderTag", "24h")
            .build()

        val workerParams = mockk<WorkerParameters>(relaxed = true)
        every { workerParams.inputData } returns data

        every { auth.currentUser?.uid } returns "user-silent"
        coEvery { profileDao.getProfileOneShot("user-silent") } returns ProfileEntity(
            userId = "user-silent",
            fullName = "Seu José",
            notificationsEnabled = false
        )

        val worker = ConsultationReminderWorker(appContext, workerParams, profileDao, auth)
        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        verify(exactly = 0) {
            NotificationHelper.sendConsultationReminderNotification(any(), any(), any(), any(), any())
        }
    }
}
