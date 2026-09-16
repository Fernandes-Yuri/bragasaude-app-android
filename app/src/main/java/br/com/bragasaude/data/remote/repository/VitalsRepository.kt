package br.com.bragasaude.data.remote.repository

import android.content.Context
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.data.util.toRemote
import br.com.bragasaude.data.util.toEntity
import br.com.bragasaude.data.util.parseDate
import br.com.bragasaude.ui.util.FamilyNotificationService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * TASK-VIT-01 — Limiares de crise para disparo automatico de alerta ao cuidador.
 */
object CriticalVitalThresholds {
    const val CRITICAL_SYSTOLIC = 160            // PA sistolica >= 160 mmHg
    const val CRITICAL_DIASTOLIC = 100           // PA diastolica >= 100 mmHg
    const val CRITICAL_GLUCOSE_HIGH = 300        // Glicose > 300 mg/dL
    const val CRITICAL_SPO2_LOW = 90             // SpO2 < 90%
    const val CRITICAL_HEART_RATE_HIGH = 120     // FC > 120 bpm
    const val CRITICAL_HEART_RATE_LOW = 50       // FC < 50 bpm
    const val ALERT_DEBOUNCE_MINUTES = 15        // Cooldown entre alerts do mesmo tipo
}

@Singleton
class VitalsRepository @Inject constructor(
    private val apiClient: BragaApiClient,
    private val vitalSignDao: VitalSignDao,
    private val profileDao: ProfileDao,
    private val familyDao: FamilyDao,
    private val vitalAlertLogDao: VitalAlertLogDao,
    private val syncScheduler: SyncScheduler,
    @ApplicationContext private val appContext: Context
) {
    private val guestId = "00000000-0000-0000-0000-000000000000"

    /** Cache em memoria das ultimas notificaciones de crise por (caregiverUserId + alertType). */
    private val recentAlertLog = mutableMapOf<String, Long>()

    fun getVitalSigns(userId: String): Flow<List<VitalSignEntity>> = vitalSignDao.getAll(userId)

    fun getBloodPressureRecords(userId: String): Flow<List<VitalSignEntity>> = vitalSignDao.getBloodPressureRecords(userId)

    fun getBloodPressureRecent30Days(userId: String): Flow<List<VitalSignEntity>> {
        val thirtyDaysAgoMillis = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        return vitalSignDao.getBloodPressureRecent30Days(userId, thirtyDaysAgoMillis)
    }

    fun getGlucoseRecords(userId: String): Flow<List<VitalSignEntity>> = vitalSignDao.getGlucoseRecords(userId)

    fun getGlucoseRecent30Days(userId: String): Flow<List<VitalSignEntity>> {
        val thirtyDaysAgoMillis = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        return vitalSignDao.getGlucoseRecent30Days(userId, thirtyDaysAgoMillis)
    }

    fun getHydrationRecords(userId: String): Flow<List<VitalSignEntity>> = vitalSignDao.getHydrationRecords(userId)

    fun getHydrationRecent30Days(userId: String): Flow<List<VitalSignEntity>> {
        val thirtyDaysAgoMillis = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        return vitalSignDao.getHydrationRecent30Days(userId, thirtyDaysAgoMillis)
    }

    fun getRecent30Days(userId: String): Flow<List<VitalSignEntity>> {
        val thirtyDaysAgoMillis = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        return vitalSignDao.getRecent30Days(userId, thirtyDaysAgoMillis)
    }

    /**
     * Agrega o consumo hidrico de uma data especifica somando todas as ingestaes registradas (ex: 4x 500ml = 2000ml).
     */
    fun getDailyHydration(userId: String, targetDate: Date = Date()): Flow<Int> {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val targetDayStr = sdf.format(targetDate)
        return vitalSignDao.getHydrationRecords(userId).map { list ->
            list.filter { entity ->
                sdf.format(entity.measuredAt) == targetDayStr
            }.sumOf { it.hydrationMl ?: 0 }
        }
    }

    /**
     * Retorna a agregacao diaria de hidratacao dos ultimos N dias (padrao 30 dias) mapeada por data (yyyy-MM-dd -> total ml).
     */
    fun getDailyHydrationHistory(userId: String, days: Int = 30): Flow<Map<String, Int>> {
        val thirtyDaysAgoMillis = System.currentTimeMillis() - (days.toLong() * 24 * 60 * 60 * 1000)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        return vitalSignDao.getHydrationRecent30Days(userId, thirtyDaysAgoMillis).map { list ->
            val map = mutableMapOf<String, Int>()
            list.forEach { entity ->
                val day = sdf.format(entity.measuredAt)
                val current = map.getOrDefault(day, 0)
                map[day] = current + (entity.hydrationMl ?: 0)
            }
            map
        }
    }

    suspend fun purgeOldVitals(daysRetention: Int = 30) {
        val cutoffMillis = System.currentTimeMillis() - (daysRetention.toLong() * 24 * 60 * 60 * 1000)
        vitalSignDao.purgeOlderThan(cutoffMillis)
    }

    suspend fun getVitalSignsSync(userId: String): List<RemoteVitalSign> {
        return vitalSignDao.getAll(userId).first().map { it.toRemote() }
    }

    suspend fun saveVitalSigns(vitals: List<RemoteVitalSign>) {
        vitals.forEach { vital ->
            val remoteWithId = if (vital.id == null) vital.copy(id = UUID.randomUUID().toString()) else vital
            val entity = remoteWithId.toEntity().copy(pendingSync = false)
            
            val localRowId = vitalSignDao.insert(entity)
            
            if (vital.userId == guestId) return@forEach
            
            try {
                val serverUuid = apiClient.syncVitalSign(entity)
                if (serverUuid != null) {
                    vitalSignDao.insert(entity.copy(localId = localRowId, remoteId = serverUuid, pendingSync = false))
                } else {
                    vitalSignDao.insert(entity.copy(localId = localRowId, pendingSync = true))
                    triggerSync()
                }
            } catch (e: Exception) {
                android.util.Log.e("VitalsRepository", "Erro na sincronização de sinal vital com servidor: ${e.message}")
                vitalSignDao.insert(entity.copy(localId = localRowId, pendingSync = true))
                triggerSync()
            }

            // TASK-VIT-01: verifica sinais criticos e notifica cuidadores
            if (vital.userId != guestId) {
                checkAndNotifyCriticalVitals(entity)
            }
        }
    }

    suspend fun deleteLastHydration(userId: String) {
        vitalSignDao.deleteLastHydration(userId)
    }

    suspend fun deduplicateVitals() {
        vitalSignDao.deduplicateVitals()
    }

    /**
     * Verifica se algum sinal vital está em estado critico e dispara notificacao para todos
     * os cuidadores ativos vinculados ao paciente.
     */
    private suspend fun checkAndNotifyCriticalVitals(vital: VitalSignEntity) {
        val patientId = vital.userId
        
        // Obtém perfil do paciente para nome e diagnostico
        val profile = profileDao.getProfileOneShot(patientId)
        val patientName = profile?.fullName ?: "Seu familiar"

        // Obtém lista de cuidadores ativos vinculados a este paciente
        val activeBindings = familyDao.getActiveBindingsForPatient(patientId)
            .first() // Convert Flow to single value

        if (activeBindings.isEmpty()) return

        val now = System.currentTimeMillis()
        val cooldownMs = CriticalVitalThresholds.ALERT_DEBOUNCE_MINUTES * 60 * 1000L

        // Lista de alerts a disparar: pair(alertType, messageText)
        val alertsToNotify = mutableListOf<Pair<String, String>>()

        // Verificar PA Sistolica
        val sys = vital.systolicPressure
        val dia = vital.diastolicPressure
        if (sys != null && sys >= CriticalVitalThresholds.CRITICAL_SYSTOLIC) {
            val severityLabel = if (sys >= 180) "Pressão bem acima da faixa habitual"
            else if (sys >= 160) "Pressão Muito Alta"
            else "Pressão Alta"
            val msg = "Aviso de cuidado: A pressão de $patientName foi registrada como ${sys}/${dia ?: '?'}. $severityLabel. Vale conversar ou mandar uma mensagem para saber como ele(a) está."
            alertsToNotify.add("CRITICAL_BP_SYSTOLIC:$patientId" to msg)
        }

        // Verificar PA Diastolica
        if (dia != null && dia >= CriticalVitalThresholds.CRITICAL_DIASTOLIC && !(sys != null && sys >= CriticalVitalThresholds.CRITICAL_SYSTOLIC)) {
            val msg = "Aviso de cuidado: A pressão diastólica de $patientName foi registrada como ${sys ?: '?'}/${dia} mmHg. Valor acima do esperado. Vale acompanhar com carinho."
            alertsToNotify.add("CRITICAL_BP_DIASTOLIC:$patientId" to msg)
        }

        // Verificar Glicose
        val glu = vital.glucoseLevel
        if (glu != null && glu > CriticalVitalThresholds.CRITICAL_GLUCOSE_HIGH) {
            val msg = "Aviso de cuidado: A glicemia de $patientName foi registrada como ${glu} mg/dL. Valor acima da faixa esperada. Vale checar como ele(a) está se sentindo."
            alertsToNotify.add("CRITICAL_GLUCOSE:$patientId" to msg)
        }

        // Verificar Frequencia Cardiaca
        val hr = vital.heartRate
        if (hr != null && hr > CriticalVitalThresholds.CRITICAL_HEART_RATE_HIGH) {
            val msg = "Aviso de cuidado: A frequência cardíaca de $patientName foi registrada como ${hr} bpm. Vale checar com carinho como ele(a) está se sentindo."
            alertsToNotify.add("CRITICAL_HR_HIGH:$patientId" to msg)
        }
        if (hr != null && hr < CriticalVitalThresholds.CRITICAL_HEART_RATE_LOW) {
            val msg = "Aviso de cuidado: A frequência cardíaca de $patientName foi registrada como ${hr} bpm. Vale checar com carinho como ele(a) está se sentindo."
            alertsToNotify.add("CRITICAL_HR_LOW:$patientId" to msg)
        }

        // Para cada alert unico, notificar TODOS os cuidadores ativos
        val seenAlertTypes = mutableSetOf<String>()
        for ((alertKey, alertMessage) in alertsToNotify) {
            if (alertKey in seenAlertTypes) continue
            seenAlertTypes.add(alertKey)

            for (binding in activeBindings) {
                val caregiverId = binding.caregiverUserId
                val caregiverName = binding.caregiverName

                // Verificar debounce (anti-duplicacao 15 min)
                val dedupKey = "$caregiverId:$alertKey"
                val lastAlert = recentAlertLog[dedupKey]
                val dbAlert = vitalAlertLogDao.getLatestAlert(caregiverId, alertKey)
                if ((lastAlert != null && now - lastAlert < cooldownMs) ||
                    dbAlert?.lastSentAtMs?.let { now - it < cooldownMs } == true) continue

                // Disparar notificacao local Android
                sendLocalCriticalAlert(appContext, patientName, binding, alertMessage)

                // Só registra cooldown de entrega quando o FCM aceita o destinatário autorizado.
                val accepted = apiClient.sendCaregiverHealthAlert(patientId, caregiverId, alertMessage)
                if (!accepted) {
                    android.util.Log.w("VitalsRepo", "Alerta remoto não aceito; não há confirmação de entrega.")
                    continue
                }

                // Registrar no DB local
                try {
                    vitalAlertLogDao.insert(VitalAlertLogEntity(
                        caregiverUserId = caregiverId,
                        alertType = alertKey,
                        patientUserId = patientId,
                        alertTitle = "Alerta Critico: $patientName",
                        alertText = alertMessage,
                        lastSentAtMs = now
                    ))
                } catch (e: Exception) {
                    android.util.Log.e("VitalsRepo", "Falha ao registrar alerta no vitalAlertLogDao: ${e.message}")
                }

                // Atualizar cache em memoria
                recentAlertLog[dedupKey] = now


            }
        }
    }

    /**
     * Envia notificacao local Android de alta prioridade para o cuidador.
     */
    private fun sendLocalCriticalAlert(
        context: Context,
        patientName: String,
        binding: FamilyBindingEntity,
        alertMessage: String
    ) {
        try {
            FamilyNotificationService.notifyCaregiverCriticalVitals(context, patientName, alertMessage)
        } catch (e: Exception) {
            android.util.Log.e("VitalsRepo", "Erro ao enviar notificacao local de crise: ${e.message}")
        }
    }

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
