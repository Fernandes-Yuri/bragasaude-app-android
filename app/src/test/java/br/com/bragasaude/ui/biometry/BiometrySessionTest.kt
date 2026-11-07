package br.com.bragasaude.ui.biometry

import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.BiometryEntity
import br.com.bragasaude.data.remote.repository.BiometryRepository
import br.com.bragasaude.domain.HealthEngine
import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BiometrySessionTest {
    @Test
    fun `a viewmodel created before login observes the new account and forgets the old account`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val auth = mockk<FirebaseAuth>(relaxed = true)
        val repository = mockk<BiometryRepository>()
        val listener = slot<FirebaseAuth.AuthStateListener>()
        every { auth.addAuthStateListener(capture(listener)) } just Runs
        every { auth.currentUser } returns null
        val first = MutableStateFlow(listOf(BiometryEntity(userId = "first", weight = 70f, height = 170f, imc = 24f)))
        val second = MutableStateFlow(listOf(BiometryEntity(userId = "second", weight = 80f, height = 180f, imc = 25f)))
        every { repository.getBiometry(BragaConstants.GUEST_UID) } returns MutableStateFlow(emptyList())
        every { repository.getBiometry("first") } returns first
        every { repository.getBiometry("second") } returns second
        val viewModel = BiometryViewModel(repository, mockk<HealthEngine>(), auth)
        try {
            runCurrent()
            assertNull(viewModel.latestBiometry.value)
            val user = mockk<FirebaseUser>()
            every { user.uid } returns "first"
            every { auth.currentUser } returns user
            listener.captured.onAuthStateChanged(auth)
            runCurrent()
            assertEquals("first", viewModel.latestBiometry.value?.userId)
            every { user.uid } returns "second"
            listener.captured.onAuthStateChanged(auth)
            runCurrent()
            first.value = listOf(BiometryEntity(userId = "first", weight = 90f, height = 170f, imc = 31f))
            runCurrent()
            assertEquals("second", viewModel.latestBiometry.value?.userId)
            every { auth.currentUser } returns null
            listener.captured.onAuthStateChanged(auth)
            runCurrent()
            assertNull(viewModel.latestBiometry.value)
        } finally {
            viewModel.viewModelScope.cancel()
            runCurrent()
            verify { auth.removeAuthStateListener(listener.captured) }
            Dispatchers.resetMain()
        }
    }
}
