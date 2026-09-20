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
import br.com.bragasaude.util.BragaConstants

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
    private val guestId = BragaConstants.GUEST_UID

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

        // Lista de alerts a disparar: triple(alertType, msg cuidador, msg autocuidado).
        // doc 10 §3.4: a notificação LOCAL é de autocuidado (mesmo aparelho que mediu);
        // o cuidador é avisado pelo push do servidor, não pela notificação local.
        val alertsToNotify = mutableListOf<Triple<String, String, String>>()

        // Verificar PA Sistolica
        val sys = vital.systolicPressure
        val dia = vital.diastolicPressure
        if (sys != null && sys >= CriticalVitalThresholds.CRITICAL_SYSTOLIC) {
            val severityLabel = if (sys >= 180) "Pressão bem acima da faixa habitual"
            else if (sys >= 160) "Pressão Muito Alta"
            else "Pressão Alta"
            alertsToNotify.add(Triple(
                "CRITICAL_BP_SYSTOLIC:$patientId",
                "Aviso de cuidado: A pressão de $patientName foi registrada como ${sys}/${dia ?: '?'}. $severityLabel. Vale conversar ou mandar uma mensagem para saber como ele(a) está.",
                "Sua pressão foi registrada como ${sys}/${dia ?: '?'} mmHg. $severityLabel. Se sentir dor de cabeça forte, tontura ou falta de ar, procure atendimento médico."
            ))
        }

        // Verificar PA Diastolica
        if (dia != null && dia >= CriticalVitalThresholds.CRITICAL_DIASTOLIC && !(sys != null && sys >= CriticalVitalThresholds.CRITICAL_SYSTOLIC)) {
            alertsToNotify.add(Triple(
                "CRITICAL_BP_DIASTOLIC:$patientId",
                "Aviso de cuidado: A pressão diastólica de $patientName foi registrada como ${sys ?: '?'}/${dia} mmHg. Valor acima do esperado. Vale acompanhar com carinho.",
                "Sua pressão foi registrada como ${sys ?: '?'}/${dia} mmHg. Valor acima do esperado. Beba água, descanse e, se não melhorar, procure atendimento médico."
            ))
        }

        // Verificar Glicose
        val glu = vital.glucoseLevel
        if (glu != null && glu > CriticalVitalThresholds.CRITICAL_GLUCOSE_HIGH) {
            alertsToNotify.add(Triple(
                "CRITICAL_GLUCOSE:$patientId",
                "Aviso de cuidado: A glicemia de $patientName foi registrada como ${glu} mg/dL. Valor acima da faixa esperada. Vale checar como ele(a) está se sentindo.",
                "Sua glicemia foi registrada como ${glu} mg/dL. Valor acima da faixa esperada. Beba água e, se sentir sede excessiva ou visão turva, procure atendimento médico."
            ))
        }

        // Verificar Frequencia Cardiaca
        val hr = vital.heartRate
        if (hr != null && hr > CriticalVitalThresholds.CRITICAL_HEART_RATE_HIGH) {
            alertsToNotify.add(Triple(
                "CRITICAL_HR_HIGH:$patientId",
                "Aviso de cuidado: A frequência cardíaca de $patientName foi registrada como ${hr} bpm. Vale checar com carinho como ele(a) está sentindo.",
                "Sua frequência cardíaca foi registrada como ${hr} bpm. Se sentir palpitação forte, dor no peito ou falta de ar, procure atendimento médico."
            ))
        }
        if (hr != null && hr < CriticalVitalThresholds.CRITICAL_HEART_RATE_LOW) {
            alertsToNotify.add(Triple(
                "CRITICAL_HR_LOW:$patientId",
                "Aviso de cuidado: A frequência cardíaca de $patientName foi registrada como ${hr} bpm. Vale checar com carinho como ele(a) está se sentindo.",
                "Sua frequência cardíaca foi registrada como ${hr} bpm. Se sentir tontura, fraqueza ou desmaio, procure atendimento médico."
            ))
        }

        // Verificar Saturacao de Oxigenio (AUD-AN19): a constante CRITICAL_SPO2_LOW
        // existia mas NUNCA era usada — um idoso com 78% de SpO2 não alertava o
        // cuidador. Hipoxemia silenciosa é uma das maiores urgências geriátricas.
        val spo2 = vital.oxygenSaturation
        if (spo2 != null && spo2 < CriticalVitalThresholds.CRITICAL_SPO2_LOW) {
            alertsToNotify.add(Triple(
                "CRITICAL_SPO2_LOW:$patientId",
                "Aviso de cuidado: A saturação de oxigênio de $patientName foi registrada como ${spo2}%. Abaixo de 90% é sinal de alerta. Vale checar como ele(a) está respirando e considerar atendimento médico.",
                "Sua saturação de oxigênio foi registrada como ${spo2}%. Abaixo de 90% é sinal de alerta. Se sentir falta de ar, confusão ou lábios/dedos arroxeados, procure atendimento médico imediatamente."
            ))
        }

        // Para cada alert unico, notificar TODOS os cuidadores ativos
        val seenAlertTypes = mutableSetOf<String>()
        for ((alertKey, caregiverMessage, selfCareMessage) in alertsToNotify) {
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

                // Só registra cooldown de entrega quando o FCM aceita o destinatário autorizado.
                val accepted = apiClient.sendCaregiverHealthAlert(patientId, caregiverId, caregiverMessage)
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
                        alertText = caregiverMessage,
                        lastSentAtMs = now
                    ))
                } catch (e: Exception) {
                    android.util.Log.e("VitalsRepo", "Falha ao registrar alerta no vitalAlertLogDao: ${e.message}")
                }

                // Atualizar cache em memoria
                recentAlertLog[dedupKey] = now


            }
        }

        // Aviso LOCAL de autocuidado para quem mediu (uma única vez por alerta).
        if (seenAlertTypes.isNotEmpty()) {
            sendLocalSelfCareAlert(patientName, alertsToNotify.first { it.first in seenAlertTypes }.third)
        }
    }

    /**
     * Notificação local de AUTOCUIDADO no aparelho de quem mediu (doc 10 §3.4).
     * O cuidador já foi avisado pelo push do servidor; esta é para o próprio paciente.
     */
    private fun sendLocalSelfCareAlert(patientName: String, selfCareMessage: String) {
        try {
            FamilyNotificationService.notifyPatientSelfCareAlert(
                appContext,
                "Aviso de Autocuidado",
                selfCareMessage
            )
        } catch (e: Exception) {
            android.util.Log.e("VitalsRepo", "Erro ao enviar notificação local de autocuidado: ${e.message}")
        }
    }

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
