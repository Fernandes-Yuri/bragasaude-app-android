package br.com.bragasaude.domain

/** A Firebase identity exists before either onboarding path is complete. */
object ProfileOnboarding {
    fun modeAfterAddingCaregiving(selfCareComplete: Boolean, selectedMode: String): String =
        if (selfCareComplete) "HYBRID" else selectedMode

    fun isComplete(role: String?, mode: String?, basicComplete: Boolean, selfCareComplete: Boolean): Boolean =
        basicComplete && when (role) {
            "PATIENT" -> selfCareComplete
            "CAREGIVER" -> mode == "VIEWER_ONLY" || (mode == "HYBRID" && selfCareComplete)
            else -> false
        }

    fun needsSelfCare(role: String?, mode: String?, basicComplete: Boolean, selfCareComplete: Boolean): Boolean =
        basicComplete && !selfCareComplete && (role == "PATIENT" || (role == "CAREGIVER" && mode == "HYBRID"))
}
