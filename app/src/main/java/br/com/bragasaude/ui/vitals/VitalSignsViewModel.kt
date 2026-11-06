package br.com.bragasaude.ui.vitals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.remote.repository.VitalsRepository
import br.com.bragasaude.domain.HealthEngine
import br.com.bragasaude.domain.VitalSaveStatus
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
import br.com.bragasaude.util.BragaConstants

@HiltViewModel
class VitalSignsViewModel @Inject constructor(
    private val repository: VitalsRepository,
    private val profileRepository: ProfileRepository,
    private val syncManager: SyncManager,
    private val healthEngine: HealthEngine,
    private val auth: FirebaseAuth
) : ViewModel() {

    val profile: StateFlow<RemoteProfile?> = profileRepository
        .getProfile(auth.currentUser?.uid ?: BragaConstants.GUEST_UID)
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

    // DT-052: desfecho do último salvamento — alimenta o chip de sincronização
    // ("Salvando…" -> "Enviado à nuvem ✓" / "Aguardando rede").
    private val _saveStatus = MutableStateFlow<VitalSaveStatus?>(null)
    val saveStatus = _saveStatus.asStateFlow()

    init {
        val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
        viewModelScope.launch {
            repository.getVitalSigns(userId).collectLatest { entities ->
                _allVitals.value = entities.map { it.toRemote() }
            }
        }
        // DT-052: Room vazio (wipe/troca de conta) precisa puxar o histórico da
        // nuvem ao abrir a tela; syncUserData é throttled internamente e não
        // bloqueia a UI (o Room observa e atualiza sozinho).
        if (userId != BragaConstants.GUEST_UID) {
            viewModelScope.launch {
                try {
                    if (repository.getVitalSignsSync(userId).isEmpty()) {
                        syncManager.syncUserData(userId, force = false)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("VitalSignsVM", "Sync ao abrir a tela de vitais falhou: ${e.message}")
                }
            }
        }
    }

    fun saveVitalSigns(systolic: Int, diastolic: Int, glucose: Int, hydration: Int) {
        val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
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

                _saveStatus.value = VitalSaveStatus.SAVING
                val synced = repository.saveVitalSigns(listOf(vital))
                _saveStatus.value = if (userId != BragaConstants.GUEST_UID && synced.all { it }) {
                    VitalSaveStatus.SYNCED
                } else if (userId == BragaConstants.GUEST_UID) {
                    null // convidado não tem nuvem — sem indicador de sync
                } else {
                    VitalSaveStatus.PENDING_RETRY
                }
                
                // ACIONA O CÉREBRO (HEALTH ENGINE)
                val analysis = healthEngine.analyzeVitals(userId, listOf(vital))
                
                // Processa Recomendações Estruturadas
                analysis.recommendations.firstOrNull()?.let {
                    _latestRecommendation.emit(it)
                    // AUD-AN09: o canal sosAlert existia mas NUNCA era emitido
                    // — e o coletor na tela tinha corpo vazio. Em emergências
                    // (ex: PA Stage 3) a notificação pode passar despercebida;
                    // um alerta in-app é a segunda camada de aviso.
                    if (it.isEmergency) {
                        _sosAlert.emit(it.message)
                    }
                }
                
                // Processa Marcos (Recompensas)
                analysis.newMilestones.firstOrNull()?.let { 
                    _milestoneAlert.emit("${it.title}\n${it.description}")
                }
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
