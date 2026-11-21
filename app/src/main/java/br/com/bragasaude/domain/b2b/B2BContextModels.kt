package br.com.bragasaude.domain.b2b

import kotlinx.serialization.Serializable

/**
 * Modelos de contexto B2C / B2B e onboarding empresarial em Kotlin puro
 * (prontos para KMP / iOS). Zero dependencia de classes Android ou Room.
 */
@Serializable
enum class AppAccountMode {
    PERSONAL,
    BUSINESS_ONLY,
    DUAL;

    companion object {
        fun fromWire(raw: String?): AppAccountMode {
            return when (raw?.trim()?.uppercase()) {
                "BUSINESS_ONLY" -> BUSINESS_ONLY
                "DUAL" -> DUAL
                else -> PERSONAL
            }
        }
    }
}

@Serializable
enum class AppContextMode {
    PERSONAL,
    PARTNER_OWNER,
    PARTNER_PARTICIPANT;

    companion object {
        fun fromWire(raw: String?): AppContextMode {
            return when (raw?.trim()?.uppercase()) {
                "PARTNER_OWNER" -> PARTNER_OWNER
                "PARTNER_PARTICIPANT" -> PARTNER_PARTICIPANT
                else -> PERSONAL
            }
        }
    }
}

@Serializable
enum class PartnerApprovalStatus(val labelPtBr: String) {
    DRAFT("Em preparação"),
    PENDING_REVIEW("Em análise"),
    CHANGES_REQUESTED("Ajustes solicitados"),
    APPROVED("Empresa aprovada"),
    SUSPENDED("Programa suspenso"),
    REJECTED("Cadastro recusado");

    companion object {
        fun fromWire(raw: String?): PartnerApprovalStatus {
            return when (raw?.trim()?.uppercase()) {
                "PENDING_REVIEW" -> PENDING_REVIEW
                "CHANGES_REQUESTED" -> CHANGES_REQUESTED
                "APPROVED" -> APPROVED
                "SUSPENDED" -> SUSPENDED
                "REJECTED" -> REJECTED
                else -> DRAFT
            }
        }
    }
}

@Serializable
data class ProgramFeaturesDto(
    val assistantChatEnabled: Boolean = true,
    val directPartnerChatEnabled: Boolean = false,
    val aiReceptionistEnabled: Boolean = false,
    val familyLinkEnabled: Boolean = false,
    val partnerRemindersEnabled: Boolean = true,
    val appointmentsEnabled: Boolean = true,
    val preConsultationPdfEnabled: Boolean = true
)

@Serializable
data class DirectChatPolicyDto(
    val serviceHoursLabel: String = "Seg a Sex, 08h às 18h",
    val responseSlaLabel: String = "Resposta em até 1 dia útil",
    val allowOffHoursQueue: Boolean = true
)

@Serializable
data class AiReceptionistPolicyDto(
    val personaName: String = "Assistente Virtual do Programa",
    val activationMode: String = "OUT_OF_HOURS_ONLY",
    val greetingMessage: String = "Olá! Sou a recepcionista virtual deste programa. Posso tirar dúvidas sobre horários, funcionamento e como enviar seu resumo — ou chamar o responsável quando precisar.",
    val partnerFaqKnowledge: String = "",
    val autoEscalateToHumanOnUnknown: Boolean = true
)

@Serializable
data class AppointmentsPolicyDto(
    val allowedModalities: List<String> = listOf("IN_PERSON", "REMOTE"),
    val bookingMode: String = "REQUEST_CONFIRMATION",
    val availabilitiesSummary: String = "Terças e Quintas, 09h às 17h",
    val externalBookingUrl: String? = null
)

@Serializable
data class ProgramExperienceConfigDto(
    val schemaVersion: Int = 1,
    val templateKey: String = "CLINICA_GERAL",
    val welcomeHeadline: String = "Seu espaço de acompanhamento entre consultas",
    val welcomeMessage: String = "Organize passos, hidratação e registros de rotina para compartilhar apenas o que desejar antes do retorno.",
    val enabledMetrics: List<String> = listOf("steps", "hydration", "medications", "authorized_records"),
    val features: ProgramFeaturesDto = ProgramFeaturesDto(),
    val directChatPolicy: DirectChatPolicyDto = DirectChatPolicyDto(),
    val aiReceptionistPolicy: AiReceptionistPolicyDto = AiReceptionistPolicyDto(),
    val appointmentsPolicy: AppointmentsPolicyDto = AppointmentsPolicyDto()
) {
    companion object {
        fun defaultForTemplate(rawTemplateKey: String?): ProgramExperienceConfigDto {
            return when (rawTemplateKey?.trim()?.uppercase()) {
                "NUTRICAO_BEM_ESTAR" -> ProgramExperienceConfigDto(
                    templateKey = "NUTRICAO_BEM_ESTAR",
                    welcomeHeadline = "Hábitos diários mais leves e constantes",
                    welcomeMessage = "Acompanhe sua hidratação, movimento e registros autorizados no seu ritmo, mantendo o controle do que compartilha.",
                    enabledMetrics = listOf("steps", "hydration")
                )
                "FISIO_REABILITACAO" -> ProgramExperienceConfigDto(
                    templateKey = "FISIO_REABILITACAO",
                    welcomeHeadline = "Sua rotina de movimento passo a passo",
                    welcomeMessage = "Registre sua movimentação diária e marque horários de retorno combinados com o programa.",
                    enabledMetrics = listOf("steps", "authorized_records")
                )
                "CUIDADO_SENIOR_HOMECARE" -> ProgramExperienceConfigDto(
                    templateKey = "CUIDADO_SENIOR_HOMECARE",
                    welcomeHeadline = "Cuidado conectado com quem apoia você",
                    welcomeMessage = "Com sua permissão, seu familiar de apoio também acompanha os lembretes e agendamentos do programa.",
                    enabledMetrics = listOf("steps", "hydration", "medications", "authorized_records"),
                    features = ProgramFeaturesDto(familyLinkEnabled = true)
                )
                "FARMACIA_ADESAO" -> ProgramExperienceConfigDto(
                    templateKey = "FARMACIA_ADESAO",
                    welcomeHeadline = "Lembretes e organização da sua rotina",
                    welcomeMessage = "Use o alarme pessoal do seu app no dia a dia; aqui combinamos avisos simples de acompanhamento e apoio.",
                    enabledMetrics = listOf("hydration", "medications"),
                    features = ProgramFeaturesDto(appointmentsEnabled = false)
                )
                "SAUDE_CORPORATIVA" -> ProgramExperienceConfigDto(
                    templateKey = "SAUDE_CORPORATIVA",
                    welcomeHeadline = "Bem-estar preventivo no dia a dia",
                    welcomeMessage = "Acompanhe seus objetivos de caminhada e hidratação com privacidade garantida: seus documentos clínicos não são expostos.",
                    enabledMetrics = listOf("steps", "hydration"),
                    features = ProgramFeaturesDto(preConsultationPdfEnabled = false, appointmentsEnabled = false)
                )
                "CUSTOM" -> ProgramExperienceConfigDto(
                    templateKey = "CUSTOM",
                    welcomeHeadline = "Programa personalizado com a nossa marca",
                    welcomeMessage = "Escolha apenas os registros que deseja compartilhar e gerencie seu vínculo quando quiser."
                )
                else -> ProgramExperienceConfigDto(templateKey = "CLINICA_GERAL")
            }
        }
    }
}

