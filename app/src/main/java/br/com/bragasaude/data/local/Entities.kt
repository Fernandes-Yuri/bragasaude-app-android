package br.com.bragasaude.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import java.util.Date

@Entity(tableName = "vital_signs_local")
data class VitalSignEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String? = null,
    val userId: String,
    val systolicPressure: Int? = null,
    val diastolicPressure: Int? = null,
    val heartRate: Int? = null,
    val oxygenSaturation: Int? = null,
    val glucoseLevel: Int? = null,
    val glucoseType: String? = null,
    val hydrationMl: Int? = null,
    val steps: Int? = null,
    val distanceMeters: Float? = null,
    val measuredAt: Date = Date(),
    val status: String = "recorded",
    val pendingSync: Boolean = false,
    val confirmedVia: String? = null,
    val confirmationCode: String? = null
)

@Entity(tableName = "profiles_local")
data class ProfileEntity(
    @PrimaryKey val userId: String,
    val fullName: String? = null,
    val birthDate: String? = null,
    val weightGoal: Double? = null,
    val dailyCalorieTarget: Double? = null,
    val hydrationTargetMl: Int? = null,
    val stepGoal: Int? = 8000,
    val hasDiabetes: Boolean = false,
    val diabetesType: String? = null,
    val foodAllergies: List<String> = emptyList(),
    val customFoodRestrictions: String? = null,
    val hasHypertension: Boolean = false,
    val hasThyroidIssue: Boolean = false,
    val hasRenalIssue: Boolean = false,
    val hasBoneIssue: Boolean = false,
    val hasMuscularIssue: Boolean = false,
    val pointsDiscipline: Int = 0,
    val currentXp: Int = 0,
    val totalXp: Int = 0,
    val currentLevel: Int = 1,
    val currentStreak: Int = 0,
    val lastXpAt: Date? = null,
    val showInFeed: Boolean = true,
    val gender: String? = null,
    val weight: Double? = null,
    val height: Double? = null,
    val isSmoker: Boolean = false,
    val activityLevel: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactRelation: String? = null,
    val emergencyContactPhone: String? = null,
    val phone: String? = null,
    val notificationsEnabled: Boolean = true,
    val locationEnabled: Boolean = true,
    val sleepStartTime: String? = "22:00",
    val sleepEndTime: String? = "06:00",
    val consentAcceptedAt: Date? = null,
    val updatedAt: Date? = null,
    val pendingSync: Boolean = false,
    /** Papel do usuario no app: "PATIENT" (autocuidado) ou "CAREGIVER" (familiar/cuidador). */
    val userRole: String? = null,
    val basicProfileComplete: Boolean = false,
    val selfCareComplete: Boolean = false,
    val selfCareSetupPending: Boolean = false,
    val caregiverMode: String? = null,
    val avatarIdentifier: String? = null,
    val customPhotoUri: String? = null
)

@Entity(tableName = "exams_local")
data class ExamEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String? = null,
    val userId: String,
    val title: String,
    val category: String? = null,
    val examDate: Date,
    val resultSummary: String? = null,
    val fileUrl: String? = null,
    val status: String = "uploaded",
    val aiExtractedData: String? = null,
    val validatedBy: String? = null,
    val validationNotes: String? = null,
    val createdAt: Date? = null,
    val pendingSync: Boolean = false
)

@Entity(tableName = "clinical_references_local")
data class ClinicalReferenceEntity(
    @PrimaryKey val itemKey: String,
    val itemName: String,
    val category: String,
    val gender: String = "BOTH",
    val minTarget: Double? = null,
    val maxTarget: Double? = null,
    val minCritical: Double? = null,
    val maxCritical: Double? = null,
    val unit: String? = null,
    val interpretationHint: String? = null,
    val institution: String? = "AHA",
    val documentVersion: String? = "2026",
    val parameter: String? = null,
    val updatedAt: Date = Date()
)

@Entity(tableName = "exam_items_local")
data class ExamItemEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String? = null,
    val examId: String,
    val userId: String,
    val itemKey: String,
    val itemName: String,
    val valueNumeric: Double? = null,
    val valueText: String? = null,
    val unit: String? = null,
    val referenceText: String? = null,
    val status: String? = null,
    val measuredAt: Date? = null,
    val createdAt: Date = Date(),
    val pendingSync: Boolean = false
)

