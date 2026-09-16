package br.com.bragasaude.data.util

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.local.DailyMetricsDao
import br.com.bragasaude.data.local.WearableReading
import br.com.bragasaude.data.remote.sync.SyncScheduler
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

data class DeviceSyncState(val running: Boolean = false, val message: String = "Autorize o acesso para importar suas leituras.")
internal fun localDayStart(now: Instant, zone: ZoneId): Instant = now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

class HealthConnectAccess @Inject constructor(@ApplicationContext private val context: Context) {
    fun availability(): Int = HealthConnectClient.getSdkStatus(context)
    fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)
}

@Singleton
class HealthConnectManager @Inject constructor(
    private val access: HealthConnectAccess,
    private val database: BragaDatabase,
    private val dailyMetricsDao: DailyMetricsDao,
    private val auth: FirebaseAuth,
    private val syncScheduler: SyncScheduler
) {
    private val mutex = Mutex()
    private val lastSuccessByUser = mutableMapOf<String, Long>()
    private val _syncState = MutableStateFlow(DeviceSyncState())
    val syncState = _syncState.asStateFlow()
    val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
    val heartPermission = HealthPermission.getReadPermission(HeartRateRecord::class)
    val oxygenPermission = HealthPermission.getReadPermission(OxygenSaturationRecord::class)
    val permissions = setOf(stepsPermission, heartPermission, oxygenPermission)

    fun availability(): Int = access.availability()
    fun isAvailable(): Boolean = availability() == HealthConnectClient.SDK_AVAILABLE
    suspend fun grantedPermissions(): Set<String> = if (isAvailable()) {
        access.client().permissionController.getGrantedPermissions().intersect(permissions)
    } else emptySet()
    suspend fun checkHasPermissions(): Boolean = try {
        grantedPermissions().isNotEmpty()
    } catch (e: CancellationException) { throw e } catch (_: Exception) { false }

    suspend fun syncHealthConnectData(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val uid = auth.currentUser?.uid ?: return@withLock false
            if (!isAvailable()) return@withLock false
            if (!force && System.currentTimeMillis() - (lastSuccessByUser[uid] ?: 0L) < 15 * 60_000L) return@withLock true
            _syncState.value = DeviceSyncState(true, "Buscando leituras compartilhadas…")
            try {
                val client = access.client()
                val granted = grantedPermissions()
                if (granted.isEmpty()) {
                    _syncState.value = DeviceSyncState(message = "Nenhuma permissão autorizada. Você pode tentar novamente ou continuar sem relógio.")
                    return@withLock false
                }
                val now = Instant.now()
                val zone = ZoneId.systemDefault()
                val today = localDayStart(now, zone)
                var steps = 0L
                var imported = 0
                if (stepsPermission in granted && today < now) {
                    steps = client.aggregate(AggregateRequest(
                        metrics = setOf(StepsRecord.COUNT_TOTAL),
                        timeRangeFilter = TimeRangeFilter.between(today, now)
                    ))[StepsRecord.COUNT_TOTAL] ?: 0L
                    if (auth.currentUser?.uid != uid) return@withLock false
                    if (steps > 0) dailyMetricsDao.mergeDeviceSteps(uid, now.atZone(zone).toLocalDate().toString(), steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                }
                val range = TimeRangeFilter.between(now.minusSeconds(30L * 86400), now)
                val dao = database.wearableReadingDao()
                if (heartPermission in granted) {
                    var page: String? = null
                    do {
                        val response = client.readRecords(ReadRecordsRequest(HeartRateRecord::class, range, pageToken = page))
                        val readings = response.records.flatMap { record ->
                            record.samples.map { sample ->
                                WearableReading(uid, "heart:${record.metadata.id}:${sample.time}", "HEART_RATE", sample.beatsPerMinute.toDouble(), sample.time.toEpochMilli(), record.metadata.dataOrigin.packageName, record.metadata.device?.model)
                            }
                        }
                        if (auth.currentUser?.uid != uid) return@withLock false
                        dao.upsert(readings)
                        imported += readings.size
                        page = response.pageToken
                    } while (page != null)
                }
                if (oxygenPermission in granted) {
                    var page: String? = null
                    do {
                        val response = client.readRecords(ReadRecordsRequest(OxygenSaturationRecord::class, range, pageToken = page))
                        val readings = response.records.map { record ->
                            WearableReading(uid, "oxygen:${record.metadata.id}", "OXYGEN_SATURATION", record.percentage.value, record.time.toEpochMilli(), record.metadata.dataOrigin.packageName, record.metadata.device?.model)
                        }
                        if (auth.currentUser?.uid != uid) return@withLock false
                        dao.upsert(readings)
                        imported += readings.size
                        page = response.pageToken
                    } while (page != null)
                }
                if (steps > 0) syncScheduler.scheduleSync()
                lastSuccessByUser[uid] = System.currentTimeMillis()
                _syncState.value = DeviceSyncState(message = if (steps == 0L && imported == 0) {
                    "Acesso autorizado, mas sem leituras no período. Abra o app do relógio e habilite o compartilhamento com Health Connect."
                } else {
                    "Sincronização concluída: $steps passos hoje e $imported leituras consultadas. Registros repetidos não são duplicados."
                })
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: SecurityException) {
                _syncState.value = DeviceSyncState(message = "O acesso mudou. Revise as permissões e tente novamente. Leituras já salvas foram mantidas.")
                false
            } catch (_: Exception) {
                _syncState.value = DeviceSyncState(message = "Não foi possível concluir a sincronização. As leituras já salvas foram mantidas. Tente novamente.")
                false
            } finally {
                if (_syncState.value.running) _syncState.value = DeviceSyncState(message = "Sincronização interrompida. Você pode tentar novamente.")
            }
        }
    }
}
