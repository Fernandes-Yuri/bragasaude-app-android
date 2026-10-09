package br.com.bragasaude.data.remote.sync

import android.content.Context
import androidx.work.WorkerParameters
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.VitalSignDao
import br.com.bragasaude.ui.util.NotificationHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class HydrationReminderWorkerTest {

    private val context = mockk<Context>(relaxed = true)
    private val workerParams = mockk<WorkerParameters>(relaxed = true)
    private val vitalSignDao = mockk<VitalSignDao>(relaxed = true)
    private val profileDao = mockk<ProfileDao>()
    private val auth = mockk<FirebaseAuth>()
    private val firebaseUser = mockk<FirebaseUser>()

    private lateinit var worker: HydrationReminderWorker

    @Before
    fun setup() {
        mockkObject(NotificationHelper)
        every { auth.currentUser } returns firebaseUser
        every { firebaseUser.uid } returns "test_user_id"
        every { NotificationHelper.cancelHydrationReminders(any()) } just Runs
        every { NotificationHelper.sendHydrationNotification(any(), any(), any()) } just Runs

        worker = HydrationReminderWorker(
            appContext = context,
            workerParams = workerParams,
            vitalSignDao = vitalSignDao,
            profileDao = profileDao,
            auth = auth
        )
    }

    @Test
    fun `viewer only caregiver cancels hydration reminders`() = runBlocking {
        val viewerOnlyProfile = ProfileEntity(
            userId = "test_user_id",
            fullName = "Cuidador Exclusivo",
            userRole = "CAREGIVER",
            caregiverMode = "VIEWER_ONLY"
        )
        coEvery { profileDao.getProfileOneShot("test_user_id") } returns viewerOnlyProfile

        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        verify(exactly = 1) { NotificationHelper.cancelHydrationReminders(context) }
    }

    @Test
    fun `hybrid caregiver does not cancel hydration reminders`() = runBlocking {
        val hybridProfile = ProfileEntity(
            userId = "test_user_id",
            fullName = "Cuidador Hibrido",
            userRole = "CAREGIVER",
            caregiverMode = "HYBRID",
            notificationsEnabled = false
        )
        coEvery { profileDao.getProfileOneShot("test_user_id") } returns hybridProfile

        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        verify(exactly = 0) { NotificationHelper.cancelHydrationReminders(context) }
    }

    @Test
    fun `patient does not cancel hydration reminders`() = runBlocking {
        val patientProfile = ProfileEntity(
            userId = "test_user_id",
            fullName = "Paciente Autocuidado",
            userRole = "PATIENT",
            caregiverMode = null,
            notificationsEnabled = false
        )
        coEvery { profileDao.getProfileOneShot("test_user_id") } returns patientProfile

        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        verify(exactly = 0) { NotificationHelper.cancelHydrationReminders(context) }
    }
}
