package br.com.bragasaude.ui.home

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.local.DailyMetricsDao
import br.com.bragasaude.data.remote.repository.MedicationRepository
import br.com.bragasaude.data.remote.repository.MilestonesRepository
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.remote.repository.VitalsRepository
import br.com.bragasaude.data.remote.sync.SyncManager
import br.com.bragasaude.data.util.HealthConnectManager
import br.com.bragasaude.data.util.MovementManager
import br.com.bragasaude.domain.HealthEngine
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @Test fun activityGuidanceSurvivesTheHomePipelineAndKeepsItsReadAndDismissIdentity() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined)
        val vitals = mockk<VitalsRepository>(relaxed = true)
        val milestones = mockk<MilestonesRepository>()
        val profiles = mockk<ProfileRepository>()
        val medications = mockk<MedicationRepository>(relaxed = true)
        val database = mockk<BragaDatabase>(relaxed = true)
        val sync = mockk<SyncManager>()
        val movement = mockk<MovementManager>()
        val metrics = mockk<DailyMetricsDao>()
        val engine = mockk<HealthEngine>()
        val healthConnect = mockk<HealthConnectManager>()
        val auth = mockk<FirebaseAuth>()
        val context = mockk<Context>()
        val preferences = mockk<SharedPreferences>(relaxed = true)
        val guidance = "Faça uma pausa para caminhar ou alongar.\n\nAcompanhe seus passos ao longo do dia."
        val fullAlert = "Momento de Movimento\n\n$guidance"
        every { auth.currentUser } returns null
        every { context.getSharedPreferences(any(), any()) } returns preferences
        every { preferences.getStringSet(any(), any()) } returns emptySet()
        every { profiles.getProfile(any()) } returns flowOf(null)
        every { vitals.getVitalSigns(any()) } returns flowOf(emptyList())
        every { milestones.getMilestones(any()) } returns flowOf(emptyList())
        every { medications.getMedications(any()) } returns flowOf(emptyList())
        every { database.wearableReadingDao().observeRecent(any(), any()) } returns flowOf(emptyList())
        every { metrics.getRecent30Days(any()) } returns flowOf(emptyList())
        every { movement.currentSteps } returns MutableStateFlow(100)
        every { healthConnect.isAvailable() } returns false
        coEvery { sync.syncUserData(any(), any()) } returns Result.success(Unit)
        coEvery { engine.analyzeVitals(any(), any(), any()) } returns HealthEngine.AnalysisResult(emptyList())
        coEvery { engine.analyzeActivityPatterns(any(), any(), any()) } returns listOf(
            HealthEngine.ActivityRecommendation("Momento de Movimento", guidance)
        )
        var viewModel: HomeViewModel? = null
        try {
            val vm = HomeViewModel(vitals, milestones, profiles, medications, database, sync,
                movement, metrics, engine, healthConnect, auth, context)
            viewModel = vm
            val alerts = withTimeout(10_000) { vm.clinicalAlerts.first { it.isNotEmpty() } }
            assertEquals(listOf(fullAlert), alerts)

            vm.markAlertAsRead(fullAlert)
            assertEquals(setOf(fullAlert), vm.readAlerts.value)
            vm.dismissAlert(fullAlert)
            assertEquals(emptyList<String>(), vm.clinicalAlerts.value)
            verify { preferences.edit().putStringSet(match { it.startsWith("dismissed_alerts_") }, setOf(fullAlert)) }
        } finally {
            viewModel?.viewModelScope?.cancel()
            Dispatchers.resetMain()
        }
    }
}
