package br.com.bragasaude.ui.modules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.model.RemoteDetectedCondition
import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.remote.repository.ConditionRepository
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.util.toRemote
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import br.com.bragasaude.util.BragaConstants

data class CareTask(
    val id: String,
    val moduleName: String,
    val title: String,
    val description: String,
    val isCompleted: Boolean = false
)

@HiltViewModel
class CareModulesViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val conditionRepository: ConditionRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _detectedConditions = MutableStateFlow<List<RemoteDetectedCondition>>(emptyList())
    val detectedConditions = _detectedConditions.asStateFlow()

    private val _profile = MutableStateFlow<RemoteProfile?>(null)
    val profile = _profile.asStateFlow()

    private val _tasks = MutableStateFlow<List<CareTask>>(
        listOf(
            CareTask("ht_1", "Hipertensão", "Medir Pressão Arterial", "Aferir em repouso pela manhã."),
            CareTask("ht_2", "Hipertensão", "Tomar Medicação", "Ingerir a dose prescrita no horário certo."),
            CareTask("ht_3", "Hipertensão", "Reduzir Sal", "Evitar alimentos ultraprocessados no almoço."),
            CareTask("ht_4", "Hipertensão", "Caminhada de 20 min", "Atividade física leve estimula a dilatação vascular."),
            
            CareTask("db_1", "Diabetes", "Glicemia de Jejum", "Registrar o valor na ponta do dedo ao acordar."),
            CareTask("db_2", "Diabetes", "Cuidado com os Pés", "Verificar hidratação e calçados adequados."),
            CareTask("db_3", "Diabetes", "Fibras na Refeição", "Priorizar vegetais e grãos integrais."),
            
            CareTask("rn_1", "Saúde Renal", "Bater Meta de Água", "Ingerir a meta recomendada de água hoje."),
            CareTask("rn_2", "Saúde Renal", "Cuidado com Medicamentos", "Converse com seu médico antes de usar anti-inflamatórios.")
        )
    )
    val tasks = _tasks.asStateFlow()

    init {
        val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
        
        viewModelScope.launch {
            profileRepository.getProfile(userId).collectLatest { entity ->
                _profile.value = entity?.toRemote()
            }
        }
        
        loadDetected(userId)
    }

    private fun loadDetected(userId: String) {
        viewModelScope.launch {
            try {
                _detectedConditions.value = conditionRepository.getDetectedConditions(userId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun toggleTask(taskId: String) {
        _tasks.value = _tasks.value.map { task ->
            if (task.id == taskId) task.copy(isCompleted = !task.isCompleted) else task
        }
    }

    fun toggleModule(conditionName: String, activate: Boolean) {
        val currentProfile = _profile.value ?: return
        val updatedProfile = when (conditionName) {
            "Hipertensão" -> currentProfile.copy(hasHypertension = activate)
            "Diabetes" -> currentProfile.copy(hasDiabetes = activate)
            "Saúde Renal" -> currentProfile.copy(hasRenalIssue = activate)
            else -> currentProfile
        }
        
        viewModelScope.launch {
            try {
                profileRepository.saveProfile(updatedProfile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
