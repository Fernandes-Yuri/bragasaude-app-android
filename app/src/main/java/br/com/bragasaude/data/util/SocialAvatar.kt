package br.com.bragasaude.data.util

import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.SocialPostEntity

/** O perfil local é a fonte atual para os posts do próprio usuário, inclusive ao remover a foto. */
fun SocialPostEntity.withProfileAvatar(profile: ProfileEntity?, googlePhotoUrl: String?): SocialPostEntity {
    if (profile == null) return copy(userAvatarUrl = userAvatarUrl ?: googlePhotoUrl)
    if (profile.userId != userId) return this
    return copy(
        userAvatarIdentifier = profile.avatarIdentifier,
        userAvatarUrl = profile.customPhotoUri?.takeIf { it.isNotBlank() }
            ?: googlePhotoUrl?.takeIf { it.isNotBlank() }
    )
}
