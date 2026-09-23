package br.com.bragasaude.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteVitalSign(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("systolic_pressure") val systolicPressure: Int? = null,
    @SerialName("diastolic_pressure") val diastolicPressure: Int? = null,
    @SerialName("heart_rate") val heartRate: Int? = null,
    @SerialName("oxygen_saturation") val oxygenSaturation: Int? = null,
    @SerialName("glucose_level") val glucoseLevel: Int? = null,
    @SerialName("glucose_type") val glucoseType: String? = null,
    @SerialName("hydration_ml") val hydrationMl: Int? = null,
    val steps: Int? = null,
    @SerialName("distance_meters") val distanceMeters: Float? = null,
    @SerialName("measured_at") val measuredAt: String? = null,
    val status: String = "recorded",
    @SerialName("confirmed_via") val confirmedVia: String? = null,
    @SerialName("confirmation_code") val confirmationCode: String? = null
)

@Serializable
data class RemoteMedication(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val name: String,
    val dosage: String? = null,
    @SerialName("dosage_mg") val dosageMg: Double? = null,
    @SerialName("pill_quantity") val pillQuantity: Int? = null,
    @SerialName("schedule_time") val scheduleTime: String? = null,
    @SerialName("schedule_times") val scheduleTimes: String? = null,
    val notes: String? = null
)

@Serializable
data class RemoteMedicationLog(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("medication_id") val medicationId: String,
    @SerialName("taken_at") val takenAt: String,
    @SerialName("scheduled_for") val scheduledFor: String? = null
)

@Serializable
data class RemoteExam(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val title: String,
    val category: String? = null,
    @SerialName("exam_date") val examDate: String,
    @SerialName("result_summary") val resultSummary: String? = null,
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    /** "uploaded" | "analyzed" | "confirmed" */
    val status: String = "uploaded",
    @SerialName("ai_extracted_data") val aiExtractedData: String? = null,
    @SerialName("validated_by") val validatedBy: String? = null,
    @SerialName("validation_notes") val validationNotes: String? = null
)

@Serializable
data class RemoteClinicalReference(
    @SerialName("item_key") val itemKey: String,
    @SerialName("item_name") val itemName: String,
    val category: String,
    val gender: String = "BOTH",
    @SerialName("min_target") val minTarget: Double? = null,
    @SerialName("max_target") val maxTarget: Double? = null,
    @SerialName("min_critical") val minCritical: Double? = null,
    @SerialName("max_critical") val maxCritical: Double? = null,
    val unit: String? = null,
    @SerialName("interpretation_hint") val interpretationHint: String? = null
)

@Serializable
data class RemoteExamItem(
    val id: String? = null,
    @SerialName("exam_id") val examId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("item_key") val itemKey: String,
    @SerialName("item_name") val itemName: String,
    @SerialName("value_numeric") val valueNumeric: Double? = null,
    @SerialName("value_text") val valueText: String? = null,
    val unit: String? = null,
    @SerialName("reference_text") val referenceText: String? = null,
    val status: String? = null,
    @SerialName("measured_at") val measuredAt: String? = null
)