@Serializable
data class TemplateCatalogItemDto(
    val templateKey: String,
    val title: String,
    val subtitle: String,
    val sampleSafeReminder: String,
    val defaultConfig: ProgramExperienceConfigDto
)

@Serializable
data class UpdateExperienceRequest(
    val templateKey: String? = null,
    val applyTemplateDefaults: Boolean = false,
    val experienceConfig: ProgramExperienceConfigDto? = null
)

@Serializable
data class B2BContextDescriptor(
    val mode: String = AppContextMode.PERSONAL.name,
    val userId: String = "",
    val programId: String? = null,
    val programName: String? = null,
    val companyName: String? = null,
    val primaryColor: String? = null,
    val logoUrl: String? = null,
    val approvalStatus: String? = null,
    val sharedCategories: List<String> = emptyList(),
    val templateKey: String? = null,
    val experienceConfig: ProgramExperienceConfigDto? = null,
    val familyShareEnabled: Boolean = false
) {
    val parsedMode: AppContextMode
        get() = AppContextMode.fromWire(mode)

    val isPartnerContext: Boolean
        get() = parsedMode != AppContextMode.PERSONAL && !programId.isNullOrBlank()

    fun resolvedExperienceConfig(): ProgramExperienceConfigDto =
        experienceConfig ?: ProgramExperienceConfigDto.defaultForTemplate(templateKey)

    fun cacheKey(): String {
        return if (parsedMode == AppContextMode.PERSONAL || programId.isNullOrBlank()) {
            AppContextMode.PERSONAL.name
        } else {
            "${parsedMode.name}:${programId.trim()}"
        }
    }

    companion object {
        fun personal(userId: String = ""): B2BContextDescriptor = B2BContextDescriptor(
            mode = AppContextMode.PERSONAL.name,
            userId = userId,
            programId = null
        )
    }
}

@Serializable
data class B2BContextsEnvelope(
    val accountMode: String = AppAccountMode.PERSONAL.name,
    val defaultMode: String = AppContextMode.PERSONAL.name,
    val contexts: List<B2BContextDescriptor> = emptyList()
)

@Serializable
data class UpdateAccountModeResult(
    val accountMode: String? = null,
    val defaultMode: String? = null,
    val errorDetail: String? = null
) {
    val isSuccess: Boolean
        get() = !accountMode.isNullOrBlank()
}

@Serializable
data class SelectB2BContextRequest(
    val mode: String = AppContextMode.PERSONAL.name,
    val programId: String? = null
)

@Serializable
data class SelectB2BContextEnvelope(
    val activeContext: B2BContextDescriptor
)

@Serializable
data class ProofDocumentSummaryDto(
    val id: String,
    val docType: String,
    val fileName: String,
    val contentType: String = "application/pdf",
    val sizeBytes: Long = 0,
    val uploadedAt: String = ""
)

@Serializable
data class PartnerProgramDto(
    val programId: String,
    val ownerUserId: String,
    val companyName: String,
    val programName: String,
    val cnpj: String? = null,
    val primaryColor: String = B2BOnboardingValidator.DEFAULT_PRIMARY_COLOR,
    val logoUrl: String? = null,
    val approvalStatus: String = PartnerApprovalStatus.DRAFT.name,
    val reviewNote: String? = null,
    val inviteCode: String? = null,
    val invitePaused: Boolean = false,
    val aiPaused: Boolean = false,
    val seatLimit: Int = 10,
    val activeParticipants: Int = 0,
    val canPromote: Boolean = false,
    val templateKey: String = "CLINICA_GERAL",
    val experienceConfig: ProgramExperienceConfigDto =
        ProgramExperienceConfigDto.defaultForTemplate(templateKey),
    val responsibleName: String? = null,
    val responsibleCpfMasked: String? = null,
    val responsibleRole: String? = null,
    val responsiblePhone: String? = null,
    val councilType: String? = null,
    val councilNumber: String? = null,
    val councilUf: String? = null,
    val city: String? = null,
    val state: String? = null,
    val termsAcceptedAt: String? = null,
    val termsVersion: String? = null,
    val autoKybStatus: String? = null,
    val autoKybFlags: List<String> = emptyList(),
    val proofDocuments: List<ProofDocumentSummaryDto> = emptyList()
) {
    val parsedStatus: PartnerApprovalStatus
        get() = PartnerApprovalStatus.fromWire(approvalStatus)
}

