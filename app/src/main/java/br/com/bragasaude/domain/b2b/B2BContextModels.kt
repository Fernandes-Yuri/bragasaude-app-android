package br.com.bragasaude.domain.b2b

import kotlinx.serialization.Serializable

/**
 * Modelos de contexto B2C / B2B e onboarding empresarial em Kotlin puro
 * (prontos para KMP / iOS). Zero dependencia de classes Android ou Room.
 */
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
    REJECTED("Cadastro recusado");

    companion object {
        fun fromWire(raw: String?): PartnerApprovalStatus {
            return when (raw?.trim()?.uppercase()) {
                "PENDING_REVIEW" -> PENDING_REVIEW
                "CHANGES_REQUESTED" -> CHANGES_REQUESTED
                "APPROVED" -> APPROVED
                "REJECTED" -> REJECTED
                else -> DRAFT
            }
        }
    }
}

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
    val sharedCategories: List<String> = emptyList()
) {
    val parsedMode: AppContextMode
        get() = AppContextMode.fromWire(mode)

    val isPartnerContext: Boolean
        get() = parsedMode != AppContextMode.PERSONAL && !programId.isNullOrBlank()

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
    val defaultMode: String = AppContextMode.PERSONAL.name,
    val contexts: List<B2BContextDescriptor> = emptyList()
)

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
    val seatLimit: Int = 10,
    val activeParticipants: Int = 0,
    val canPromote: Boolean = false
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
    val logoUrl: String? = null
)

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
        descriptionPtBr = "Confirmações de rotina e horários registrados no app."
    ),
    AUTHORIZED_RECORDS(
        wireKey = "authorized_records",
        titlePtBr = "Registros autorizados de rotina",
        descriptionPtBr = "Leituras de bem-estar que você escolher disponibilizar ao programa."
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
    val sharedCategories: List<String>
)

@Serializable
data class UpdatePartnerConsentRequest(
    val sharedCategories: List<String>
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
    val consentedAt: String? = null
)

@Serializable
data class PartnerParticipantsPageDto(
    val program: PartnerProgramDto? = null,
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
data class SharedAuthorizedRecordsSectionDto(
    val source: String,
    val zeroFileEgressNotice: String,
    val records: List<SharedVitalEntryDto> = emptyList()
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
    val stepsSection: SharedStepsSectionDto? = null,
    val hydrationSection: SharedHydrationSectionDto? = null,
    val medicationsSection: SharedMedicationsSectionDto? = null,
    val authorizedRecordsSection: SharedAuthorizedRecordsSectionDto? = null
)

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

    private val HEX_COLOR_REGEX = Regex("^#[0-9A-Fa-f]{6}$")
    private val INVITE_CODE_REGEX = Regex("^[A-Za-z0-9_-]{3,32}$")

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
        return digits.length == 14 && digits.toSet().size > 1
    }

    fun formatCnpjOrNull(raw: String?): String? {
        val digits = extractCnpjDigits(raw)
        if (digits.length != 14 || digits.toSet().size <= 1) return null
        return "${digits.substring(0, 2)}.${digits.substring(2, 5)}.${digits.substring(5, 8)}/" +
            "${digits.substring(8, 12)}-${digits.substring(12, 14)}"
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

    fun buildInviteDeepLink(rawCode: String?): String? {
        val clean = rawCode?.trim()?.uppercase() ?: return null
        if (!isValidInviteCode(clean)) return null
        return "https://bragasaude.online/convite/$clean"
    }
}
