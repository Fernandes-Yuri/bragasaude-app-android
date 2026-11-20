package br.com.bragasaude.domain

import br.com.bragasaude.domain.b2b.AppAccountMode

/** A Firebase identity exists before either onboarding path is complete. */
object ProfileOnboarding {
    fun modeAfterAddingCaregiving(selfCareComplete: Boolean, selectedMode: String): String =
        if (selfCareComplete) "HYBRID" else selectedMode

    fun isComplete(
        role: String?,
        mode: String?,
        basicComplete: Boolean,
        selfCareComplete: Boolean,
        accountMode: String? = null
    ): Boolean {
        if (AppAccountMode.fromWire(accountMode) == AppAccountMode.BUSINESS_ONLY) {
            return true
        }
        return basicComplete && when (role) {
            "PATIENT" -> selfCareComplete
            "CAREGIVER" -> mode == "VIEWER_ONLY" || (mode == "HYBRID" && selfCareComplete)
            else -> false
        }
    }

    fun needsSelfCare(
        role: String?,
        mode: String?,
        basicComplete: Boolean,
        selfCareComplete: Boolean,
        accountMode: String? = null
    ): Boolean {
        if (AppAccountMode.fromWire(accountMode) == AppAccountMode.BUSINESS_ONLY) {
            return false
        }
        return basicComplete && !selfCareComplete && (role == "PATIENT" || (role == "CAREGIVER" && mode == "HYBRID"))
    }
}
