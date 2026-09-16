package br.com.bragasaude.ui.auth

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.model.*
import br.com.bragasaude.data.remote.repository.ProfileRepository
import com.google.firebase.auth.*
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.util.Date

@OptIn(ExperimentalCoroutinesApi::class)
class AuthProfileRecoveryTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: ProfileRepository
    private lateinit var auth: FirebaseAuth
    private lateinit var listener: FirebaseAuth.AuthStateListener
    private lateinit var vm: AuthViewModel
    private val local = MutableStateFlow<ProfileEntity?>(null)
    private val complete = ProfileEntity(userId = "owner", userRole = "PATIENT", consentAcceptedAt = Date(), basicProfileComplete = true, selfCareComplete = true)
    @Before fun setup() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        val user = mockk<FirebaseUser>()
        every { user.uid } returns "owner"
        every { auth.currentUser } returns user
        every { auth.addAuthStateListener(any()) } answers { listener = firstArg() }
        every { repository.getProfile(any()) } returns local
        coEvery { repository.getProfileOneShotLocal(any()) } answers { local.value }
        coEvery { repository.refreshProfileForLogin(any()) } returns ProfileLookup.Unavailable()
        vm = AuthViewModel(auth, mockk(relaxed = true), repository, mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
    }
    @After fun cleanup() {
        every { auth.currentUser } returns null
        listener.onAuthStateChanged(auth)
        Dispatchers.resetMain()
    }
    @Test fun nullThenCompletedCacheRecoversAfterRemoteFailure() = runTest(dispatcher) {
        runCurrent()
        assertNotNull(vm.profileError.value)
        assertNull(vm.isProfileComplete.value)
        local.value = complete
        runCurrent()
        assertTrue(vm.isProfileComplete.value == true)
        assertNull(vm.profileError.value)
    }
    @Test fun staleIncompleteCacheShowsRetry() = runTest(dispatcher) {
        local.value = complete.copy(basicProfileComplete = false, selfCareComplete = false)
        runCurrent()
        assertNull(vm.isProfileComplete.value)
        assertNotNull(vm.profileError.value)
    }
    @Test fun retryDownloadsRealFlagsIntoRoom() = runTest(dispatcher) {
        runCurrent()
        coEvery { repository.refreshProfileForLogin("owner") } coAnswers {
            local.value = complete
            ProfileLookup.Found(RemoteProfile(id = "owner", basicProfileComplete = true, selfCareComplete = true))
        }
        vm.retryProfile()
        runCurrent()
        assertEquals(true, vm.isProfileComplete.value)
        assertNull(vm.profileError.value)
    }
    @Test fun lateResponseFromOldAccountCannotSetSessionFlags() = runTest(dispatcher) {
        coEvery { repository.refreshProfileForLogin("owner") } coAnswers { delay(1000); ProfileLookup.NotFound }
        runCurrent()
        every { auth.currentUser } returns null
        listener.onAuthStateChanged(auth)
        advanceUntilIdle()
        assertNull(vm.isProfileComplete.value)
        assertNull(vm.hasAcceptedConsent.value)
    }
}