@Serializable
@Entity(tableName = "food_catalog_local")
data class FoodEntity(
    @PrimaryKey val remoteId: String,
    val name: String,
    val category: String? = null,
    val kcal: Double? = null,
    val carbsG: Double? = null,
    val proteinG: Double? = null,
    val fatG: Double? = null,
    val fiberG: Double? = null,
    val sodiumMg: Double? = null,
    val calciumMg: Double? = null,
    val status: String? = null,
    val isDiabetesSafe: Boolean = true,
    val isHypertensionSafe: Boolean = true,
    val isThyroidSafe: Boolean = true,
    val preparationRule: String? = null,
    val functionalTags: List<String> = emptyList(),
    val healthBenefits: String? = null,
    val consumptionTip: String? = null,
    val servingSizeGrams: Int = 100,
    val servingUnit: String = "100g",
    val minServingGrams: Int = 1,
    val maxServingGrams: Int = 500,
    val suitableMeals: List<String> = emptyList()
)

@Entity(tableName = "meal_rules_local")
data class MealRuleEntity(
    @PrimaryKey val id: Int,
    val mealName: String,
    val suggestedTime: String? = null,
    val caloriePercentage: Double? = null,
    val vegPercentage: Double = 0.50,
    val proteinPercentage: Double = 0.25,
    val carbPercentage: Double = 0.25
)

@Entity(tableName = "milestones_local")
data class MilestoneEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String? = null,
    val userId: String,
    val title: String,
    val description: String? = null,
    val badgeType: String? = null,
    val achievedAt: Date = Date(),
    val pendingSync: Boolean = false
)

@Entity(tableName = "medications_local")
data class MedicationEntity(
    @PrimaryKey val id: String, // UUID
    val userId: String,
    val name: String,
    val dosage: String? = null,
    val dosageMg: Double? = null,
    val pillQuantity: Int? = null,
    val scheduleTime: String? = null,
    val scheduleTimes: String? = null,
    val notes: String? = null,
    val pendingSync: Boolean = false
)

@Entity(tableName = "medication_logs_local")
data class MedicationLogEntity(
    @PrimaryKey val id: String, // UUID
    val userId: String,
    val medicationId: String,
    val takenAt: Date = Date(),
    val scheduledFor: String? = null,
    val pendingSync: Boolean = false
)

@Entity(tableName = "biometry_local")
data class BiometryEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val remoteId: String? = null,
    val userId: String,
    val weight: Float,
    val height: Float,
    val imc: Float,
    val measuredAt: Date = Date(),
    val pendingSync: Boolean = false
)

@Entity(tableName = "daily_metrics_local", primaryKeys = ["userId", "date"])
data class DailyMetricsEntity(
    val userId: String,
    val date: String, // yyyy-MM-dd
    val steps: Int = 0,

    // ATENCAO-BUILD [REMOVER]: Este campo historico mantem a soma legada.
    // A Fase 2 do reconciliador NAO escreve mais nele - escreve em distanceFinalMeters.
    // Mantido apenas por compat de leitura e pela migration 19->20 (ver Migrations.kt).
    val distanceMeters: Float = 0f,

    // Fase 2 - fontes separadas + resultado reconciliado (ver domain/ActivityReconciler.kt)
    /** Distancia medida pelo GPS na sessao do dia (metros). */
    val distanceGpsMeters: Float = 0f,
    /** Distancia estimada por passos (passos x passada calibrada) na sessao (metros). */
    val distanceStepsMeters: Float = 0f,
    /** Distancia final reconciliada (metros) - valor autoritativo para exibicao/sync. */
    val distanceFinalMeters: Float = 0f,
    /** Score de confiabilidade 0..1. Abaixo de ~0.4 a sessao nao deve render XP. */
    val reliabilityScore: Float = 0.5f,

    val caloriesBurned: Float = 0f,
    val activeMinutes: Int = 0,
    val pendingSync: Boolean = false
)

