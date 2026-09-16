package br.com.bragasaude.data.remote.model

sealed interface ProfileLookup {
    data class Found(val profile: RemoteProfile) : ProfileLookup
    data object NotFound : ProfileLookup
    data class Unavailable(val statusCode: Int? = null) : ProfileLookup
}
