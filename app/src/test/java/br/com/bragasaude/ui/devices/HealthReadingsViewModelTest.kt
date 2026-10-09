package br.com.bragasaude.ui.devices

import br.com.bragasaude.data.local.*
import br.com.bragasaude.domain.HealthReadingInput
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HealthReadingsViewModelTest {
    @Test fun voiceDraftIsSavedOnlyOnceWhenConfirmedAndKeepsItsOrigin() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dao = mockk<WearableReadingDao>(relaxed = true)
            val db = mockk<BragaDatabase>()
            val auth = mockk<FirebaseAuth>(relaxed = true)
            every { db.wearableReadingDao() } returns dao
            every { auth.currentUser?.uid } returns "user"
            every { dao.observeRecent("user", any()) } returns flowOf(emptyList())
            val vm = HealthReadingsViewModel(db, auth)
            coVerify(exactly = 0) { dao.upsert(any()) }
            var saved = 0
            vm.save(HealthReadingInput.OXYGEN, "98,5", true) { saved++ }
            vm.save(HealthReadingInput.OXYGEN, "98,5", true) { saved++ }
            advanceUntilIdle()
            assertEquals(1, saved)
            coVerify(exactly = 1) { dao.upsert(match { it.single().value == 98.5 && it.single().sourcePackage == HealthReadingInput.VOICE && it.single().userId == "user" }) }
            assertFalse(vm.saving.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun invalidReadingOrChangedAccountNeverWritesData() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dao = mockk<WearableReadingDao>(relaxed = true)
            val db = mockk<BragaDatabase>()
            val auth = mockk<FirebaseAuth>(relaxed = true)
            every { db.wearableReadingDao() } returns dao
            every { auth.currentUser?.uid } returns "user"
            every { dao.observeRecent("user", any()) } returns flowOf(emptyList())
            val vm = HealthReadingsViewModel(db, auth)
            vm.save(HealthReadingInput.OXYGEN, "198", false) { fail("Invalid reading was saved") }
            every { auth.currentUser?.uid } returns "other"
            vm.save(HealthReadingInput.HEART, "72", false) { fail("Changed account was saved") }
            advanceUntilIdle()
            coVerify(exactly = 0) { dao.upsert(any()) }
        } finally { Dispatchers.resetMain() }
    }
}
