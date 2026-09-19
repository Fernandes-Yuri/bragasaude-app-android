package br.com.bragasaude.domain

import br.com.bragasaude.R
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import br.com.bragasaude.data.local.DailyMetricsEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.*
import br.com.bragasaude.data.remote.repository.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class HealthEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vitalsRepository: VitalsRepository,
    private val examsRepository: ExamsRepository,
    private val milestonesRepository: MilestonesRepository,
    private val conditionRepository: ConditionRepository,
    private val profileRepository: ProfileRepository,
    private val biometryRepository: BiometryRepository,
    private val riskManager: RiskManager,
    private val auth: com.google.firebase.auth.FirebaseAuth,
    // doc 10 §3.3: exame crítico avisa o cuidador pelo mesmo caminho dos vitais.
    private val apiClient: BragaApiClient,
    private val familyDao: br.com.bragasaude.data.local.FamilyDao
) {

    private val CHANNEL_ID = "health_alerts_channel"
    private val NOTIFICATION_ID_EMERGENCY = 2001
    private val NOTIFICATION_ID_ALERT = 2002
    private val NOTIFICATION_ID_MILESTONE = 2003
    private val NOTIFICATION_ID_EVOLUTION = 2004

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Avisos de Bem-Estar e Cuidados"
            val descriptionText = "Orientações preventivas, conquistas e alertas de cuidado"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private suspend fun showNotification(title: String, message: String, notificationId: Int = System.currentTimeMillis().toInt()) {
        val userId = auth.currentUser?.uid ?: return
        val profile = profileRepository.getProfile(userId).firstOrNull()
        if (profile?.notificationsEnabled == false) return

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, builder.build())
    }

    data class HealthRecommendation(
        val message: String,
        val action: String,
        val recheckMinutes: Int? = null,
        val isEmergency: Boolean = false
    )

    data class ActivityRecommendation(
        val message: String,
        val action: String,
        val isAchievement: Boolean = false
    )

    data class AnalysisResult(
        val alerts: List<String>,
        val recommendations: List<HealthRecommendation> = emptyList(),
        val newMilestones: List<RemoteMilestone> = emptyList(),
        val detectedConditions: List<RemoteDetectedCondition> = emptyList()
    )

    private suspend fun checkImmediateSOS(vital: RemoteVitalSign): HealthRecommendation? {
        val candidates = mutableListOf<HealthRecommendation>()
        // Valores persistidos já estão em mmHg; converter somente na entrada.
        // Sistólica
        vital.systolicPressure?.let { rawValue ->
            val value = rawValue
            
            if (value >= 180) {
                return@let HealthRecommendation(
                    message = context.getString(R.string.bp_high_crisis, value),
                    action = context.getString(R.string.bp_high_crisis_action),
                    isEmergency = true
                )
            }
            if (value > 140) {
                return@let HealthRecommendation(
                    message = context.getString(R.string.bp_high, value),
                    action = context.getString(R.string.bp_high_action),
                    recheckMinutes = 15
                )
            }
            if (value < 90) {
                return@let HealthRecommendation(
                    message = context.getString(R.string.bp_low, value),
                    action = context.getString(R.string.bp_low_action),
                    recheckMinutes = 30
                )
            }
            null
        }?.let(candidates::add)

        // Diastólica
        vital.diastolicPressure?.let { rawValue ->
            val value = rawValue
            
            if (value >= 120) {
                return@let HealthRecommendation(
                    message = context.getString(R.string.bp_diastolic_high_crisis, value),
                    action = context.getString(R.string.bp_diastolic_high_crisis_action),
                    isEmergency = true
                )
            }
            if (value < 60) {
                return@let HealthRecommendation(
                    message = context.getString(R.string.bp_diastolic_low, value),
                    action = context.getString(R.string.bp_diastolic_low_action),
                    isEmergency = true
                )
            }
            null
        }?.let(candidates::add)

        // Frequência Cardíaca (valores muito fora da faixa geral de referência)
        vital.heartRate?.let { value ->
            if (value < 50) {
                return@let HealthRecommendation(
                    message = context.getString(R.string.heart_rate_low, value),
                    action = context.getString(R.string.heart_rate_low_action),
                    isEmergency = true
                )
            }
            if (value > 100) {
                val isSevere = value > 120
                return@let HealthRecommendation(
                    message = if (isSevere) context.getString(R.string.heart_rate_high_crisis, value) else context.getString(R.string.heart_rate_high, value),
                    action = if (isSevere) context.getString(R.string.heart_rate_high_crisis_action) else context.getString(R.string.heart_rate_high_action),
                    isEmergency = isSevere,
                    recheckMinutes = if (isSevere) null else 15
                )
            }
            null
        }?.let(candidates::add)

        // Glicose
        vital.glucoseLevel?.let { value ->
            if (value > 300) {
                return@let HealthRecommendation(
                    message = context.getString(R.string.glucose_emergency, value),
                    action = context.getString(R.string.glucose_emergency_action),
                    isEmergency = true
                )
            }
            if (value > 250) {
                return@let HealthRecommendation(
                    message = context.getString(R.string.glucose_high, value),
                    action = context.getString(R.string.glucose_high_action),
                    recheckMinutes = 60
                )
            }
            if (value < 60) {
                return@let HealthRecommendation(
                    message = context.getString(R.string.glucose_hypo_severe, value),
                    action = context.getString(R.string.glucose_hypo_severe_action),
                    isEmergency = true
                )
            }
            if (value < 70) {
                return@let HealthRecommendation(
                    message = context.getString(R.string.glucose_hypo, value),
                    action = context.getString(R.string.glucose_hypo_action),
                    recheckMinutes = 15
                )
            }
            null
        }?.let(candidates::add)
        
        if (candidates.isEmpty()) return null
        val emergencies = candidates.filter { it.isEmergency }
        val selected = emergencies.ifEmpty { candidates }
        return HealthRecommendation(
            message = selected.joinToString("\n") { it.message },
            action = selected.map { it.action }.distinct().joinToString("\n"),
            isEmergency = emergencies.isNotEmpty(),
            recheckMinutes = if (emergencies.isNotEmpty()) null else selected.mapNotNull { it.recheckMinutes }.minOrNull()
        )
    }

    suspend fun analyzeVitals(userId: String, newVitals: List<RemoteVitalSign>, silent: Boolean = false): AnalysisResult {
        val history = vitalsRepository.getVitalSignsSync(userId)
        val alerts = mutableListOf<String>()
        val recommendations = mutableListOf<HealthRecommendation>()
        val milestones = mutableListOf<RemoteMilestone>()
        val conditions = mutableListOf<RemoteDetectedCondition>()

        val now = System.currentTimeMillis()
        val fiveMinutesAgo = now - (5 * 60 * 1000)

        for (vital in newVitals) {
            // Só notifica se for um dado recente (últimos 5 minutos)
            // Dados baixados do sync histórico não devem disparar push notifications
            val measuredAtMillis = vital.measuredAt?.let { parseDate(it)?.time } ?: now
            val shouldNotify = measuredAtMillis >= fiveMinutesAgo && !silent

            checkImmediateSOS(vital)?.let { rec ->
                recommendations.add(rec)
                alerts.add(rec.message)
                
                if (rec.isEmergency && shouldNotify) {
                    riskManager.emitRisk("SOS", rec.message)
                }

                if (shouldNotify) {
                    showNotification(
                        if (rec.isEmergency) context.getString(R.string.health_emergency_title) else context.getString(R.string.health_alert_title),
                        "${rec.message} ${rec.action}",
                        if (rec.isEmergency) NOTIFICATION_ID_EMERGENCY else NOTIFICATION_ID_ALERT
                    )
                }
            }
            conditions.addAll(checkHealthObservations(userId, vital, history))
            
            // --- ANÁLISE DE SITUAÇÃO: ESTABILIZAÇÃO DE PRESSÃO ---
            checkPressureStabilization(vital, history)?.let { rec ->
                recommendations.add(rec)
                alerts.add(rec.message)
                if (shouldNotify) {
                    showNotification(context.getString(R.string.health_evolution_title), rec.message, NOTIFICATION_ID_EVOLUTION)
                }
            }
        }

        // --- ANÁLISE DE SITUAÇÃO: META DE HIDRATAÇÃO ---
        checkHydrationMilestone(userId, newVitals)?.let { milestone ->
            milestones.add(milestone)
            if (!silent) {
                milestonesRepository.saveMilestone(milestone)
                showNotification(context.getString(R.string.health_milestone_title), milestone.title, NOTIFICATION_ID_MILESTONE)
            }
        }

        if (!silent) {
            conditions.forEach { conditionRepository.saveDetectedCondition(it) }
            milestones.forEach { 
                milestonesRepository.saveMilestone(it)
            }
        }

        return AnalysisResult(alerts, recommendations.sortedByDescending { it.isEmergency }, milestones, conditions)
    }

    private fun parseDate(dateStr: String): java.util.Date? = br.com.bragasaude.data.util.parseDate(dateStr)

    private fun checkPressureStabilization(current: RemoteVitalSign, history: List<RemoteVitalSign>): HealthRecommendation? {
        val sys = current.systolicPressure ?: return null
        if (sys >= 130) return null

        val pastHigh = history.take(5).any { it.systolicPressure != null && it.systolicPressure > 140 }
        val recentNormal = history.take(2).all { it.systolicPressure != null && it.systolicPressure < 130 }

        if (pastHigh && recentNormal) {
            return HealthRecommendation(
                message = context.getString(R.string.pressure_stabilized, sys),
                action = context.getString(R.string.pressure_stabilized_action)
            )
        }
        return null
    }

    private suspend fun checkHydrationMilestone(userId: String, newVitals: List<RemoteVitalSign>): RemoteMilestone? {
        val hasNewHydration = newVitals.any { it.hydrationMl != null }
        if (!hasNewHydration) return null

        val profile = profileRepository.getProfile(userId).firstOrNull() ?: return null
        val target = profile.hydrationTargetMl ?: 2000

        val vitals = vitalsRepository.getVitalSigns(userId).first()
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        
        val todayTotal = vitals.filter { 
            val dateStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(it.measuredAt)
            dateStr == today 
        }.sumOf { it.hydrationMl ?: 0 }

        if (todayTotal >= target) {
            // Verifica se já ganhou hoje para não duplicar
            val existingMilestones = milestonesRepository.getMilestones(userId).first()
            val alreadyAchievedToday = existingMilestones.any { 
                it.badgeType == "hydration_daily" && 
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(it.achievedAt) == today 
            }

            if (!alreadyAchievedToday) {
                return RemoteMilestone(
                    userId = userId,
                    title = context.getString(R.string.hydration_milestone_title),
                    description = context.getString(R.string.hydration_milestone_desc, target),
                    badgeType = "hydration_daily"
                )
            }
        }
        return null
    }

    suspend fun analyzeExamItems(userId: String, items: List<RemoteExamItem>, silent: Boolean = false): AnalysisResult {
        val alerts = mutableListOf<String>()
        val recommendations = mutableListOf<HealthRecommendation>()
        val conditions = mutableListOf<RemoteDetectedCondition>()
        
        // Busca histórico para comparar evolução
        val history = examsRepository.getExamItems(userId).first()

        items.forEach { item ->
            val ref = examsRepository.getClinicalReference(item.itemKey)
            val nameLower = item.itemName.lowercase()
            val value = item.valueNumeric

            if (value != null) {
                // Sugestões Nutricionais Preventivas por Inteligência Artificial
                if (nameLower.contains("glic") || nameLower.contains("glicose")) {
                    if (value >= 100.0) {
                        recommendations.add(
                            HealthRecommendation(
                                message = context.getString(R.string.nutrition_glucose_msg, value.toInt()),
                                action = context.getString(R.string.nutrition_glucose_action),
                                isEmergency = false
                            )
                        )
                    }
                } else if (item.itemKey in setOf("total_cholesterol", "ldl", "vldl")) {
                    if (ref?.maxTarget != null && item.unit == ref.unit && value > ref.maxTarget) {
                        recommendations.add(
                            HealthRecommendation(
                                message = context.getString(R.string.nutrition_cholesterol_msg, item.itemName, value.toInt()),
                                action = context.getString(R.string.nutrition_cholesterol_action),
                                isEmergency = false
                            )
                        )
                    }
                } else if (nameLower.contains("triglic")) {
                    if (value >= 150.0) {
                        recommendations.add(
                            HealthRecommendation(
                                message = context.getString(R.string.nutrition_triglycerides_msg, value.toInt()),
                                action = context.getString(R.string.nutrition_triglycerides_action),
                                isEmergency = false
                            )
                        )
                    }
                } else if (nameLower.contains("úrico") || nameLower.contains("urico")) {
                    if (value >= 7.0) {
                        recommendations.add(
                            HealthRecommendation(
                                message = context.getString(R.string.nutrition_uric_acid_msg, value.toString()),
                                action = context.getString(R.string.nutrition_uric_acid_action),
                                isEmergency = false
                            )
                        )
                    }
                }
            }

            if (ref != null && item.valueNumeric != null && item.valueNumeric.isFinite() && item.unit == ref.unit) {
                val numVal = item.valueNumeric
                
                // --- SITUAÇÃO: ALERTA CRÍTICO ---
                val isLow = ref.minCritical?.let { numVal <= it } == true
                val isHigh = ref.maxCritical?.let { numVal >= it } == true
                if (isLow || isHigh) {
                    val rec = HealthRecommendation(
                        message = context.getString(if (isLow) R.string.exam_critical_low_msg else R.string.exam_critical_msg, item.itemName, numVal.toString()),
                        action = context.getString(R.string.exam_critical_action),
                        isEmergency = true
                    )
                    recommendations.add(rec)
                    alerts.add(rec.message)
                    
                    if (!silent) {
                        showNotification(context.getString(R.string.exam_critical_alert), "${rec.message} ${rec.action}", NOTIFICATION_ID_EMERGENCY)
                        // doc 10 §3.3: exame crítico avisa os cuidadores ativos,
                        // pelo mesmo caminho do alerta de sinal vital.
                        notifyCaregiversOfCriticalExam(userId, item.itemName, numVal, isLow)
                        conditions.add(RemoteDetectedCondition(
                            userId = userId,
                            conditionName = context.getString(if (isLow) R.string.observation_exam_low else R.string.observation_exam_high, item.itemName),
                            evidenceType = "exam_items",
                            evidenceId = item.id ?: "new"
                        ))
                    }
                }

                // --- SITUAÇÃO: EVOLUÇÃO POSITIVA (SAIU DO CRÍTICO) ---
                val previousItem = history.filter { it.itemKey == item.itemKey && it.remoteId != item.id }
                    .sortedByDescending { it.measuredAt }
                    .firstOrNull()

                if (previousItem != null && previousItem.valueNumeric != null) {
                    val prevValue = previousItem.valueNumeric
                    val wasCritical = ref.minCritical?.let { prevValue <= it } == true || ref.maxCritical?.let { prevValue >= it } == true
                    val inTarget = (ref.minTarget != null || ref.maxTarget != null) &&
                        (ref.minTarget == null || numVal >= ref.minTarget) &&
                        (ref.maxTarget == null || numVal <= ref.maxTarget)
                    if (wasCritical && inTarget && !isLow && !isHigh && previousItem.unit == item.unit) {
                        val rec = HealthRecommendation(
                            message = context.getString(R.string.exam_evolution_msg, item.itemName),
                            action = context.getString(R.string.exam_evolution_action)
                        )
                        recommendations.add(rec)
                        alerts.add(rec.message)
                        if (!silent) {
                            showNotification(context.getString(R.string.health_evolution_title), rec.message, NOTIFICATION_ID_EVOLUTION)
                        }
                    }
                }
            }
        }

        if (!silent) {
            conditions.forEach { conditionRepository.saveDetectedCondition(it) }
        }

        return AnalysisResult(alerts, recommendations.sortedByDescending { it.isEmergency }, emptyList(), conditions)
    }

    /**
     * Avisa os cuidadores ativos sobre um exame laboratorial crítico (doc 10 §3.3).
     *
     * Mesmo caminho do alerta de sinal vital: /api/family/health-alert, que
     * valida o vínculo no servidor e entrega o push FCM no aparelho do cuidador.
     * Best-effort — a notificação local de autocuidado já foi ao paciente.
     */
    private suspend fun notifyCaregiversOfCriticalExam(
        userId: String,
        itemName: String,
        value: Double,
        isLow: Boolean
    ) {
        try {
            val patientName = profileRepository.getProfile(userId).firstOrNull()?.fullName
                ?: "Seu familiar"
            val direction = if (isLow) "abaixo" else "acima"
            val message = "Aviso de cuidado: O exame de $patientName ($itemName) " +
                "veio $direction da faixa crítica: ${value}. Vale acompanhar com a equipe de saúde."

            val bindings = familyDao.getActiveBindingsForPatient(userId).first()
            for (binding in bindings) {
                try {
                    apiClient.sendCaregiverHealthAlert(userId, binding.caregiverUserId, message)
                } catch (e: Exception) {
                    android.util.Log.w("HealthEngine", "Falha ao avisar cuidador do exame crítico: ${e.message}")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("HealthEngine", "Falha ao resolver destinatários do exame crítico: ${e.message}")
        }
    }

    suspend fun analyzeActivityPatterns(userId: String, dailyMetrics: List<DailyMetricsEntity>): List<ActivityRecommendation> {
        val recommendations = mutableListOf<ActivityRecommendation>()
        
        // 1. Alerta de Sedentarismo (Baseado nos dados do dia atual)
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val todayMetrics = dailyMetrics.find { it.date == today }
        
        if (todayMetrics != null && todayMetrics.steps < 500 && Calendar.getInstance().get(Calendar.HOUR_OF_DAY) > 10) {
            recommendations.add(ActivityRecommendation(
                message = context.getString(R.string.sedentary_alert),
                action = context.getString(R.string.sedentary_msg)
            ))
        }

        // 2. Metas diárias atingidas
        val profile = profileRepository.getProfile(userId).firstOrNull()
        val stepTarget = profile?.stepGoal ?: 8000
        todayMetrics?.let { 
            if (it.steps >= stepTarget) {
                recommendations.add(ActivityRecommendation(
                    message = context.getString(R.string.activity_goal_title),
                    action = context.getString(R.string.activity_goal_msg, stepTarget),
                    isAchievement = true
                ))
            }
        }

        return recommendations
    }

    // DECISOES.md (D4): o app nunca nomeia condição/doença. Este motor gera
    // OBSERVAÇÕES descritivas (valor x faixa de referência geral) que a UI exibe
    // sempre com o disclaimer e o encaminhamento a um profissional.
    private fun checkHealthObservations(userId: String, current: RemoteVitalSign, history: List<RemoteVitalSign>): List<RemoteDetectedCondition> {
        // A1: limites superiores documentados, com três registros distintos.
        val metrics = listOf<Triple<String, (RemoteVitalSign) -> Int?, (Int) -> Boolean>>(
            Triple("Pressão sistólica", { it.systolicPressure }, { it >= 180 }),
            Triple("Pressão diastólica", { it.diastolicPressure }, { it >= 120 }),
            Triple("Frequência cardíaca", { it.heartRate }, { it > 100 })
        ).toMutableList()
        val glucoseType = current.glucoseType
        if (glucoseType in setOf("jejum", "fasting", "post_prandial", "pos_prandial")) {
            val threshold = if (glucoseType in setOf("jejum", "fasting")) 180 else 200
            metrics.add(Triple("Glicemia", { if (it.glucoseType == glucoseType) it.glucoseLevel else null }, { it > threshold }))
        }
        return metrics.mapNotNull { (label, value, high) ->
            if (!HealthHistoryRules.startsHighTrend(current, history, value, high)) return@mapNotNull null
            RemoteDetectedCondition(
                userId = userId,
                conditionName = context.getString(R.string.observation_metric_trend, label),
                evidenceType = "vital_signs",
                evidenceId = current.id ?: "new"
            )
        }
    }

    suspend fun analyzeBiometry(userId: String, current: RemoteBiometry, silent: Boolean = false): AnalysisResult {
        val alerts = mutableListOf<String>()
        val recommendations = mutableListOf<HealthRecommendation>()
        val milestones = mutableListOf<RemoteMilestone>()

        val imc = current.imc

        // DECISOES.md (D4): nunca nomear classificação clínica — apenas o valor x faixa geral.
        if (imc >= 30.0 || imc < 18.5) {
            val rec = HealthRecommendation(
                message = context.getString(R.string.imc_alert, String.format("%.1f", imc)),
                action = context.getString(R.string.imc_alert_action)
            )
            recommendations.add(rec)
            alerts.add(rec.message)
        }

        // --- ANÁLISE DE SITUAÇÃO: TENDÊNCIA DE PESO ---
        try {
            val history = biometryRepository.getBiometry(userId).first()
            val measuredAt = current.measuredAt?.let { parseDate(it)?.time } ?: System.currentTimeMillis()
            HealthHistoryRules.weightGain(current.weight, measuredAt, history)?.let { gain ->
                val rec = HealthRecommendation(
                    message = context.getString(R.string.weight_gain_48h, String.format("%.1f", gain)),
                    action = context.getString(R.string.weight_gain_48h_action),
                    isEmergency = true
                )
                recommendations.add(0, rec)
                alerts.add(0, rec.message)
                if (!silent && measuredAt >= System.currentTimeMillis() - 5 * 60 * 1000) {
                    riskManager.emitRisk("SOS", "${rec.message} ${rec.action}")
                    showNotification(context.getString(R.string.health_emergency_title), "${rec.message} ${rec.action}", NOTIFICATION_ID_EMERGENCY)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // --- ANÁLISE DE SITUAÇÃO: META ALCANÇADA ---
        if (!silent) {
            try {
                val profile = profileRepository.getProfile(userId).firstOrNull()
                profile?.weightGoal?.let { goal ->
                    if (current.weight <= goal.toFloat() && current.weight > 0) {
                        val existingMilestones = milestonesRepository.getMilestones(userId).first()
                        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
                        val alreadyAchievedToday = existingMilestones.any { 
                            it.badgeType == "weight_goal" && 
                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(it.achievedAt) == today 
                        }
                        if (!alreadyAchievedToday) {
                            val milestone = RemoteMilestone(
                                userId = userId,
                                title = context.getString(R.string.weight_goal_milestone_title),
                                description = context.getString(R.string.weight_goal_milestone_desc, String.format("%.1f", goal)),
                                badgeType = "weight_goal"
                            )
                            milestones.add(milestone)
                            milestonesRepository.saveMilestone(milestone)
                            showNotification(context.getString(R.string.health_milestone_title), milestone.title, NOTIFICATION_ID_MILESTONE)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return AnalysisResult(alerts, recommendations, milestones)
    }
}
