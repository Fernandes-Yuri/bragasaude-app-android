package br.com.bragasaude.data.remote.sync

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.mockk.mockk
import io.mockk.verify
import org.junit.Before
import org.junit.Test

class SyncSchedulerTest {

    private val workManager = mockk<WorkManager>(relaxed = true)
    private lateinit var syncScheduler: SyncScheduler

    @Before
    fun setUp() {
        syncScheduler = SyncScheduler(workManager)
    }

    @Test
    fun `scheduleSync enqueues unique work with KEEP policy by default`() {
        syncScheduler.scheduleSync()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                SyncScheduler.UNIQUE_ONE_TIME_SYNC_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `scheduleExpeditedSync enqueues unique work with REPLACE policy`() {
        syncScheduler.scheduleExpeditedSync()

        verify(exactly = 1) {
            workManager.enqueueUniqueWork(
                SyncScheduler.UNIQUE_ONE_TIME_SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                any<OneTimeWorkRequest>()
            )
        }
    }

    @Test
    fun `schedulePeriodicRecoverySync enqueues periodic work with KEEP policy`() {
        syncScheduler.schedulePeriodicRecoverySync()

        verify(exactly = 1) {
            workManager.enqueueUniquePeriodicWork(
                SyncScheduler.UNIQUE_PERIODIC_SYNC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                any<PeriodicWorkRequest>()
            )
        }
    }

    @Test
    fun `cancelSync cancels unique work`() {
        syncScheduler.cancelSync()

        verify(exactly = 1) {
            workManager.cancelUniqueWork(SyncScheduler.UNIQUE_ONE_TIME_SYNC_WORK_NAME)
        }
    }
}
