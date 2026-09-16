package br.com.bragasaude.ui.steps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.DailyMetricsEntity
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.data.remote.repository.ActivityRepository
import br.com.bragasaude.data.remote.repository.VitalsRepository
import br.com.bragasaude.data.util.DailyMetrics
import br.com.bragasaude.data.util.MovementManager
import br.com.bragasaude.data.util.ActivityState
import br.com.bragasaude.data.util.toRemote
import br.com.bragasaude.domain.XpGrantService
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.*
import android.content.Context
import android.content.Intent
import br.com.bragasaude.ui.util.StepTrackingService
import dagger.hilt.android.qualifiers.ApplicationContext

enum class ActivityMetricType(val label: String, val unit: String) {
    STEPS("Passos", "passos"),
    HEART_POINTS("Pontos de Cardio (OMS)", "pts"),
    CALORIES("Gasto Calórico", "kcal"),
    DISTANCE("Distância", "km"),
    ACTIVE_MINUTES("Minutos Ativos", "min")
}

data class CardioDayProgress(
    val dayLabel: String,
    val fullDayName: String,
    val dateFormatted: String,
    val points: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
    val isGoalMet: Boolean
)

data class StepDayProgress(
    val dayLabel: String,
    val fullDayName: String,
    val dateFormatted: String,
    val steps: Int,
    val activeMinutes: Int,
    val isToday: Boolean,
    val isFuture: Boolean,
    val diffFromPreviousDay: Int? = null,
    val isGoalMet: Boolean
)

