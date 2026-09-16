package br.com.bragasaude.ui.hydration

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.remote.repository.VitalsRepository
import br.com.bragasaude.data.util.toRemote
import br.com.bragasaude.domain.GamificationActionType
import br.com.bragasaude.domain.GamificationEngine
import br.com.bragasaude.domain.HealthEngine
import br.com.bragasaude.domain.XpGrantService
import br.com.bragasaude.ui.util.NotificationHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.*

data class HydrationEntry(
    val amountMl: Int,
    val timeFormatted: String
)

@HiltViewModel
class HydrationViewModel @Inject constructor(
    private val repository: VitalsRepository,
    private val profileRepository: ProfileRepository,
    private val healthEngine: HealthEngine,
    private val xpGrantService: XpGrantService,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _currentHydration = MutableStateFlow(0.0)
    val currentHydration = _currentHydration.asStateFlow()

    private val _targetHydration = MutableStateFlow(2000.0)
    val targetHydration = _targetHydration.asStateFlow()

    private val _isCustomTarget = MutableStateFlow(false)
    val isCustomTarget = _isCustomTarget.asStateFlow()

    private val _userWeight = MutableStateFlow<Double?>(null)
    val userWeight = _userWeight.asStateFlow()

    private val _autoRecommendedTarget = MutableStateFlow(2000)
    val autoRecommendedTarget = _autoRecommendedTarget.asStateFlow()

    private val _todayLogs = MutableStateFlow<List<HydrationEntry>>(emptyList())
    val todayLogs = _todayLogs.asStateFlow()

    private val _hydrationHistory = MutableStateFlow<List<Double>>(emptyList())
    val hydrationHistory = _hydrationHistory.asStateFlow()

    private val _isReminderEnabled = MutableStateFlow(true)
    val isReminderEnabled = _isReminderEnabled.asStateFlow()

    private val _milestoneAlert = MutableSharedFlow<String>()
    val milestoneAlert = _milestoneAlert.asSharedFlow()

    private val userId: String
        get() = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"

    init {
        val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
        _isReminderEnabled.value = prefs.getBoolean("hydration_reminders_2h_enabled", true)

        viewModelScope.launch {
            profileRepository.getProfile(userId).collectLatest { profile ->
                val weight = profile?.weight
                _userWeight.value = weight

                // Regra padrão: 35 ml por kg
                val calculatedAuto = if (weight != null && weight > 0.0) {
                    (weight * 35).toInt()
                } else {
                    2000
                }
                _autoRecommendedTarget.value = calculatedAuto

                val isCustom = prefs.getBoolean("hydration_target_is_custom_$userId", false)
                _isCustomTarget.value = isCustom

                val target = if (isCustom && profile?.hydrationTargetMl != null && profile.hydrationTargetMl > 0) {
                    profile.hydrationTargetMl.toDouble()
                } else {
                    calculatedAuto.toDouble()
                }
                _targetHydration.value = target

                // Ativação automática de lembretes quando há meta positiva (TASK-HYD-01)
                if (_isReminderEnabled.value && target > 0) {
                    startHydrationReminders(context)
                } else if (target <= 0) {
                    cancelHydrationReminders(context)
                }

                // Sincronizar o valor de hidratação no perfil caso não exista ou seja padrão
                if (profile != null && (!isCustom || profile.hydrationTargetMl == null || profile.hydrationTargetMl <= 0)) {
                    val updatedProfile = profile.toRemote().copy(hydrationTargetMl = target.toInt())
                    profileRepository.saveProfile(updatedProfile)
                }
            }
        }

        viewModelScope.launch {
            repository.getVitalSigns(userId).collectLatest { entities ->
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val hydrationEntities = entities.filter { it.hydrationMl != null && it.hydrationMl > 0 }

                // Registros de Hoje
                val todayEntities = hydrationEntities.filter {
                    val entityDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it.measuredAt)
                    entityDate == today
                }

                _currentHydration.value = todayEntities.sumOf { it.hydrationMl?.toDouble() ?: 0.0 }

                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                _todayLogs.value = todayEntities.map {
                    HydrationEntry(
                        amountMl = it.hydrationMl ?: 0,
                        timeFormatted = timeFormat.format(it.measuredAt)
                    )
                }

                // Agrupamento diário dos últimos 30 dias para o gráfico de tendência
                val groupedByDay = hydrationEntities
                    .groupBy { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(it.measuredAt) }
                    .mapValues { entry -> entry.value.sumOf { (it.hydrationMl ?: 0).toDouble() } }

                val daysList = (0..29).map { i ->
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -i)
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                }.reversed()

                _hydrationHistory.value = daysList.map { day -> groupedByDay[day] ?: 0.0 }
            }
        }
    }

    fun setReminderEnabled(enabled: Boolean) {
        _isReminderEnabled.value = enabled
        context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("hydration_reminders_2h_enabled", enabled)
            .apply()

        if (enabled) {
            val currentTarget = _targetHydration.value
            if (currentTarget > 0) {
                startHydrationReminders(context)
            }
        } else {
            cancelHydrationReminders(context)
        }
    }

    /**
     * Inicia os lembretes periƅdicos de hidrataƌo via WorkManager (TASK-HYD-01).
     * Usa unique periodic work com policy UPDATE para evitar duplicaçāo.
     */
    private fun startHydrationReminders(appContext: Context) {
        viewModelScope.launch {
            try {
                val hasExistingWork = isHydrationWorkRunning(appContext)
                if (!hasExistingWork) {
                    NotificationHelper.scheduleHydrationReminders(appContext)
                }
            } catch (e: Exception) {
                android.util.Log.e("HydrationVM", "Erro ao iniciar lembretes de hidrataƌo: ${e.message}")
            }
        }
    }

    /**
     * Cancela os lembretes periƅdicos de hidrataƌo (TASK-HYD-01).
     */
    private fun cancelHydrationReminders(appContext: Context) {
        viewModelScope.launch {
            try {
                NotificationHelper.cancelHydrationReminders(appContext)
            } catch (e: Exception) {
                android.util.Log.e("HydrationVM", "Erro ao cancelar lembres de hidrataƌo: ${e.message}")
            }
        }
    }

    /**
     * Verifica se jǇ existe um HydrationReminderWorker ativo/enfileirado (TASK-HYD-01).
     */
    private suspend fun isHydrationWorkRunning(appContext: Context): Boolean {
        return try {
            val workInfos = WorkManager.getInstance(appContext)
                .getWorkInfosForUniqueWorkFlow("braga_hydration_reminders")
                .first()
            workInfos.any { info ->
                info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING
            }
        } catch (e: Exception) {
            false
        }
    }

    fun addWater(ml: Int) {
        if (ml <= 0) return
        viewModelScope.launch {
            try {
                val newVital = RemoteVitalSign(
                    userId = userId,
                    hydrationMl = ml,
                    measuredAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.getDefault()).format(Date())
                )
                repository.saveVitalSigns(listOf(newVital))
                
                val analysis = healthEngine.analyzeVitals(userId, listOf(newVital))
                analysis.newMilestones.firstOrNull()?.let { 
                    _milestoneAlert.emit(it.title)
                    // FASE 3 — XP de marco conquistado (anti-farming limita a 1 por dia)
                    xpGrantService.grantXp(
                        userId = userId,
                        action = GamificationActionType.MILESTONE_ACHIEVED,
                        isActionValid = true,
                        invalidReason = ""
                    )
                }

                // FASE 3 — XP quando a meta diária de hidratação é atingida
                // (o Flow do banco ainda pode não ter atualizado, então somamos manualmente)
                val totalToday = _currentHydration.value.toInt() + ml
                val goalHit = GamificationEngine.isHydrationGoalHit(totalToday, _targetHydration.value.toInt())
                xpGrantService.grantXp(
                    userId = userId,
                    action = GamificationActionType.HYDRATION_GOAL_HIT,
                    isActionValid = goalHit,
                    invalidReason = "Meta de hidratação ainda não atingida"
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeLastWater() {
        viewModelScope.launch {
            try {
                repository.deleteLastHydration(userId)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setCustomTarget(newTargetMl: Int) {
        if (newTargetMl <= 0) return
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("hydration_target_is_custom_$userId", true).apply()
                _isCustomTarget.value = true
                _targetHydration.value = newTargetMl.toDouble()

                profileRepository.getProfile(userId).first()?.let { profile ->
                    val updated = profile.toRemote().copy(hydrationTargetMl = newTargetMl)
                    profileRepository.saveProfile(updated)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resetToAutoTarget() {
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("hydration_target_is_custom_$userId", false).apply()
                _isCustomTarget.value = false
                val autoTarget = _autoRecommendedTarget.value
                _targetHydration.value = autoTarget.toDouble()

                profileRepository.getProfile(userId).first()?.let { profile ->
                    val updated = profile.toRemote().copy(hydrationTargetMl = autoTarget)
                    profileRepository.saveProfile(updated)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateHydrationTarget(newTargetMl: Int) {
        setCustomTarget(newTargetMl)
    }

    fun triggerTestNotification(context: Context) {
        NotificationHelper.sendHydrationNotification(
            context,
            _currentHydration.value.toInt(),
            _targetHydration.value.toInt()
        )
    }
}