@Serializable
data class RemoteProfile(
    val id: String,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("birth_date") val birthDate: String? = null,
    @SerialName("weight_goal") val weightGoal: Double? = null,
    @SerialName("daily_calorie_target") val dailyCalorieTarget: Double? = null,
    @SerialName("has_diabetes") val hasDiabetes: Boolean = false,
    @SerialName("has_hypertension") val hasHypertension: Boolean = false,
    @SerialName("has_thyroid_issue") val hasThyroidIssue: Boolean = false,
    @SerialName("hydration_target_ml") val hydrationTargetMl: Int? = null,
    @SerialName("step_goal") val stepGoal: Int? = 8000,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("has_renal_issue") val hasRenalIssue: Boolean = false,
    @SerialName("has_bone_issue") val hasBoneIssue: Boolean = false,
    @SerialName("has_muscular_issue") val hasMuscularIssue: Boolean = false,
    @SerialName("points_discipline") val pointsDiscipline: Int = 0,
    @SerialName("current_xp") val currentXp: Int = 0,
    @SerialName("total_xp") val totalXp: Int = 0,
    @SerialName("current_level") val currentLevel: Int = 1,
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("last_xp_at") val lastXpAt: String? = null,
    @SerialName("show_in_feed") val showInFeed: Boolean = true,
    val gender: String? = null,
    val weight: Double? = null,
    val height: Double? = null,
    @SerialName("is_smoker") val isSmoker: Boolean = false,
    @SerialName("activity_level") val activityLevel: String? = null,
    @SerialName("emergency_contact_name") val emergencyContactName: String? = null,
    @SerialName("emergency_contact_relation") val emergencyContactRelation: String? = null,
    @SerialName("emergency_contact_phone") val emergencyContactPhone: String? = null,
    @SerialName("phone") val phone: String? = null,
    @SerialName("notifications_enabled") val notificationsEnabled: Boolean = true,
    @SerialName("location_enabled") val locationEnabled: Boolean = true,
    @SerialName("sleep_start_time") val sleepStartTime: String? = "22:00",
    @SerialName("sleep_end_time") val sleepEndTime: String? = "06:00",
    @SerialName("consent_accepted_at") val consentAcceptedAt: String? = null,
    @SerialName("diabetes_type") val diabetesType: String? = null,
    @SerialName("food_allergies") val foodAllergies: List<String>? = null,
    @SerialName("custom_food_restrictions") val customFoodRestrictions: String? = null,
    @SerialName("user_role") val userRole: String? = null,
    @SerialName("basic_profile_complete") val basicProfileComplete: Boolean = false,
    @SerialName("self_care_complete") val selfCareComplete: Boolean = false,
    @SerialName("self_care_setup_pending") val selfCareSetupPending: Boolean = false,
    @SerialName("caregiver_mode") val caregiverMode: String? = null,
    @SerialName("avatar_identifier") val avatarIdentifier: String? = null,
    /** Segredo TOTP (base32) do vínculo de WhatsApp; app gera, gateway guarda. */
    @SerialName("totp_secret") val whatsappTotpSecret: String? = null,
    @SerialName("whatsapp_phone") val whatsappPhone: String? = null
)

@Serializable
data class RemoteUserConsumption(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("food_id") val foodId: String,
    @SerialName("meal_type") val mealType: String,
    @SerialName("was_consumed") val wasConsumed: Boolean = true,
    @SerialName("consumed_at") val consumedAt: String? = null
)

@Serializable
data class RemoteFood(
    val id: String? = null,
    val name: String,
    val category: String? = null,
    val kcal: Double? = null,
    @SerialName("carbs_g") val carbsG: Double? = null,
    @SerialName("protein_g") val proteinG: Double? = null,
    @SerialName("fat_g") val fatG: Double? = null,
    @SerialName("fiber_g") val fiberG: Double? = null,
    @SerialName("sodium_mg") val sodiumMg: Double? = null,
    val status: String? = null,
    @SerialName("is_diabetes_safe") val isDiabetesSafe: Boolean = true,
    @SerialName("is_hypertension_safe") val isHypertensionSafe: Boolean = true,
    @SerialName("is_thyroid_safe") val isThyroidSafe: Boolean = true,
    @SerialName("preparation_rule") val preparationRule: String? = null,
    @SerialName("average_price") val averagePrice: Double? = null,
    val tags: List<String>? = null,
    @SerialName("calcium_mg") val calciumMg: Double? = null,
    @SerialName("serving_size_grams") val servingSizeGrams: Int = 100,
    @SerialName("serving_unit") val servingUnit: String = "100g",
    @SerialName("min_serving_grams") val minServingGrams: Int = 1,
    @SerialName("max_serving_grams") val maxServingGrams: Int = 500
)

@Serializable
data class RemoteMealRule(
    val id: Int? = null,
    @SerialName("meal_name") val mealName: String,
    @SerialName("suggested_time") val suggestedTime: String? = null,
    @SerialName("calorie_percentage") val caloriePercentage: Double? = null,
    @SerialName("veg_percentage") val vegPercentage: Double = 0.50,
    @SerialName("protein_percentage") val proteinPercentage: Double = 0.25,
    @SerialName("carb_percentage") val carbPercentage: Double = 0.25
)

@Serializable
data class RemoteBiometry(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val weight: Float,
    val height: Float,
    val imc: Float,
    @SerialName("measured_at") val measuredAt: String? = null
)

