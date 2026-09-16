package br.com.bragasaude.data.remote.service

import android.util.JsonWriter
import br.com.bragasaude.data.local.AuditLogDao
import br.com.bragasaude.data.local.AuditLogEntity
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

/**
 * Serviço de telemetria e auditoria para rastrear eventos do aplicativo.
 * 
 * Loga eventos localmente (offline-first) e sincroniza posteriormente com o servidor web.
 */
@Singleton
class TelemetryService @Inject constructor(
    private val auditLogDao: AuditLogDao
) {
    
    private val scope = CoroutineScope(Dispatchers.IO)
    
    /**
     * Registra um evento de auditoria de forma assíncrona.
     * 
     * @param userId ID do usuário que executou a ação
     * @param eventType Tipo de evento (VOICE_ASSISTANT, CLINICAL_VITAL, CAREGIVER_ACTION, SOCIAL_FEED, GOAL_ACHIEVED)
     * @param action Ação realizada (OPEN, CLOSE, PARSE, RECORD, VIEW, LIKE, SHARE)
     * @param metadata Dados adicionais como JSON opcional
     */
    fun logEvent(
        userId: String,
        eventType: String,
        action: String,
        metadata: JSONObject? = null
    ) {
        scope.launch {
            try {
                val log = AuditLogEntity(
                    id = UUID.randomUUID().toString(),
                    userId = userId,
                    eventType = eventType,
                    action = action,
                    metadataJson = metadata?.toString(),
                    timestamp = System.currentTimeMillis(),
                    pendingSync = true
                )
                auditLogDao.insert(log)
            } catch (e: Exception) {
                android.util.Log.e("TelemetryService", "Erro ao registrar evento: ${e.message}")
            }
        }
    }
    
    /**
     * Registra evento de assistente de voz.
     */
    fun logVoiceEvent(userId: String, action: String, transcript: String? = null, intent: String? = null) {
        val metadata = JSONObject().apply {
            transcript?.let { put("transcript", it) }
            intent?.let { put("intent", it) }
        }
        logEvent(userId, "VOICE_ASSISTANT", action, metadata)
    }

    /**
     * Registra interação completa do assistente de IA para formação do dataset de fine-tuning.
     */
    fun logAiConversation(
        userId: String,
        userPrompt: String,
        aiResponse: String,
        detectedIntent: String,
        isConfirmed: Boolean = false,
        rawPayload: String? = null
    ) {
        val metadata = JSONObject().apply {
            put("userPrompt", userPrompt)
            put("aiResponse", aiResponse)
            put("detectedIntent", detectedIntent)
            put("isConfirmed", isConfirmed)
            rawPayload?.let { put("rawPayload", it) }
        }
        logEvent(userId, "VOICE_AI_CONVERSATION", "CHAT_TURN", metadata)
    }
    
    /**
     * Registra evento de registro clínico (sinais vitais).
     */
    fun logClinicalVital(userId: String, action: String, vitalType: String, value: Any? = null) {
        val metadata = JSONObject().apply {
            put("vitalType", vitalType)
            value?.let { put("value", it.toString()) }
        }
        logEvent(userId, "CLINICAL_VITAL", action, metadata)
    }
    
    /**
     * Registra ação de cuidador.
     */
    fun logCaregiverAction(userId: String, action: String, patientId: String, details: String? = null) {
        val metadata = JSONObject().apply {
            put("patientId", patientId)
            details?.let { put("details", it) }
        }
        logEvent(userId, "CAREGIVER_ACTION", action, metadata)
    }
    
    /**
     * Registra interação no feed social.
     */
    fun logSocialFeed(userId: String, action: String, postId: String, postType: String? = null) {
        val metadata = JSONObject().apply {
            put("postId", postId)
            postType?.let { put("postType", it) }
        }
        logEvent(userId, "SOCIAL_FEED", action, metadata)
    }
    
    /**
     * Registra conquista de meta alcançada.
     */
    fun logGoalAchieved(userId: String, goalType: String, goalName: String, xpGranted: Int) {
        val metadata = JSONObject().apply {
            put("goalType", goalType)
            put("goalName", goalName)
            put("xpGranted", xpGranted)
        }
        logEvent(userId, "GOAL_ACHIEVED", "COMPLETE", metadata)
    }
    
    /**
     * Busca logs pendentes de sincronização.
     */
    suspend fun getPendingLogs(): List<AuditLogEntity> {
        return withContext(Dispatchers.IO) {
            auditLogDao.getPendingSync()
        }
    }
    
    /**
     * Marca logs como sincronizados.
     */
    suspend fun markLogsSynced(logIds: List<String>) {
        withContext(Dispatchers.IO) {
            logIds.forEach { id ->
                auditLogDao.markAsSynced(id)
            }
        }
    }
    
    /**
     * Limpa logs antigos (mais recentes mantidos).
     */
    suspend fun purgeOldLogs(daysToKeep: Int = 30) {
        withContext(Dispatchers.IO) {
            val cutoff = System.currentTimeMillis() - (daysToKeep.toLong() * 24 * 60 * 60 * 1000)
            auditLogDao.purgeOlderThan(cutoff)
        }
    }
}
