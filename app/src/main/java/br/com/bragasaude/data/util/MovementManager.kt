package br.com.bragasaude.data.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import br.com.bragasaude.data.local.DailyMetricsDao
import br.com.bragasaude.data.local.DailyMetricsEntity
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.domain.ActivityFlag
import br.com.bragasaude.domain.ActivityReconciler
import br.com.bragasaude.domain.ReconciliationInput
import br.com.bragasaude.domain.ReconciliationResult
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

enum class ActivityState { RESTING, LIGHT, MODERATE, VIGOROUS }

data class DailyMetrics(
    val date: String, // yyyy-MM-dd
    val steps: Int = 0,
    val distanceMeters: Float = 0f,
    val caloriesBurned: Float = 0f,
    val activeMinutes: Int = 0
)

@Singleton
class MovementManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val profileRepository: ProfileRepository,
    private val dailyMetricsDao: DailyMetricsDao,
    private val workManager: androidx.work.WorkManager,
    private val auth: FirebaseAuth
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // --- StateFlows Reativos para a UI ---
    private val _currentSteps = MutableStateFlow(0)
    val currentSteps = _currentSteps.asStateFlow()

    private val _currentDistance = MutableStateFlow(0f) // Metros totais do dia
    val currentDistance = _currentDistance.asStateFlow()

    private val _currentActiveMinutes = MutableStateFlow(0)
    val currentActiveMinutes = _currentActiveMinutes.asStateFlow()

    private val _currentTotalCalories = MutableStateFlow(0f)
    val currentTotalCalories = _currentTotalCalories.asStateFlow()

    private val _currentBasalCalories = MutableStateFlow(0f)
    val currentBasalCalories = _currentBasalCalories.asStateFlow()

    private val _currentActiveCalories = MutableStateFlow(0f)
    val currentActiveCalories = _currentActiveCalories.asStateFlow()

    private val _activityState = MutableStateFlow(ActivityState.RESTING)
    val activityState = _activityState.asStateFlow()

    // Diagnósticos internos
    private val _currentGpsDistance = MutableStateFlow(0f)
    val currentGpsDistance = _currentGpsDistance.asStateFlow()

    private val _currentStepsDistance = MutableStateFlow(0f)
    val currentStepsDistance = _currentStepsDistance.asStateFlow()

    private val _currentReliability = MutableStateFlow(0.9f)
    val currentReliability = _currentReliability.asStateFlow()

    private val _currentSpeedKmh = MutableStateFlow(0f)
    val currentSpeedKmh = _currentSpeedKmh.asStateFlow()

    private val _currentPaceMinPerKm = MutableStateFlow(0f)
    val currentPaceMinPerKm = _currentPaceMinPerKm.asStateFlow()

    private val _calibratedStrideMeters = MutableStateFlow(DEFAULT_STRIDE_METERS)
    val calibratedStrideMeters = _calibratedStrideMeters.asStateFlow()

    // --- Controle de Estado e Sensores ---
    private var isStepCounterRegistered = false
    private var isTrackingLocation = false
    private var lastSensorStepCount = -1

    // Dados do Perfil em Cache
    private var cachedWeightKg: Double = 70.0
    private var cachedHeightCm: Double = 170.0
    private var cachedAgeYears: Int = 30
    private var cachedIsFemale: Boolean = false

    // Controle de Sessão Contínua de Movimento
    private var lastStepTimestamp = 0L
    private var movementSessionStartTimestamp = 0L
    private var movementSessionSteps = 0
    private var movementSessionGpsMeters = 0f
    private var restingDetectionJob: Job? = null
    private var activeTimerJob: Job? = null
    private var periodicRefreshJob: Job? = null

    // Acumuladores do Dia
    private var accumulatedActiveSecondsToday = 0L
    private var baseDailySteps = 0

    // Controle de Coerência Biomecânica & Anti-Fraude
    private var lastAcceptedStepTimestamp = 0L
    private var windowStartStepCount = 0
    private var windowStartGpsMeters = 0f
    private var windowStartTimestamp = 0L
    private var isStationaryShakingDetected = false
    private var latestGpsAccuracyMeters = 999f
    private var latestGpsTimestamp = 0L
    private var smoothedCadence = 0.0
    private val recentStepsQueue = java.util.ArrayDeque<Long>()

    // Limiares e Constantes Biomecânicas
    private val MIN_STEP_INTERVAL_MS = 260L // Máx ~230 passos/min (rejeita agitação frenética de mão)
    private val WINDOW_STEPS_THRESHOLD = 70 // Janela de 70 passos para correlação com GPS
    private val MIN_DISPLACEMENT_PER_STEP_METERS = 0.28f // 70 passos necessitam de >= 19.6m com GPS confiável
    private val MAX_HUMAN_SPEED_KMH = 18.0f // Acima de 18 km/h é veículo

    // --- Zona residencial/trabalho (100m de raio fixo) ---
    private val ZONE_RADIUS_METERS = 100f
    private val LOCATION_HISTORY_LIMIT = 200
    private val CLUSTER_MIN_SAMPLES = 15

    private var homeLat = 0.0
    private var homeLng = 0.0
    private var workLat = 0.0
    private var workLng = 0.0
    private var zonesDetermined = false
    private var lastClusterTime = 0L

    private val _isInHomeZone = MutableStateFlow(true)
    val isInHomeZone = _isInHomeZone.asStateFlow()

    private val _isInWorkZone = MutableStateFlow(true)
    val isInWorkZone = _isInWorkZone.asStateFlow()

    private val _isOutsideBothZones = MutableStateFlow(true)
    val isOutsideBothZones = _isOutsideBothZones.asStateFlow()

    // Configurações de Limite
    private val INACTIVITY_TIMEOUT_MS = 6000L // 6s sem passos = repouso
    private val CLOUD_SYNC_COOLDOWN_MS = 15 * 60 * 1000L // 15 min de intervalo mínimo
    private var lastCloudSyncTimestamp = 0L
    private var lastTrackedDate: String = ""
    private val prefs = context.getSharedPreferences("braga_movement_prefs", Context.MODE_PRIVATE)

    init {
        loadUserProfile()
        loadTodayMetrics()
        loadZoneStateFromPrefs()
        startStepCounter()
        startPeriodicTimeTicker()
        registerDateChangeReceiver()

        auth.addAuthStateListener { fbAuth ->
            if (fbAuth.currentUser != null) {
                loadUserProfile()
                loadTodayMetrics()
            }
        }
    }

    private fun registerDateChangeReceiver() {
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_DATE_CHANGED)
            addAction(android.content.Intent.ACTION_TIME_CHANGED)
            addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: android.content.Intent?) {
                checkDateRollover()
            }
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (e: Exception) {
            android.util.Log.w("MovementManager", "Falha ao registrar receiver de data: ${e.message}")
        }
    }

    private var profileObserverJob: Job? = null

    /** Carrega e observa os dados biométricos do usuário para cálculos precisos de passada e BMR. */
    private fun loadUserProfile() {
        val userId = auth.currentUser?.uid ?: return
        profileObserverJob?.cancel()
        profileObserverJob = scope.launch {
            profileRepository.getProfile(userId).collectLatest { profile ->
                if (profile != null) {
                    cachedWeightKg = profile.weight ?: 70.0
                    cachedHeightCm = profile.height ?: 170.0
                    cachedAgeYears = calculateAge(profile.birthDate)
                    cachedIsFemale = profile.gender?.lowercase() in listOf("female", "feminino", "f")

                    // Passada biomecânica padrão: Altura * 0.415
                    val calculatedStride = ((cachedHeightCm / 100.0) * 0.415).toFloat().coerceIn(0.50f, 0.90f)
                    _calibratedStrideMeters.value = calculatedStride
                }
                recalculateCalories()
            }
        }
    }

    /** Carrega os dados já salvos no Room para o dia atual. */
    fun loadTodayMetrics() {
        val userId = auth.currentUser?.uid ?: return
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val savedLastDate = prefs.getString("last_tracked_date", "")

        val isNewDay = !savedLastDate.isNullOrEmpty() && savedLastDate != today
        if (isNewDay) {
            baseDailySteps = 0
            accumulatedActiveSecondsToday = 0L
            _currentSteps.value = 0
            _currentDistance.value = 0f
            _currentActiveMinutes.value = 0
            movementSessionSteps = 0
            movementSessionGpsMeters = 0f
        }

        lastTrackedDate = today
        prefs.edit().putString("last_tracked_date", today).apply()

        scope.launch {
            val entity = dailyMetricsDao.getByDate(userId, today)
            val cachedSteps = prefs.getInt("steps_${userId}_${today}", 0)
            val cachedActiveSecs = prefs.getLong("active_secs_${userId}_${today}", 0L)

            val resolvedSteps = if (isNewDay && cachedSteps == 0 && entity == null) 0 else maxOf(entity?.steps ?: 0, cachedSteps)
            val resolvedActiveSecs = if (isNewDay && cachedActiveSecs == 0L && entity == null) 0L else maxOf((entity?.activeMinutes ?: 0) * 60L, cachedActiveSecs)

            baseDailySteps = resolvedSteps
            accumulatedActiveSecondsToday = resolvedActiveSecs

            _currentSteps.value = resolvedSteps
            _currentActiveMinutes.value = (resolvedActiveSecs / 60).toInt()

            val stride = _calibratedStrideMeters.value
            _currentDistance.value = resolvedSteps * stride

            recalculateCalories()
        }
    }

    /** Verifica se a data do calendário virou (meia-noite) e reinicia os acumuladores diários. */
    private fun checkDateRollover() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (lastTrackedDate.isNotEmpty() && today != lastTrackedDate) {
            android.util.Log.i("MovementManager", "Virada de dia detectada: $lastTrackedDate -> $today. Resetando contadores diários.")
            // 1. Salva métricas acumuladas do dia anterior para o dia correto (lastTrackedDate) antes do reset
            persistCurrentMetricsToDatabase(lastTrackedDate)

            // 2. Reseta acumuladores do dia em memória
            baseDailySteps = 0
            accumulatedActiveSecondsToday = 0L
            _currentSteps.value = 0
            _currentDistance.value = 0f
            _currentActiveMinutes.value = 0
            movementSessionSteps = 0
            movementSessionGpsMeters = 0f

            // 3. Atualiza data atual e recarrega dados para a nova data
            lastTrackedDate = today
            prefs.edit().putString("last_tracked_date", today).apply()
            loadTodayMetrics()
        }
    }

    /** Loop periódico de 10 segundos para atualizar calorias basais horárias e checar virada de dia. */
    private fun startPeriodicTimeTicker() {
        periodicRefreshJob?.cancel()
        periodicRefreshJob = scope.launch {
            while (isActive) {
                delay(10000L) // A cada 10s
                checkDateRollover()
                recalculateCalories()
            }
        }
    }

    /**
     * Cálculo Determinístico e Infalível de Calorias:
     * - Basal: BMR * (minutos decorridos no dia / 1440)
     * - Ativo: Distância (km) * Peso (kg) * 0.75
     */
    private fun recalculateCalories() {
        val bmr = calculateDailyBmr(cachedWeightKg, cachedHeightCm, cachedAgeYears, cachedIsFemale)

        // Minutos decorridos desde a meia-noite
        val calendar = Calendar.getInstance()
        val minutesElapsedToday = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
        val basalToday = (bmr * (minutesElapsedToday.toDouble() / 1440.0)).coerceAtLeast(0.0)

        val distanceKm = _currentDistance.value / 1000.0
        val activeCalories = (distanceKm * cachedWeightKg * 0.75).coerceAtLeast(0.0)

        val totalCalories = basalToday + activeCalories

        _currentBasalCalories.value = basalToday.toFloat()
        _currentActiveCalories.value = activeCalories.toFloat()
        _currentTotalCalories.value = totalCalories.toFloat()
    }

    // ------------------------------------------------------------------
    // REGISTRO DE SENSORES
    // ------------------------------------------------------------------
    fun startStepCounter() {
        if (isStepCounterRegistered) return

        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else true

        if (!hasPermission) return

        val stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        if (stepDetector != null) {
            sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_UI)
            isStepCounterRegistered = true
        } else {
            val stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (stepCounter != null) {
                sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_UI)
                isStepCounterRegistered = true
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (isTrackingLocation) return

        val userId = auth.currentUser?.uid ?: return
        scope.launch {
            val profile = profileRepository.getProfile(userId).firstOrNull()
            if (profile?.locationEnabled == false) return@launch

            val hasLocationPerm = ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasLocationPerm) return@launch

            isTrackingLocation = true
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4000)
                .setMinUpdateIntervalMillis(2000)
                .build()

            val callback = object : LocationCallback() {
                private var lastLoc: Location? = null

                override fun onLocationResult(result: LocationResult) {
                    for (loc in result.locations) {
                        latestGpsAccuracyMeters = loc.accuracy
                        latestGpsTimestamp = loc.time
                        addLocationToHistory(loc.latitude, loc.longitude)
                        checkZoneState(loc.latitude, loc.longitude)

                        lastLoc?.let { prev ->
                            val dist = prev.distanceTo(loc)
                            val timeSec = ((loc.time - prev.time) / 1000.0).coerceAtLeast(1.0)
                            val speedKmh = (dist / timeSec) * 3.6

                            // TRAVA 1: Rejeição de Veículo por Velocidade (> 18 km/h)
                            if (speedKmh > MAX_HUMAN_SPEED_KMH) {
                                return@let
                            }

                            // TRAVA 2: Rejeição de Repouso / Jitter de Mesa (Zero-Step Drift)
                            val timeSinceLastStepSec = (System.currentTimeMillis() - lastStepTimestamp) / 1000.0
                            if (_activityState.value == ActivityState.RESTING || timeSinceLastStepSec > 8.0) {
                                return@let
                            }

                            // TRAVA 3: Filtro de Micro-Jitter (< 2.0 metros)
                            if (dist >= 2.0f) {
                                movementSessionGpsMeters += dist
                                _currentGpsDistance.value = movementSessionGpsMeters
                                _currentSpeedKmh.value = speedKmh.toFloat()
                                if (speedKmh > 0.3) {
                                    _currentPaceMinPerKm.value = (60.0 / speedKmh).toFloat().coerceIn(2f, 30f)
                                }
                                onMovementSignalReceived()
                            }
                        }
                        lastLoc = loc
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        }
    }

    // ------------------------------------------------------------------
    // MÁQUINA DE ESTADOS E DETECÇÃO DE MOVIMENTO
    // ------------------------------------------------------------------
    private fun onMovementSignalReceived() {
        val now = System.currentTimeMillis()
        lastStepTimestamp = now

        if (_activityState.value == ActivityState.RESTING) {
            _activityState.value = ActivityState.LIGHT
            movementSessionStartTimestamp = now
            movementSessionSteps = 0
            movementSessionGpsMeters = 0f
            startActiveSecondsTracker()
        }

        restingDetectionJob?.cancel()
        restingDetectionJob = scope.launch {
            delay(INACTIVITY_TIMEOUT_MS)
            onTransitionToResting()
        }
    }

    private fun startActiveSecondsTracker() {
        activeTimerJob?.cancel()
        activeTimerJob = scope.launch {
            while (isActive && _activityState.value != ActivityState.RESTING) {
                delay(1000L)

                // Avalia cadência com janela estabilizada de 5 segundos
                val now = System.currentTimeMillis()
                val recentCount = synchronized(recentStepsQueue) {
                    while (recentStepsQueue.isNotEmpty() && now - recentStepsQueue.peekFirst() > 5000L) {
                        recentStepsQueue.removeFirst()
                    }
                    recentStepsQueue.size
                }

                // Cadência instantânea calculada a partir dos últimos 5 segundos (passos/min)
                val instantCadence = (recentCount / 5.0) * 60.0

                // Filtro EMA (Exponential Moving Average) para transição suave (alfa = 0.35)
                smoothedCadence = if (smoothedCadence == 0.0) instantCadence else (0.35 * instantCadence) + (0.65 * smoothedCadence)

                val sessionSecs = ((now - movementSessionStartTimestamp) / 1000L).coerceAtLeast(1L)

                // Transição suave com histerese: exige ao menos 3 segundos de movimento contínuo
                _activityState.value = if (sessionSecs < 3L || recentCount == 0) {
                    ActivityState.LIGHT
                } else when {
                    smoothedCadence >= 135.0 -> ActivityState.VIGOROUS
                    smoothedCadence >= 80.0 -> ActivityState.MODERATE
                    else -> ActivityState.LIGHT
                }

                // Diretrizes OMS: apenas atividade moderada ou vigorosa conta como minutos ativos cardiorrespiratórios
                if (_activityState.value == ActivityState.MODERATE || _activityState.value == ActivityState.VIGOROUS) {
                    if (isOutsideBothZones.value) {
                        accumulatedActiveSecondsToday += 1L
                    }
                    val currentMins = (accumulatedActiveSecondsToday / 60).toInt()
                    _currentActiveMinutes.value = currentMins
                }
            }
        }
    }

    private fun onTransitionToResting() {
        _activityState.value = ActivityState.RESTING
        smoothedCadence = 0.0
        synchronized(recentStepsQueue) {
            recentStepsQueue.clear()
        }
        activeTimerJob?.cancel()
        activeTimerJob = null

        // Reconciliação e Persistência Final da Sessão
        persistCurrentMetricsToDatabase()
    }

    private fun earthDistanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val R = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        return (2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))).toFloat()
    }

    private fun addLocationToHistory(lat: Double, lng: Double) {
        val key = "location_history"
        val existing = prefs.getString(key, "")?.takeIf { it.isNotEmpty() } ?: ""
        val updated = appendLocationHistory(existing, lat, lng, LOCATION_HISTORY_LIMIT)
        prefs.edit().putString(key, updated).apply()
        if (System.currentTimeMillis() - lastClusterTime > 30000L) {
            clusterAndDetermineZones()
        }
    }

    private fun clusterAndDetermineZones() {
        val key = "location_history"
        val raw = prefs.getString(key, "") ?: return
        val entries = raw.split(";").filter { it.isNotEmpty() }
        if (entries.size < CLUSTER_MIN_SAMPLES) return

        val locations = entries.map {
            val parts = it.split("|")
            if (parts.size == 2) {
                Pair(parts[0].toDoubleOrNull() ?: 0.0, parts[1].toDoubleOrNull() ?: 0.0)
            } else null
        }.filterNotNull()

        if (locations.size < CLUSTER_MIN_SAMPLES) return

        val clusters = mutableListOf<Pair<List<Pair<Double, Double>>, Pair<Double, Double>>>()
        val assigned = BooleanArray(locations.size)

        for (i in locations.indices) {
            if (assigned[i]) continue
            val cluster = mutableListOf<Pair<Double, Double>>()
            var centerLat = locations[i].first
            var centerLng = locations[i].second

            for (j in i until locations.size) {
                if (assigned[j]) continue
                val dist = earthDistanceMeters(centerLat, centerLng, locations[j].first, locations[j].second)
                if (dist <= ZONE_RADIUS_METERS * 5) {
                    cluster.add(locations[j])
                    assigned[j] = true
                    centerLat = cluster.map { it.first }.average()
                    centerLng = cluster.map { it.second }.average()
                }
            }
            if (cluster.isNotEmpty()) {
                clusters.add(Pair(cluster, Pair(centerLat, centerLng)))
            }
        }

        clusters.sortByDescending { it.first.size }

        if (clusters.isNotEmpty()) {
            homeLat = clusters[0].second.first
            homeLng = clusters[0].second.second
        }
        if (clusters.size >= 2) {
            workLat = clusters[1].second.first
            workLng = clusters[1].second.second
        }

        zonesDetermined = true
        lastClusterTime = System.currentTimeMillis()

        prefs.edit()
            .putString("home_lat", homeLat.toString())
            .putString("home_lng", homeLng.toString())
            .putString("work_lat", workLat.toString())
            .putString("work_lng", workLng.toString())
            .putBoolean("zones_determined", zonesDetermined)
            .apply()
    }

    private fun checkZoneState(lat: Double, lng: Double) {
        if (!zonesDetermined) {
            _isInHomeZone.value = true
            _isInWorkZone.value = true
            _isOutsideBothZones.value = true
            return
        }

        val inHome = earthDistanceMeters(lat, lng, homeLat, homeLng) <= ZONE_RADIUS_METERS
        val inWork = earthDistanceMeters(lat, lng, workLat, workLng) <= ZONE_RADIUS_METERS

        _isInHomeZone.value = inHome
        _isInWorkZone.value = inWork
        _isOutsideBothZones.value = !inHome && !inWork
    }

    private fun loadZoneStateFromPrefs() {
        val key = "zones_determined"
        zonesDetermined = prefs.getBoolean(key, false)
        if (zonesDetermined) {
            homeLat = prefs.getString("home_lat", "0.0")?.toDoubleOrNull() ?: 0.0
            homeLng = prefs.getString("home_lng", "0.0")?.toDoubleOrNull() ?: 0.0
            workLat = prefs.getString("work_lat", "0.0")?.toDoubleOrNull() ?: 0.0
            workLng = prefs.getString("work_lng", "0.0")?.toDoubleOrNull() ?: 0.0
        }
    }

    private fun onSingleStepDetected() {
        checkDateRollover()
        val now = System.currentTimeMillis()

        // 1. Filtro Biomecânico de Frequência (Anti-Shake rápido: rejeita agitação frenética de mão > 230 passos/min)
        if (lastAcceptedStepTimestamp > 0L && (now - lastAcceptedStepTimestamp) < MIN_STEP_INTERVAL_MS) {
            return
        }
        lastAcceptedStepTimestamp = now

        // Atualiza fila deslizante dos últimos 5s para cálculo suave de cadência
        synchronized(recentStepsQueue) {
            recentStepsQueue.addLast(now)
            while (recentStepsQueue.isNotEmpty() && now - recentStepsQueue.peekFirst() > 5000L) {
                recentStepsQueue.removeFirst()
            }
        }

        // 2. Trava Anti-Fraude com GPS (Correlação de Passos vs Deslocamento Real)
        if (windowStartTimestamp == 0L) {
            windowStartTimestamp = now
            windowStartStepCount = _currentSteps.value
            windowStartGpsMeters = movementSessionGpsMeters
        } else if (_currentSteps.value - windowStartStepCount >= WINDOW_STEPS_THRESHOLD) {
            val gpsDelta = movementSessionGpsMeters - windowStartGpsMeters
            val hasGoodGps = latestGpsAccuracyMeters <= 20f && (now - latestGpsTimestamp) < 8000L

            if (hasGoodGps && gpsDelta < 3.0f) {
                // Passos detectados sem deslocamento físico com GPS preciso (sacudida contínua no lugar / esteira)
                isStationaryShakingDetected = true
                _currentReliability.value = 0.3f
            } else {
                isStationaryShakingDetected = false
                _currentReliability.value = 0.9f
            }
            // Reinicia a janela de amostragem
            windowStartTimestamp = now
            windowStartStepCount = _currentSteps.value
            windowStartGpsMeters = movementSessionGpsMeters
        }

        // 3. Incremento Imediato de Passos
        _currentSteps.value += 1
        movementSessionSteps += 1
        onMovementSignalReceived()

        // 4. Distância Dinâmica em Tempo Real
        val stride = _calibratedStrideMeters.value
        val stepDistance = _currentSteps.value * stride

        // Ajuste fino com GPS apenas se o deslocamento for comprovado e confiável
        val finalDist = if (!isStationaryShakingDetected && movementSessionGpsMeters > 50f && movementSessionSteps > 20) {
            val sessionStepDist = movementSessionSteps * stride
            val errorRatio = abs(movementSessionGpsMeters - sessionStepDist) / movementSessionGpsMeters
            if (errorRatio < 0.25f) {
                (stepDistance - sessionStepDist) + ((movementSessionGpsMeters + sessionStepDist) / 2f)
            } else {
                stepDistance
            }
        } else {
            stepDistance
        }

        _currentDistance.value = finalDist
        _currentStepsDistance.value = movementSessionSteps * stride

        // 5. Recálculo das Calorias (Basal do Horário + Ativo da Distância)
        recalculateCalories()

        // 6. Persistência periódica a cada 50 passos
        if (_currentSteps.value % 50 == 0) {
            persistCurrentMetricsToDatabase()
        }
    }

    // ------------------------------------------------------------------
    // PERSISTÊNCIA ATÔMICA NO ROOM E DISPARO DE SYNC
    // ------------------------------------------------------------------
    private fun persistCurrentMetricsToDatabase(targetDate: String? = null) {
        val userId = auth.currentUser?.uid ?: return
        val dateToSave = targetDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val steps = _currentSteps.value
        val distance = _currentDistance.value
        val activeMins = (accumulatedActiveSecondsToday / 60).toInt()
        val totalCalories = _currentTotalCalories.value

        // Grava no SharedPreferences de forma síncrona/rápida
        prefs.edit()
            .putInt("steps_${userId}_${dateToSave}", steps)
            .putFloat("dist_${userId}_${dateToSave}", distance)
            .putLong("active_secs_${userId}_${dateToSave}", accumulatedActiveSecondsToday)
            .putFloat("cals_${userId}_${dateToSave}", totalCalories)
            .putString("last_tracked_date", dateToSave)
            .apply()

        // Grava no Room Database de forma atômica
        scope.launch {
            val entity = DailyMetricsEntity(
                userId = userId,
                date = dateToSave,
                steps = steps,
                distanceMeters = distance,
                distanceGpsMeters = movementSessionGpsMeters,
                distanceStepsMeters = steps * _calibratedStrideMeters.value,
                distanceFinalMeters = distance,
                reliabilityScore = _currentReliability.value,
                caloriesBurned = totalCalories,
                activeMinutes = activeMins,
                pendingSync = true
            )
            dailyMetricsDao.mergePhoneMetrics(entity)
            triggerSmartCloudSync()
        }
    }

    /** Força a gravação imediata de todos os acumuladores na memória para o banco Room. */
    fun flush() {
        persistCurrentMetricsToDatabase()
    }

    private fun triggerSmartCloudSync() {
        val now = System.currentTimeMillis()
        if (now - lastCloudSyncTimestamp < CLOUD_SYNC_COOLDOWN_MS) return
        lastCloudSyncTimestamp = now

        val constraints = androidx.work.Constraints.Builder()
            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
            .build()

        val request = androidx.work.OneTimeWorkRequestBuilder<br.com.bragasaude.data.remote.sync.SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            "braga_sync",
            androidx.work.ExistingWorkPolicy.KEEP,
            request
        )
    }

    // ------------------------------------------------------------------
    // FÓRMULAS FISIOLÓGICAS (BMR E IDADE)
    // ------------------------------------------------------------------
    fun calculateDailyBmr(weightKg: Double, heightCm: Double, ageYears: Int, isFemale: Boolean): Double {
        val base = (10.0 * weightKg) + (6.25 * heightCm) - (5.0 * ageYears)
        return if (isFemale) (base - 161.0).coerceAtLeast(900.0) else (base + 5.0).coerceAtLeast(1100.0)
    }

    private fun calculateAge(birthDateStr: String?): Int {
        if (birthDateStr.isNullOrBlank()) return 30
        return try {
            val iso = HealthFormatter.toIsoDateString(birthDateStr) ?: birthDateStr
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val birthDate = sdf.parse(iso) ?: return 30
            val dob = Calendar.getInstance().apply { time = birthDate }
            val today = Calendar.getInstance()
            var age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                age--
            }
            age.coerceIn(12, 115)
        } catch (e: Exception) {
            30
        }
    }

    // ------------------------------------------------------------------
    // EVENTOS DOS SENSORES DO ANDROID
    // ------------------------------------------------------------------
    override fun onSensorChanged(event: SensorEvent?) {
        val type = event?.sensor?.type ?: return

        if (type == Sensor.TYPE_STEP_DETECTOR) {
            onSingleStepDetected()
            return
        }

        if (type == Sensor.TYPE_STEP_COUNTER) {
            val count = event.values[0].toInt()
            if (lastSensorStepCount == -1) {
                lastSensorStepCount = count
                return
            }

            var delta = count - lastSensorStepCount
            if (delta < 0) delta = count // Reboot do dispositivo

            if (delta > 0) {
                lastSensorStepCount = count
                repeat(delta) {
                    onSingleStepDetected()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        const val DEFAULT_STRIDE_METERS = 0.70f
    }
}

