package br.com.bragasaude.data.remote.sync

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.util.toEntity
import br.com.bragasaude.data.util.parseDate
import br.com.bragasaude.util.BragaConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SyncState {
    object Idle : SyncState
    object Syncing : SyncState
    data class Success(val timestamp: Long = System.currentTimeMillis()) : SyncState
    data class Error(val message: String, val cause: Throwable? = null) : SyncState
}

@Singleton
class SyncManager @Inject constructor(
    private val apiClient: BragaApiClient,
    private val profileDao: ProfileDao,
    private val vitalSignDao: VitalSignDao,
    private val biometryDao: BiometryDao,
    private val examDao: ExamDao,
    private val examItemDao: ExamItemDao,
    private val medicationDao: MedicationDao,
    private val milestoneDao: MilestoneDao,
    private val dailyMetricsDao: DailyMetricsDao,
    private val clinicalReferenceDao: ClinicalReferenceDao,
    private val syncPreferences: SyncPreferences,
    private val familyBridgeRepository: br.com.bragasaude.data.remote.repository.FamilyBridgeRepository,
    private val movementManagerProvider: javax.inject.Provider<br.com.bragasaude.data.util.MovementManager>
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    suspend fun syncUserData(userId: String, force: Boolean = false): Result<Unit> {
        if (userId == BragaConstants.GUEST_UID) return Result.success(Unit)
        if (!force && !syncPreferences.shouldSync(userId)) {
            android.util.Log.d(BragaConstants.SYNC_LOG_TAG, "Cache do Room ainda é recente. Pulando download.")
            return Result.success(Unit)
        }
        return downloadIncrementalUserData(userId, fullSync = force)
    }

    suspend fun downloadAllUserData(userId: String): Result<Unit> {
        return downloadIncrementalUserData(userId, fullSync = true)
    }

    suspend fun downloadIncrementalUserData(userId: String, fullSync: Boolean = false): Result<Unit> {
        if (userId == BragaConstants.GUEST_UID) return Result.success(Unit)
        _syncState.value = SyncState.Syncing
        android.util.Log.d(
            BragaConstants.SYNC_LOG_TAG,
            "Iniciando sync ${if (fullSync) "COMPLETO" else "INCREMENTAL (Delta)"} para: $userId"
        )

        return try {
            val nowUtc = System.currentTimeMillis()

            // 1. Profile (Download sempre completo por ser leve e único)
            val profileBeforeDownload = profileDao.getProfileOneShot(userId)
            val p = apiClient.getProfile(userId)
            if (p != null) {
                profileDao.cacheRemoteProfile(p.toEntity(), profileBeforeDownload)
            }

            // 2. Vital Signs (Delta Sync baseado no último registro)
            val lastVitalsSync = if (fullSync) 0L else syncPreferences.getEntityLastSync(userId, "vitals")
            val remoteVitals = apiClient.getVitalSigns(userId)
            val filteredVitals = if (lastVitalsSync > 0) {
                remoteVitals.filter { v ->
                    val measured = v.measuredAt?.let { parseDate(it) }?.time ?: 0L
                    measured > lastVitalsSync
                }
            } else {
                remoteVitals
            }
            if (filteredVitals.isNotEmpty()) {
                filteredVitals.forEach { v ->
                    val measuredDate = v.measuredAt?.let { parseDate(it) } ?: Date()
                    vitalSignDao.insert(
                        VitalSignEntity(
                            remoteId = v.id,
                            userId = userId,
                            systolicPressure = v.systolicPressure,
                            diastolicPressure = v.diastolicPressure,
                            heartRate = v.heartRate,
                            oxygenSaturation = v.oxygenSaturation,
                            glucoseLevel = v.glucoseLevel,
                            glucoseType = v.glucoseType,
                            hydrationMl = v.hydrationMl,
                            measuredAt = measuredDate,
                            status = "recorded",
                            pendingSync = false
                        )
                    )
                }
                vitalSignDao.deduplicateVitals()
                syncPreferences.recordEntitySyncSuccess(userId, "vitals", nowUtc)
            }

            // 3. Daily Metrics (Delta Sync)
            try {
                val remoteMetrics = apiClient.getDailyMetrics(userId)
                for (m in remoteMetrics) {
                    val local = dailyMetricsDao.getByDate(userId, m.date)
                    if (local == null) {
                        dailyMetricsDao.insert(m.copy(pendingSync = false))
                    } else if (!local.pendingSync) {
                        dailyMetricsDao.insert(
                            local.copy(
                                steps = maxOf(local.steps, m.steps),
                                distanceMeters = maxOf(local.distanceMeters, m.distanceMeters),
                                distanceGpsMeters = maxOf(local.distanceGpsMeters, m.distanceGpsMeters),
                                distanceStepsMeters = maxOf(local.distanceStepsMeters, m.distanceStepsMeters),
                                distanceFinalMeters = maxOf(local.distanceFinalMeters, m.distanceFinalMeters),
                                caloriesBurned = maxOf(local.caloriesBurned, m.caloriesBurned),
                                activeMinutes = maxOf(local.activeMinutes, m.activeMinutes),
                                pendingSync = false
                            )
                        )
                    }
                }
                syncPreferences.recordEntitySyncSuccess(userId, "metrics", nowUtc)
                movementManagerProvider.get().loadTodayMetrics()
            } catch (e: Exception) {
                android.util.Log.e(BragaConstants.SYNC_LOG_TAG, "Erro ao sincronizar métricas diárias: ${e.message}")
            }

            // 4. Vínculos Familiares
            try {
                familyBridgeRepository.syncBindingsForPatient(userId)
                familyBridgeRepository.syncBindingsForCaregiver(userId)
                familyBridgeRepository.cleanExpiredCodes()
            } catch (e: Exception) {
                android.util.Log.e(BragaConstants.SYNC_LOG_TAG, "Erro ao sincronizar vínculos familiares: ${e.message}")
            }

            // 5. Mensagens Familiares
            try {
                familyBridgeRepository.syncFamilyMessages(userId)
            } catch (e: Exception) {
                android.util.Log.e(BragaConstants.SYNC_LOG_TAG, "Erro ao sincronizar mensagens: ${e.message}")
            }

            // 6. Dados de familiares se for cuidador
            try {
                val bindings = familyBridgeRepository.getActiveBindingsForCaregiver(userId).first()
                bindings.forEach { binding ->
                    familyBridgeRepository.syncPatientDataForCaregiver(binding.patientUserId)
                }
            } catch (e: Exception) {
                android.util.Log.w(BragaConstants.SYNC_LOG_TAG, "Aviso: Falha ao sincronizar dependentes: ${e.message}")
            }

            // 7. Retenção de 30 Dias
            try {
                val cal = java.util.Calendar.getInstance()
                cal.add(java.util.Calendar.DAY_OF_YEAR, -30)
                val cutoffDateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(cal.time)
                val thirtyDaysAgoMillis = cal.timeInMillis

                vitalSignDao.purgeOlderThan(thirtyDaysAgoMillis)
                biometryDao.purgeOlderThan(thirtyDaysAgoMillis)
                dailyMetricsDao.purgeOlderThan(cutoffDateStr)
            } catch (e: Exception) {
                android.util.Log.w(BragaConstants.SYNC_LOG_TAG, "Aviso: Falha na retenção de dados locais: ${e.message}")
            }

            syncPreferences.recordSyncSuccess(userId)
            _syncState.value = SyncState.Success(nowUtc)
            Result.success(Unit)
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Erro ao baixar dados do usuário"
            android.util.Log.e(BragaConstants.SYNC_LOG_TAG, "Falha no sync delta: $errorMsg", e)
            _syncState.value = SyncState.Error(errorMsg, e)
            Result.failure(e)
        }
    }

    fun startRealtimeSync() {
        // Realtime local com Room Flow
    }
}