@Serializable
data class UpsertPartnerProgramRequest(
    val companyName: String,
    val programName: String,
    val cnpj: String? = null,
    val primaryColor: String = B2BOnboardingValidator.DEFAULT_PRIMARY_COLOR,
    val logoUrl: String? = null,
    val templateKey: String? = null,
    val responsibleName: String? = null,
    val responsibleCpf: String? = null,
    val responsibleRole: String? = null,
    val responsiblePhone: String? = null,
    val councilType: String? = null,
    val councilNumber: String? = null,
    val councilUf: String? = null,
    val city: String? = null,
    val state: String? = null,
    val termsAccepted: Boolean? = null,
    val termsVersion: String? = null
)

@Serializable
data class SavePartnerProgramResult(
    val program: PartnerProgramDto? = null,
    val errorDetail: String? = null
) {
    val isSuccess: Boolean
        get() = program != null
}

@Serializable
data class SubmitPartnerProgramResult(
    val program: PartnerProgramDto? = null,
    val kybDecision: String? = null,
    val flags: List<String> = emptyList(),
    val requiresManualProof: Boolean = false,
    val errorDetail: String? = null
) {
    val isSuccess: Boolean
        get() = program != null
}

@Serializable
data class UploadPartnerProofResult(
    val proofId: String? = null,
    val program: PartnerProgramDto? = null,
    val errorDetail: String? = null
) {
    val isSuccess: Boolean
        get() = proofId != null && program != null
}

@Serializable
data class PartnerInvitePreviewDto(
    val programId: String,
    val companyName: String,
    val programName: String,
    val primaryColor: String = B2BOnboardingValidator.DEFAULT_PRIMARY_COLOR,
    val logoUrl: String? = null,
    val inviteCode: String,
    val invitePaused: Boolean = false,
    val hasAvailableSeats: Boolean = true
)

@Serializable
enum class SharedCategoryOption(
    val wireKey: String,
    val titlePtBr: String,
    val descriptionPtBr: String
) {
    STEPS(
        wireKey = "steps",
        titlePtBr = "Passos e atividade física",
        descriptionPtBr = "Contagem diária de passos e ritmo de caminhada."
    ),
    HYDRATION(
        wireKey = "hydration",
        titlePtBr = "Hidratação diária",
        descriptionPtBr = "Registros de água e acompanhamento da meta de hidratação."
    ),
    MEDICATIONS(
        wireKey = "medications",
        titlePtBr = "Adesão a medicamentos",
        descriptionPtBr = "O gestor pode consultar nomes, horários e confirmações dos lembretes para acompanhar sua rotina."
    ),
    AUTHORIZED_RECORDS(
        wireKey = "authorized_records",
        titlePtBr = "Registros autorizados de rotina",
        descriptionPtBr = "O gestor pode consultar pressão, glicemia, frequência cardíaca, saturação, peso, altura e IMC para acompanhar sua rotina."
    );

    companion object {
        val ALL_WIRE_KEYS: Set<String> = entries.map { it.wireKey }.toSet()

        fun fromWire(raw: String?): SharedCategoryOption? {
            val clean = raw?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.wireKey == clean }
        }
    }
}

@Serializable
data class JoinPartnerProgramRequest(
    val inviteCode: String,
    val sharedCategories: List<String>,
    val familyShareEnabled: Boolean = false
)

@Serializable
data class UpdatePartnerConsentRequest(
    val sharedCategories: List<String>,
    val familyShareEnabled: Boolean? = null
)

/** Visão autorizada pelo servidor, mantida apenas em memória pelo cuidador. */
@Serializable
data class FamilyProgramOverviewDto(
    val patientUserId: String,
    val programs: List<FamilyProgramOverviewItemDto> = emptyList()
)

@Serializable
data class FamilyProgramOverviewItemDto(
    val programId: String,
    val programName: String,
    val companyName: String = "",
    val primaryColor: String = "#1A6B5C",
    val logoUrl: String? = null,
    val templateKey: String = "CUSTOM",
    val consentedAt: String = "",
    val familyShareEnabled: Boolean = false,
    val remindersEnabled: Boolean = false,
    val appointmentsEnabled: Boolean = false,
    val reminders: List<ProgramReminderDto> = emptyList(),
    val appointments: List<ProgramAppointmentDto> = emptyList()
)

@Serializable
data class FamilyReminderAcknowledgementDto(
    val acknowledgedAt: String,
    val acknowledgedByUid: String? = null
)

@Serializable
data class PartnerEnrollmentResult(
    val activeContext: B2BContextDescriptor? = null,
    val errorDetail: String? = null
) {
    val isSuccess: Boolean
        get() = activeContext != null
}

@Serializable
data class PartnerParticipantItemDto(
    val patientUserId: String,
    val displayName: String,
    val sharedCategories: List<String> = emptyList(),
    val effectiveCategories: List<String> = emptyList(),
    val missingProgramCategories: List<String> = emptyList(),
    val familySupportActive: Boolean = false,
    val familySupportSummary: String? = null,
    val pendingAppointmentsCount: Int = 0,
    val unreadDirectMessagesCount: Int = 0,
    val consentedAt: String? = null,
    val activityDatesLast7Days: List<String>? = null,
    val pendingRemindersCount: Int? = null,
    val upcomingAppointmentsCount: Int? = null,
    val consentStatus: String? = null
)

@Serializable
data class PartnerWalletKpisDto(
    val activeUsers: Int = 0,
    val inactiveUsers: Int = 0,
    val retentionRate: Double? = null,
    val remindersRead: Int = 0,
    /** Total de lembretes enviados no período; 0 quando o gateway não informa. */
    val remindersTotal: Int = 0,
    val appointmentsConfirmed: Int = 0,
    /** Agendamentos aguardando confirmação do profissional; 0 quando ausente. */
    val pendingAppointmentsTotal: Int = 0,
    /** Mensagens diretas não lidas dos participantes; 0 quando ausente. */
    val unreadMessagesTotal: Int = 0,
    val periodDays: Int = 7,
    val asOf: String? = null,
    val dailyActiveUsers: List<Int>? = null,
    val appointmentsDelta: Int? = null
)

@Serializable
data class PartnerParticipantsPageDto(
    val program: PartnerProgramDto? = null,
    val kpis: PartnerWalletKpisDto? = null,
    val participants: List<PartnerParticipantItemDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)

