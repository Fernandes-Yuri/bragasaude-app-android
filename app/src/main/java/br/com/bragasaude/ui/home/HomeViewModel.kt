package br.com.bragasaude.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.model.*
import br.com.bragasaude.data.remote.repository.*
import br.com.bragasaude.data.util.toRemote
import br.com.bragasaude.domain.HealthCalculators
import br.com.bragasaude.domain.HealthEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import br.com.bragasaude.data.remote.sync.userIds
import javax.inject.Inject

import br.com.bragasaude.ui.util.NotificationHelper
import android.content.Context
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import br.com.bragasaude.data.remote.sync.SyncManager
import dagger.hilt.android.qualifiers.ApplicationContext
import br.com.bragasaude.util.BragaConstants

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val vitalsRepository: VitalsRepository,
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
    @ApplicationContext private val context: Context,
    private val notificationCenterRepository: NotificationCenterRepository? = null
) : ViewModel() {

    constructor(
        vitalsRepository: VitalsRepository,
        milestonesRepository: MilestonesRepository,
        profileRepository: ProfileRepository,
        medicationRepository: MedicationRepository,
        database: br.com.bragasaude.data.local.BragaDatabase,
        syncManager: SyncManager,
        movementManager: br.com.bragasaude.data.util.MovementManager,
        dailyMetricsDao: br.com.bragasaude.data.local.DailyMetricsDao,
        healthEngine: HealthEngine,
        healthConnectManager: br.com.bragasaude.data.util.HealthConnectManager,
        auth: FirebaseAuth,
        context: Context
    ) : this(
        vitalsRepository = vitalsRepository,
        milestonesRepository = milestonesRepository,
        profileRepository = profileRepository,
        medicationRepository = medicationRepository,
        database = database,
        syncManager = syncManager,
        movementManager = movementManager,
        dailyMetricsDao = dailyMetricsDao,
        healthEngine = healthEngine,
        healthConnectManager = healthConnectManager,
        auth = auth,
        context = context,
        notificationCenterRepository = null
    )

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _userName = MutableStateFlow("Usuário")
    val userName = _userName.asStateFlow()

    private val todayDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

    private val _dashboardVitals = MutableStateFlow(RemoteVitalSign(userId = ""))
    val dashboardVitals = _dashboardVitals.asStateFlow()
    private val _readings = MutableStateFlow(br.com.bragasaude.domain.HomeReadingSummary())
    val readings = _readings.asStateFlow()

    private val _latestMilestone = MutableStateFlow<RemoteMilestone?>(null)
    val latestMilestone = _latestMilestone.asStateFlow()

    private val _clinicalAlerts = MutableStateFlow<List<String>>(emptyList())
    val clinicalAlerts = _clinicalAlerts.asStateFlow()

    private val userIds = auth.userIds().stateIn(
        viewModelScope, SharingStarted.Eagerly, auth.currentUser?.uid ?: BragaConstants.GUEST_UID
    )

    val userStepGoal: StateFlow<Int> = userIds
        .flatMapLatest { profileRepository.getProfile(it) }
        .map { it?.stepGoal ?: 8000 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 8000)

    val avatarProfile = userIds.flatMapLatest { profileRepository.getProfile(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val profile: StateFlow<RemoteProfile?> = userIds
        .flatMapLatest { profileRepository.getProfile(it) }
        .map { it?.toRemote() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val medications = userIds
        .flatMapLatest { medicationRepository.getMedications(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun readingHistory(metric: String) = userIds.flatMapLatest { uid ->
        combine(
            database.wearableReadingDao().observeRecent(uid, metric),
            vitalsRepository.getVitalSigns(uid)
        ) { wearable, vitals -> br.com.bragasaude.domain.HealthReadingHistory.merge(uid, metric, wearable, vitals) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val heartReadings = readingHistory(br.com.bragasaude.domain.HealthReadingInput.HEART)
    val oxygenReadings = readingHistory(br.com.bragasaude.domain.HealthReadingInput.OXYGEN)

    private val alertPrefs = context.getSharedPreferences("braga_alerts_prefs", Context.MODE_PRIVATE)
    private var dismissedAlertsKey = "dismissed_alerts_${userIds.value}_$todayDateStr"
    private val dismissedAlerts: MutableSet<String> = alertPrefs.getStringSet(dismissedAlertsKey, emptySet())?.toMutableSet() ?: mutableSetOf()

    private val readAlertsKey = "read_alerts_${auth.currentUser?.uid ?: BragaConstants.GUEST_UID}_$todayDateStr"
    private val readStateStore = br.com.bragasaude.data.local.AlertReadStateStore(alertPrefs, readAlertsKey)
    val readAlerts = readStateStore.readAlerts
    val alertIdentifiedAt = readStateStore.identifiedAt

    private val _selectedNotificationCategory = MutableStateFlow(NotificationCategoryFilter.ALL)
    val selectedNotificationCategory: StateFlow<NotificationCategoryFilter> = _selectedNotificationCategory.asStateFlow()

    private val _notificationsLoading = MutableStateFlow(false)
    val notificationsLoading: StateFlow<Boolean> = _notificationsLoading.asStateFlow()

    private val _notificationsLoadingMore = MutableStateFlow(false)
    val notificationsLoadingMore: StateFlow<Boolean> = _notificationsLoadingMore.asStateFlow()

    private val _notificationsSyncError = MutableStateFlow<String?>(null)
    val notificationsSyncError: StateFlow<String?> = _notificationsSyncError.asStateFlow()

    private val _unavailableDestinationMessage = MutableStateFlow<String?>(null)
    val unavailableDestinationMessage: StateFlow<String?> = _unavailableDestinationMessage.asStateFlow()

    private val rawPersistedNotifications: StateFlow<List<br.com.bragasaude.data.local.NotificationItemEntity>> =
        combine(userIds, _selectedNotificationCategory) { uid, category -> uid to category }
            .flatMapLatest { (uid, category) ->
                notificationCenterRepository?.observeActiveItems(uid, category) ?: flowOf(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val persistedNotifications: StateFlow<List<NotificationDisplayItem>> =
        rawPersistedNotifications
            .map { entities ->
                val repo = notificationCenterRepository ?: return@map emptyList()
                entities.map { repo.toDisplayItem(it) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val persistedUnreadCount: StateFlow<Int> = userIds
        .flatMapLatest { uid ->
            notificationCenterRepository?.observeUnreadGroupCount(uid) ?: flowOf(0)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val notificationsHasMore: StateFlow<Boolean> = userIds
        .flatMapLatest { uid ->
            notificationCenterRepository?.observeSyncState(uid)?.map { it?.hasMorePages == true } ?: flowOf(false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val totalUnreadNotificationsCount: StateFlow<Int> = combine(
        persistedUnreadCount,
        _clinicalAlerts,
        readAlerts
    ) { persistedUnread, alerts, readSet ->
        val localUnread = alerts.count { it !in readSet }
        persistedUnread + localUnread
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    private val alertPrefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        when (key) {
            dismissedAlertsKey -> {
                dismissedAlerts.clear()
                dismissedAlerts.addAll(prefs.getStringSet(key, emptySet()).orEmpty())
                _clinicalAlerts.value = _clinicalAlerts.value.filterNot { it in dismissedAlerts }
            }
        }
    }

    private fun saveDismissedAlerts() {
        alertPrefs.edit().putStringSet(dismissedAlertsKey, dismissedAlerts).apply()
    }

    init {
        alertPrefs.registerOnSharedPreferenceChangeListener(alertPrefsListener)
        viewModelScope.launch {
            try {
                vitalsRepository.deduplicateVitals()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {}
        }
        viewModelScope.launch { userIds.collectLatest { observeUser(it) } }
    }

    private suspend fun observeUser(userId: String) = coroutineScope {
        _userName.value = "Usuário"
        _dashboardVitals.value = RemoteVitalSign(userId = userId)
        _readings.value = br.com.bragasaude.domain.HomeReadingSummary(userId = userId)
        _latestMilestone.value = null
        _clinicalAlerts.value = emptyList()
        _notificationsSyncError.value = null
        _unavailableDestinationMessage.value = null
        dismissedAlertsKey = "dismissed_alerts_${userId}_$todayDateStr"
        dismissedAlerts.clear()
        dismissedAlerts.addAll(alertPrefs.getStringSet(dismissedAlertsKey, emptySet()).orEmpty())
        readStateStore.switchKey("read_alerts_${userId}_$todayDateStr")

        launch {
            profileRepository.getProfile(userId).collectLatest { profile ->
                _userName.value = profile?.fullName?.split(" ")?.firstOrNull() ?: "Visitante"
            }
        }

        // Evita cancelar a análise de alertas a cada passo durante uma caminhada.
        // O debounce mantém a atualização dos dados de movimento e dos alertas.
        val debouncedSteps = movementManager.currentSteps.debounce(2_000).onStart { emit(movementManager.currentSteps.value) }

        val healthDataFlow = combine(
            vitalsRepository.getVitalSigns(userId),
            profileRepository.getProfile(userId),
            dailyMetricsDao.getRecent30Days(userId),
            debouncedSteps
        ) { vitalsEntities, profileEntity, dailyMetrics, liveSteps ->
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

            Pair(latest, Triple(userId, vitals, updatedMetrics))
        }.shareIn(this, SharingStarted.WhileSubscribed())

        // Atualiza UI principal
        launch {
            healthDataFlow.collect { (latest, data) ->
                _dashboardVitals.value = latest
                _readings.value = br.com.bragasaude.domain.HomeReadingSummary.from(userId, data.second)
            }
        }

        // Análise de Alertas (com collectLatest para evitar processamento pesado repetitivo)
        launch {
            healthDataFlow.map { it.second }.collectLatest { (uid, vitals, metrics) ->
                val profile = profileRepository.getProfile(uid).firstOrNull()?.toRemote()

                val (vitalAnalysis, activityRecommendations) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    healthEngine.analyzeVitals(uid, vitals.take(1), silent = true) to
                        healthEngine.analyzeActivityPatterns(uid, metrics)
                }

                val alerts = mutableListOf<String>()
                alerts.addAll(vitalAnalysis.alerts)
                alerts.addAll(activityRecommendations.map { it.alertText })

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
                readStateStore.identify(_clinicalAlerts.value.toSet())
            }
        }

        launch {
            milestonesRepository.getMilestones(userId).collectLatest { entities ->
                _latestMilestone.value = entities.firstOrNull()?.toRemote()
            }
        }

        launch {
            kotlinx.coroutines.delay(350)
            if (userId == BragaConstants.GUEST_UID) return@launch
            try {
                vitalsRepository.migrateAndSyncLegacyManualReadings(userId) { auth.currentUser?.uid }
                medicationRepository.syncMedicationsFromServer(userId)
                notificationCenterRepository?.syncNotifications()
                syncManager.syncUserData(userId, force = false)
                if (healthConnectManager.isAvailable() && healthConnectManager.checkHasPermissions()) {
                    healthConnectManager.syncHealthConnectData()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("HomeViewModel", "Falha ao sincronizar início: ${e.message}")
            }
        }
    }

    fun refreshData() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                medicationRepository.syncMedicationsFromServer(userId)
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

    /**
     * Sincroniza ativamente as permissões concedidas pelo sistema e expurga os cartões
     * de solicitação de permissão sem exigir descarte manual (SPEC-02).
     */
    fun syncGrantedPermissions(customContext: Context? = null) {
        val ctx = customContext ?: context
        val hasNotifications = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

        val hasLocation = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                          ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val hasActivity = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else true

        val currentAlerts = _clinicalAlerts.value
        val updatedAlerts = currentAlerts.filterNot { alert ->
            when {
                alert.startsWith("PERMISSION_NOTIFICATIONS:") -> hasNotifications
                alert.startsWith("PERMISSION_REQUIRED:") -> (hasLocation && hasActivity)
                else -> false
            }
        }
        if (updatedAlerts != currentAlerts) {
            _clinicalAlerts.value = updatedAlerts
        }
    }

    fun markAlertAsRead(alert: String) {
        readStateStore.markRead(setOf(alert))
    }

    fun markAlertsAsRead() {
        readStateStore.markRead(_clinicalAlerts.value.toSet())
        val repo = notificationCenterRepository ?: return
        viewModelScope.launch {
            runCatching { repo.markAllRead() }
        }
    }

    fun selectNotificationCategory(filter: NotificationCategoryFilter) {
        _selectedNotificationCategory.value = filter
    }

    fun refreshNotifications(forceSnapshotIfEmpty: Boolean = false) {
        val repo = notificationCenterRepository ?: return
        val uid = auth.currentUser?.uid ?: return
        if (uid.isBlank() || uid == BragaConstants.GUEST_UID) return
        viewModelScope.launch {
            _notificationsLoading.value = true
            _notificationsSyncError.value = null
            try {
                repo.syncNotifications(forceSnapshotIfEmpty = forceSnapshotIfEmpty)
                    .onFailure {
                        _notificationsSyncError.value = "Não foi possível atualizar todos os avisos agora. Exibindo o histórico salvo neste aparelho."
                    }
            } finally {
                _notificationsLoading.value = false
            }
        }
    }

    fun loadMoreNotifications() {
        val repo = notificationCenterRepository ?: return
        if (_notificationsLoadingMore.value) return
        viewModelScope.launch {
            _notificationsLoadingMore.value = true
            _notificationsSyncError.value = null
            try {
                repo.loadMore(_selectedNotificationCategory.value)
                    .onFailure {
                        _notificationsSyncError.value = "Não foi possível carregar mais avisos. Verifique sua conexão e tente novamente."
                    }
            } finally {
                _notificationsLoadingMore.value = false
            }
        }
    }

    fun openPersistedNotification(
        item: NotificationDisplayItem,
        onNavigate: (br.com.bragasaude.ui.util.Screen) -> Unit
    ) {
        val repo = notificationCenterRepository ?: return
        val rawEntity = rawPersistedNotifications.value.firstOrNull { it.id == item.id } ?: return
        viewModelScope.launch {
            when (val result = repo.openItemAndResolveNavigation(rawEntity)) {
                is NotificationNavigationResult.Navigate -> onNavigate(result.screen)
                is NotificationNavigationResult.Unavailable -> {
                    _unavailableDestinationMessage.value = result.message
                }
            }
        }
    }

    fun archivePersistedNotification(item: NotificationDisplayItem) {
        val repo = notificationCenterRepository ?: return
        viewModelScope.launch {
            runCatching { repo.archiveItem(item.id) }
        }
    }

    fun dismissUnavailableDestinationMessage() {
        _unavailableDestinationMessage.value = null
    }

    override fun onCleared() {
        alertPrefs.unregisterOnSharedPreferenceChangeListener(alertPrefsListener)
        readStateStore.close()
        super.onCleared()
    }

}
