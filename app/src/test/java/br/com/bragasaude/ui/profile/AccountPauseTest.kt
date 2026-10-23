package br.com.bragasaude.ui.profile

import android.app.Application
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.local.smoking.SmokingSupportSession
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.repository.*
import br.com.bragasaude.data.util.MovementManager
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AccountPauseTest {
    @Test fun confirmedPauseSignsOutAndPreservesEncryptedLocalData() = runTest { pauseCase(true) }
    @Test fun failedPauseKeepsSessionAndLocalData() = runTest { pauseCase(false) }

    private suspend fun kotlinx.coroutines.test.TestScope.pauseCase(confirmed: Boolean) {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val auth = mockk<FirebaseAuth>(relaxed = true)
            every { auth.currentUser?.uid } returns "owner"
            val db = mockk<BragaDatabase>(relaxed = true)
            val api = mockk<BragaApiClient>()
            coEvery { api.deactivateAccount("owner") } returns confirmed
            val repo = mockk<ProfileRepository>(relaxed = true)
            every { repo.getProfile("owner") } returns flowOf(ProfileEntity(userId = "owner"))
            val alarms = mockk<MedicationRepository>(relaxed = true)
            val session = mockk<SmokingSupportSession>(relaxed = true)
            val context = RuntimeEnvironment.getApplication()
            val model = SettingsViewModel(repo, auth, db, mockk<MovementManager>(relaxed = true), alarms, api, session, context)
            var completed = false
            model.deactivateAccount { completed = true }
            advanceUntilIdle()
            model.accountBusy.first { !it }
            assertEquals(confirmed, completed)
            verify(exactly = 0) { db.clearAllTables() }
            verify(exactly = if (confirmed) 1 else 0) { auth.signOut() }
            verify(exactly = if (confirmed) 1 else 0) { session.closeForCleanup() }
            if (!confirmed) assertNotNull(model.accountError.value)
        } finally { Dispatchers.resetMain() }
    }
}