@Serializable
data class RemovePartnerParticipantResult(
    val program: PartnerProgramDto? = null,
    val removedPatientUserId: String? = null,
    val errorDetail: String? = null
) {
    val isSuccess: Boolean
        get() = removedPatientUserId != null
}

@Serializable
data class UploadPartnerLogoResult(
    val logoUrl: String? = null,
    val program: PartnerProgramDto? = null,
    val errorDetail: String? = null
) {
    val isSuccess: Boolean
        get() = !logoUrl.isNullOrBlank()
}

@Serializable
data class SharedStepsDayDto(
    val date: String,
    val steps: Int,
    val activeMinutes: Int,
    val distanceMeters: Double
)

@Serializable
data class SharedStepsSectionDto(
    val source: String,
    val averageSteps: Long,
    val daysRecorded: Int,
    val days: List<SharedStepsDayDto> = emptyList()
)

@Serializable
data class SharedHydrationDayDto(
    val date: String,
    val totalMl: Long
)

@Serializable
data class SharedHydrationSectionDto(
    val source: String,
    val averageMl: Long,
    val daysRecorded: Int,
    val days: List<SharedHydrationDayDto> = emptyList()
)

@Serializable
data class SharedMedicationItemDto(
    val name: String,
    val dosageMg: Double? = null,
    val schedule: String = ""
)

@Serializable
data class SharedMedicationsSectionDto(
    val source: String,
    val reminderCount: Int,
    val userConfirmationsInPeriod: Long,
    val note: String,
    val items: List<SharedMedicationItemDto> = emptyList()
)

@Serializable
data class SharedVitalEntryDto(
    val measuredAt: String,
    val origin: String,
    val systolicPressure: Int? = null,
    val diastolicPressure: Int? = null,
    val heartRate: Int? = null,
    val glucoseLevel: Int? = null,
    val glucoseType: String? = null,
    val oxygenSaturation: Double? = null
)

@Serializable
data class SharedBiometryEntryDto(
    val measuredAt: String,
    val weightKg: Double,
    val heightM: Double? = null,
    val imc: Double? = null
)

@Serializable
data class SharedAuthorizedRecordsSectionDto(
    val source: String,
    val zeroFileEgressNotice: String,
    val records: List<SharedVitalEntryDto> = emptyList(),
    val biometries: List<SharedBiometryEntryDto> = emptyList()
)

@Serializable
data class ParticipantRoutineSummaryDto(
    val programId: String,
    val programName: String,
    val companyName: String,
    val patientUserId: String,
    val participantName: String,
    val periodDays: Int,
    val sharedCategories: List<String> = emptyList(),
    val familySupportActive: Boolean = false,
    val familySupportSummary: String? = null,
    val stepsSection: SharedStepsSectionDto? = null,
    val hydrationSection: SharedHydrationSectionDto? = null,
    val medicationsSection: SharedMedicationsSectionDto? = null,
    val authorizedRecordsSection: SharedAuthorizedRecordsSectionDto? = null,
    val periodEnd: String? = null,
    val availableDetails: List<String> = emptyList(),
    val detailCategory: String? = null
)

@Serializable
enum class PartnerReminderType(val wireValue: String, val labelPtBr: String) {
    ROUTINE("ROUTINE", "Rotina geral"),
    HYDRATION("HYDRATION", "Hidratação"),
    ACTIVITY("ACTIVITY", "Atividade física"),
    PRE_CONSULT("PRE_CONSULT", "Pré-consulta");

    companion object {
        fun fromWire(raw: String?): PartnerReminderType {
            val clean = raw?.trim()?.uppercase()
            return entries.firstOrNull { it.wireValue == clean } ?: ROUTINE
        }
    }
}

@Serializable
data class ProgramReminderDto(
    val reminderId: String,
    val programId: String,
    val targetPatientId: String? = null,
    val targetPatientName: String? = null,
    val targetCategory: String? = null,
    val reminderType: String = "ROUTINE",
    val title: String,
    val body: String,
    val createdAt: String,
    val acknowledgedCount: Int = 0,
    val acknowledgedAt: String? = null,
    val acknowledgedByUid: String? = null,
    val recipientCount: Int? = null
)

@Serializable
data class EnrollmentRemindersFeedDto(
    val programId: String,
    val programName: String = "",
    val primaryColor: String = "#1A6B5C",
    val muted: Boolean = false,
    val reminders: List<ProgramReminderDto> = emptyList()
)

@Serializable
data class CreateProgramReminderRequest(
    val targetPatientId: String? = null,
    val targetCategory: String? = null,
    val reminderType: String = "ROUTINE",
    val title: String,
    val body: String
)

@Serializable
data class CreateProgramReminderResult(
    val reminder: ProgramReminderDto? = null,
    val recipientCount: Int = 0,
    val errorDetail: String? = null
) {
    val isSuccess: Boolean
        get() = reminder != null
}

@Serializable
data class ReportProgramAbuseRequest(
    val reason: String,
    val details: String? = null,
    val muteAlso: Boolean = true
)

@Serializable
data class ReportProgramAbuseResult(
    val reportId: String? = null,
    val programId: String? = null,
    val muted: Boolean = false,
    val errorDetail: String? = null
) {
    val isSuccess: Boolean
        get() = !reportId.isNullOrBlank()
}

val ABUSE_REPORT_REASONS: List<Pair<String, String>> = listOf(
    "PRESCRICAO_MEDICA_INDEVIDA" to "Orientação de remédio ou diagnóstico indevido",
    "SPAM_OU_ASSEDIO" to "Spam ou mensagens excessivas",
    "CONTEUDO_INADEQUADO" to "Conteúdo inadequado ou enganoso",
    "OUTRO" to "Outro motivo"
)

enum class ProgramAppointmentStatus(val wireKey: String, val labelPtBr: String) {
    PENDING("PENDING", "Aguardando confirmação"),
    CONFIRMED("CONFIRMED", "Confirmado"),
    RESCHEDULE_REQUESTED("RESCHEDULE_REQUESTED", "Reagendamento sugerido"),
    CANCELLED("CANCELLED", "Cancelado"),
    COMPLETED("COMPLETED", "Concluído");

