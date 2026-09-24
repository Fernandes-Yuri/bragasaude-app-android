package br.com.bragasaude.data.remote.api

import android.content.Context
import android.util.Log
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.model.*
import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.di.BaseUrl
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import br.com.bragasaude.util.safeString
import br.com.bragasaude.util.safeNullableString
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cliente HTTP REST para sincronização de dados com o PostgreSQL no Lenovo G460.
 *
 * Substitui o SDK do Firebase Data Connect com alta performance,
 * latência ultrabaixa na rede local e dependência zero de bibliotecas pesadas.
 */
@Singleton
class BragaApiClient @Inject constructor(
    @ApplicationContext private val context: Context,
    // AUD-AN40: baseUrl era `var` publico mutavel num @Singleton — ninguem mutava
    // em producao, mas nada impedia (race de concorrencia se um dia mutassem).
    // Imutavel; producao vem do @BaseUrl (BuildConfig) e testes injetam o mock.
    @BaseUrl private val _baseUrl: String = DEFAULT_BASE_URL,
    private val authService: AuthService
) {
    companion object {
        private const val TAG = "BragaApiClient"
        val DEFAULT_BASE_URL = br.com.bragasaude.BuildConfig.BASE_URL
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 10000
    }

    val baseUrl: String get() = _baseUrl

    private val isoFormat get() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
    private val dateFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Parser tolerante de ISO-8601 vindo do gateway Python (`datetime.isoformat()`):
     * aceita fração de segundos com microssegundos e offsets "+00:00"/"Z".
     */
    private fun parseIsoMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:?\d{2})?$""")
            .find(raw.trim()) ?: return null
        val base = match.groupValues[1]
        val fraction = match.groupValues.getOrNull(2)?.take(3)?.padEnd(3, '0') ?: "000"
        val zone = when (val z = match.groupValues.getOrNull(3) ?: "") {
            "", "Z" -> "+0000"
            else -> z.replace(":", "")
        }
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).parse("$base.$fraction$zone")?.time
        } catch (_: Exception) { null }
    }

    // ==================== PERFIL ====================

    suspend fun syncProfile(p: ProfileEntity): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", p.userId)
                put("fullName", p.fullName ?: "")
                if (p.birthDate != null) put("birthDate", p.birthDate)
                if (p.gender != null) put("gender", p.gender)
                if (p.height != null) put("height", p.height)
                if (p.weight != null) put("weight", p.weight)
                put("isSmoker", p.isSmoker)
                put("hasDiabetes", p.hasDiabetes)
                put("hasHypertension", p.hasHypertension)
                put("hasThyroidIssue", p.hasThyroidIssue)
                put("hasRenalIssue", p.hasRenalIssue)
                put("hasBoneIssue", p.hasBoneIssue)
                put("hasMuscularIssue", p.hasMuscularIssue)
                put("hydrationTargetMl", p.hydrationTargetMl)
                put("dailyCalorieTarget", p.dailyCalorieTarget ?: 1800)
                put("stepGoal", p.stepGoal ?: 8000)
                if (p.weightGoal != null) put("weightGoal", p.weightGoal)
                put("sleepStartTime", p.sleepStartTime ?: "22:00")
                put("sleepEndTime", p.sleepEndTime ?: "06:00")
                if (p.emergencyContactName != null) put("emergencyContactName", p.emergencyContactName)
                if (p.emergencyContactRelation != null) put("emergencyContactRelation", p.emergencyContactRelation)
                if (p.emergencyContactPhone != null) put("emergencyContactPhone", p.emergencyContactPhone)
                if (p.phone != null) put("phone", p.phone)
                put("notificationsEnabled", p.notificationsEnabled)
                put("locationEnabled", p.locationEnabled)
                put("currentXp", p.currentXp)
                put("totalXp", p.totalXp)
                put("currentLevel", p.currentLevel)
                put("currentStreak", p.currentStreak)
                put("showInFeed", p.showInFeed)
                put("userRole", p.userRole ?: JSONObject.NULL)
                put("consentAcceptedAt", p.consentAcceptedAt?.let { isoFormat.format(it) } ?: JSONObject.NULL)
                put("basicProfileComplete", p.basicProfileComplete)
                put("selfCareComplete", p.selfCareComplete)
                put("selfCareSetupPending", p.selfCareSetupPending)
                put("diabetesType", p.diabetesType ?: JSONObject.NULL)
                put("activityLevel", p.activityLevel ?: JSONObject.NULL)
                put("foodAllergies", JSONArray(p.foodAllergies))
                put("customFoodRestrictions", p.customFoodRestrictions ?: JSONObject.NULL)
                put("caregiverMode", p.caregiverMode ?: JSONObject.NULL)
                if (p.avatarIdentifier != null) put("avatarIdentifier", p.avatarIdentifier)
                // Segredo TOTP do vínculo de WhatsApp (app gera; gateway só guarda).
                if (!p.whatsappTotpSecret.isNullOrBlank()) {
                    put("totpSecret", p.whatsappTotpSecret)
                }
            }
            val res = postJson("$baseUrl/api/sync/profile", json)
            return@withContext res?.optString("status") == "success"
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao sincronizar perfil: ${e.message}")
            return@withContext false
        }
    }

    suspend fun getProfile(userId: String): RemoteProfile? =
        (getProfileLookup(userId) as? ProfileLookup.Found)?.profile

    suspend fun getProfileLookup(userId: String): ProfileLookup = withContext(Dispatchers.IO) {
        try {
            val res = getJson("$baseUrl/api/profile/$userId", strict = true)
            if (res == null) return@withContext ProfileLookup.Unavailable()
            if (res.optString("id") != userId) return@withContext ProfileLookup.Unavailable()
            if (listOf("basic_profile_complete", "self_care_complete", "self_care_setup_pending")
                    .any { res.opt(it) !is Boolean }) return@withContext ProfileLookup.Unavailable()
            return@withContext ProfileLookup.Found(RemoteProfile(
                id = res.getString("id"),
                fullName = res.nullableString("full_name"),
                birthDate = res.nullableString("birth_date"),
                gender = res.nullableString("gender"),
                height = res.optDouble("height").takeIf { !it.isNaN() },
                weight = res.optDouble("weight").takeIf { !it.isNaN() },
                isSmoker = res.optBoolean("is_smoker", false),
                hasDiabetes = res.optBoolean("has_diabetes", false),
                hasHypertension = res.optBoolean("has_hypertension", false),
                hasThyroidIssue = res.optBoolean("has_thyroid_issue", false),
                hasRenalIssue = res.optBoolean("has_renal_issue", false),
                hasBoneIssue = res.optBoolean("has_bone_issue", false),
                hasMuscularIssue = res.optBoolean("has_muscular_issue", false),
                hydrationTargetMl = res.optInt("hydration_target_ml", 2000),
                dailyCalorieTarget = res.optDouble("daily_calorie_target", 1800.0),
                stepGoal = res.optInt("step_goal", 8000),
                weightGoal = res.optDouble("weight_goal").takeIf { !it.isNaN() },
                sleepStartTime = res.optString("sleep_start_time", "22:00"),
                sleepEndTime = res.optString("sleep_end_time", "06:00"),
                emergencyContactName = res.nullableString("emergency_contact_name"),
                emergencyContactRelation = res.nullableString("emergency_contact_relation"),
                emergencyContactPhone = res.nullableString("emergency_contact_phone"),
                phone = res.nullableString("phone"),
                consentAcceptedAt = res.nullableString("consent_accepted_at"),
                notificationsEnabled = res.optBoolean("notifications_enabled", true),
                locationEnabled = res.optBoolean("location_enabled", true),
                currentXp = res.optInt("current_xp", 0),
                totalXp = res.optInt("total_xp", 0),
                currentLevel = res.optInt("current_level", 1),
                currentStreak = res.optInt("current_streak", 0),
                showInFeed = res.optBoolean("show_in_feed", true),
                userRole = res.nullableString("user_role"),
                basicProfileComplete = res.optBoolean("basic_profile_complete", false),
                selfCareComplete = res.optBoolean("self_care_complete", false),
                selfCareSetupPending = res.optBoolean("self_care_setup_pending", false),
                diabetesType = res.nullableString("diabetes_type"),
                activityLevel = res.nullableString("activity_level"),
                foodAllergies = res.optJSONArray("food_allergies")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                customFoodRestrictions = res.nullableString("custom_food_restrictions"),
                caregiverMode = res.nullableString("caregiver_mode"),
                avatarIdentifier = res.nullableString("avatar_identifier"),
                // Segredo TOTP do vínculo de WhatsApp. Sem ler este campo, o
                // app nunca fica sabendo do segredo que o backend garante — e o
                // botão "Vincular meu WhatsApp" não tem ação (ProfileViewModel
                // sai quando o segredo é null).
                whatsappTotpSecret = res.nullableString("totp_secret")
            ))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: HttpReadException) {
            if (e.status == 404) ProfileLookup.NotFound else ProfileLookup.Unavailable(e.status)
        } catch (e: Exception) {
            ProfileLookup.Unavailable()
        }
    }

    // ==================== SINAIS VITAIS ====================

    suspend fun syncVitalSign(v: VitalSignEntity): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("userId", v.userId)
                if (v.systolicPressure != null) put("systolicPressure", v.systolicPressure)
                if (v.diastolicPressure != null) put("diastolicPressure", v.diastolicPressure)
                if (v.heartRate != null) put("heartRate", v.heartRate)
                if (v.oxygenSaturation != null) put("oxygenSaturation", v.oxygenSaturation)
                if (v.glucoseLevel != null) put("glucoseLevel", v.glucoseLevel)
                if (v.glucoseType != null) put("glucoseType", v.glucoseType)
                if (v.hydrationMl != null) put("hydrationMl", v.hydrationMl)
                put("measuredAt", isoFormat.format(v.measuredAt))
            }
            val res = postJson("$baseUrl/api/sync/vitals", json)
            return@withContext res?.optString("id", null)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao sincronizar sinal vital: ${e.message}")
            return@withContext null
        }
    }

    suspend fun getVitalSigns(userId: String): List<RemoteVitalSign> = withContext(Dispatchers.IO) {
        try {
            val arr = getJsonArray("$baseUrl/api/vitals/$userId") ?: return@withContext emptyList()
            val list = mutableListOf<RemoteVitalSign>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    RemoteVitalSign(
                        id = obj.getString("id"),
                        userId = obj.getString("user_id"),
                        systolicPressure = if (obj.isNull("systolic_pressure")) null else obj.getInt("systolic_pressure"),
                        diastolicPressure = if (obj.isNull("diastolic_pressure")) null else obj.getInt("diastolic_pressure"),
                        heartRate = if (obj.isNull("heart_rate")) null else obj.getInt("heart_rate"),
                        oxygenSaturation = if (obj.isNull("oxygen_saturation")) null else obj.getInt("oxygen_saturation"),
                        glucoseLevel = if (obj.isNull("glucose_level")) null else obj.getInt("glucose_level"),
                        glucoseType = obj.optString("glucose_type", null),
                        hydrationMl = if (obj.isNull("hydration_ml")) null else obj.getInt("hydration_ml"),
                        measuredAt = obj.getString("measured_at"),
                        status = "recorded",
                        confirmedVia = obj.optString("confirmed_via", null),
                        confirmationCode = obj.optString("confirmation_code", null)
                    )
                )
            }
            return@withContext list
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao buscar sinais vitais do servidor: ${e.message}")
            return@withContext emptyList()
        }
    }

    // ==================== MÉTRICAS DIÁRIAS ====================

    suspend fun syncDailyMetric(m: DailyMetricsEntity): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("userId", m.userId)
                put("date", m.date)
                put("steps", m.steps)
                put("distanceMeters", m.distanceMeters.toDouble())
                put("distanceGpsMeters", m.distanceGpsMeters.toDouble())
                put("distanceStepsMeters", m.distanceStepsMeters.toDouble())
                put("distanceFinalMeters", m.distanceFinalMeters.toDouble())
                put("reliabilityScore", m.reliabilityScore.toDouble())
                put("caloriesBurned", m.caloriesBurned.toDouble())
                put("activeMinutes", m.activeMinutes)
            }
            val res = postJson("$baseUrl/api/sync/daily-metrics", json)
            return@withContext res?.optString("id", null)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao sincronizar métrica diária: ${e.message}")
            return@withContext null
        }
    }

    suspend fun getDailyMetrics(userId: String, days: Int = 30): List<DailyMetricsEntity> = withContext(Dispatchers.IO) {
        try {
            val arr = getJsonArray("$baseUrl/api/metrics/daily/$userId?days=$days") ?: return@withContext emptyList()
            val list = mutableListOf<DailyMetricsEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val mDate = obj.getString("date")
                val steps = obj.optInt("steps", 0)
                val dist = obj.optDouble("distance_meters", 0.0).toFloat()
                val gpsDist = obj.optDouble("distance_gps_meters", 0.0).toFloat()
                val stepsDist = obj.optDouble("distance_steps_meters", 0.0).toFloat()
                val finalDist = obj.optDouble("distance_final_meters", dist.toDouble()).toFloat()
                val relScore = obj.optDouble("reliability_score", 0.5).toFloat()
                val calories = obj.optDouble("calories_burned", 0.0).toFloat()
                val activeMin = obj.optInt("active_minutes", 0)

                list.add(
                    DailyMetricsEntity(
                        userId = userId,
                        date = mDate,
                        steps = steps,
                        distanceMeters = dist,
                        distanceGpsMeters = gpsDist,
                        distanceStepsMeters = stepsDist,
                        distanceFinalMeters = finalDist,
                        reliabilityScore = relScore,
                        caloriesBurned = calories,
                        activeMinutes = activeMin,
                        pendingSync = false
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao buscar métricas diárias do servidor: ${e.message}")
            emptyList()
        }
    }

    // ==================== FEED SOCIAL ====================

    suspend fun syncSocialPost(p: SocialPostEntity): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("userId", p.userId)
                put("postType", p.postType)
                put("title", p.title)
                if (p.description != null) put("description", p.description)
                if (p.relatedMilestoneId != null) put("relatedMilestoneId", p.relatedMilestoneId)
                put("createdAt", isoFormat.format(p.createdAt))
            }
            val res = postJson("$baseUrl/api/sync/social-post", json)
            return@withContext res?.optString("id", null)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao sincronizar post do feed: ${e.message}")
            return@withContext null
        }
    }

    suspend fun getSocialFeed(currentUserId: String, limit: Int = 30): List<SocialPostEntity> = withContext(Dispatchers.IO) {
        try {
            val arr = getJsonArray("$baseUrl/api/social/feed?limit=$limit")
                ?: throw java.io.IOException("Não foi possível carregar o mural.")
            val list = mutableListOf<SocialPostEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val cAt = try { isoFormat.parse(obj.getString("created_at")) ?: Date() } catch (_: Exception) { Date() }
                list.add(
                    SocialPostEntity(
                        id = obj.getString("id"),
                        userId = obj.getString("user_id"),
                        userName = obj.optString("user_name", "Usuário"),
                        userLevel = 1,
                        postType = obj.getString("post_type"),
                        title = obj.getString("title"),
                        description = obj.optString("description", null),
                        relatedMilestoneId = null,
                        createdAt = cAt,
                        isVisible = true,
                        reactionCount = obj.optInt("reaction_count", 0),
                        hasUserReacted = false,
                        pendingSync = false
                    )
                )
            }
            return@withContext list
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao buscar feed social: ${e.message}")
            throw e
        }
    }

    suspend fun reactToPost(postId: String, userId: String, reactionType: String = "apoio"): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("postId", postId)
                put("userId", userId)
                put("reactionType", reactionType)
                put("createdAt", isoFormat.format(Date()))
            }
            val res = postJson("$baseUrl/api/sync/post-reaction", json)
            return@withContext res != null
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao registrar reação no post: ${e.message}")
            return@withContext false
        }
    }

    // ==================== PONTE FAMILIAR ====================

    suspend fun syncFamilyBinding(b: FamilyBindingEntity): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", b.remoteId ?: b.id)
                put("patientId", b.patientUserId)
                if (b.caregiverUserId.isNotEmpty()) put("caregiverId", b.caregiverUserId)
                put("caregiverName", b.caregiverName)
                put("caregiverRelation", b.caregiverRelation)
                put("connectionCode", b.connectionCode)
                put("status", b.status)
                put("createdAt", isoFormat.format(Date(b.createdAt)))
                put("expiresAt", isoFormat.format(Date(b.expiresAt)))
                put("caregiverRole", b.caregiverRole)
                put("permissions", JSONArray(b.permissionsJson))
            }
            val res = postJson("$baseUrl/api/sync/family-binding", json)
            return@withContext res?.optString("id", null)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao sincronizar vínculo familiar: ${e.message}")
            return@withContext null
        }
    }

    suspend fun getFamilyBindings(userId: String): List<FamilyBindingEntity> = withContext(Dispatchers.IO) {
        val arr = getJsonArray("$baseUrl/api/family/bindings/$userId")
            ?: throw java.io.IOException("Não foi possível atualizar os vínculos.")
        (0 until arr.length()).map { parseBinding(arr.getJSONObject(it)) }
    }

    suspend fun syncFamilyMessage(m: FamilyMessageEntity): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", m.remoteId ?: m.id)
                put("patientId", m.patientUserId)
                put("senderId", m.senderUserId)
                put("senderName", m.senderName)
                put("messageText", m.messageText)
                put("iconType", m.iconType)
                put("isRead", m.isRead)
                put("sentAt", isoFormat.format(Date(m.sentAt)))
            }
            val res = postJson("$baseUrl/api/sync/family-message", json)
            return@withContext res?.optString("id", null)
        } catch (e: Exception) {
            return@withContext null
        }
    }

    suspend fun getFamilyMessages(patientId: String): List<FamilyMessageEntity> = withContext(Dispatchers.IO) {
        try {
            val arr = org.json.JSONArray()
            var offset = 0
            while (true) {
                val batch = getJsonArray("$baseUrl/api/family/messages/$patientId?limit=200&offset=$offset")
                    ?: throw java.io.IOException("Não foi possível atualizar as mensagens.")
                for (index in 0 until batch.length()) arr.put(batch.getJSONObject(index))
                if (batch.length() < 200) break
                offset += batch.length()
            }
            val list = mutableListOf<FamilyMessageEntity>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val sAt = parseIsoMillis(obj.optString("sent_at", null)) ?: System.currentTimeMillis()
                // D47: TTL e tombstone vindos do servidor (retrocompatível com gateway antigo)
                val expAt = parseIsoMillis(obj.optString("expires_at", null)) ?: (sAt + FAMILY_MESSAGE_TTL_MS)
                val delAt = parseIsoMillis(obj.optString("deleted_at", null))
                list.add(
                    FamilyMessageEntity(
                        id = obj.getString("id"),
                        remoteId = obj.getString("id"),
                        patientUserId = obj.getString("patient_id"),
                        senderUserId = obj.getString("sender_id"),
                        senderName = obj.getString("sender_name"),
                        messageText = obj.getString("message_text"),
                        iconType = obj.optString("icon_type", "LOVE"),
                        isRead = obj.optBoolean("is_read", false),
                        sentAt = sAt,
                        pendingSync = false,
                        expiresAt = expAt,
                        deletedAt = delAt
                    )
                )
            }
            return@withContext list
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * D47 — Exclusão da própria mensagem pelo remetente (tombstone no servidor).
     * Retorna true quando o servidor confirmou a exclusão.
     */
    suspend fun deleteFamilyMessage(messageId: String, patientId: String? = null, sentAt: Long? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", messageId)
                patientId?.let { put("patientId", it) }
                sentAt?.let { put("sentAt", isoFormat.format(Date(it))) }
            }
            val res = postJson("$baseUrl/api/family/message/delete", json)
            return@withContext res?.optString("status") == "success"
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao apagar mensagem familiar no servidor: ${e.message}")
            return@withContext false
        }
    }

    // ==================== ATUALIZAÇÃO DO APP — OTA (doc 10 §1B) ====================

    /**
     * Código da última versão publicada no gateway, ou null se indisponível.
     * Rota pública — o AuthInterceptor libera /api/app/latest.
     */
    suspend fun getLatestAppVersionCode(): Int? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val urlString = "$baseUrl/api/app/latest"
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                attachIdentity(urlString)
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
            val code = JSONObject(body).optInt("version_code", -1)
            return@withContext if (code >= 0) code else null
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao consultar /api/app/latest: ${e.message}")
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    // ==================== FEEDBACK ====================

    suspend fun syncFeedback(json: JSONObject): String? = withContext(Dispatchers.IO) {
        try {
            val res = postJson("$baseUrl/api/sync/feedback", json)
            return@withContext res?.optString("id", null)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao sincronizar feedback: ${e.message}")
            return@withContext null
        }
    }

    /**
     * doc 10 §4.1: pull incremental das respostas da equipe aos feedbacks.
     * Retorna a lista de respostas ainda não entregues a este aparelho.
     */
    suspend fun getFeedbackReplies(): List<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val res = getJson("$baseUrl/api/feedback/replies") ?: return@withContext emptyList()
            val arr = res.optJSONArray("replies") ?: JSONArray()
            (0 until arr.length()).map { arr.getJSONObject(it) }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao puxar respostas de feedback: ${e.message}")
            emptyList()
        }
    }

    // ==================== EXAMES ====================

    suspend fun syncExam(exam: ExamEntity): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                if (exam.remoteId != null) put("id", exam.remoteId)
                put("userId", exam.userId)
                put("title", exam.title)
                if (exam.category != null) put("category", exam.category)
                put("examDate", dateFormat.format(exam.examDate))
                if (exam.resultSummary != null) put("resultSummary", exam.resultSummary)
                if (exam.fileUrl != null) put("fileUrl", exam.fileUrl)
                put("status", exam.status)
                if (exam.aiExtractedData != null) put("aiExtractedData", exam.aiExtractedData)
                if (exam.validatedBy != null) put("validatedBy", exam.validatedBy)
                if (exam.validationNotes != null) put("validationNotes", exam.validationNotes)
            }
            val res = postJson("$baseUrl/api/sync/exam", json)
            return@withContext res?.optString("id", null)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao sincronizar exame: ${e.message}")
            return@withContext null
        }
    }

    suspend fun syncExamItem(item: ExamItemEntity): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                if (item.remoteId != null) put("id", item.remoteId)
                put("examId", item.examId)
                put("userId", item.userId)
                put("itemKey", item.itemKey)
                put("itemName", item.itemName)
                if (item.valueNumeric != null) put("valueNumeric", item.valueNumeric)
                if (item.valueText != null) put("valueText", item.valueText)
                if (item.unit != null) put("unit", item.unit)
                if (item.referenceText != null) put("referenceText", item.referenceText)
                if (item.status != null) put("status", item.status)
                if (item.measuredAt != null) put("measuredAt", isoFormat.format(item.measuredAt))
            }
            val res = postJson("$baseUrl/api/sync/exam-item", json)
            return@withContext res?.optString("id", null)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao sincronizar item de exame: ${e.message}")
            return@withContext null
        }
    }

    suspend fun getExams(userId: String): List<RemoteExam> = withContext(Dispatchers.IO) {
        try {
            val arr = getJsonArray("$baseUrl/api/exams/$userId") ?: return@withContext emptyList()
            val list = mutableListOf<RemoteExam>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    RemoteExam(
                        id = obj.getString("id"),
                        userId = obj.getString("user_id"),
                        title = obj.getString("title"),
                        category = obj.optString("category", null),
                        examDate = obj.getString("exam_date"),
                        resultSummary = obj.optString("result_summary", null),
                        fileUrl = obj.optString("file_url", null),
                        status = obj.optString("status", "uploaded"),
                        aiExtractedData = obj.optString("ai_extracted_data", null),
                        validatedBy = obj.optString("validated_by", null),
                        validationNotes = obj.optString("validation_notes", null),
                        createdAt = obj.optString("created_at", null)
                    )
                )
            }
            return@withContext list
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao buscar exames do servidor: ${e.message}")
            return@withContext emptyList()
        }
    }

    suspend fun sendCaregiverHealthAlert(patientId: String, caregiverId: String, message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = postJson("$baseUrl/api/family/health-alert", JSONObject().apply {
                put("patientId", patientId)
                put("caregiverId", caregiverId)
                put("message", message)
            })
            result?.optString("status") == "accepted" && result?.optInt("recipients") == 1
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao enviar alerta de saúde ao cuidador: ${e.message}")
            false
        }
    }

    // ==================== CONSULTAS (doc 10 §2.2) ====================

    /**
     * Cria uma consulta no gateway. Retorna o id remoto, ou null em falha.
     * O push para os cuidadores ativos é disparado pelo servidor.
     */
    suspend fun createConsultation(title: String, scheduledDateIso: String): String? = withContext(Dispatchers.IO) {
        try {
            val res = postJson("$baseUrl/api/consultation/create", JSONObject().apply {
                put("title", title)
                put("scheduledDate", scheduledDateIso)
            })
            res?.optString("id", null)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao criar consulta: ${e.message}")
            null
        }
    }

    suspend fun acceptConsultation(consultationId: String, caregiverName: String?, caregiverRelation: String?): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = postJson("$baseUrl/api/consultation/accept", JSONObject().apply {
                put("consultationId", consultationId)
                if (!caregiverName.isNullOrBlank()) put("caregiverName", caregiverName)
                if (!caregiverRelation.isNullOrBlank()) put("caregiverRelation", caregiverRelation)
            })
            res?.optString("status") == "success"
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao aceitar consulta: ${e.message}")
            false
        }
    }

    suspend fun rejectConsultation(consultationId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = postJson("$baseUrl/api/consultation/reject", JSONObject().apply {
                put("consultationId", consultationId)
            })
            res?.optString("status") == "success"
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao recusar consulta: ${e.message}")
            false
        }
    }

    suspend fun completeConsultation(consultationId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = postJson("$baseUrl/api/consultation/complete", JSONObject().apply {
                put("consultationId", consultationId)
            })
            res?.optString("status") == "success"
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao concluir consulta: ${e.message}")
            false
        }
    }

    suspend fun cancelConsultation(consultationId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val res = postJson("$baseUrl/api/consultation/cancel", JSONObject().apply {
                put("consultationId", consultationId)
            })
            res?.optString("status") == "success"
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao cancelar consulta: ${e.message}")
            false
        }
    }

    // ==================== MEDICAMENTOS ====================

    suspend fun syncMedication(m: MedicationEntity): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", m.id)
                put("userId", m.userId)
                put("name", m.name)
                if (m.dosageMg != null) put("dosageMg", m.dosageMg)
                if (m.pillQuantity != null) put("pillQuantity", m.pillQuantity)
                if (m.scheduleTime != null) put("scheduleTime", m.scheduleTime)
                if (m.scheduleTimes != null) put("scheduleTimes", m.scheduleTimes)
            }
            val res = postJson("$baseUrl/api/sync/medication", json)
            return@withContext if (m.scheduleTimes != null && res?.optInt("medicationContract", 0) != 2) null else res?.optString("id", null)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao sincronizar medicamento: ${e.message}")
            return@withContext null
        }
    }

    suspend fun syncMedicationLog(l: MedicationLogEntity): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", l.id)
                put("userId", l.userId)
                put("medicationId", l.medicationId)
                if (l.scheduledFor != null) put("scheduledFor", l.scheduledFor)
                put("takenAt", isoFormat.format(l.takenAt))
            }
            val res = postJson("$baseUrl/api/sync/medication-log", json)
            return@withContext if (l.scheduledFor != null && res?.optInt("medicationContract", 0) != 2) null else res?.optString("id", null)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao sincronizar log de medicamento: ${e.message}")
            return@withContext null
        }
    }

    suspend fun getMedications(userId: String): List<RemoteMedication> = withContext(Dispatchers.IO) {
        try {
            val arr = getJsonArray("$baseUrl/api/medications/$userId") ?: return@withContext emptyList()
            val list = mutableListOf<RemoteMedication>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    RemoteMedication(
                        id = obj.getString("id"),
                        userId = obj.getString("user_id"),
                        name = obj.getString("name"),
                        dosageMg = if (obj.isNull("dosage_mg")) null else obj.getDouble("dosage_mg"),
                        pillQuantity = if (obj.isNull("pill_quantity")) null else obj.getInt("pill_quantity"),
                        scheduleTime = obj.optString("schedule_time", null),
                        scheduleTimes = obj.nullableString("schedule_times")
                    )
                )
            }
            return@withContext list
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao buscar medicamentos do servidor: ${e.message}")
            return@withContext emptyList()
        }
    }

    // ==================== LIGA SAUDÁVEL ====================

    suspend fun syncLeagueMembership(m: LeagueMembershipEntity): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", m.id)
                put("userId", m.userId)
                if (m.userName != null) put("userName", m.userName)
                put("userLevel", m.userLevel)
                put("userStreak", m.userStreak)
                put("leagueCycleId", m.leagueCycleId)
                put("xpEarned", m.xpEarned)
                if (m.rankAtClose != null) put("rankAtClose", m.rankAtClose)
                if (m.outcome != null) put("outcome", m.outcome)
            }
            val res = postJson("$baseUrl/api/sync/league-membership", json)
            return@withContext res?.optString("id", null)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao sincronizar membership de liga: ${e.message}")
            return@withContext null
        }
    }

    // ==================== AUDITORIA ====================

    suspend fun syncAuditLog(log: AuditLogEntity): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("id", log.id)
                put("userId", log.userId)
                put("eventType", log.eventType)
                put("action", log.action)
                if (log.metadataJson != null) put("metadataJson", log.metadataJson)
            }
            val res = postJson("$baseUrl/api/sync/audit-log", json)
            return@withContext res?.optString("id", null)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao sincronizar log de auditoria: ${e.message}")
            return@withContext null
        }
    }

    // ==================== AUTENTICAÇÃO OTP ====================

    data class OtpDispatch(val status: String, val waLink: String?)

    /**
     * Plano B OTP (sem template): cria o código e devolve o link wa.me
     * quando o envio direto é impossível (fora da janela de 24h).
     */

    suspend fun sendOtp(identifier: String, purpose: String = "PASSWORD_RESET", channel: String = "EMAIL"): Result<OtpDispatch> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("identifier", identifier)
                put("purpose", purpose)
                put("channel", channel)
            }
            val res = postJsonDetailed("$baseUrl/api/auth/otp/send", json)
            if (res.code in 200..299) {
                Result.success(OtpDispatch(
                    status = res.body?.optString("status", "code_dispatched") ?: "code_dispatched",
                    waLink = res.body?.optString("waLink", null)?.takeIf { it.isNotBlank() }
                ))
            } else {
                Result.failure(Exception(res.errorDetail ?: "Erro ao enviar código"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun verifyOtp(identifier: String, code: String, purpose: String = "PASSWORD_RESET"): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("identifier", identifier)
                put("code", code)
                put("purpose", purpose)
            }
            val res = postJsonDetailed("$baseUrl/api/auth/otp/verify", json)
            if (res.code in 200..299) {
                val resetToken = res.body?.optString("resetToken", null)
                Result.success(resetToken)
            } else {
                Result.failure(Exception(res.errorDetail ?: "Código inválido"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resetPassword(identifier: String, resetToken: String, newPassword: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("identifier", identifier)
                put("resetToken", resetToken)
                put("newPassword", newPassword)
            }
            val res = postJsonDetailed("$baseUrl/api/auth/password/reset", json)
            if (res.code in 200..299) {
                Result.success(true)
            } else {
                Result.failure(Exception(res.errorDetail ?: "Erro ao redefinir senha"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class AccountCheckResponse(
        val exists: Boolean,
        val provider: String?,
        val canCreateDirect: Boolean,
        val message: String
    )

    suspend fun checkAccount(identifier: String): Result<AccountCheckResponse> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("identifier", identifier)
            }
            val res = postJsonDetailed("$baseUrl/api/auth/check-account", json)
            if (res.code in 200..299 && res.body != null) {
                val b = res.body!!
                Result.success(
                    AccountCheckResponse(
                        exists = b.optBoolean("exists", false),
                        provider = if (b.isNull("provider")) null else b.optString("provider", null),
                        canCreateDirect = b.optBoolean("canCreateDirect", true),
                        message = b.optString("message", "")
                    )
                )
            } else {
                Result.failure(Exception(res.errorDetail ?: "Erro ao verificar conta"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendWelcomeEmail(email: String, name: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("email", email)
                if (!name.isNullOrBlank()) {
                    put("name", name)
                }
            }
            val res = postJsonDetailed("$baseUrl/api/auth/welcome-email", json)
            if (res.code in 200..299) {
                Result.success(true)
            } else {
                Result.failure(Exception(res.errorDetail ?: "Erro ao enviar e-mail de boas-vindas"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    // ==================== PONTE FAMILIAR - BUSCA POR CÓDIGO ====================

    suspend fun findBindingByCode(code: String): FamilyBindingEntity? = withContext(Dispatchers.IO) {
        try {
            val res = getJson("$baseUrl/api/family/find-by-code/$code") ?: return@withContext null
            val cAt = try { isoFormat.parse(res.getString("created_at"))?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
            val eAt = try { isoFormat.parse(res.getString("expires_at"))?.time ?: System.currentTimeMillis() } catch (_: Exception) { System.currentTimeMillis() }
            return@withContext FamilyBindingEntity(
                id = res.getString("id"),
                patientUserId = res.getString("patient_id"),
                caregiverUserId = res.optString("caregiver_id", ""),
                caregiverName = res.getString("caregiver_name"),
                caregiverRelation = res.getString("caregiver_relation"),
                connectionCode = res.getString("connection_code"),
                status = res.optString("status", "PENDING"),
                createdAt = cAt,
                expiresAt = eAt,
                pendingSync = false
            )
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao buscar vínculo por código: ${e.message}")
            return@withContext null
        }
    }

    // ==================== AUXILIARES HTTP ====================

    // Called only by HTTP helpers running on Dispatchers.IO. OTP precedes login.
    private fun HttpURLConnection.attachIdentity(urlString: String) {
        val path = URL(urlString).path
        if (path.startsWith("/api/auth/")) return
        val token = authService.getTokenBlocking()
            ?: throw java.io.IOException("Sessão expirada. Entre novamente.")
        setRequestProperty("Authorization", "Bearer $token")
    }

    private fun JSONObject.nullableString(key: String): String? = safeNullableString(key)

    private fun parseBinding(obj: JSONObject): FamilyBindingEntity = FamilyBindingEntity(
        id = obj.getString("id"), remoteId = obj.getString("id"),
        patientUserId = obj.getString("patient_id"),
        caregiverUserId = if (obj.isNull("caregiver_id")) "" else obj.getString("caregiver_id"),
        caregiverName = obj.optString("caregiver_name", ""),
        caregiverRelation = obj.optString("caregiver_relation", ""),
        patientName = obj.optString("patient_name", null),
        connectionCode = obj.getString("connection_code"), status = obj.getString("status"),
        createdAt = java.time.OffsetDateTime.parse(obj.getString("created_at")).toInstant().toEpochMilli(),
        expiresAt = if (obj.isNull("expires_at")) 0L else java.time.OffsetDateTime.parse(obj.getString("expires_at")).toInstant().toEpochMilli(),
        caregiverRole = obj.optString("caregiver_role", "CAREGIVER_VIEWER"),
        permissionsJson = (obj.optJSONArray("permissions") ?: JSONArray()).toString(),
        pendingSync = false
    )

    suspend fun acceptFamilyInvitation(code: String, name: String, relation: String): FamilyBindingEntity = withContext(Dispatchers.IO) {
        val response = postJsonDetailed("$baseUrl/api/family/accept", JSONObject().apply {
            put("code", code.trim().uppercase()); put("caregiverName", name); put("caregiverRelation", relation)
        })
        if (response.code !in 200..299 || response.body == null) {
            throw java.io.IOException(response.errorDetail ?: "Não foi possível confirmar o convite. Verifique a conexão e tente novamente.")
        }
        parseBinding(response.body)
    }

    data class ApiResponse(val body: JSONObject?, val code: Int, val errorDetail: String?)

    private fun postJsonDetailed(urlString: String, json: JSONObject): ApiResponse {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = false
                attachIdentity(urlString)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(json.toString()) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = if (stream != null) {
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    val text = reader.readText()
                    if (text.isNotEmpty()) JSONObject(text) else null
                }
            } else null
            val detail = body?.optString("detail", null)
            ApiResponse(body, code, detail)
        } catch (e: Exception) {
            ApiResponse(null, 0, e.message)
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun postJson(urlString: String, json: JSONObject): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = false
                attachIdentity(urlString)
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(json.toString()) }
            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    val text = reader.readText()
                    return if (text.isNotEmpty()) JSONObject(text) else JSONObject()
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private class HttpReadException(val status: Int) : java.io.IOException("HTTP $status")

    private fun getJson(urlString: String, strict: Boolean = false): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = false
                attachIdentity(urlString)
            }
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    return JSONObject(reader.readText())
                }
            } else {
                if (strict) throw HttpReadException(responseCode)
                null
            }
        } catch (e: Exception) {
            if (strict || e is kotlinx.coroutines.CancellationException) throw e
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private fun getJsonArray(urlString: String): JSONArray? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = false
                attachIdentity(urlString)
            }
            if (conn.responseCode in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
                    return JSONArray(reader.readText())
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun uploadExamFile(userId: String, fileName: String, fileBytes: ByteArray): String? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val encodedFileName = java.net.URLEncoder.encode(fileName, "UTF-8")
            val url = URL("$baseUrl/api/upload/exam/$userId?filename=$encodedFileName")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 15000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/pdf")
                setFixedLengthStreamingMode(fileBytes.size)
                attachIdentity(url.toString())
            }
            conn.outputStream.use { os ->
                os.write(fileBytes)
                os.flush()
            }
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                json.optString("fileUrl", null)
            } else {
                Log.w(TAG, "Falha no upload do exame: HTTP ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao fazer upload do exame para o servidor: ${e.message}", e)
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    // ==================== NOVOS CONTRATOS DE EXAMES (D49 / FASE 3) ====================

    private fun deleteRequest(urlString: String): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = false
                attachIdentity(urlString)
            }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    suspend fun deleteExam(examId: String): Boolean = withContext(Dispatchers.IO) {
        deleteRequest("$baseUrl/api/exams/$examId")
    }

    /**
     * Exclusão da conta na nuvem — Direito ao Esquecimento (LGPD Art. 18).
     * O gateway remove o perfil; ON DELETE CASCADE cuida das 14 tabelas filhas.
     * Retorna true somente se o servidor confirmou (204/200).
     */
    suspend fun deleteAccount(userId: String): Boolean = withContext(Dispatchers.IO) {
        deleteRequest("$baseUrl/api/profile/$userId")
    }

    suspend fun syncManualExam(
        examId: String?,
        title: String,
        category: String,
        examDate: String,
        items: List<RemoteExamItem>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                if (examId != null) put("exam_id", examId)
                put("title", title)
                put("category", category)
                put("exam_date", examDate)
                val itemsArr = JSONArray()
                for (it in items) {
                    val itObj = JSONObject().apply {
                        put("item_key", it.itemKey)
                        put("item_name", it.itemName)
                        put("value_numeric", it.valueNumeric ?: 0.0)
                        put("value_text", it.valueText ?: it.valueNumeric?.toString() ?: "")
                        put("unit", it.unit ?: "")
                        if (it.referenceText != null) put("reference_text", it.referenceText)
                        put("status", it.status ?: "confirmed")
                    }
                    itemsArr.put(itObj)
                }
                put("items", itemsArr)
            }
            val res = postJson("$baseUrl/api/exams/manual", json)
            res?.optBoolean("success", false) == true
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao sincronizar exame manual: ${e.message}", e)
            false
        }
    }

    suspend fun uploadExamContract(
        examId: String,
        title: String,
        category: String,
        examDate: String,
        examType: String,
        cloudConsent: Boolean,
        termsVersion: String,
        fileName: String,
        fileBytes: ByteArray
    ): RemoteExamUploadResponse? = withContext(Dispatchers.IO) {
        val boundary = "Boundary-${System.currentTimeMillis()}"
        val lineEnd = "\r\n"
        val twoHyphens = "--"
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/exams/upload")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10000
                readTimeout = 25000
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
                attachIdentity(url.toString())
            }

            conn.outputStream.use { os ->
                fun writeFormField(fieldName: String, value: String) {
                    os.write("$twoHyphens$boundary$lineEnd".toByteArray())
                    os.write("Content-Disposition: form-data; name=\"$fieldName\"$lineEnd$lineEnd".toByteArray())
                    os.write("$value$lineEnd".toByteArray())
                }

                writeFormField("exam_id", examId)
                writeFormField("title", title)
                writeFormField("category", category)
                writeFormField("exam_date", examDate)
                writeFormField("exam_type", examType)
                writeFormField("cloud_consent", cloudConsent.toString())
                writeFormField("terms_version", termsVersion)

                val mimeType = when {
                    fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                    fileName.endsWith(".png", ignoreCase = true) -> "image/png"
                    fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
                    else -> "image/jpeg"
                }
                os.write("$twoHyphens$boundary$lineEnd".toByteArray())
                os.write("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"$lineEnd".toByteArray())
                os.write("Content-Type: $mimeType$lineEnd$lineEnd".toByteArray())
                os.write(fileBytes)
                os.write(lineEnd.toByteArray())

                os.write("$twoHyphens$boundary$twoHyphens$lineEnd".toByteArray())
                os.flush()
            }

            if (conn.responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(responseText)
                RemoteExamUploadResponse(
                    success = json.optBoolean("success", false),
                    examId = json.optString("exam_id", examId),
                    storageStatus = json.optString("storage_status", "LOCAL_ONLY"),
                    fileUrl = json.optString("file_url", null)
                )
            } else {
                Log.w(TAG, "Falha no upload multipart do exame: HTTP ${conn.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao enviar exame multipart: ${e.message}", e)
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    // ==================== CARE OS — D62 (FIRST CONTRACT) ====================
    // Contratos canônicos de scripts/server/openapi_care_os.json. Tipos estritos
    // em data/remote/model/RemoteEntities.kt — sem "achismo" de dados.

    /**
     * Resultado de uma tentativa de registro de dose (POST .../take).
     * O 409 é um caminho feliz: a dose já foi registrada por outro
     * cuidador/dispositivo e NÃO deve decrementar estotoque duplicado.
     */
    sealed class TakeMedicationResult {
        /** Decremento efetuado no servidor (HTTP 200). */
        data object Success : TakeMedicationResult()
        /** HTTP 409 — dose já registrada por outro ator. Estado local sincroniza. */
        data object AlreadyTaken : TakeMedicationResult()
        /** Falha real de rede/servidor (o app mantém o registro local e re-tenta). */
        data class Failure(val message: String) : TakeMedicationResult()
    }

    /** GET /api/anvisa/medications/barcode/{ean} — catálogo ANVISA (EAN-13 da caixa). */
    suspend fun lookupBarcode(ean: String): BarcodeMedication? = withContext(Dispatchers.IO) {
        try {
            val res = getJson("$baseUrl/api/anvisa/medications/barcode/${java.net.URLEncoder.encode(ean, "UTF-8")}", strict = true)
                ?: return@withContext null
            BarcodeMedication(
                eanBarcode = res.safeString("ean_barcode"),
                name = res.safeString("name"),
                activePrinciple = res.safeNullableString("active_principle"),
                concentration = res.safeNullableString("concentration"),
                pharmaceuticalForm = res.safeNullableString("pharmaceutical_form"),
                manufacturer = res.safeNullableString("manufacturer"),
                farmaciaPopularEligible = if (res.has("farmacia_popular_eligible") && !res.isNull("farmacia_popular_eligible")) res.getBoolean("farmacia_popular_eligible") else null,
                sourceName = res.safeString("source_name"),
                sourceUrl = res.safeNullableString("source_url"),
                sourceCheckedAt = res.safeNullableString("source_checked_at"),
                anvisaRegistrationNumber = res.safeNullableString("anvisa_registration_number")
            ).takeIf { it.eanBarcode.length == 13 && it.name.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao consultar catálogo ANVISA: ${e.message}")
            null
        }
    }

    /** POST /api/family/patients/{patient_id}/medications — cadastro com receita. */
    suspend fun createMedication(patientId: String, body: MedicationCreate): String? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                body.eanBarcode?.let { if (it.matches(Regex("^\\d{13}$"))) put("ean_barcode", it) }
                put("name", body.name)
                body.activePrinciple?.let { put("active_principle", it) }
                body.manufacturer?.let { put("manufacturer", it) }
                body.dosageMg?.let { put("dosage_mg", it) }
                body.pharmaceuticalForm?.let { put("pharmaceutical_form", it) }
                put("schedule_times", JSONArray(body.scheduleTimes))
                put("total_units", body.totalUnits)
                put("alert_threshold_days", body.alertThresholdDays)
                body.photoReferenceUrl?.let { put("photo_reference_url", it) }
                put("confirmed_with_prescription", body.confirmedWithPrescription)
                body.prescription?.let { p ->
                    put("prescription", JSONObject().apply {
                        p.imageUrl?.let { put("image_url", it) }
                        put("issued_on", p.issuedOn)
                        put("validity_days", p.validityDays)
                        put("prescriber_name", p.prescriberName)
                        put("prescriber_crm", p.prescriberCrm)
                    })
                }
            }
            val res = postJsonDetailed("$baseUrl/api/family/patients/$patientId/medications", json)
            if (res.code in 200..299) res.body?.optString("id")?.takeIf { it.isNotBlank() } else null
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao cadastrar medicamento no Care OS: ${e.message}")
            null
        }
    }

    /** GET /api/family/patients/{patient_id}/medications/stock. */
    suspend fun getMedicationStock(patientId: String): List<MedicationStockItem> = withContext(Dispatchers.IO) {
        try {
            val arr = getJsonArray("$baseUrl/api/family/patients/$patientId/medications/stock")
                ?: return@withContext emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                MedicationStockItem(
                    id = o.optString("id", null),
                    name = o.optString("name"),
                    currentUnits = o.optInt("current_units", 0),
                    daysRemaining = if (o.isNull("days_remaining")) null else o.optDouble("days_remaining").takeIf { !it.isNaN() },
                    isCritical = if (o.has("is_critical") && !o.isNull("is_critical")) o.getBoolean("is_critical") else null,
                    alertThresholdDays = o.optInt("alert_threshold_days", 5)
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao buscar estoque de medicamentos: ${e.message}")
            emptyList()
        }
    }

    /** POST /api/medications/{id}/restock — ajuste autoritativo e idempotente. */
    suspend fun restockMedication(medicationId: String, body: MedicationRestockRequest): Int? =
        withContext(Dispatchers.IO) {
            try {
                val res = postJsonDetailed(
                    "$baseUrl/api/medications/$medicationId/restock",
                    JSONObject()
                        .put("new_total_units", body.newTotalUnits.coerceIn(1, 10_000))
                        .put("idempotency_key", body.idempotencyKey)
                )
                if (res.code in 200..299) res.body?.optInt("current_units") else null
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao repor estoque no Care OS: ${e.message}")
                null
            }
        }

    /** Upload real de foto da caixa/receita; devolve a URL protegida. */
    suspend fun uploadMedicationPhoto(
        patientId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): String? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty() || bytes.size > 8 * 1024 * 1024) return@withContext null
        val boundary = "Boundary-${System.currentTimeMillis()}"
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/family/patients/$patientId/medications/photo")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
                attachIdentity(url.toString())
            }
            conn.outputStream.use { output ->
                output.write("--$boundary\r\n".toByteArray())
                output.write(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"${fileName.replace("\"", "")}\"\r\n".toByteArray()
                )
                output.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
                output.write(bytes)
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val json = JSONObject(conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            json.optString("file_url").takeIf(String::isNotBlank)
        } catch (e: Exception) {
            Log.w(TAG, "Falha no upload da foto do medicamento: ${e.message}")
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    suspend fun analyzePrescription(
        patientId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray
    ): PrescriptionAnalysisResponseDto? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty() || bytes.size > 10 * 1024 * 1024) return@withContext null
        val boundary = "Boundary-${System.currentTimeMillis()}"
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/family/patients/$patientId/prescriptions/analyze")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30000
                readTimeout = 45000
                doOutput = true
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("Accept", "application/json")
                attachIdentity(url.toString())
            }
            conn.outputStream.use { output ->
                output.write("--$boundary\r\n".toByteArray())
                output.write(
                    "Content-Disposition: form-data; name=\"file\"; filename=\"${fileName.replace("\"", "")}\"\r\n".toByteArray()
                )
                output.write("Content-Type: $mimeType\r\n\r\n".toByteArray())
                output.write(bytes)
                output.write("\r\n--$boundary--\r\n".toByteArray())
            }
            if (conn.responseCode !in 200..299) return@withContext null
            val rawJson = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val jsonObj = JSONObject(rawJson)
            val status = jsonObj.optString("status", "success")
            val imageUrl = jsonObj.optString("prescription_image_url").takeIf { it.isNotBlank() }
            val snippet = jsonObj.optString("raw_ocr_snippet").takeIf { it.isNotBlank() }
            val totalPages = jsonObj.optInt("total_pages_analyzed", 1)
            val duplicatesMerged = jsonObj.optInt("duplicates_merged_count", 0)
            val medsArray = jsonObj.optJSONArray("medications")
            val medsList = mutableListOf<AnalyzedMedicationItemDto>()
            if (medsArray != null) {
                for (i in 0 until medsArray.length()) {
                    val m = medsArray.getJSONObject(i)
                    val timesArray = m.optJSONArray("suggested_times")
                    val times = mutableListOf<String>()
                    if (timesArray != null) {
                        for (t in 0 until timesArray.length()) times.add(timesArray.getString(t))
                    }
                    val pagesArray = m.optJSONArray("source_pages")
                    val sourcePages = mutableListOf<Int>()
                    if (pagesArray != null) {
                        for (p in 0 until pagesArray.length()) sourcePages.add(pagesArray.getInt(p))
                    }
                    val frequencyIntervalHours = if (m.has("frequency_interval_hours") && !m.isNull("frequency_interval_hours")) m.optInt("frequency_interval_hours") else null
                    val dailyDosesCount = m.optInt("daily_doses_count", 1)
                    val treatmentDurationDays = if (m.has("treatment_duration_days") && !m.isNull("treatment_duration_days")) m.optInt("treatment_duration_days") else null
                    val anvisaRegistrationNumber = m.safeNullableString("anvisa_registration_number")
                    medsList.add(
                        AnalyzedMedicationItemDto(
                            name = m.safeString("name"),
                            nameDivergent = m.optBoolean("name_divergent", false),
                            dosage = m.safeString("dosage"),
                            dosageDivergent = m.optBoolean("dosage_divergent", false),
                            dosageMg = if (m.has("dosage_mg") && !m.isNull("dosage_mg")) m.optDouble("dosage_mg") else null,
                            frequency = m.safeString("frequency"),
                            frequencyDivergent = m.optBoolean("frequency_divergent", false),
                            suggestedTimes = times,
                            eanBarcode = m.safeNullableString("ean_barcode"),
                            activePrinciple = m.safeNullableString("active_principle"),
                            confidenceScore = m.optDouble("confidence_score", 0.0),
                            requiresHumanFill = m.optBoolean("requires_human_fill", false),
                            divergenceReason = m.safeNullableString("divergence_reason"),
                            sourcePages = sourcePages,
                            frequencyIntervalHours = frequencyIntervalHours,
                            dailyDosesCount = dailyDosesCount,
                            treatmentDurationDays = treatmentDurationDays,
                            anvisaRegistrationNumber = anvisaRegistrationNumber
                        )
                    )
                }
            }
            PrescriptionAnalysisResponseDto(
                status = status,
                prescriptionImageUrl = imageUrl,
                totalPagesAnalyzed = totalPages,
                duplicatesMergedCount = duplicatesMerged,
                medications = medsList,
                rawOcrSnippet = snippet
            )
        } catch (e: Exception) {
            Log.w(TAG, "Falha na análise de receita: ${e.message}")
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    suspend fun createMedicationsBatch(
        patientId: String,
        items: List<MedicationBatchItemCreateDto>
    ): Boolean = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext false
        var conn: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/api/family/patients/$patientId/medications/batch")
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                attachIdentity(url.toString())
            }
            val array = JSONArray()
            for (item in items) {
                val obj = JSONObject().apply {
                    put("name", item.name)
                    item.dosageMg?.let { put("dosage_mg", it) }
                    val timesArray = JSONArray()
                    item.scheduleTimes.forEach { timesArray.put(it) }
                    put("schedule_times", timesArray)
                    put("total_units", item.totalUnits)
                    item.eanBarcode?.let { put("ean_barcode", it) }
                    put("confirmed_with_prescription", item.confirmedWithPrescription)
                    item.photoReferenceUrl?.let { put("photo_reference_url", it) }
                    item.frequencyIntervalHours?.let { put("frequency_interval_hours", it) }
                    item.treatmentDurationDays?.let { put("treatment_duration_days", it) }
                    item.anvisaRegistrationNumber?.let { put("anvisa_registration_number", it) }
                }
                array.put(obj)
            }
            val root = JSONObject().put("medications", array)
            conn.outputStream.use { it.write(root.toString().toByteArray(Charsets.UTF_8)) }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Falha no cadastro em remessa de medicamentos: ${e.message}")
            false
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /**
     * POST /api/medications/{medication_id}/take. Trata 409 graciosamente:
     * dose já registrada por outro cuidador/dispositivo → AlreadyTaken.
     */
    suspend fun takeMedication(medicationId: String, body: MedicationTakeRequest): TakeMedicationResult =
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("scheduled_for", body.scheduledFor)
                    put("units_taken", body.unitsTaken.coerceIn(1, 20))
                    put("idempotency_key", body.idempotencyKey)
                }
                val res = postJsonDetailed("$baseUrl/api/medications/$medicationId/take", json)
                when (res.code) {
                    in 200..299 -> TakeMedicationResult.Success
                    409 -> {
                        val detail = res.errorDetail.orEmpty()
                        if (detail.contains("confirmad", ignoreCase = true) ||
                            detail.contains("registrad", ignoreCase = true) ||
                            detail.contains("duplic", ignoreCase = true)
                        ) TakeMedicationResult.AlreadyTaken
                        else TakeMedicationResult.Failure(detail.ifBlank { "Conflito ao registrar dose" })
                    }
                    else -> TakeMedicationResult.Failure("HTTP ${res.code}: ${res.errorDetail ?: "Erro ao registrar dose"}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao registrar dose no Care OS: ${e.message}")
                TakeMedicationResult.Failure(e.message ?: "Erro de conexão")
            }
        }

    /** GET /api/family/patients/{patient_id}/activity-feed?limit=50 — mural de cuidado. */
    suspend fun getActivityFeed(patientId: String, limit: Int = 50): List<CareActivityEntry> = withContext(Dispatchers.IO) {
        try {
            val arr = getJsonArray("$baseUrl/api/family/patients/$patientId/activity-feed?limit=${limit.coerceIn(1, 100)}")
                ?: return@withContext emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                CareActivityEntry(
                    id = o.optString("id", null),
                    actorId = o.optString("actor_id", null),
                    actorName = o.optString("actor_name"),
                    actionType = o.optString("action_type"),
                    details = o.opt("details")?.takeUnless { it === JSONObject.NULL }?.toString(),
                    occurredAt = o.optString("occurred_at")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao buscar mural de cuidado: ${e.message}")
            emptyList()
        }
    }

    suspend fun getDailyBulletin(patientId: String, day: String? = null): DailyCareBulletin? =
        withContext(Dispatchers.IO) {
            try {
                val suffix = day?.let { "?day=$it" }.orEmpty()
                val o = getJson("$baseUrl/api/family/patients/$patientId/daily-bulletin$suffix")
                    ?: return@withContext null
                DailyCareBulletin(
                    patientId = o.optString("patient_id", patientId),
                    day = o.optString("day"),
                    medicationsTaken = o.optInt("medications_taken"),
                    medicationsExpected = o.optInt("medications_expected"),
                    latestBloodPressure = o.optString("latest_blood_pressure", null),
                    hydrationMl = o.optInt("hydration_ml"),
                    privacyScope = o.optString("privacy_scope", "CONSOLIDATED_SAFETY_ONLY")
                )
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao buscar boletim diário: ${e.message}")
                null
            }
        }

    suspend fun getClinicalCorrelations(patientId: String): ClinicalCorrelationsResult? =
        withContext(Dispatchers.IO) {
            try {
                val o = getJson("$baseUrl/api/patients/$patientId/clinical-correlations")
                    ?: return@withContext null
                val arr = o.optJSONArray("correlations") ?: JSONArray()
                val items = (0 until arr.length()).map { index ->
                    val item = arr.getJSONObject(index)
                    val evidence = item.optJSONArray("evidence") ?: JSONArray()
                    ClinicalCorrelation(
                        eventAt = item.optString("event_at"),
                        eventType = item.optString("event_type"),
                        observation = item.optString("observation"),
                        evidence = (0 until evidence.length()).map { evidence.optString(it) },
                        windowHours = item.optInt("window_hours", 24),
                        interpretation = item.optString("interpretation", "TEMPORAL_ASSOCIATION_NOT_DIAGNOSIS")
                    )
                }
                ClinicalCorrelationsResult(
                    patientId = o.optString("patient_id", patientId),
                    correlations = items,
                    disclaimer = o.optString("disclaimer")
                )
            } catch (e: Exception) {
                Log.w(TAG, "Falha ao buscar correlações clínicas: ${e.message}")
                null
            }
        }

    /** POST /api/symptoms/check-in — Cena C37 (check-in matinal). */
    suspend fun symptomCheckIn(body: SymptomCheckInCreate): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                body.patientId?.let { put("patient_id", it) }
                put("reported_at", body.reportedAt)
                put("symptoms_text", body.symptomsText.take(1000))
                body.sleepQuality?.let { put("sleep_quality", it.coerceIn(1, 5)) }
                body.disposition?.let { put("disposition", it.coerceIn(1, 5)) }
                put("input_method", if (body.inputMethod == "TEXT") "TEXT" else "VOICE")
            }
            val res = postJsonDetailed("$baseUrl/api/symptoms/check-in", json)
            res.code in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao enviar check-in de sintomas: ${e.message}")
            false
        }
    }

    /** GET /api/patients/{patient_id}/symptoms-diary?limit=30 — histórico de check-ins de sintomas na nuvem. */
    suspend fun getSymptomsDiary(patientId: String, limit: Int = 30): List<RemoteSymptomsDiaryEntry> = withContext(Dispatchers.IO) {
        try {
            val arr = getJsonArray("$baseUrl/api/patients/$patientId/symptoms-diary?limit=${limit.coerceIn(1, 100)}")
                ?: getJsonArray("$baseUrl/api/symptoms/check-in?patient_id=$patientId&limit=${limit.coerceIn(1, 100)}")
                ?: return@withContext emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RemoteSymptomsDiaryEntry(
                    id = o.optString("id", null),
                    patientId = o.optString("patient_id", patientId),
                    reportedBy = o.optString("reported_by", patientId),
                    reportedAt = o.optString("reported_at"),
                    symptomsText = o.optString("symptoms_text"),
                    sleepQuality = if (o.has("sleep_quality") && !o.isNull("sleep_quality")) o.optInt("sleep_quality") else null,
                    disposition = if (o.has("disposition") && !o.isNull("disposition")) o.optInt("disposition") else null,
                    inputMethod = o.optString("input_method", "VOICE")
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao buscar diário de sintomas: ${e.message}")
            emptyList()
        }
    }

    /** POST /api/patients/{patient_id}/medical-access/generate — modo "Leva pro Doutor". */
    suspend fun generateMedicalAccess(patientId: String): MedicalAccessGrant? = withContext(Dispatchers.IO) {
        try {
            val res = postJsonDetailed(
                "$baseUrl/api/patients/$patientId/medical-access/generate",
                JSONObject().put("purpose", "CONSULTATION")
            )
            val body = res.body
            if (res.code !in 200..299 || body == null) return@withContext null
            MedicalAccessGrant(
                accessToken = body.optString("access_token"),
                magicLink = body.optString("magic_link"),
                qrCodePayload = body.optString("qr_code_payload"),
                expiresAt = body.optString("expires_at"),
                expiresInSeconds = body.optInt("expires_in_seconds", 7200)
            ).takeIf { it.accessToken.isNotBlank() && it.qrCodePayload.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao gerar acesso médico: ${e.message}")
            null
        }
    }

    /**
     * GET /api/family/patients/{patient_id}/doctor-report/pdf?days=30 — PDF
     * executivo de 1 página. Bytes prontos para abrir; nulo deixa o fallback
     * on-device (PdfReportGenerator) assumir.
     */
    suspend fun getDoctorReportPdf(patientId: String, days: Int = 30): ByteArray? = withContext(Dispatchers.IO) {
        getBytes("$baseUrl/api/family/patients/$patientId/doctor-report/pdf?days=${days.coerceIn(1, 90)}")
    }

    /** POST /api/patients/{patient_id}/emergency-access/generate — ficha de emergência. */
    suspend fun generateEmergencyAccess(patientId: String): EmergencyTokenGrant? = withContext(Dispatchers.IO) {
        try {
            val res = postJsonDetailed("$baseUrl/api/patients/$patientId/emergency-access/generate", JSONObject())
            val body = res.body
            if (res.code !in 200..299 || body == null) return@withContext null
            EmergencyTokenGrant(
                emergencyToken = body.optString("emergency_token"),
                rescueLink = body.optString("rescue_link"),
                expiresAt = body.optString("expires_at")
            ).takeIf { it.emergencyToken.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao gerar acesso de emergência: ${e.message}")
            null
        }
    }

    /** POST /api/telemetry/ble — ingestão GATT (BLOOD_PRESSURE / GLUCOSE). */
    suspend fun ingestBleTelemetry(body: BleTelemetryRequest): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("patient_id", body.patientId)
                put("device_id", body.deviceId)
                put("device_type", body.deviceType)
                put("measured_at", body.measuredAt)
                body.systolicPressure?.let { put("systolic_pressure", it) }
                body.diastolicPressure?.let { put("diastolic_pressure", it) }
                body.glucoseLevel?.let { put("glucose_level", it) }
                put("protocol", "GATT")
            }
            val res = postJsonDetailed("$baseUrl/api/telemetry/ble", json)
            res.code in 200..299
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao ingerir telemetria BLE: ${e.message}")
            false
        }
    }

    /** Download de bytes (PDF do relatório executivo). Aceita só PDF válido. */
    private fun getBytes(urlString: String): ByteArray? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/pdf")
                instanceFollowRedirects = true
                attachIdentity(urlString)
            }
            if (conn.responseCode !in 200..299) return null
            val contentType = conn.contentType.orEmpty()
            // gateway pode devolver JSON em vez de PDF em falhas known — não engole.
            if (!contentType.contains("pdf", ignoreCase = true)) return null
            conn.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            Log.w(TAG, "Falha no download do PDF: ${e.message}")
            null
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(READ_TIMEOUT_MS.toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .build()
    }

    private fun patchJson(urlString: String, json: JSONObject): Boolean {
        val token = authService.getTokenBlocking()
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = json.toString().toRequestBody(mediaType)
        val requestBuilder = Request.Builder()
            .url(urlString)
            .patch(body)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("Accept", "application/json")
        if (token != null && !URL(urlString).path.startsWith("/api/auth/")) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        return try {
            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Log.w(TAG, "patchJson falhou: ${e.message}")
            false
        }
    }

    private fun deleteJson(urlString: String): JSONObject? {
        val ok = deleteRequest(urlString)
        return if (ok) JSONObject().put("status", "success") else null
    }

    /**
     * GET /api/family/patients/{patient_id}/medications
     * Busca todos os medicamentos do paciente para espelhamento no banco local Room.
     */
    suspend fun getPatientMedications(patientId: String): List<MedicationEntity> = withContext(Dispatchers.IO) {
        try {
            val arr = getJsonArray("$baseUrl/api/family/patients/$patientId/medications") ?: return@withContext emptyList()
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val scheduleTimesStr = when {
                    o.optJSONArray("schedule_times") != null -> {
                        val stArr = o.getJSONArray("schedule_times")
                        (0 until stArr.length()).map { stArr.getString(it) }.joinToString(",")
                    }
                    else -> o.safeString("schedule_times")
                }
                val scheduleTimeStr = o.safeString("schedule_time").ifBlank {
                    scheduleTimesStr.split(",").firstOrNull()?.trim()?.ifBlank { "08:00" } ?: "08:00"
                }
                MedicationEntity(
                    id = o.safeString("id"),
                    userId = o.safeString("user_id").ifBlank { patientId },
                    name = o.safeString("name"),
                    dosage = o.safeNullableString("dosage") ?: if (o.has("dosage_mg") && !o.isNull("dosage_mg")) "${o.optDouble("dosage_mg")}mg" else null,
                    dosageMg = if (o.has("dosage_mg") && !o.isNull("dosage_mg")) o.optDouble("dosage_mg") else null,
                    pillQuantity = if (o.has("pill_quantity") && !o.isNull("pill_quantity")) o.optInt("pill_quantity") else null,
                    scheduleTime = scheduleTimeStr,
                    scheduleTimes = scheduleTimesStr.ifBlank { scheduleTimeStr },
                    notes = o.safeNullableString("notes") ?: o.safeNullableString("meal_context"),
                    eanBarcode = o.safeNullableString("ean_barcode"),
                    activePrinciple = o.safeNullableString("active_principle"),
                    manufacturer = o.safeNullableString("manufacturer"),
                    pharmaceuticalForm = o.safeNullableString("pharmaceutical_form"),
                    totalUnits = o.optInt("total_units", 30),
                    currentUnits = o.optInt("current_units", o.optInt("total_units", 30)),
                    alertThresholdDays = o.optInt("alert_threshold_days", 5),
                    photoReferenceUrl = o.safeNullableString("photo_reference_url"),
                    confirmedWithPrescription = o.optBoolean("confirmed_with_prescription", true),
                    lastRestockDate = BragaTime.nowMillis(),
                    pendingSync = false
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao buscar medicamentos completos do paciente: ${e.message}")
            emptyList()
        }
    }

    /**
     * DELETE /api/family/patients/{patient_id}/medications/{medication_id}
     * Exclusão definitiva de medicamento (LGPD Art. 18).
     */
    suspend fun deleteMedication(patientId: String, medicationId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            deleteRequest("$baseUrl/api/family/patients/$patientId/medications/$medicationId")
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao excluir medicamento: ${e.message}")
            false
        }
    }

    /**
     * PATCH /api/family/patients/{patient_id}/medications/{medication_id}
     * Edição de horários, posologia e contexto alimentar.
     */
    suspend fun updateMedicationSchedule(
        patientId: String,
        medicationId: String,
        scheduleTimes: List<String>,
        mealContext: String? = null,
        frequencyIntervalHours: Int? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("schedule_times", JSONArray(scheduleTimes))
                mealContext?.let { put("meal_context", it) }
                frequencyIntervalHours?.let { put("frequency_interval_hours", it) }
            }
            patchJson("$baseUrl/api/family/patients/$patientId/medications/$medicationId", json)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao atualizar horários do medicamento: ${e.message}")
            false
        }
    }
}

data class RemoteExamUploadResponse(
    val success: Boolean,
    val examId: String,
    val storageStatus: String,
    val fileUrl: String?
)

