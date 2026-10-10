package br.com.bragasaude.ui.auth

import androidx.lifecycle.ViewModelStore
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.remote.model.ProfileLookup
import br.com.bragasaude.data.remote.repository.ProfileRepository
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthStartupDeduplicationTest {
    @Test fun `init e listener compartilham uma consulta e retry continua disponivel`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val auth = mockk<FirebaseAuth>(relaxed = true)
            val repository = mockk<ProfileRepository>(relaxed = true)
            val local = MutableStateFlow<ProfileEntity?>(null)
            val listener = slot<FirebaseAuth.AuthStateListener>()
            every { auth.currentUser?.uid } returns "u"
            every { auth.addAuthStateListener(capture(listener)) } answers {
                listener.captured.onAuthStateChanged(auth)
            }
            every { repository.getProfile("u") } returns local
            coEvery { repository.getProfileOneShotLocal("u") } returns null
            coEvery { repository.refreshProfileForLogin("u") } returns ProfileLookup.Unavailable()
            val vm = AuthViewModel(auth, mockk<BragaDatabase>(relaxed = true), repository,
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
                mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
            store.put("vm", vm)
            advanceUntilIdle()
            coVerify(exactly = 1) { repository.refreshProfileForLogin("u") }
            listener.captured.onAuthStateChanged(auth)
            advanceUntilIdle()
            coVerify(exactly = 1) { repository.refreshProfileForLogin("u") }
            vm.retryProfile(); advanceUntilIdle()
            coVerify(exactly = 2) { repository.refreshProfileForLogin("u") }
        } finally { store.clear(); Dispatchers.resetMain() }
    }
}
