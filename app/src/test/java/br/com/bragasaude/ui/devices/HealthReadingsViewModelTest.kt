package br.com.bragasaude.ui.devices

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.repository.VitalsRepository
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.domain.HealthReadingInput
import br.com.bragasaude.domain.HealthReadingSaveResult
import br.com.bragasaude.domain.HealthReadingSaveStatus
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthReadingsViewModelTest {
    @Test fun voiceDraftIsSavedOnlyOnceIntoCanonicalVitalsAndKeepsItsOrigin() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dao = mockk<WearableReadingDao>(relaxed = true)
            val db = mockk<BragaDatabase>()
            val auth = mockk<FirebaseAuth>(relaxed = true)
            val api = mockk<BragaApiClient>()
            val syncScheduler = mockk<SyncScheduler>(relaxed = true)
            val vitalDao = mockk<VitalSignDao>(relaxed = true)
            every { db.wearableReadingDao() } returns dao
            every { db.vitalSignDao() } returns vitalDao
            every { vitalDao.getAll("user") } returns flowOf(emptyList())
            coEvery { vitalDao.migrateLegacyManualReadings("user") } returns emptyList()
            coEvery { vitalDao.getPendingSync() } returns emptyList()
            val captured = slot<VitalSignEntity>()
            coEvery { vitalDao.upsertManualReading(capture(captured)) } answers { captured.captured.copy(localId = 10L) }
            coEvery { api.syncVitalSign(any()) } answers { firstArg<VitalSignEntity>().remoteId }
            every { auth.currentUser?.uid } returns "user"
            every { dao.observeRecent("user", any()) } returns flowOf(emptyList())
            val repo = VitalsRepository(
                apiClient = api,
                vitalSignDao = vitalDao,
                profileDao = mockk(relaxed = true),
                familyDao = mockk { every { getActiveBindingsForPatient(any()) } returns flowOf(emptyList()) },
                vitalAlertLogDao = mockk(relaxed = true),
                syncScheduler = syncScheduler,
                appContext = mockk(relaxed = true)
            )
            val vm = HealthReadingsViewModel(db, auth, repo)
            var saved = 0
            val results = mutableListOf<HealthReadingSaveResult>()
            vm.saveWithResult(HealthReadingInput.OXYGEN, "98,5", true) { saved++; results += it }
            vm.saveWithResult(HealthReadingInput.OXYGEN, "98,5", true) { saved++; results += it }
            advanceUntilIdle()
            assertEquals(1, saved)
            assertEquals(HealthReadingSaveStatus.SYNCED, results.single().status)
            assertEquals(98.5, captured.captured.oxygenSaturation!!, 0.001)
            assertEquals(HealthReadingInput.VOICE, captured.captured.confirmedVia)
            assertEquals("user", captured.captured.userId)
            assertTrue(captured.captured.pendingSync)
            coVerify(exactly = 1) { vitalDao.acknowledgeSync(match { it.localId == 10L && it.oxygenSaturation == 98.5 }, captured.captured.remoteId!!) }
            assertFalse(vm.saving.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun offlineSaveMarksPendingAndSchedulesBackgroundRetry() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dao = mockk<WearableReadingDao>(relaxed = true)
            val db = mockk<BragaDatabase>()
            val auth = mockk<FirebaseAuth>(relaxed = true)
            val api = mockk<BragaApiClient>()
            val syncScheduler = mockk<SyncScheduler>(relaxed = true)
            val vitalDao = mockk<VitalSignDao>(relaxed = true)
            every { db.wearableReadingDao() } returns dao
            every { db.vitalSignDao() } returns vitalDao
            every { vitalDao.getAll("user") } returns flowOf(emptyList())
            coEvery { vitalDao.migrateLegacyManualReadings("user") } returns emptyList()
            coEvery { vitalDao.getPendingSync() } returns emptyList()
            coEvery { vitalDao.upsertManualReading(any()) } answers { firstArg<VitalSignEntity>().copy(localId = 11L) }
            coEvery { api.syncVitalSign(any()) } returns null
            every { auth.currentUser?.uid } returns "user"
            every { dao.observeRecent("user", any()) } returns flowOf(emptyList())
            val repo = VitalsRepository(
                apiClient = api,
                vitalSignDao = vitalDao,
                profileDao = mockk(relaxed = true),
                familyDao = mockk { every { getActiveBindingsForPatient(any()) } returns flowOf(emptyList()) },
                vitalAlertLogDao = mockk(relaxed = true),
                syncScheduler = syncScheduler,
                appContext = mockk(relaxed = true)
            )
            val vm = HealthReadingsViewModel(db, auth, repo)
            var result: HealthReadingSaveResult? = null
            vm.saveWithResult(HealthReadingInput.HEART, "103", false) { result = it }
            advanceUntilIdle()
            assertEquals(HealthReadingSaveStatus.SAVED_LOCALLY_PENDING_SYNC, result?.status)
            verify(atLeast = 1) { syncScheduler.scheduleSync() }
            coVerify(exactly = 0) { vitalDao.acknowledgeSync(any(), any()) }
        } finally { Dispatchers.resetMain() }
    }

    @Test fun invalidReadingOrChangedAccountNeverWritesDataOrFiresStaleCallback() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dao = mockk<WearableReadingDao>(relaxed = true)
            val db = mockk<BragaDatabase>()
            val auth = mockk<FirebaseAuth>(relaxed = true)
            val api = mockk<BragaApiClient>()
            val syncScheduler = mockk<SyncScheduler>(relaxed = true)
            val vitalDao = mockk<VitalSignDao>(relaxed = true)
            every { db.wearableReadingDao() } returns dao
            every { db.vitalSignDao() } returns vitalDao
            every { vitalDao.getAll("user") } returns flowOf(emptyList())
            coEvery { vitalDao.migrateLegacyManualReadings("user") } returns emptyList()
            coEvery { vitalDao.getPendingSync() } returns emptyList()
            every { auth.currentUser?.uid } returns "user"
            every { dao.observeRecent("user", any()) } returns flowOf(emptyList())
            val repo = VitalsRepository(
                apiClient = api,
                vitalSignDao = vitalDao,
                profileDao = mockk(relaxed = true),
                familyDao = mockk { every { getActiveBindingsForPatient(any()) } returns flowOf(emptyList()) },
                vitalAlertLogDao = mockk(relaxed = true),
                syncScheduler = syncScheduler,
                appContext = mockk(relaxed = true)
            )
            val vm = HealthReadingsViewModel(db, auth, repo)
            vm.save(HealthReadingInput.OXYGEN, "198", false) { fail("Invalid reading was saved") }
            vm.save(HealthReadingInput.HEART, "15", false) { fail("Out-of-bounds heart rate was saved") }
            every { auth.currentUser?.uid } returns "other"
            vm.save(HealthReadingInput.HEART, "72", false) { fail("Changed account was saved") }
            advanceUntilIdle()
            coVerify(exactly = 0) { vitalDao.upsertManualReading(any()) }

            // Switch account while network request is in flight: callback for account A must not fire in account B
            var currentUid = "user"
            every { auth.currentUser?.uid } answers { currentUid }
            val vmInFlight = HealthReadingsViewModel(db, auth, repo)
            val gate = CompletableDeferred<String?>()
            coEvery { vitalDao.upsertManualReading(any()) } answers { firstArg<VitalSignEntity>().copy(localId = 12L) }
            coEvery { api.syncVitalSign(any()) } coAnswers { gate.await() }
            var callbackFiredAfterSwitch = false
            vmInFlight.save(HealthReadingInput.HEART, "88", false) { callbackFiredAfterSwitch = true }
            testScheduler.runCurrent()
            currentUid = "account-b"
            gate.complete("id-from-a")
            advanceUntilIdle()
            assertFalse(callbackFiredAfterSwitch)
        } finally { Dispatchers.resetMain() }
    }
}
