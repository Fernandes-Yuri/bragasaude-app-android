package br.com.bragasaude.data.util

import br.com.bragasaude.BuildConfig
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.SocialPostEntity
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Apenas fotos já publicadas pelo endpoint de avatar podem representar a autoria no mural. */
fun sharedSocialPhotoUrl(value: String?): String? {
    val gateway = BuildConfig.BASE_URL.toHttpUrlOrNull() ?: return null
    val url = if (value?.startsWith("/api/files/avatars/") == true) gateway.resolve(value)
        else value?.toHttpUrlOrNull()
    url ?: return null
    return url.toString().takeIf { url.scheme == "https" && url.host == gateway.host &&
        url.port == gateway.port && url.encodedPath.startsWith("/api/files/avatars/") &&
        url.username.isEmpty() && url.password.isEmpty() }
}

/** O mural não simula uma foto Google/local que os demais usuários não conseguem receber. */
fun SocialPostEntity.withProfileAvatar(profile: ProfileEntity?): SocialPostEntity {
    if (profile == null || profile.userId != userId) return this
    return copy(userAvatarIdentifier = profile.avatarIdentifier,
        userAvatarUrl = sharedSocialPhotoUrl(profile.customPhotoUri))
}

/** Importação restrita à origem de fotos Google, sem redirecionamentos ou credenciais. */
fun googlePhotoDownloadUrl(value: String?): String? {
    val url = value?.toHttpUrlOrNull() ?: return null
    return value.takeIf { url.scheme == "https" && url.port == 443 &&
        (url.host == "googleusercontent.com" || url.host.endsWith(".googleusercontent.com")) &&
        url.username.isEmpty() && url.password.isEmpty() }
}
