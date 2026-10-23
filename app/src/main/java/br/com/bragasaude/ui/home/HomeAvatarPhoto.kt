package br.com.bragasaude.ui.home

internal fun resolveHomeAvatarPhoto(customPhotoUri: String?, googlePhotoUrl: String?, useGooglePhoto: Boolean): String? =
    customPhotoUri?.takeIf { it.isNotBlank() } ?: googlePhotoUrl?.takeIf { useGooglePhoto && it.isNotBlank() }
