package br.com.bragasaude.ui.hydration

import android.content.Context
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.remote.repository.VitalsRepository
import br.com.bragasaude.domain.HealthEngine
import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HydrationViewModelTest {
    @Test fun undoWaitsForPendingWaterSaveAndAnalysis() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vitals = mockk<VitalsRepository>()
        val profiles = mockk<ProfileRepository>()
        val engine = mockk<HealthEngine>()
        val auth = mockk<FirebaseAuth>()
        val context = mockk<Context>(relaxed = true)
        every { auth.currentUser } returns null
        every { profiles.getProfile(any()) } returns flowOf(null)
        every { vitals.getVitalSigns(any()) } returns flowOf(emptyList())
        val finishSaving = CompletableDeferred<Unit>()
        coEvery { vitals.saveVitalSigns(any()) } coAnswers { finishSaving.await() }
        coEvery { engine.analyzeVitals(any(), any(), any()) } returns HealthEngine.AnalysisResult(emptyList())
        coEvery { engine.removeLastHydration(any()) } just Runs
        val vm = HydrationViewModel(vitals, profiles, engine, auth, context)
        try {
            vm.addWater(500)
            runCurrent()
            vm.removeLastWater()
            runCurrent()
            coVerify(exactly = 0) { engine.removeLastHydration(any()) }
            finishSaving.complete(Unit)
            advanceUntilIdle()
            coVerifyOrder {
                vitals.saveVitalSigns(any())
                engine.analyzeVitals(BragaConstants.GUEST_UID, any(), false)
                engine.removeLastHydration(BragaConstants.GUEST_UID)
            }
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test fun undoUsesSerializedAchievementRollbackForGuest() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vitals = mockk<VitalsRepository>()
        val profiles = mockk<ProfileRepository>()
        val engine = mockk<HealthEngine>()
        val auth = mockk<FirebaseAuth>()
        val context = mockk<Context>(relaxed = true)
        every { auth.currentUser } returns null
        every { profiles.getProfile(any()) } returns flowOf(null)
        every { vitals.getVitalSigns(any()) } returns flowOf(emptyList())
        coEvery { engine.removeLastHydration(any()) } just Runs
        val vm = HydrationViewModel(vitals, profiles, engine, auth, context)
        try {
            vm.removeLastWater()
            advanceUntilIdle()
            coVerify(exactly = 1) { engine.removeLastHydration(BragaConstants.GUEST_UID) }
            coVerify(exactly = 0) { vitals.deleteLastHydration(any(), any()) }
        } finally {
            vm.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}