    companion object {
        fun fromWire(raw: String?): ProgramAppointmentStatus {
            val upper = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.wireKey == upper } ?: PENDING
        }
    }
}

enum class AppointmentModality(val wireKey: String, val labelPtBr: String) {
    IN_PERSON("IN_PERSON", "Presencial"),
    REMOTE("REMOTE", "Remoto");

    companion object {
        fun fromWire(raw: String?): AppointmentModality {
            val upper = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.wireKey == upper } ?: IN_PERSON
        }
    }
}

@Serializable
data class ProgramAppointmentDto(
    val appointmentId: String,
    val programId: String,
    val patientUserId: String,
    val patientName: String? = null,
    val modality: String,
    val requestedSlotText: String,
    /** Período preferido normalizado pelo gateway (ex.: "PROXIMA_SEMANA_MANHA"); opcional. */
    val requestedPeriod: String? = null,
    val scheduledAt: String? = null,
    val patientNote: String? = null,
    val partnerNote: String? = null,
    val status: String = "PENDING",
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    val parsedStatus: ProgramAppointmentStatus
        get() = ProgramAppointmentStatus.fromWire(status)
    val parsedModality: AppointmentModality
        get() = AppointmentModality.fromWire(modality)
}

@Serializable
data class OwnerAppointmentsFeedDto(
    val programId: String,
    val programName: String = "",
    val appointmentsPolicy: AppointmentsPolicyDto = AppointmentsPolicyDto(),
    val appointments: List<ProgramAppointmentDto> = emptyList()
)

@Serializable
data class EnrollmentAppointmentsFeedDto(
    val programId: String,
    val programName: String = "",
    val appointmentsPolicy: AppointmentsPolicyDto = AppointmentsPolicyDto(),
    val pendingCount: Int = 0,
    val appointments: List<ProgramAppointmentDto> = emptyList()
)

@Serializable
data class AppointmentMutationResult(
    val appointment: ProgramAppointmentDto? = null,
    val errorDetail: String? = null
) {
    val isSuccess: Boolean
        get() = appointment != null
}

enum class DirectMessageSenderRole(val wireKey: String, val labelPtBr: String) {
    PARTNER("PARTNER", "Equipe do programa"),
    PATIENT("PATIENT", "Participante"),
    AI_RECEPTIONIST("AI_RECEPTIONIST", "Recepcionista IA");

    companion object {
        fun fromWire(raw: String?): DirectMessageSenderRole {
            val upper = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.wireKey == upper } ?: PARTNER
        }
    }
}

enum class ThreadHandlingMode(val wireKey: String, val labelPtBr: String) {
    AI_ACTIVE("AI_ACTIVE", "Recepcionista IA ativa"),
    HUMAN_ACTIVE("HUMAN_ACTIVE", "Atendimento humano ativo"),
    WAITING_HUMAN("WAITING_HUMAN", "Aguardando atendente humano");

    companion object {
        fun fromWire(raw: String?): ThreadHandlingMode {
            val upper = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.wireKey == upper } ?: AI_ACTIVE
        }
    }
}

@Serializable
data class DirectThreadSummaryDto(
    val programId: String,
    val patientUserId: String,
    val handlingMode: String = "AI_ACTIVE",
    val takenOverBy: String? = null,
    val handoffNote: String? = null,
    val updatedAt: String = ""
) {
    val parsedHandlingMode: ThreadHandlingMode
        get() = ThreadHandlingMode.fromWire(handlingMode)
}

@Serializable
data class DirectChatMessageDto(
    val messageId: String,
    val programId: String,
    val patientUserId: String,
    val senderUserId: String,
    val senderRole: String,
    val body: String,
    val createdAt: String = "",
    val readAt: String? = null
) {
    val parsedRole: DirectMessageSenderRole
        get() = DirectMessageSenderRole.fromWire(senderRole)
}

@Serializable
data class DirectChatThreadPageDto(
    val programId: String,
    val programName: String = "",
    val companyName: String = "",
    val assistantChatEnabled: Boolean = true,
    val aiReceptionistEnabled: Boolean = false,
    val aiPaused: Boolean = false,
    val directChatPolicy: DirectChatPolicyDto = DirectChatPolicyDto(),
    val aiReceptionistPolicy: AiReceptionistPolicyDto = AiReceptionistPolicyDto(),
    val isOffHoursNow: Boolean = false,
    val thread: DirectThreadSummaryDto? = null,
    val messages: List<DirectChatMessageDto> = emptyList(),
    val nextCursor: String? = null,
    val hasMore: Boolean = false
)

@Serializable
data class SendDirectMessageResult(
    val message: DirectChatMessageDto? = null,
    val sentOffHours: Boolean = false,
    val aiReply: DirectChatMessageDto? = null,
    val aiFallbackReason: String? = null,
    val thread: DirectThreadSummaryDto? = null,
    val errorDetail: String? = null
) {
    val isSuccess: Boolean
        get() = message != null
}

@Serializable
data class ThreadHandoffResult(
    val thread: DirectThreadSummaryDto? = null,
    val errorDetail: String? = null
) {
    val isSuccess: Boolean
        get() = thread != null
}

object B2BOnboardingValidator {
    const val DEFAULT_PRIMARY_COLOR = "#1A6B5C"
    const val REFERENCE_SUBTITLE =
        "Um espaço com a sua marca, dentro do Braga Saúde. Personalize nome, cor e logo sobre os recursos da base comum."

    fun buildConsentHeading(programName: String?): String {
        val target = programName?.trim()?.takeIf { it.isNotBlank() } ?: "o programa parceiro"
        return "Escolha quais registros compartilhar com $target."
    }

    fun normalizeSharedCategories(raw: Collection<String>): List<String> {
        return raw.mapNotNull { SharedCategoryOption.fromWire(it)?.wireKey }.distinct()
    }

