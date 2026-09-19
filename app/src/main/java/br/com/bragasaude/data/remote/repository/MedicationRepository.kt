package br.com.bragasaude.data.remote.repository

import android.content.Context
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.RemoteMedication
import br.com.bragasaude.data.remote.model.RemoteMedicationLog
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.data.util.toEntity
import br.com.bragasaude.ui.util.MedicationAlarmReceiver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import br.com.bragasaude.domain.MedicationSchedule
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

        if (med.userId == guestId) return

        try {
            // Sincronização via API
            medicationDao.insert(entity.copy(pendingSync = true))
        } catch (e: Exception) {
            medicationDao.insert(entity.copy(pendingSync = true))
            triggerSync()
        }
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

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
