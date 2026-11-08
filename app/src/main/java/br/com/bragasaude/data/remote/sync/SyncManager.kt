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
    private val medicationDao: MedicationDao,
    private val milestoneDao: MilestoneDao,
    private val dailyMetricsDao: DailyMetricsDao,
    private val syncPreferences: SyncPreferences,
    private val familyBridgeRepository: br.com.bragasaude.data.remote.repository.FamilyBridgeRepository,
    private val movementManagerProvider: javax.inject.Provider<br.com.bragasaude.data.util.MovementManager>,
    private val notificationCenterRepository: br.com.bragasaude.data.remote.repository.NotificationCenterRepository? = null
) {
    constructor(
        apiClient: BragaApiClient,
        profileDao: ProfileDao,
        vitalSignDao: VitalSignDao,
        biometryDao: BiometryDao,
        medicationDao: MedicationDao,
        milestoneDao: MilestoneDao,
        dailyMetricsDao: DailyMetricsDao,
        syncPreferences: SyncPreferences,
        familyBridgeRepository: br.com.bragasaude.data.remote.repository.FamilyBridgeRepository,
        movementManagerProvider: javax.inject.Provider<br.com.bragasaude.data.util.MovementManager>
    ) : this(
        apiClient = apiClient,
        profileDao = profileDao,
        vitalSignDao = vitalSignDao,
        biometryDao = biometryDao,
        medicationDao = medicationDao,
        milestoneDao = milestoneDao,
        dailyMetricsDao = dailyMetricsDao,
        syncPreferences = syncPreferences,
        familyBridgeRepository = familyBridgeRepository,
        movementManagerProvider = movementManagerProvider,
        notificationCenterRepository = null
    )
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
            // Nova instalação publica sua chave antes de baixar dados ou habilitar o chat.
            // Sync incremental não deve disputar a identidade com outra instalação em segundo plano.
            if (fullSync) familyBridgeRepository.prepareSessionKeys(userId)
            val nowUtc = System.currentTimeMillis()

            // 1. Profile (Download sempre completo por ser leve e único)
            val profileBeforeDownload = profileDao.getProfileOneShot(userId)
            val p = apiClient.getProfile(userId)
            if (p != null) {
                profileDao.cacheRemoteProfile(p.toEntity(), profileBeforeDownload)
            }

            // Cursor de alterações também inclui registros atrasados, correções e estornos.
            familyBridgeRepository.syncVitalChanges(userId)
            familyBridgeRepository.syncConsultations(userId)

            // Biometria compartilhada entre app e WhatsApp; cache por ID remoto.
            for (biometry in apiClient.getBiometry(userId, requireAvailable = fullSync)) {
                biometryDao.cacheRemote(biometry.toEntity())
            }

            // 3. Daily Metrics (Delta Sync)
            try {
                // DT-052: getDailyMetrics devolve null em falha de rede — não
                // registrar sucesso (o cursor não avança com dados ausentes).
                val remoteMetrics = apiClient.getDailyMetrics(userId)
                if (remoteMetrics == null) {
                    android.util.Log.w(BragaConstants.SYNC_LOG_TAG, "Falha ao baixar métricas diárias; cursor de métricas preservado.")
                } else for (m in remoteMetrics) {
                    dailyMetricsDao.cacheRemote(m)
                }

                if (remoteMetrics != null) {
                    syncPreferences.recordEntitySyncSuccess(userId, "metrics", nowUtc)
                }
                movementManagerProvider.get().loadTodayMetrics()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(BragaConstants.SYNC_LOG_TAG, "Erro ao sincronizar métricas diárias: ${e.message}")
            }

            // 4. Vínculos Familiares
            try {
                familyBridgeRepository.syncBindingsForPatient(userId, strict = fullSync)
                familyBridgeRepository.syncBindingsForCaregiver(userId, strict = fullSync)
                familyBridgeRepository.cleanExpiredCodes()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (fullSync) throw e
                android.util.Log.e(BragaConstants.SYNC_LOG_TAG, "Erro ao sincronizar vínculos familiares: ${e.message}")
            }

            // 5. Mensagens Familiares
            try {
                familyBridgeRepository.syncFamilyMessages(userId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e(BragaConstants.SYNC_LOG_TAG, "Erro ao sincronizar mensagens: ${e.message}")
            }

            // 6. Dados de familiares se for cuidador
            try {
                val bindings = familyBridgeRepository.getActiveBindingsForCaregiver(userId).first()
                bindings.forEach { binding ->
                    familyBridgeRepository.syncPatientDataForCaregiver(binding.patientUserId)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(BragaConstants.SYNC_LOG_TAG, "Aviso: Falha ao sincronizar dependentes: ${e.message}")
            }

            // 6B. Central de Notificações persistente (RTL-07)
            try {
                notificationCenterRepository?.syncNotifications(forceSnapshotIfEmpty = fullSync)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(BragaConstants.SYNC_LOG_TAG, "Aviso: Falha ao sincronizar central de notificações: ${e.message}")
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
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(BragaConstants.SYNC_LOG_TAG, "Aviso: Falha na retenção de dados locais: ${e.message}")
            }

            syncPreferences.recordSyncSuccess(userId)
            _syncState.value = SyncState.Success(nowUtc)
            Result.success(Unit)
        } catch (e: kotlinx.coroutines.CancellationException) {
            _syncState.value = SyncState.Idle
            throw e
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
