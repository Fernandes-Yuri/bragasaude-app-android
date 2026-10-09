package br.com.bragasaude.ui.profile

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.repository.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.RemoteProfile
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Test
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileCaregivingTest {
    @Test fun `adding caregiving preserves identity biometrics and goals`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val base = ProfileEntity(userId = "same-user", fullName = "Maria", userRole = "PATIENT", basicProfileComplete = true, selfCareComplete = true, weight = 72.0, height = 165.0, stepGoal = 9000)
            val repository = mockk<ProfileRepository>(relaxed = true)
            val feedback = mockk<FeedbackRepository>(relaxed = true)
            val auth = mockk<FirebaseAuth>(relaxed = true)
            every { auth.currentUser?.uid } returns "same-user"
            every { repository.getProfile("same-user") } returns flowOf(base)
            every { feedback.getUserFeedbacks("same-user") } returns flowOf(emptyList())
            coEvery { repository.getProfileOneShotLocal("same-user") } returns base
            val saved = slot<RemoteProfile>()
            coEvery { repository.saveProfile(capture(saved)) } just Runs
            val vm = ProfileViewModel(repository, feedback, auth, mockk<BragaDatabase>(), mockk<BragaApiClient>(relaxed = true))
            var completed = false
            vm.saveCaregiverProfile("Maria", "VIEWER_ONLY") { completed = it }
            advanceUntilIdle()
            assertTrue(completed)
            assertEquals("same-user", saved.captured.id)
            assertEquals("CAREGIVER", saved.captured.userRole)
            assertEquals("HYBRID", saved.captured.caregiverMode)
            assertEquals(base.weight, saved.captured.weight)
            assertEquals(base.height, saved.captured.height)
            assertEquals(base.stepGoal, saved.captured.stepGoal)
            assertTrue(saved.captured.selfCareComplete)
        } finally { Dispatchers.resetMain() }
    }
}