@Entity(tableName = "feedbacks_local")
data class FeedbackEntity(
    @PrimaryKey val id: String,
    val userId: String? = null,
    val userEmail: String? = null,
    val userName: String? = null,
    val category: String,
    val title: String? = null,
    val message: String,
    val inputMethod: String = "text",
    val appVersion: String? = null,
    val deviceInfo: String? = null,
    val screenshotBase64: String? = null,
    val status: String = "pending",
    val createdAt: Date = Date(),
    val pendingSync: Boolean = true
)

@Entity(tableName = "league_cycles_local")
data class LeagueCycleEntity(
    @PrimaryKey val id: String, // UUID
    val level: Int,
    val weekStartDate: String, // yyyy-MM-dd
    val weekEndDate: String, // yyyy-MM-dd
    val status: String = "active" // active | closed
)

@Entity(tableName = "league_memberships_local")
data class LeagueMembershipEntity(
    @PrimaryKey val id: String, // UUID
    val userId: String,
    val userName: String? = null,
    val userLevel: Int = 1,
    val userStreak: Int = 0,
    val leagueCycleId: String,
    val xpEarned: Int = 0,
    val rankAtClose: Int? = null,
    val outcome: String? = null, // promoted | maintained | demoted
    val pendingSync: Boolean = false
)

@Entity(tableName = "social_posts_local")
data class SocialPostEntity(
    @PrimaryKey val id: String, // UUID
    val userId: String,
    val userName: String? = null,
    val userLevel: Int = 1,
    val postType: String, // milestone | streak | level_up | goal_hit | weekly_recap | family
    val title: String,
    val description: String? = null,
    val relatedMilestoneId: String? = null,
    val createdAt: Date = Date(),
    val isVisible: Boolean = true,
    val reactionCount: Int = 0,
    val hasUserReacted: Boolean = false,
    val pendingSync: Boolean = false,
    // Rede Social Intergeracional - Publicacoes de Orgulho Familiar
    val isFamilyPost: Boolean = false,
    val caregiverName: String? = null,
    val patientName: String? = null,
    // Visibilidade do post (PUBLIC ou FAMILY)
    val visibility: String = "PUBLIC"
)

/**
 * Fase 3 - Registro de concessoes de XP (anti-farming).
 * Uma linha por (usuario, dia, tipo de acao): garante que cada tipo de acao
 * so rende XP uma vez por dia, mesmo que o usuario repita o registro.
 */
@Entity(tableName = "xp_awards_local", primaryKeys = ["userId", "date", "actionType"])
data class XpAwardEntity(
    val userId: String,
    val date: String, // yyyy-MM-dd
    val actionType: String, // GamificationActionType.name
    val xp: Int,
    val awardedAt: Date = Date()
)

@Entity(tableName = "post_reactions_local")
data class PostReactionEntity(
    @PrimaryKey val id: String, // UUID
    val postId: String,
    val userId: String,
    val userName: String? = null,
    val reactionType: String = "apoio",
    val createdAt: Date = Date(),
    val pendingSync: Boolean = false
)

/**
 * Ponte Familiar - item da lista de compras compartilhada entre o familiar e o cuidador.
 */
@Entity(tableName = "grocery_list_local")
data class GroceryListItemEntity(
    @PrimaryKey val remoteId: String,
    val userId: String,
    val weekStartDate: String, // yyyy-MM-dd
    val foodId: String,
    val foodName: String,
    val category: String, // "Hortifruti & Feira", "Cereais & Graos", "Acougue & Ovos", "Mercearia & Chas"
    val suggestedServingWeekGrams: Int,
    val purchaseWeightGrams: Int, // com margem de compra
    val purchaseUnitText: String, // ex: "1 palma (~850g)", "1 duzia (12 un)", "1 pacote (200g)"
    val estimatedPriceBrl: Double,
    val isCheckedInPantry: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Entidade de vínculo familiar - Ponte Familiar & Modo Cuidador.
 * Armazena conexões entre pacientes e cuidadores/familiares.
 */
@Entity(tableName = "family_bindings_local")
data class FamilyBindingEntity(
    @PrimaryKey val id: String, // UUID local
    val patientUserId: String,
    val caregiverUserId: String,
    val caregiverName: String,
    val caregiverRelation: String, // "Filho", "Filha", "Cuidador", "Esposa", "Marido", etc.
    val connectionCode: String, // Ex: "A1B2C3D4" (8 caracteres alfanuméricos)
    val status: String, // "ACTIVE", "PENDING", "REVOKED", "EXPIRED"
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000), // 7 dias de validade
    val remoteId: String? = null, // UUID no Firebase Data Connect (null enquanto não sincronizado)
    val pendingSync: Boolean = false
)