@HiltViewModel
class StepsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val movementManager: MovementManager,
    private val repository: VitalsRepository,
    private val activityRepository: ActivityRepository,
    private val profileRepository: br.com.bragasaude.data.remote.repository.ProfileRepository,
    private val healthConnectManager: br.com.bragasaude.data.util.HealthConnectManager,
    private val xpGrantService: XpGrantService,
    private val auth: FirebaseAuth
) : ViewModel() {

    val currentSteps = movementManager.currentSteps
    val currentDistance = movementManager.currentDistance
    val currentActiveMinutes = movementManager.currentActiveMinutes
    val currentTotalCalories = movementManager.currentTotalCalories
    val currentBasalCalories = movementManager.currentBasalCalories
    val currentActiveCalories = movementManager.currentActiveCalories
    val activityState = movementManager.activityState

    // Fase 2 — exposição de diagnóstico/confiabilidade da reconciliação
    val currentGpsDistance = movementManager.currentGpsDistance
    val currentStepsDistance = movementManager.currentStepsDistance
    val currentReliability = movementManager.currentReliability
    val currentSpeedKmh = movementManager.currentSpeedKmh
    val currentPaceMinPerKm = movementManager.currentPaceMinPerKm
    val calibratedStrideMeters = movementManager.calibratedStrideMeters

    // Pontos de Cardio (Diretriz OMS - Meta 150 pts/semana)
    val targetWeeklyHeartPoints = 150
    val currentDailyHeartPoints = combine(currentActiveMinutes, activityState) { mins, state ->
        if (state == ActivityState.VIGOROUS) mins * 2 else mins
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _selectedMetric = MutableStateFlow(ActivityMetricType.STEPS)
    val selectedMetric = _selectedMetric.asStateFlow()

    val targetSteps: StateFlow<Int> = profileRepository
        .getProfile(auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000")
        .map { it?.stepGoal ?: 8000 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8000)

    private val _dailyMetrics = MutableStateFlow<DailyMetrics?>(null)
    val dailyMetrics = _dailyMetrics.asStateFlow()

    private val _weeklyAverage = MutableStateFlow<Pair<Int, Int>>(0 to 0) // steps, activeMinutes
    val weeklyAverage = _weeklyAverage.asStateFlow()

    private val _monthlyAverageSteps = MutableStateFlow(0)
    val monthlyAverageSteps = _monthlyAverageSteps.asStateFlow()

    private val _monthlyTrend = MutableStateFlow<List<DailyMetricsEntity>>(emptyList())
    val monthlyTrend = _monthlyTrend.asStateFlow()

    companion object {
        fun getMondayOfCurrentWeek(now: Calendar = Calendar.getInstance(Locale("pt", "BR"))): Calendar {
            val cal = now.clone() as Calendar
            cal.firstDayOfWeek = Calendar.MONDAY
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val daysFromMonday = when (dayOfWeek) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> 0
            }
            cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal
        }
    }

    /**
     * Histórico da semana corrente (iniciando segunda-feira 00:01 e finalizando domingo 23:59)
     */
    val weeklyCardioHistory: StateFlow<List<CardioDayProgress>> = combine(
        _monthlyTrend,
        currentDailyHeartPoints
    ) { trend, todayLivePoints ->
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val shortDateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val todayCal = Calendar.getInstance(Locale("pt", "BR")).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val monday = getMondayOfCurrentWeek()
        val trendMap = trend.associateBy { it.date }

        val dayNames = listOf(
            "Seg" to "Segunda-feira",
            "Ter" to "Terça-feira",
            "Qua" to "Quarta-feira",
            "Qui" to "Quinta-feira",
            "Sex" to "Sexta-feira",
            "Sáb" to "Sábado",
            "Dom" to "Domingo"
        )

        (0..6).map { dayOffset ->
            val dayCal = (monday.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            val dateKey = dateFormat.format(dayCal.time)
            val isToday = dayCal.timeInMillis == todayCal.timeInMillis
            val isFuture = dayCal.timeInMillis > todayCal.timeInMillis

            val points = when {
                isFuture -> 0
                isToday -> todayLivePoints
                else -> trendMap[dateKey]?.activeMinutes ?: 0
            }

            val (shortLabel, fullLabel) = dayNames[dayOffset]
            CardioDayProgress(
                dayLabel = if (isToday) "Hoje" else shortLabel,
                fullDayName = if (isToday) "Hoje ($shortLabel)" else fullLabel,
                dateFormatted = shortDateFormat.format(dayCal.time),
                points = points,
                isToday = isToday,
                isFuture = isFuture,
                isGoalMet = points >= 22 // Meta diária proporcional da OMS para 150/sem
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Histórico semanal de passos da semana corrente (Segunda a Domingo)
     * com cálculo de variação em relação ao dia anterior.
     */
    val weeklyStepsHistory: StateFlow<List<StepDayProgress>> = combine(
        _monthlyTrend,
        currentSteps,
        targetSteps
    ) { trend, liveSteps, goal ->
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val shortDateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())
        val todayCal = Calendar.getInstance(Locale("pt", "BR")).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val monday = getMondayOfCurrentWeek()
        val trendMap = trend.associateBy { it.date }

        val dayNames = listOf(
            "Seg" to "Segunda-feira",
            "Ter" to "Terça-feira",
            "Qua" to "Quarta-feira",
            "Qui" to "Quinta-feira",
            "Sex" to "Sexta-feira",
            "Sáb" to "Sábado",
            "Dom" to "Domingo"
        )

        var previousDaySteps: Int? = null

        (0..6).map { dayOffset ->
            val dayCal = (monday.clone() as Calendar).apply {
                add(Calendar.DAY_OF_YEAR, dayOffset)
            }
            val dateKey = dateFormat.format(dayCal.time)
            val isToday = dayCal.timeInMillis == todayCal.timeInMillis
            val isFuture = dayCal.timeInMillis > todayCal.timeInMillis

            val steps = when {
                isFuture -> 0
                isToday -> liveSteps
                else -> trendMap[dateKey]?.steps ?: 0
            }
            val minutes = when {
                isFuture -> 0
                isToday -> currentActiveMinutes.value
                else -> trendMap[dateKey]?.activeMinutes ?: 0
            }

            val diff = if (!isFuture && previousDaySteps != null) {
                steps - previousDaySteps!!
            } else {
                null
            }

            if (!isFuture) {
                previousDaySteps = steps
            }

            val (shortLabel, fullLabel) = dayNames[dayOffset]
            StepDayProgress(
                dayLabel = if (isToday) "Hoje" else shortLabel,
                fullDayName = if (isToday) "Hoje ($shortLabel)" else fullLabel,
                dateFormatted = shortDateFormat.format(dayCal.time),
                steps = steps,
                activeMinutes = minutes,
                isToday = isToday,
                isFuture = isFuture,
                diffFromPreviousDay = diff,
                isGoalMet = steps >= goal
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentWeeklyHeartPoints: StateFlow<Int> = weeklyCardioHistory.map { history ->
        history.sumOf { it.points }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /** Trava local: evita chamar o serviço a cada passo após a meta já ter sido atingida. */
    private val stepGoalAwardAttempted = java.util.concurrent.atomic.AtomicBoolean(false)

    fun selectMetric(metric: ActivityMetricType) {
        _selectedMetric.value = metric
    }

    init {
        val userId = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"

        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            
            // 1. Fetch Cloud-Calculated Stats (Infalível)
            activityRepository.getUserStatsSummary(userId)?.let { summary ->
                _weeklyAverage.value = summary.weeklyAvgSteps to 0 // activeMinutes coming soon from SQL
                _monthlyAverageSteps.value = summary.monthlyAvgSteps
            }

            // 2. Observe recent 30 days trend for charts
            activityRepository.getRecent30Days(userId).collect { trend ->
                _monthlyTrend.value = trend
                
                // Update daily metrics for today
                val todayData = trend.find { it.date == today }
                _dailyMetrics.value = todayData?.let { 
                    DailyMetrics(it.date, it.steps, it.distanceMeters, it.caloriesBurned, it.activeMinutes)
                }
                // Calculate weekly average
                _weeklyAverage.value = calculateWeeklyAverage(trend)
            }
        }

        // Sincroniza dados com Health Connect se disponível
        syncHealthConnect()

        // FASE 3 — XP quando a meta diária de passos é atingida (com anti-fraude de confiabilidade)
        viewModelScope.launch {
            combine(currentSteps, targetSteps) { steps: Int, goal: Int -> steps to goal }.collect { (steps, goal) ->
                if (goal > 0 && steps >= goal && stepGoalAwardAttempted.compareAndSet(false, true)) {
                    val uid = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"
                    xpGrantService.grantStepGoalXp(
                        userId = uid,
                        stepsTaken = steps,
                        stepGoal = goal,
                        reliabilityScore = movementManager.currentReliability.value
                    )
                }
            }
        }
    }

    fun syncHealthConnect() {
        viewModelScope.launch {
            if (healthConnectManager.isAvailable() && healthConnectManager.checkHasPermissions()) {
                healthConnectManager.syncHealthConnectData()
            }
        }
    }

    private fun calculateWeeklyAverage(metrics: List<DailyMetricsEntity>): Pair<Int, Int> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val monday = getMondayOfCurrentWeek()
        val todayCal = Calendar.getInstance(Locale("pt", "BR")).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val trendMap = metrics.associateBy { it.date }

        val elapsedWeekDays = (0..6).map { dayOffset ->
            val dayCal = (monday.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
            val isFuture = dayCal.timeInMillis > todayCal.timeInMillis
            val dateKey = dateFormat.format(dayCal.time)
            val isToday = dayCal.timeInMillis == todayCal.timeInMillis

            val steps = if (isFuture) 0 else if (isToday) currentSteps.value else (trendMap[dateKey]?.steps ?: 0)
            val mins = if (isFuture) 0 else if (isToday) currentActiveMinutes.value else (trendMap[dateKey]?.activeMinutes ?: 0)
            Triple(isFuture, steps, mins)
        }.filter { !it.first }

        if (elapsedWeekDays.isEmpty()) return 0 to 0
        val avgSteps = elapsedWeekDays.sumOf { it.second } / elapsedWeekDays.size
        val avgMinutes = elapsedWeekDays.sumOf { it.third } / elapsedWeekDays.size
        return avgSteps to avgMinutes
    }

    fun startTracking() {
        val intent = Intent(context, StepTrackingService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun updateTargetSteps(newGoal: Int) {
        val sanitized = newGoal.coerceIn(500, 50000)
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                val currentEntity = profileRepository.getProfile(userId).firstOrNull()
                val profile = (currentEntity?.toRemote() ?: br.com.bragasaude.data.remote.model.RemoteProfile(id = userId)).copy(stepGoal = sanitized)
                profileRepository.saveProfile(profile)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun updateSteps(steps: Int) {
        // Test only
    }
}
