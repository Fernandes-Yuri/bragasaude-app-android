package br.com.bragasaude.data.remote.repository

import android.content.Context
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.MedicationCreate
import br.com.bragasaude.data.remote.model.MedicationStockItem
import br.com.bragasaude.data.remote.model.MedicationTakeRequest
import br.com.bragasaude.data.remote.model.MedicationRestockRequest
import br.com.bragasaude.data.remote.model.PrescriptionCreate
import br.com.bragasaude.data.remote.model.BarcodeMedication
import br.com.bragasaude.data.remote.model.RemoteMedication
import br.com.bragasaude.data.remote.model.RemoteMedicationLog
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.data.util.toEntity
import br.com.bragasaude.ui.util.MedicationAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import br.com.bragasaude.domain.MedicationSchedule
import br.com.bragasaude.util.BragaTime
import java.util.UUID
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import br.com.bragasaude.util.BragaConstants

@Singleton
class MedicationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiClient: BragaApiClient,
    private val medicationDao: MedicationDao,
    private val medicationLogDao: MedicationLogDao,
    private val careAuditDao: CareAuditDao,
    private val syncScheduler: SyncScheduler
) {
    private val guestId = BragaConstants.GUEST_UID

    fun getMedications(userId: String): Flow<List<MedicationEntity>> = medicationDao.getAll(userId)

    fun getLogsForToday(userId: String): Flow<List<MedicationLogEntity>> {
        return medicationLogDao.getAllLogs(userId)
    }

    suspend fun saveMedication(med: RemoteMedication) {
        val old = medicationDao.getAllSync(med.userId).filter { it.id == med.id }
        MedicationAlarmReceiver.cancelAllAlarms(context, old)
        val entity = med.toEntity().copy(pendingSync = true)
        medicationDao.insert(entity)

        // Re-agendar todos os alarmes do usuário após inserir/atualizar
        syncAlarmsWithDatabase(entity.userId)
        triggerSync()

        // AUD-AN31: REMOVIDO bloco "Sincronização via API" — apiClient era
        // injetado e nunca usado; o bloco só re-inseria a MESMA entity (insert
        // duplicado no catch "por segurança"). A sincronização real é feita
        // pelo SyncWorker via pendingSync, já setado acima. Código morto com
        // comentário enganoso removido.
        if (med.userId == guestId) return
    }

    suspend fun takeMedication(userId: String, medId: String, time: String? = null,
                               date: java.time.LocalDate = java.time.LocalDate.now()): Boolean {
        val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: guestId
        require(currentUser == userId) { "A conta mudou. Abra novamente o aplicativo." }
        val med = medicationDao.getAllSync(userId).firstOrNull { it.id == medId } ?: return false
        val times = MedicationSchedule.times(med.scheduleTimes, med.scheduleTime)
        val doseTime = time ?: times.firstOrNull() ?: return false
        require(doseTime in times && !date.isAfter(java.time.LocalDate.now()))
        require(MedicationSchedule.available(date, doseTime, java.time.LocalDateTime.now()))
        val key = MedicationSchedule.key(date, doseTime)
        val legacyTaken = doseTime == times.firstOrNull() && medicationLogDao.getAllLogs(userId).first().any {
            it.medicationId == medId && it.scheduledFor == null &&
                it.takenAt.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate() == date
        }
        if (legacyTaken) return false
        val entity = MedicationLogEntity(id = MedicationSchedule.logId(userId, medId, key),
            userId = userId, medicationId = medId, scheduledFor = key, takenAt = Date(), pendingSync = true)
        if (medicationLogDao.insertOnce(entity) == -1L) return false
        triggerSync()
        return true
    }

    suspend fun deleteMedication(medId: String, userId: String) {
        MedicationAlarmReceiver.cancelAllAlarms(context, medicationDao.getAllSync(userId).filter { it.id == medId })
        medicationDao.deleteById(medId)

        // Re-agendar alarmes após remoção
        syncAlarmsWithDatabase(userId)
    }

    /**
     * TASK-MED-02: sincroniza alarmes do AlarmManager com o banco local.
     * Cancela TODOS os alarmes do usuário e reagenda baseado nos medicamentos atuais.
     * Garante consistência após inserir, atualizar ou remover medicamentos.
     */
    suspend fun syncAlarmsWithDatabase(userId: String) {
        try {
            val medications = medicationDao.getAllSync(userId)
            MedicationAlarmReceiver.cancelAllAlarms(context, medications)
            MedicationAlarmReceiver.scheduleAllAlarms(context, userId, medications)
        } catch (e: Exception) {
            android.util.Log.e("MedAlarm", "Erro ao sincronizar alarmes: ${e.message}", e)
        }
    }

    /**
     * TASK-MED-02: cancela todos os alarmes de um usuário.
     * Usado em logout e exclusão de conta para limpeza completa.
     */
    suspend fun cancelAllUserAlarms(userId: String) {
        try {
            val medications = medicationDao.getAllSync(userId)
            MedicationAlarmReceiver.cancelAllAlarms(context, medications)
        } catch (e: Exception) {
            android.util.Log.e("MedAlarm", "Erro ao cancelar alarmes no logout: ${e.message}", e)
        }
    }

    // ==================== CARE OS — D62 (FIRST CONTRACT) ====================

    /** Consulta o catálogo ANVISA pelo EAN-13 da caixa (scanner). */
    suspend fun lookupBarcode(ean: String): BarcodeMedication? = apiClient.lookupBarcode(ean)

    companion object {
        /** Chave idempotente canônica: paciente * medicamento * instante agendado. */
        const val IDEMPOTENCY_SEPARATOR = "*"
        private const val IDEMPOTENCY_MAX_LENGTH = 100

        /**
         * Gera a chave idempotente da dose (${patientId}*${medicationId}*${scheduledForIso}).
         * Limitada ao maxLength do contrato (100); caso exceda, cai num digest
         * estável — a propriedade necessária é que a MESMA dose sempre gere a
         * MESMA chave, em qualquer aparelho.
         */
        fun idempotencyKey(patientId: String, medicationId: String, scheduledForIso: String): String {
            val raw = "$patientId$IDEMPOTENCY_SEPARATOR$medicationId$IDEMPOTENCY_SEPARATOR$scheduledForIso"
            if (raw.length <= IDEMPOTENCY_MAX_LENGTH) return raw
            val sha = java.security.MessageDigest.getInstance("SHA-256")
                .digest(raw.toByteArray(Charsets.UTF_8))
            return "idem-" + sha.joinToString("") { "%02x".format(it) }.take(88)
        }
    }

    /**
     * Registra a toma de uma dose no Care OS (offline-first + idempotente).
     *
     * Fluxo: grava local primeiro (insert determinístico por PK + índice único
     * de idempotência), depois chama o gateway. HTTP 409 significa que a dose
     * já foi registrada por outro cuidador/dispositivo — o estado local é
     * sincronizado e NENHUM decremento duplicado acontece.
     */
    suspend fun takeDose(
        userId: String,
        medId: String,
        time: String? = null,
        date: java.time.LocalDate = java.time.LocalDate.now(BragaTime.ZONE),
        units: Int = 1,
        actorId: String = userId
    ): BragaApiClient.TakeMedicationResult {
        val med = medicationDao.getById(medId)
            ?: return BragaApiClient.TakeMedicationResult.Failure("Medicamento não encontrado.")
        val times = MedicationSchedule.times(med.scheduleTimes, med.scheduleTime)
        val doseTime = time?.takeIf { it in times } ?: times.firstOrNull()
            ?: return BragaApiClient.TakeMedicationResult.Failure("Horário de dose não definido.")
        val scheduleKey = MedicationSchedule.key(date, doseTime)
        val scheduledForIso = BragaTime.toIso(date, java.time.LocalTime.parse(doseTime))
        val idemKey = idempotencyKey(userId, medId, scheduledForIso)

        // 1) Local-first: PK determinística + índice único de idempotência.
        val logId = MedicationSchedule.logId(userId, medId, scheduleKey)
        val log = MedicationLogEntity(
            id = logId,
            userId = userId,
            medicationId = medId,
            scheduledFor = scheduleKey,
            takenAt = Date(),
            actorId = actorId,
            unitsTaken = units.coerceAtLeast(1),
            idempotencyKey = idemKey,
            careOsScheduledFor = scheduledForIso,
            pendingSync = true
        )
        if (medicationLogDao.insertOnce(log) == -1L) {
            // Já estava registrado localmente — nada a decrementar.
            return BragaApiClient.TakeMedicationResult.AlreadyTaken
        }

        // 2) Gateway: decremento autoritativo.
        val result = apiClient.takeMedication(
            medId,
            MedicationTakeRequest(scheduledFor = scheduledForIso, unitsTaken = units, idempotencyKey = idemKey)
        )
        return when (result) {
            is BragaApiClient.TakeMedicationResult.Success -> {
                medicationDao.decrementUnits(medId, units.coerceAtLeast(1))
                medicationLogDao.markSynced(logId)
                syncStockFromServer(userId)
                recordAudit(userId, "MEDICATION_TAKEN", "${med.name} registrado", actorId)
                triggerSync()
                BragaApiClient.TakeMedicationResult.Success
            }
            is BragaApiClient.TakeMedicationResult.AlreadyTaken -> {
                // 409: outro ator já registrou. Sincroniza sem decrementar duas vezes.
                medicationLogDao.markSynced(logId)
                syncStockFromServer(userId)
                BragaApiClient.TakeMedicationResult.AlreadyTaken
            }
            is BragaApiClient.TakeMedicationResult.Failure -> {
                // Mantém pendingSync para re-tentativa pelo SyncWorker.
                triggerSync()
                result
            }
        }
    }

    /**
     * Cadastro Care OS: bipou a caixa (EAN-13), consultou a ANVISA, informou a
     * quantidade da caixa e confirmou contra a receita (RDC 657/2022).
     * Retorna o id local do medicamento, ou null em falha.
     */
    suspend fun createCareOsMedication(
        patientId: String,
        name: String,
        totalUnits: Int,
        scheduleTimes: List<String>,
        confirmedWithPrescription: Boolean,
        barcode: BarcodeMedication? = null,
        dosageMg: Double? = null,
        alertThresholdDays: Int = 5,
        photoReferenceUrl: String? = null,
        prescription: PrescriptionCreate? = null,
        actorName: String? = null
    ): String? {
        require(name.length >= 2) { "Nome do medicamento muito curto." }
        require(scheduleTimes.isNotEmpty()) { "Defina ao menos um horário." }
        require(totalUnits >= 0) { "Quantidade inválida." }
        // Regra inegociável: cadastro sem receita não existe (RDC 657/2022).
        require(confirmedWithPrescription) { "É necessário conferir com a receita médica." }

        val body = MedicationCreate(
            eanBarcode = barcode?.eanBarcode,
            name = name,
            activePrinciple = barcode?.activePrinciple,
            manufacturer = barcode?.manufacturer,
            dosageMg = dosageMg,
            pharmaceuticalForm = barcode?.pharmaceuticalForm,
            scheduleTimes = scheduleTimes.sorted(),
            totalUnits = totalUnits,
            alertThresholdDays = alertThresholdDays.coerceIn(1, 30),
            photoReferenceUrl = photoReferenceUrl,
            confirmedWithPrescription = true,
            prescription = prescription
        )
        val serverId = try {
            apiClient.createMedication(patientId, body)
        } catch (e: Exception) {
            null
        }
        val localId = serverId ?: "med-${UUID.randomUUID()}"
        medicationDao.insert(
            MedicationEntity(
                id = localId,
                userId = patientId,
                name = name,
                dosageMg = dosageMg,
                scheduleTimes = scheduleTimes.sorted().joinToString(","),
                eanBarcode = barcode?.eanBarcode,
                activePrinciple = barcode?.activePrinciple,
                manufacturer = barcode?.manufacturer,
                pharmaceuticalForm = barcode?.pharmaceuticalForm,
                totalUnits = totalUnits,
                currentUnits = totalUnits,
                alertThresholdDays = alertThresholdDays.coerceIn(1, 30),
                photoReferenceUrl = photoReferenceUrl,
                confirmedWithPrescription = true,
                lastRestockDate = BragaTime.nowMillis(),
                pendingSync = serverId == null
            )
        )
        recordAudit(patientId, "MEDICATION_REGISTERED", "$name cadastrado${barcode?.eanBarcode?.let { " (EAN $it)" } ?: ""}")
        syncAlarmsWithDatabase(patientId)
        if (serverId == null) triggerSync()
        return localId
    }

    /** Reabastece pelo contrato canônico; só então atualiza o espelho local. */
    suspend fun restock(medId: String, units: Int, actorName: String? = null): Boolean {
        val med = medicationDao.getById(medId) ?: return false
        if (units !in 1..10_000) return false
        val authoritative = apiClient.restockMedication(
            medId,
            MedicationRestockRequest(units, "restock-${UUID.randomUUID()}")
        ) ?: return false
        medicationDao.insert(
            med.copy(
                totalUnits = authoritative,
                currentUnits = authoritative,
                lastRestockDate = BragaTime.nowMillis(),
                pendingSync = false
            )
        )
        recordAudit(med.userId, "RESTOCK", "${med.name} reabastecido ($units unidades)")
        return true
    }

    suspend fun uploadMedicationPhoto(patientId: String, uri: android.net.Uri): String? = try {
        val resolver = context.contentResolver
        val mime = resolver.getType(uri)?.takeIf { it in setOf("image/jpeg", "image/png", "image/webp", "application/pdf") }
            ?: "image/jpeg"
        val extension = when (mime) {
            "application/pdf" -> "pdf"
            "image/png" -> "png"
            "image/webp" -> "webp"
            else -> "jpg"
        }
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        apiClient.uploadMedicationPhoto(patientId, "medication-${UUID.randomUUID()}.$extension", mime, bytes)
    } catch (e: Exception) {
        android.util.Log.w("CareOs", "uploadMedicationPhoto: ${e.message}")
        null
    }

    /**
     * Puxa o estoque autoritativo do servidor e alinha o espelho local.
     * Usado após um 409 (a verdade do estoque é o servidor).
     */
    suspend fun syncStockFromServer(patientId: String) {
        try {
            val items = apiClient.getMedicationStock(patientId)
            val local = medicationDao.getAllSync(patientId)
            for (item in items) {
                // best-effort match: id remoto primeiro; sem id, só nome único.
                val match = item.id?.let { id -> local.firstOrNull { it.id == id } }
                    ?: if (item.id == null) local.singleOrNull { matchByName(it, item) } else null
                if (match == null) continue
                medicationDao.applyAuthoritativeStock(match.id, item.currentUnits)
            }
        } catch (e: Exception) {
            android.util.Log.w("CareOs", "syncStockFromServer: ${e.message}")
        }
    }

    private fun matchByName(
        local: MedicationEntity,
        item: MedicationStockItem
    ): Boolean = local.name.trim().equals(item.name.trim(), ignoreCase = true)

    private suspend fun recordAudit(patientId: String, actionType: String, text: String, actorId: String? = null) {
        try {
            careAuditDao.insert(
                CareAuditEntity(
                    id = UUID.randomUUID().toString(),
                    patientId = patientId,
                    actorId = actorId ?: patientId,
                    actorName = actorNameFallback,
                    actionType = actionType,
                    detailsJson = org.json.JSONObject().put("text", text).toString(),
                    occurredAt = Date()
                )
            )
        } catch (e: Exception) {
            android.util.Log.w("CareOs", "recordAudit falhou: ${e.message}")
        }
    }

    private val actorNameFallback: String
        get() = try {
            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName
                ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
                ?: "Cuidador"
        } catch (_: Exception) { "Cuidador" }

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