/** TTL das mensagens familiares: 24 horas em milissegundos (decisão D47). */
const val FAMILY_MESSAGE_TTL_MS: Long = 24L * 60 * 60 * 1000

/**
 * Entidade de mensagens familiares - Bilhetes de carinho e incentivos.
 * Usada para comunicacao assincrona entre cuidadores e pacientes.
 *
 * D47 (Mensagens Efêmeras): cada mensagem nasce com [expiresAt] = sentAt + 24h
 * e pode ser apagada pelo remetente via tombstone [deletedAt]. Nenhuma linha
 * ultrapassa a retenção máxima de 24h (purga local + servidor).
 */
@Entity(tableName = "family_messages_local")
data class FamilyMessageEntity(
    @PrimaryKey val id: String, // UUID
    val patientUserId: String,
    val senderName: String,
    val messageText: String,
    val iconType: String, // "WATER", "MED", "LOVE", "WALK", "CUSTOM"
    val isRead: Boolean = false,
    val sentAt: Long = System.currentTimeMillis(),
    val senderUserId: String? = null,
    val remoteId: String? = null,
    val pendingSync: Boolean = false,
    val expiresAt: Long = sentAt + FAMILY_MESSAGE_TTL_MS,
    val deletedAt: Long? = null
)

/**
 * Entidade de registro de notificacoes/crises para controle local de alertas duplicados.
 */
@Entity(
    tableName = "vital_alert_log_local",
    primaryKeys = ["caregiverUserId", "alertType"]
)
data class VitalAlertLogEntity(
    val caregiverUserId: String,
    val alertType: String,
    val patientUserId: String,
    val alertTitle: String = "",
    val alertText: String = "",
    val lastSentAtMs: Long = 0L
)

/**
 * Entidade de auditoria e telemetria para logs de eventos do aplicativo.
 * Usada para rastrear interações do usuário e ações do sistema.
 */
@Entity(tableName = "audit_logs_local")
data class AuditLogEntity(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val userId: String,
    val eventType: String, // VOICE_ASSISTANT, CLINICAL_VITAL, CAREGIVER_ACTION, SOCIAL_FEED, GOAL_ACHIEVED
    val action: String, // OPEN, CLOSE, PARSE, RECORD, VIEW, LIKE, SHARE
    val metadataJson: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val pendingSync: Boolean = true
)

/**
 * Entidade de consulta médica - Agendamento de consultas.
 * Armazena consultas agendadas pelo usuário ou pelo cuidador/familiar.
 * Cada consulta tem status: SUGGESTED (pelo cuidador), ACCEPTED (aceita pelo usuário),
 * REJECTED (recusada pelo usuário), COMPLETED (realizada), CANCELLED (cancelada).
 */
@Entity(tableName = "consultations_local")
data class ConsultationEntity(
    @PrimaryKey val id: String, // UUID local
    val userId: String, // ID do paciente/usuario titular
    val caregiverUserId: String?, // ID do cuidador que sugeriu (se aplicável)
    val caregiverName: String?, // Nome do cuidador que sugeriu
    val caregiverRelation: String?, // Relation of caregiver (Filho, Filha, etc.)
    val title: String, // Título/Especialidade da consulta
    val description: String? = null, // Descrição opcional
    val scheduledDate: Date, // Data e hora agendadas
    val status: String, // "SUGGESTED", "ACCEPTED", "REJECTED", "COMPLETED", "CANCELLED"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val googleCalendarEventId: String? = null, // ID do evento no Google Calendar
    val pendingSync: Boolean = false
)

data class ConsultationStatusCount(
    val status: String,
    val count: Long
)

