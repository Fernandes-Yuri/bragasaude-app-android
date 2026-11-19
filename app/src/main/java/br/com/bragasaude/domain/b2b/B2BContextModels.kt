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
    val seatLimit: Int = 10,
    val activeParticipants: Int = 0,
    val canPromote: Boolean = false,
    val templateKey: String = "CLINICA_GERAL",
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

    fun buildInviteDeepLink(rawCode: String?): String? {
        val clean = rawCode?.trim()?.uppercase() ?: return null
        if (!isValidInviteCode(clean)) return null
        return "https://bragasaude.online/convite/$clean"
    }
}