@Serializable
data class RemoteDetectedCondition(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("condition_name") val conditionName: String,
    @SerialName("evidence_type") val evidenceType: String,
    @SerialName("evidence_id") val evidenceId: String,
    @SerialName("is_confirmed_by_user") val isConfirmedByUser: Boolean = false,
    @SerialName("detected_at") val detectedAt: String? = null
)

@Serializable
data class RemoteMilestone(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val title: String,
    val description: String? = null,
    @SerialName("badge_type") val badgeType: String? = null,
    @SerialName("achieved_at") val achievedAt: String? = null
)

// AUD-AN40: REMOVIDO RemoteDailyMetrics — integralmente morto. So existia
// a definicao e os 2 mappers (Mappers.kt:305-321), sem NENHUM uso real.
// O parse de /api/metrics/daily e manual (JSONObject) em
// BragaApiClient.getDailyMetrics; o upload usa a entity direta.

@Serializable
data class RemoteUserStats(
    @SerialName("user_id") val userId: String,
    @SerialName("weekly_avg_steps") val weeklyAvgSteps: Int,
    @SerialName("monthly_avg_steps") val monthlyAvgSteps: Int,
    @SerialName("steps_today") val stepsToday: Int
)

@Serializable
data class RemoteFeedback(
    val id: String? = null,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("user_email") val userEmail: String? = null,
    @SerialName("user_name") val userName: String? = null,
    val category: String, // 'sugestao', 'reclamacao', 'bug', 'outro'
    val title: String? = null,
    val message: String,
    @SerialName("input_method") val inputMethod: String? = "text", // 'voice', 'text', 'voice_edited'
    @SerialName("app_version") val appVersion: String? = "1.2.0",
    @SerialName("device_info") val deviceInfo: String? = null,
    @SerialName("screenshot_base64") val screenshotBase64: String? = null,
    @SerialName("screenshot_url") val screenshotUrl: String? = null,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class RemoteLeagueCycle(
    val id: String,
    val level: Int,
    @SerialName("week_start_date") val weekStartDate: String,
    @SerialName("week_end_date") val weekEndDate: String,
    val status: String = "active"
)

@Serializable
data class RemoteLeagueMembership(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("user_level") val userLevel: Int = 1,
    @SerialName("user_streak") val userStreak: Int = 0,
    @SerialName("league_cycle_id") val leagueCycleId: String,
    @SerialName("xp_earned") val xpEarned: Int = 0,
    @SerialName("rank_at_close") val rankAtClose: Int? = null,
    val outcome: String? = null
)

@Serializable
data class RemoteSocialPost(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("user_level") val userLevel: Int = 1,
    @SerialName("post_type") val postType: String,
    val title: String,
    val description: String? = null,
    @SerialName("related_milestone_id") val relatedMilestoneId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("is_visible") val isVisible: Boolean = true,
    @SerialName("reaction_count") val reactionCount: Int = 0,
    @SerialName("has_user_reacted") val hasUserReacted: Boolean = false
)

@Serializable
data class RemotePostReaction(
    val id: String,
    @SerialName("post_id") val postId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("user_name") val userName: String? = null,
    @SerialName("reaction_type") val reactionType: String = "apoio",
    @SerialName("created_at") val createdAt: String? = null
)

// ============================================================================
// CARE OS — D62 (First Contract). Tipos estritos do openapi_care_os.json.
// O Android consome exatamente estes shapes; nada de "achismo" de dados.
// ============================================================================

/** GET /api/anvisa/medications/barcode/{ean} — catálogo ANVISA por EAN-13. */
@Serializable
data class BarcodeMedication(
    @SerialName("ean_barcode") val eanBarcode: String,
    val name: String,
    @SerialName("active_principle") val activePrinciple: String? = null,
    val concentration: String? = null,
    @SerialName("pharmaceutical_form") val pharmaceuticalForm: String? = null,
    val manufacturer: String? = null,
    @SerialName("farmacia_popular_eligible") val farmaciaPopularEligible: Boolean? = null,
    @SerialName("source_name") val sourceName: String,
    @SerialName("source_url") val sourceUrl: String? = null,
    @SerialName("source_checked_at") val sourceCheckedAt: String? = null
)

/** Anexo de receita (RDC 657/2022) — obrigatório no cadastro. */
@Serializable
data class PrescriptionCreate(
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("issued_on") val issuedOn: String,
    @SerialName("validity_days") val validityDays: Int,
    @SerialName("prescriber_name") val prescriberName: String,
    @SerialName("prescriber_crm") val prescriberCrm: String
)

/** POST /api/family/patients/{patient_id}/medications. */
@Serializable
data class MedicationCreate(
    @SerialName("ean_barcode") val eanBarcode: String? = null,
    val name: String,
    @SerialName("active_principle") val activePrinciple: String? = null,
    val manufacturer: String? = null,
    @SerialName("dosage_mg") val dosageMg: Double? = null,
    @SerialName("pharmaceutical_form") val pharmaceuticalForm: String? = null,
    @SerialName("schedule_times") val scheduleTimes: List<String>,
    @SerialName("total_units") val totalUnits: Int,
    @SerialName("alert_threshold_days") val alertThresholdDays: Int = 5,
    @SerialName("photo_reference_url") val photoReferenceUrl: String? = null,
    @SerialName("confirmed_with_prescription") val confirmedWithPrescription: Boolean,
    val prescription: PrescriptionCreate? = null
)

/** POST /api/medications/{medication_id}/take. */
@Serializable
data class MedicationTakeRequest(
    @SerialName("scheduled_for") val scheduledFor: String,
    @SerialName("units_taken") val unitsTaken: Int = 1,
    @SerialName("idempotency_key") val idempotencyKey: String
)

/** GET /api/family/patients/{patient_id}/medications/stock — item do estoque. */
@Serializable
data class MedicationStockItem(
    val id: String? = null,
    val name: String,
    @SerialName("current_units") val currentUnits: Int,
    @SerialName("days_remaining") val daysRemaining: Double? = null,
    @SerialName("is_critical") val isCritical: Boolean? = null,
    @SerialName("alert_threshold_days") val alertThresholdDays: Int? = null
)

/** GET /api/family/patients/{patient_id}/activity-feed — linha do mural. */
@Serializable
data class CareActivityEntry(
    val id: String? = null,
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("actor_name") val actorName: String,
    @SerialName("action_type") val actionType: String,
    val details: String? = null,
    @SerialName("occurred_at") val occurredAt: String
)

/** POST /api/symptoms/check-in — Cena C37. */
@Serializable
data class SymptomCheckInCreate(
    @SerialName("patient_id") val patientId: String? = null,
    @SerialName("reported_at") val reportedAt: String,
    @SerialName("symptoms_text") val symptomsText: String,
    @SerialName("sleep_quality") val sleepQuality: Int? = null,
    val disposition: Int? = null,
    @SerialName("input_method") val inputMethod: String = "VOICE"
)

/** POST /api/patients/{patient_id}/medical-access/generate — modo consulta. */
@Serializable
data class MedicalAccessGrant(
    @SerialName("access_token") val accessToken: String,
    @SerialName("magic_link") val magicLink: String,
    @SerialName("qr_code_payload") val qrCodePayload: String,
    @SerialName("expires_at") val expiresAt: String,
    @SerialName("expires_in_seconds") val expiresInSeconds: Int = 7200
)

/** POST /api/patients/{patient_id}/emergency-access/generate — ficha de emergência. */
@Serializable
data class EmergencyTokenGrant(
    @SerialName("emergency_token") val emergencyToken: String,
    @SerialName("rescue_link") val rescueLink: String,
    @SerialName("expires_at") val expiresAt: String
)

/** POST /api/telemetry/ble — ingestão GATT. */
@Serializable
data class BleTelemetryRequest(
    @SerialName("patient_id") val patientId: String,
    @SerialName("device_id") val deviceId: String,
    @SerialName("device_type") val deviceType: String,
    @SerialName("measured_at") val measuredAt: String,
    @SerialName("systolic_pressure") val systolicPressure: Int? = null,
    @SerialName("diastolic_pressure") val diastolicPressure: Int? = null,
    @SerialName("glucose_level") val glucoseLevel: Int? = null,
    val protocol: String = "GATT"
)


