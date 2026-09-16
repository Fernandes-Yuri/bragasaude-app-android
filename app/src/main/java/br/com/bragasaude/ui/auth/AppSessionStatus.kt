package br.com.bragasaude.ui.auth

sealed class AppSessionStatus {
    object Initializing : AppSessionStatus()
    data class Authenticated(val userId: String) : AppSessionStatus()
    object NotAuthenticated : AppSessionStatus()
}
