package br.com.bragasaude.ui.vitals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.remote.repository.VitalsRepository
import br.com.bragasaude.domain.GamificationActionType
import br.com.bragasaude.domain.GamificationEngine
import br.com.bragasaude.domain.HealthEngine
import br.com.bragasaude.domain.XpGrantService
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import br.com.bragasaude.data.remote.sync.SyncManager
import br.com.bragasaude.data.util.toRemote
import kotlinx.coroutines.flow.collectLatest

import br.com.bragasaude.domain.util.BloodPressureParser

@HiltViewModel
class VitalSignsViewModel @Inject constructor(
    private val repository: VitalsRepository,
    private val profileRepository: ProfileRepository,
    private val syncManager: SyncManager,
    private val healthEngine: HealthEngine,
    private val xpGrantService: XpGrantService,
    private val auth: FirebaseAuth
) : ViewModel() {

    val profile: StateFlow<RemoteProfile?> = profileRepository
        .getProfile(auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000")
        .map { it?.toRemote() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _sosAlert = MutableSharedFlow<String>()
    val sosAlert = _sosAlert.asSharedFlow()

    private val _latestRecommendation = MutableSharedFlow<HealthEngine.HealthRecommendation>()
    val latestRecommendation = _latestRecommendation.asSharedFlow()

    private val _milestoneAlert = MutableSharedFlow<String>()
    val milestoneAlert = _milestoneAlert.asSharedFlow()

    private val _allVitals = MutableStateFlow<List<RemoteVitalSign>>(emptyList())
    val allVitals = _allVitals.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    init {
        val userId = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"
        viewModelScope.launch {
            repository.getVitalSigns(userId).collectLatest { entities ->
                _allVitals.value = entities.map { it.toRemote() }
            }
        }
    }

    fun saveVitalSigns(systolic: Int, diastolic: Int, glucose: Int, hydration: Int) {
        val userId = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val normSys = if (systolic > 0) systolic else null
                val normDia = if (diastolic > 0) diastolic else null

                val vital = RemoteVitalSign(
                    id = java.util.UUID.randomUUID().toString(),
                    measuredAt = java.time.Instant.now().toString(),
                    userId = userId,
                    systolicPressure = normSys,
                    diastolicPressure = normDia,
                    glucoseLevel = if (glucose > 0) glucose else null,
                    hydrationMl = if (hydration > 0) hydration else null,
                    glucoseType = if (glucose > 0) "jejum" else null // Default
                )

                repository.saveVitalSigns(listOf(vital))
                
                // ACIONA O CÉREBRO (HEALTH ENGINE)
                val analysis = healthEngine.analyzeVitals(userId, listOf(vital))
                
                // Processa Recomendações Estruturadas
                analysis.recommendations.firstOrNull()?.let { 
                    _latestRecommendation.emit(it) 
                }
                
                // Processa Marcos (Recompensas)
                analysis.newMilestones.firstOrNull()?.let { 
                    _milestoneAlert.emit("${it.title}\n${it.description}")
                    // FASE 3 — XP de marco conquistado (anti-farming limita a 1 por dia)
                    xpGrantService.grantXp(
                        userId = userId,
                        action = GamificationActionType.MILESTONE_ACHIEVED,
                        isActionValid = true,
                        invalidReason = ""
                    )
                }

                // FASE 3 — Concessão de XP quando os sinais vitais estão na meta clínica
                val inTarget = GamificationEngine.isVitalsInTarget(
                    systolic = vital.systolicPressure,
                    diastolic = vital.diastolicPressure,
                    glucose = vital.glucoseLevel,
                    glucoseType = vital.glucoseType ?: "fasting"
                )
                xpGrantService.grantXp(
                    userId = userId,
                    action = GamificationActionType.VITALS_RECORDED,
                    isActionValid = inTarget,
                    invalidReason = "Valores fora da faixa ideal hoje - continue monitorando"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refresh() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncManager.syncUserData(userId, force = true)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }
}
