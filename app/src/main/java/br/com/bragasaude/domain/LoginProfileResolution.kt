package br.com.bragasaude.domain

import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.remote.model.ProfileLookup

sealed interface LoginProfileResolution {
    data object Loading : LoginProfileResolution
    data class Ready(val profile: ProfileEntity?) : LoginProfileResolution
    data class Unavailable(val message: String) : LoginProfileResolution
}

/** Consent and role never substitute for a completed onboarding step. */
fun resolveLoginProfile(local: ProfileEntity?, remote: ProfileLookup?): LoginProfileResolution {
    if (local?.consentAcceptedAt != null && ProfileOnboarding.isComplete(
            local.userRole, local.caregiverMode, local.basicProfileComplete, local.selfCareComplete)) {
        return LoginProfileResolution.Ready(local)
    }
    return when (remote) {
        null -> LoginProfileResolution.Loading
        ProfileLookup.NotFound -> LoginProfileResolution.Ready(local)
        is ProfileLookup.Found -> if (local != null) LoginProfileResolution.Ready(local)
            else LoginProfileResolution.Unavailable("Não foi possível carregar o perfil salvo. Tente novamente.")
        is ProfileLookup.Unavailable -> LoginProfileResolution.Unavailable(
            if (remote.statusCode in listOf(401, 403)) "Não foi possível validar sua sessão. Tente novamente ou entre na conta novamente."
            else "Não foi possível verificar seu cadastro agora. Confira a conexão e tente novamente. Seus dados locais foram preservados.")
    }
}
