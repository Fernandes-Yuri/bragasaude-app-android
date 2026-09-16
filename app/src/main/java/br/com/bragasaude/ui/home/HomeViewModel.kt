package br.com.bragasaude.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.model.*
import br.com.bragasaude.data.remote.repository.*
import br.com.bragasaude.data.util.toRemote
import br.com.bragasaude.domain.HealthEngine
import br.com.bragasaude.domain.HealthScoreCalculator
import br.com.bragasaude.domain.ScoreBreakdown
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import br.com.bragasaude.ui.util.NotificationHelper
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import br.com.bragasaude.data.remote.sync.SyncManager
import dagger.hilt.android.qualifiers.ApplicationContext

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vitalsRepository: VitalsRepository,
    private val examsRepository: ExamsRepository,
    private val milestonesRepository: MilestonesRepository,
    private val profileRepository: ProfileRepository,
    private val medicationRepository: MedicationRepository,
    private val database: br.com.bragasaude.data.local.BragaDatabase,
    private val syncManager: SyncManager,
    private val movementManager: br.com.bragasaude.data.util.MovementManager,
    private val dailyMetricsDao: br.com.bragasaude.data.local.DailyMetricsDao,
    private val healthEngine: HealthEngine,
    private val healthConnectManager: br.com.bragasaude.data.util.HealthConnectManager,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _userName = MutableStateFlow("Usuário")
    val userName = _userName.asStateFlow()

    private val _healthScore = MutableStateFlow(0)
    val healthScore = _healthScore.asStateFlow()

    private val _scoreBreakdown = MutableStateFlow(ScoreBreakdown(0))
    val scoreBreakdown = _scoreBreakdown.asStateFlow()

    private val _dashboardVitals = MutableStateFlow(RemoteVitalSign(userId = ""))
    val dashboardVitals = _dashboardVitals.asStateFlow()

    private val _latestMilestone = MutableStateFlow<RemoteMilestone?>(null)
    val latestMilestone = _latestMilestone.asStateFlow()

    private val _clinicalAlerts = MutableStateFlow<List<String>>(emptyList())
    val clinicalAlerts = _clinicalAlerts.asStateFlow()

    val userStepGoal: StateFlow<Int> = profileRepository
        .getProfile(auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000")
        .map { it?.stepGoal ?: 8000 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8000)

    val profile: StateFlow<RemoteProfile?> = profileRepository
        .getProfile(auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000")
        .map { it?.toRemote() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val medications = medicationRepository
        .getMedications(auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val heartReadings = database.wearableReadingDao().observeRecent(auth.currentUser?.uid.orEmpty(), "HEART_RATE")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val oxygenReadings = database.wearableReadingDao().observeRecent(auth.currentUser?.uid.orEmpty(), "OXYGEN_SATURATION")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val alertPrefs = context.getSharedPreferences("braga_alerts_prefs", Context.MODE_PRIVATE)
    private val todayDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    private val dismissedAlerts: MutableSet<String> = alertPrefs.getStringSet("dismissed_alerts_$todayDateStr", emptySet())?.toMutableSet() ?: mutableSetOf()

    private fun saveDismissedAlerts() {
        alertPrefs.edit().putStringSet("dismissed_alerts_$todayDateStr", dismissedAlerts).apply()
    }

    init {
        val userId = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"
        
        viewModelScope.launch {
            try {
                vitalsRepository.deduplicateVitals()
            } catch (_: Exception) {}
        }

        viewModelScope.launch {
            profileRepository.getProfile(userId).collectLatest { profile ->
                _userName.value = profile?.fullName?.split(" ")?.firstOrNull() ?: "Visitante"
            }
        }

        // Data Flow para UI e Score
        val healthDataFlow = combine(
            vitalsRepository.getVitalSigns(userId),
            examsRepository.getExamItems(userId),
            profileRepository.getProfile(userId),
            dailyMetricsDao.getRecent30Days(userId),
            movementManager.currentSteps
        ) { vitalsEntities, examItemsEntities, profileEntity, dailyMetrics, liveSteps ->
            val vitals = vitalsEntities.map { it.toRemote() }
            val profile = profileEntity?.toRemote()
            
            val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            val dbSteps = dailyMetrics.firstOrNull { it.date == today }?.steps ?: 0
            val stepsNow = maxOf(dbSteps, liveSteps)

            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val todayHydration = vitalsEntities
                .filter { it.hydrationMl != null && it.measuredAt != null && sdf.format(it.measuredAt) == today }
                .sumOf { it.hydrationMl ?: 0 }

            val latest = RemoteVitalSign(
                userId = userId,
                systolicPressure = vitals.firstOrNull { it.systolicPressure != null }?.systolicPressure,
                diastolicPressure = vitals.firstOrNull { it.diastolicPressure != null }?.diastolicPressure,
                heartRate = vitals.firstOrNull { it.heartRate != null }?.heartRate,
                glucoseLevel = vitals.firstOrNull { it.glucoseLevel != null }?.glucoseLevel,
                glucoseType = vitals.firstOrNull { it.glucoseLevel != null }?.glucoseType,
                hydrationMl = todayHydration,
                steps = stepsNow
            )

            val updatedMetrics = if (dailyMetrics.any { it.date == today }) {
                dailyMetrics.map { if (it.date == today) it.copy(steps = stepsNow) else it }
            } else {
                listOf(br.com.bragasaude.data.local.DailyMetricsEntity(userId = userId, date = today, steps = stepsNow)) + dailyMetrics
            }

            val breakdown = HealthScoreCalculator.calculate(profile, vitals, examItemsEntities, updatedMetrics)
            
            Triple(latest, breakdown, Quadruple(userId, vitals, examItemsEntities, updatedMetrics))
        }.shareIn(viewModelScope, SharingStarted.WhileSubscribed())

        // Atualiza UI principal
        viewModelScope.launch {
            healthDataFlow.collect { (latest, breakdown, _) ->
                _dashboardVitals.value = latest
                _scoreBreakdown.value = breakdown
                _healthScore.value = breakdown.finalScore
            }
        }

        // Análise de Alertas (com collectLatest para evitar processamento pesado repetitivo)
        viewModelScope.launch {
            healthDataFlow.map { it.third }.collectLatest { (uid, vitals, examItems, metrics) ->
                val profile = profileRepository.getProfile(uid).firstOrNull()?.toRemote()
                
                val vitalAnalysis = healthEngine.analyzeVitals(uid, vitals.take(1), silent = true)
                val examAnalysis = healthEngine.analyzeExamItems(uid, examItems.map { it.toRemote() }.take(10), silent = true)
                val activityRecommendations = healthEngine.analyzeActivityPatterns(uid, metrics)
                
                val alerts = mutableListOf<String>()
                alerts.addAll(vitalAnalysis.alerts)
                alerts.addAll(examAnalysis.alerts)
                alerts.addAll(activityRecommendations.map { it.message })

                if (profile?.fullName != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        val hasNotifications = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        if (!hasNotifications) {
                            alerts.add(0, "PERMISSION_NOTIFICATIONS: Ative as notificações para receber alertas de saúde importantes.")
                        }
                    }

                    val hasLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                      ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    
                    val hasActivity = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
                    } else true

                    if (!hasLocation || !hasActivity) {
                        alerts.add(0, "PERMISSION_REQUIRED: Ative o monitoramento de passos para melhorar seu Score.")
                    }
                }
                
                _clinicalAlerts.value = alerts.distinct().filter { !dismissedAlerts.contains(it) }
            }
        }

        viewModelScope.launch {
            milestonesRepository.getMilestones(userId).collectLatest { entities ->
                _latestMilestone.value = entities.firstOrNull()?.toRemote()
            }
        }
        
        viewModelScope.launch {
            syncManager.syncUserData(userId, force = false)
            if (healthConnectManager.isAvailable() && healthConnectManager.checkHasPermissions()) {
                healthConnectManager.syncHealthConnectData()
            }
        }
    }

    fun refreshData() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                syncManager.syncUserData(userId, force = true)
                if (healthConnectManager.isAvailable() && healthConnectManager.checkHasPermissions()) {
                    healthConnectManager.syncHealthConnectData()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun dismissAlert(alert: String) {
        dismissedAlerts.add(alert)
        saveDismissedAlerts()
        _clinicalAlerts.value = _clinicalAlerts.value.filterNot { it == alert }
    }

    fun markAlertsAsRead() {
        viewModelScope.launch {
            dismissedAlerts.addAll(_clinicalAlerts.value)
            saveDismissedAlerts()
            _clinicalAlerts.value = emptyList()
        }
    }

    private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
}