    fun isValidConsentSelection(raw: Collection<String>): Boolean {
        return normalizeSharedCategories(raw).isNotEmpty()
    }

    val PRESET_COLORS = listOf(
        "#1A6B5C",
        "#0F4C81",
        "#2563EB",
        "#7C3AED",
        "#B45309",
        "#BE123C"
    )

    const val TERMS_VERSION_CURRENT = "B2B-V1-2026"

    data class CatalogOption(val key: String, val labelPtBr: String)

    val TEMPLATE_OPTIONS = listOf(
        CatalogOption("CLINICA_GERAL", "Clínica Geral / Consultório"),
        CatalogOption("NUTRICAO_BEM_ESTAR", "Nutrição e Bem-Estar"),
        CatalogOption("FISIO_REABILITACAO", "Fisioterapia e Reabilitação"),
        CatalogOption("CUIDADO_SENIOR_HOMECARE", "Cuidado Sênior e Home Care"),
        CatalogOption("FARMACIA_ADESAO", "Farmácia e Adesão"),
        CatalogOption("SAUDE_CORPORATIVA", "Saúde Corporativa / RH"),
        CatalogOption("CUSTOM", "Personalizado (Empresa/Instituição)")
    )

    val RESPONSIBLE_ROLE_OPTIONS = listOf(
        CatalogOption("SOCIO_TITULAR", "Sócio / Titular / MEI"),
        CatalogOption("RESPONSAVEL_TECNICO", "Responsável Técnico"),
        CatalogOption("GERENTE_ADMIN", "Gerente Administrativo")
    )

    val COUNCIL_TYPE_OPTIONS = listOf("CRM", "CRN", "CREFITO", "COREN", "CRF", "CRP", "CRO")

    val PROOF_DOC_TYPE_OPTIONS = listOf(
        CatalogOption("CARTAO_CNPJ_CCMEI", "Cartão CNPJ ou CCMEI"),
        CatalogOption("CRM_CARTEIRA", "Carteira do Conselho (CRM/CRN/etc.)"),
        CatalogOption("CARTA_AUTORIZACAO", "Carta de Autorização da Diretoria"),
        CatalogOption("DOC_RESPONSAVEL", "Documento do Responsável Legal")
    )

    val BR_UFS = listOf(
        "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG",
        "PA", "PB", "PR", "PE", "PI", "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO"
    )

    private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    private val INVITE_CODE_REGEX = Regex("^[A-Za-z0-9_-]{3,32}$")

    fun isAssistencialTemplate(templateKey: String?): Boolean {
        return when (templateKey?.trim()?.uppercase()) {
            "CLINICA_GERAL",
            "NUTRICAO_BEM_ESTAR",
            "FISIO_REABILITACAO",
            "CUIDADO_SENIOR_HOMECARE",
            "FARMACIA_ADESAO",
            null,
            "" -> true
            else -> false
        }
    }

    fun isValidHexColor(raw: String?): Boolean {
        if (raw.isNullOrBlank()) return false
        return HEX_COLOR_REGEX.matches(raw.trim())
    }

    fun normalizeHexColorOrDefault(raw: String?): String {
        val candidate = raw?.trim()?.uppercase().orEmpty()
        return if (HEX_COLOR_REGEX.matches(candidate)) candidate else DEFAULT_PRIMARY_COLOR
    }

    fun extractCnpjDigits(raw: String?): String {
        return raw.orEmpty().filter { it.isDigit() }
    }

