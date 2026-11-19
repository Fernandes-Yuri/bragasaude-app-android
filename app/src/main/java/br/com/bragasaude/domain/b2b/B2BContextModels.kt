package br.com.bragasaude.domain.b2b

import kotlinx.serialization.Serializable

/**
 * Modelos de contexto B2C / B2B em Kotlin puro (prontos para KMP / iOS).
 * Zero dependencia de classes Android ou Room.
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
