package br.com.bragasaude.data.avatar

import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import javax.inject.Inject

/** A pré-checagem local é obrigatória nos dois fluxos; o servidor decide a publicação. */
class AvatarPhotoUploader @Inject constructor(
    private val processor: LocalAvatarPhotoProcessor,
    private val api: BragaApiClient,
    private val auth: FirebaseAuth
) {
    suspend fun upload(userId: String, source: ByteArray): BragaApiClient.AvatarUploadResult {
        check(userId != BragaConstants.GUEST_UID && auth.currentUser?.uid == userId) { "Entre na sua conta para alterar a foto." }
        val jpeg = processor.prepare(source)
        currentCoroutineContext().ensureActive()
        check(auth.currentUser?.uid == userId) { "Sua conta mudou. Escolha a foto novamente." }
        val result = api.uploadProfileAvatar(userId, "avatar.jpg", "image/jpeg", jpeg)
        check(auth.currentUser?.uid == userId) { "Sua conta mudou. Abra o perfil novamente." }
        return result
    }
}