    fun isValidCnpj(raw: String?): Boolean {
        val digits = extractCnpjDigits(raw)
        if (digits.length != 14 || digits.toSet().size <= 1) return false
        val nums = digits.map { it - '0' }
        val w1 = intArrayOf(5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val sum1 = (0 until 12).sumOf { nums[it] * w1[it] }
        val mod1 = sum1 % 11
        val dv1 = if (mod1 < 2) 0 else 11 - mod1
        if (nums[12] != dv1) return false

        val w2 = intArrayOf(6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2)
        val sum2 = (0 until 13).sumOf { nums[it] * w2[it] }
        val mod2 = sum2 % 11
        val dv2 = if (mod2 < 2) 0 else 11 - mod2
        return nums[13] == dv2
    }

    fun formatCnpjOrNull(raw: String?): String? {
        val digits = extractCnpjDigits(raw)
        if (!isValidCnpj(digits)) return null
        return "${digits.substring(0, 2)}.${digits.substring(2, 5)}.${digits.substring(5, 8)}/" +
            "${digits.substring(8, 12)}-${digits.substring(12, 14)}"
    }

    fun extractCpfDigits(raw: String?): String {
        return raw.orEmpty().filter { it.isDigit() }
    }

    fun isValidCpf(raw: String?): Boolean {
        val digits = extractCpfDigits(raw)
        if (digits.length != 11 || digits.toSet().size <= 1) return false
        val nums = digits.map { it - '0' }
        val sum1 = (0 until 9).sumOf { nums[it] * (10 - it) }
        val dv1 = ((sum1 * 10) % 11).let { if (it == 10) 0 else it }
        if (nums[9] != dv1) return false

        val sum2 = (0 until 10).sumOf { nums[it] * (11 - it) }
        val dv2 = ((sum2 * 10) % 11).let { if (it == 10) 0 else it }
        return nums[10] == dv2
    }

    fun formatCpfOrNull(raw: String?): String? {
        val digits = extractCpfDigits(raw)
        if (!isValidCpf(digits)) return null
        return "${digits.substring(0, 3)}.${digits.substring(3, 6)}.${digits.substring(6, 9)}-${digits.substring(9, 11)}"
    }

    fun isValidResponsiblePhone(raw: String?): Boolean {
        val digits = extractCnpjDigits(raw).let {
            if (it.length in 12..13 && it.startsWith("55")) it.substring(2) else it
        }
        return digits.length in 10..11 && digits.toSet().size > 1
    }

    fun isValidInviteCode(raw: String?): Boolean {
        val clean = raw?.trim().orEmpty()
        return INVITE_CODE_REGEX.matches(clean)
    }

    fun parseInviteCodeFromUri(rawUri: String?): String? {
        val uriText = rawUri?.trim().orEmpty()
        if (uriText.isEmpty()) return null
        val lower = uriText.lowercase()
        if (!lower.startsWith("https://") && !lower.startsWith("http://")) return null

        val withoutFragment = uriText.substringBefore('#')
        val pathPart = withoutFragment.substringBefore('?')
        val queryPart = withoutFragment.substringAfter('?', "")

        if (queryPart.isNotEmpty()) {
            queryPart.split('&').forEach { pair ->
                val key = pair.substringBefore('=').trim().lowercase()
                val value = pair.substringAfter('=', "").trim().uppercase()
                if ((key == "code" || key == "codigo" || key == "invite") && isValidInviteCode(value)) {
                    return value
                }
            }
        }

        val marker = "/convite/"
        val idx = pathPart.lowercase().indexOf(marker)
        if (idx >= 0) {
            val candidate = pathPart
                .substring(idx + marker.length)
                .substringBefore('/')
                .trim()
                .uppercase()
            if (isValidInviteCode(candidate)) {
                return candidate
            }
        }
        return null
    }

    fun canSaveDraft(
        companyName: String,
        programName: String,
        primaryColor: String,
        cnpj: String? = null,
        logoUrl: String? = null
    ): Boolean {
        val companyOk = companyName.trim().length in 2..160
        val programOk = programName.trim().length in 2..120
        val colorOk = isValidHexColor(primaryColor)
        val cnpjOk = cnpj.isNullOrBlank() || isValidCnpj(cnpj)
        val logoOk = logoUrl.isNullOrBlank() || (logoUrl.trim().startsWith("https://") && logoUrl.trim().length <= 512)
        return companyOk && programOk && colorOk && cnpjOk && logoOk
    }

    fun canSubmitForReview(
        companyName: String,
        programName: String,
        cnpj: String?,
        primaryColor: String,
        logoUrl: String? = null
    ): Boolean {
        return canSaveDraft(companyName, programName, primaryColor, cnpj, logoUrl) && isValidCnpj(cnpj)
    }

    fun canSubmitKybForReview(
        companyName: String,
        programName: String,
        cnpj: String?,
        primaryColor: String,
        responsibleName: String?,
        responsibleCpf: String?,
        cpfAlreadySaved: Boolean,
        responsibleRole: String?,
        responsiblePhone: String?,
        city: String?,
        state: String?,
        termsAccepted: Boolean,
        councilType: String? = null,
        councilNumber: String? = null,
        councilUf: String? = null,
        logoUrl: String? = null
    ): Boolean {
        if (!canSubmitForReview(companyName, programName, cnpj, primaryColor, logoUrl)) {
            return false
        }
        val respNameOk = responsibleName.orEmpty().trim().length in 3..160
        val cpfOk = if (responsibleCpf.isNullOrBlank()) cpfAlreadySaved else isValidCpf(responsibleCpf)
        val roleOk = RESPONSIBLE_ROLE_OPTIONS.any { it.key == responsibleRole?.trim()?.uppercase() }
        val phoneOk = isValidResponsiblePhone(responsiblePhone)
        val cityOk = city.orEmpty().trim().length in 2..96
        val ufOk = state.orEmpty().trim().uppercase() in BR_UFS
        val councilProvided = listOf(councilType, councilNumber, councilUf).any { !it.isNullOrBlank() }
        val councilOk = !councilProvided || (
            councilType.orEmpty().trim().uppercase() in COUNCIL_TYPE_OPTIONS &&
                councilNumber.orEmpty().trim().length in 3..24 &&
                councilUf.orEmpty().trim().uppercase() in BR_UFS
            )
        return respNameOk && cpfOk && roleOk && phoneOk && cityOk && ufOk && councilOk && termsAccepted
    }

    fun formatKybFlagPtBr(flag: String): String {
        val upper = flag.trim().uppercase()
        return when {
            upper == "RECENT_CNPJ" ->
                "CNPJ aberto há menos de 30 dias — nossa equipe confirmará o início de operação."
            upper.startsWith("PROTECTED_BRAND_TERM") ->
                "O nome contém termo hospitalar ou institucional protegido e requer conferência de vínculo."
            upper == "OWNER_NOT_IN_QSA" ->
                "Responsável não localizado automaticamente no QSA da Receita Federal (envie carta de autorização ou documento)."
            upper == "MEI_UNVERIFIED_CPF" ->
                "MEI sem dígitos verificáveis de CPF na base pública — anexe o CCMEI/Cartão CNPJ abaixo para agilizar."
            upper == "CNAE_INCOMPATIBLE" ->
                "CNAE diferente dos códigos padrão deste perfil — revisão humana confirmará a atuação."
            upper == "COUNCIL_NOT_INFORMED" ->
                "Perfil assistencial sem registro profissional (CRM/CRN/etc.) informado."
            upper == "API_UNAVAILABLE" ->
                "Consulta automática da Receita Federal indisponível no momento — solicitação encaminhada para revisão."
            upper == "AUTO_APPROVE_DISABLED" ->
                "Aprovação manual obrigatória habilitada no ambiente."
            else -> "Pendência cadastral em conferência ($upper)."
        }
    }

    fun computeMissingConsentCategories(
        config: ProgramExperienceConfigDto?,
        sharedCategories: Collection<String>
    ): List<SharedCategoryOption> {
        val enabled = normalizeSharedCategories(config?.enabledMetrics.orEmpty())
        val shared = normalizeSharedCategories(sharedCategories).toSet()
        return enabled.filterNot { it in shared }
            .mapNotNull { SharedCategoryOption.fromWire(it) }
    }

    fun simulateAiReceptionistReply(
        config: ProgramExperienceConfigDto,
        programName: String,
        question: String
    ): String {
        val cleanQuestion = question.trim().lowercase()
        val brand = programName.trim().ifBlank { "este programa" }
        val faq = config.aiReceptionistPolicy.partnerFaqKnowledge.trim()

        if (cleanQuestion.isBlank()) {
            return config.aiReceptionistPolicy.greetingMessage
        }
        val clinicalKeywords = listOf("dose", "mg", "comprimido", "remedio", "remédio", "prescr", "diagn")
        if (clinicalKeywords.any { cleanQuestion.contains(it) }) {
            return "Não posso orientar doses, medicações ou diagnósticos. Para dúvidas sobre sua saúde, procure sua equipe ou solicite atendimento com o responsável de $brand."
        }
        if (cleanQuestion.contains("horario") || cleanQuestion.contains("horário") || cleanQuestion.contains("atend")) {
            return "O atendimento humano de $brand funciona em: ${config.directChatPolicy.serviceHoursLabel} (${config.directChatPolicy.responseSlaLabel})."
        }
        if (cleanQuestion.contains("agend") || cleanQuestion.contains("consulta") || cleanQuestion.contains("marcar")) {
            return if (config.features.appointmentsEnabled) {
                "Horários de agendamento de $brand: ${config.appointmentsPolicy.availabilitiesSummary} (${config.appointmentsPolicy.allowedModalities.joinToString(" / ") { if (it == "REMOTE") "Remoto" else "Presencial" }})."
            } else {
                "O agendamento pelo app está desativado neste programa no momento."
            }
        }
        if (faq.isNotBlank()) {
            val tokens = cleanQuestion.split(Regex("\\s+")).filter { it.length >= 4 }
            val matchedSentence = faq.split('\n', '.')
                .map { it.trim() }
                .firstOrNull { line ->
                    line.length > 3 && tokens.any { tok -> line.lowercase().contains(tok) }
                }
            if (matchedSentence != null) {
                return "$matchedSentence\n\n(${config.aiReceptionistPolicy.personaName} · Modo Prévia)"
            }
        }
        return if (config.aiReceptionistPolicy.autoEscalateToHumanOnUnknown) {
            "Não encontrei essa informação nas orientações de $brand. Posso encaminhar sua mensagem para o responsável responder em ${config.directChatPolicy.responseSlaLabel}."
        } else {
            "${config.aiReceptionistPolicy.greetingMessage}\n\nHorário do responsável: ${config.directChatPolicy.serviceHoursLabel}."
        }
    }

    fun buildInviteDeepLink(rawCode: String?): String? {
        val clean = rawCode?.trim()?.uppercase() ?: return null
        if (!isValidInviteCode(clean)) return null
        return "https://bragasaude.online/convite/$clean"
    }

    private val unsafeReminderWords = setOf(
        "comprimido", "comprimidos", "capsula", "capsulas", "posologia",
        "dosagem", "dose", "doses", "prescreva", "prescrever", "prescricao",
        "medicamento", "medicamentos", "remedio", "remedios", "miligrama",
        "miligramas", "gota", "gotas", "antibiotico", "analgesico",
        "antiinflamatorio", "losartana", "metformina", "insulina", "omeprazol",
        "dipirona", "paracetamol", "amoxicilina", "ibuprofeno", "atenolol"
    )

    private val unsafeDoseUnitRegex = Regex("\\b\\d+(?:[.,]\\d+)?\\s*(?:mg|mcg)\\b")

    private fun foldAsciiPtBr(raw: String): String = buildString(raw.length) {
        for (ch in raw) {
            append(
                when (ch.lowercaseChar()) {
                    'á', 'à', 'ã', 'â', 'ä' -> 'a'
                    'é', 'è', 'ê', 'ë' -> 'e'
                    'í', 'ì', 'î', 'ï' -> 'i'
                    'ó', 'ò', 'õ', 'ô', 'ö' -> 'o'
                    'ú', 'ù', 'û', 'ü' -> 'u'
                    'ç' -> 'c'
                    else -> ch.lowercaseChar()
                }
            )
        }
    }

    fun findUnsafeReminderPosologyHint(rawText: String): String? {
        val normalized = foldAsciiPtBr(rawText)
        if (normalized.contains("anti inflamatorio") || normalized.contains("anti-inflamatorio")) {
            return "Orientação de medicação ou posologia não é permitida neste canal (diretriz Não-SaMD)."
        }
        val tokens = normalized.split(Regex("[^a-z0-9]+")).filter { it.isNotBlank() }
        if (tokens.any { it in unsafeReminderWords } || unsafeDoseUnitRegex.containsMatchIn(normalized)) {
            return "Orientação de medicação ou posologia não é permitida neste canal (diretriz Não-SaMD)."
        }
        return null
    }

    const val MAX_PENDING_APPOINTMENTS_PER_PROGRAM = 3

    fun canRequestAppointment(pendingCount: Int): Boolean =
        pendingCount < MAX_PENDING_APPOINTMENTS_PER_PROGRAM

    fun findAppointmentRequestError(slotText: String, patientNote: String?): String? {
        val cleanSlot = slotText.trim()
        if (cleanSlot.length !in 2..160) {
            return "Informe a janela de horário desejada (entre 2 e 160 caracteres)."
        }
        val cleanNote = patientNote?.trim().orEmpty()
        if (cleanNote.length > 400) {
            return "A observação deve ter no máximo 400 caracteres."
        }
        return null
    }

    const val MAX_DIRECT_MESSAGE_CHARS = 1000

    fun findDirectMessageValidationError(rawBody: String): String? {
        val clean = rawBody.trim()
        if (clean.isEmpty()) {
            return "Digite uma mensagem antes de enviar."
        }
        if (clean.length > MAX_DIRECT_MESSAGE_CHARS) {
            return "A mensagem deve ter no máximo 1000 caracteres."
        }
        val lower = clean.lowercase()
        if (lower.contains("data:image/") || lower.contains("data:application/") || lower.contains("data:text/html")) {
            return "Envio de imagens ou anexos não é permitido neste canal."
        }
        if (
            lower.contains("javascript:") ||
            lower.contains("vbscript:") ||
            lower.contains("intent:") ||
            lower.contains("file://")
        ) {
            return "Links ou códigos executáveis não são permitidos neste canal."
        }
        return null
    }
}
