package br.com.bragasaude.data.remote.repository

import android.content.Context
import br.com.bragasaude.data.local.BleTelemetryReceiptDao
import br.com.bragasaude.data.local.BleTelemetryReceiptEntity
import br.com.bragasaude.data.local.CareAuditDao
import br.com.bragasaude.data.local.CareAuditEntity
import br.com.bragasaude.data.local.SymptomsDiaryDao
import br.com.bragasaude.data.local.SymptomsDiaryEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.BleTelemetryRequest
import br.com.bragasaude.data.remote.model.CareActivityEntry
import br.com.bragasaude.data.remote.model.ClinicalCorrelationsResult
import br.com.bragasaude.data.remote.model.DailyCareBulletin
import br.com.bragasaude.data.remote.model.EmergencyTokenGrant
import br.com.bragasaude.data.remote.model.MedicalAccessGrant
import br.com.bragasaude.data.remote.model.SymptomCheckInCreate
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.util.BragaTime
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Care OS — D62 (First Contract). Camada offline-first para os contratos de
 * coordenação de cuidado: check-in de sintomas (Cena C37), mural de cuidado,
 * acesso médico / emergência e telemetria BLE.
 */
@Singleton
class CareOsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: BragaApiClient,
    private val symptomsDiaryDao: SymptomsDiaryDao,
    private val careAuditDao: CareAuditDao,
    private val bleTelemetryReceiptDao: BleTelemetryReceiptDao,
    private val syncScheduler: SyncScheduler
) {

    // ==================== CENA C37 — CHECK-IN MATINAL ====================

    /**
     * Registra o check-in matinal. Grava local (SQLCipher) primeiro e envia ao
     * gateway; falha de rede deixa pendingSync=true para o SyncWorker.
     */
    suspend fun submitCheckIn(
        patientId: String,
        reportedBy: String = patientId,
        symptomsText: String,
        sleepQuality: Int? = null,
        disposition: Int? = null,
        inputMethod: String = "VOICE"
    ): Boolean {
        if (symptomsText.isBlank()) return false
        val now = BragaTime.nowMillis()
        val entry = SymptomsDiaryEntity(
            id = UUID.randomUUID().toString(),
            patientId = patientId,
            reportedBy = reportedBy,
            reportedAt = Date(now),
            symptomsText = symptomsText.take(1000),
            sleepQuality = sleepQuality?.coerceIn(1, 5),
            disposition = disposition?.coerceIn(1, 5),
            inputMethod = if (inputMethod == "TEXT") "TEXT" else "VOICE",
            pendingSync = true
        )
        symptomsDiaryDao.insert(entry)

        val ok = apiClient.symptomCheckIn(
            SymptomCheckInCreate(
                patientId = patientId,
                reportedAt = BragaTime.toIso(now),
                symptomsText = entry.symptomsText,
                sleepQuality = entry.sleepQuality,
                disposition = entry.disposition,
                inputMethod = entry.inputMethod
            )
        )
        if (ok) symptomsDiaryDao.markSynced(entry.id)
        else syncScheduler.scheduleSync()
        return ok
    }

    /** Já fez check-in hoje? (controla a primeira abertura matinal). */
    suspend fun hasCheckInForToday(patientId: String): Boolean = withContext(Dispatchers.IO) {
        symptomsDiaryDao.getForDay(patientId, BragaTime.startOfTodayMillis()) != null
    }

    fun getCheckInHistory(patientId: String): Flow<List<SymptomsDiaryEntity>> =
        symptomsDiaryDao.getAll(patientId)

    /** Sincroniza entradas recentes do diário de sintomas com a nuvem e persiste no Room local. */
    suspend fun syncSymptomsDiary(patientId: String) = withContext(Dispatchers.IO) {
        try {
            val remote = apiClient.getSymptomsDiary(patientId, limit = 30)
            if (remote.isNotEmpty()) {
                val entities = remote.map {
                    val reportedTime = it.reportedAt?.let { raw -> parseOccurredAt(raw) } ?: Date()
                    SymptomsDiaryEntity(
                        id = it.id ?: UUID.nameUUIDFromBytes(
                            "${it.patientId}|$reportedTime|${it.symptomsText}".toByteArray()
                        ).toString(),
                        patientId = patientId,
                        reportedBy = it.reportedBy ?: patientId,
                        reportedAt = reportedTime,
                        symptomsText = it.symptomsText,
                        sleepQuality = it.sleepQuality,
                        disposition = it.disposition,
                        inputMethod = it.inputMethod ?: "VOICE",
                        pendingSync = false
                    )
                }
                entities.forEach { entity -> symptomsDiaryDao.insert(entity) }
            }
        } catch (e: Exception) {
            android.util.Log.w("CareOs", "syncSymptomsDiary: ${e.message}")
        }
    }

    suspend fun refreshAll(patientId: String) = withContext(Dispatchers.IO) {
        syncCareWall(patientId)
        syncSymptomsDiary(patientId)
    }

    // ==================== MURAL DE CUIDADO (ACTIVITY FEED) ====================

    /** Linha do tempo de quem cuidou do paciente (local + servidor). */
    fun getCareWall(patientId: String): Flow<List<CareAuditEntity>> =
        careAuditDao.getRecent(patientId, 50)

    /** Sincroniza o mural com o servidor, mesclando por id remoto. */
    suspend fun syncCareWall(patientId: String) = withContext(Dispatchers.IO) {
        try {
            val remote = apiClient.getActivityFeed(patientId, limit = 50)
            if (remote.isNotEmpty()) {
                val entities = remote.map {
                    CareAuditEntity(
                        id = it.id ?: UUID.nameUUIDFromBytes(
                            "${it.actorName}|${it.actionType}|${it.occurredAt}".toByteArray()
                        ).toString(),
                        patientId = patientId,
                        actorId = it.actorId ?: patientId,
                        actorName = it.actorName,
                        actionType = it.actionType,
                        detailsJson = it.details ?: "{}",
                        occurredAt = parseOccurredAt(it.occurredAt)
                    )
                }
                careAuditDao.insertAll(entities)
            }
        } catch (e: Exception) {
            android.util.Log.w("CareOs", "syncCareWall: ${e.message}")
        }
    }

    suspend fun getDailyBulletin(patientId: String): DailyCareBulletin? = withContext(Dispatchers.IO) {
        try {
            apiClient.getDailyBulletin(patientId)
        } catch (e: Exception) {
            android.util.Log.w("CareOs", "getDailyBulletin: ${e.message}")
            null
        }
    }

    suspend fun getClinicalCorrelations(patientId: String): ClinicalCorrelationsResult? = withContext(Dispatchers.IO) {
        try {
            apiClient.getClinicalCorrelations(patientId)
        } catch (e: Exception) {
            android.util.Log.w("CareOs", "getClinicalCorrelations: ${e.message}")
            null
        }
    }

    private fun parseOccurredAt(raw: String): Date {
        return try {
            Date.from(java.time.OffsetDateTime.parse(raw).toInstant())
        } catch (_: Exception) {
            try { Date(java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).parse(raw)?.time ?: BragaTime.nowMillis()) }
            catch (_: Exception) { Date(BragaTime.nowMillis()) }
        }
    }

    // ==================== MODO CONSULTA — "LEVA PRO DOUTOR" ====================

    /** Gera o QR Code / magic link de 2 horas para o médico. */
    suspend fun generateMedicalAccess(patientId: String): MedicalAccessGrant? =
        apiClient.generateMedicalAccess(patientId)

    /**
     * Baixa o PDF executivo de 1 página do servidor. Em falha, gera on-device
     * via [br.com.bragasaude.domain.PdfReportGenerator] e devolve o arquivo.
     */
    suspend fun getDoctorReport(
        patientId: String,
        days: Int = 30,
        localFallback: (suspend () -> File?)? = null
    ): File? {
        apiClient.getDoctorReportPdf(patientId, days)?.let { bytes ->
            if (bytes.isNotEmpty()) {
                val directory = File(context.cacheDir, "shared_pdfs").apply { mkdirs() }
                val file = File(directory, "doctor_report_${patientId}_$days.pdf")
                file.outputStream().use { it.write(bytes) }
                return file
            }
        }
        return localFallback?.invoke()
    }

    // ==================== FICHA DE EMERGÊNCIA ====================

    suspend fun generateEmergencyAccess(patientId: String): EmergencyTokenGrant? =
        apiClient.generateEmergencyAccess(patientId)

    // ==================== TELEMETRIA BLE GATT ====================

    /**
     * Ingestão de telemetria BLE (pressão arterial / glicemia). Recebe local e
     * encaminha ao gateway; o receipt fica rastreado.
     */
    suspend fun ingestBle(
        patientId: String,
        deviceId: String,
        deviceType: String,
        measuredAt: Long,
        systolic: Int? = null,
        diastolic: Int? = null,
        glucose: Int? = null
    ): Boolean {
        require(deviceType == "BLOOD_PRESSURE" || deviceType == "GLUCOSE") {
            "deviceType deve ser BLOOD_PRESSURE ou GLUCOSE."
        }
        val request = BleTelemetryRequest(
            patientId = patientId,
            deviceId = deviceId,
            deviceType = deviceType,
            measuredAt = BragaTime.toIso(measuredAt),
            systolicPressure = systolic,
            diastolicPressure = diastolic,
            glucoseLevel = glucose
        )
        val ok = apiClient.ingestBleTelemetry(request)
        bleTelemetryReceiptDao.insert(
            BleTelemetryReceiptEntity(
                id = UUID.nameUUIDFromBytes(
                    "$patientId|$deviceId|$measuredAt".toByteArray()
                ).toString(),
                patientId = patientId,
                deviceId = deviceId,
                deviceType = deviceType,
                protocol = "GATT",
                measuredAt = Date(measuredAt),
                systolicPressure = systolic,
                diastolicPressure = diastolic,
                glucoseLevel = glucose,
                pendingSync = !ok
            )
        )
        if (!ok) syncScheduler.scheduleSync()
        return ok
    }

    fun getBleReceipts(patientId: String): Flow<List<BleTelemetryReceiptEntity>> =
        bleTelemetryReceiptDao.getAll(patientId)
}
