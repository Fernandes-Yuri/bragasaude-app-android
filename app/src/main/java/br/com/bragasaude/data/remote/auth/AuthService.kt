package br.com.bragasaude.data.remote.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Serviço centralizado de autenticação Firebase.
 * Cache em memória com margem de 10 min antes da expiração do token (60 min).
 * Substitui chamadas diretas a FirebaseAuth espalhadas no BragaApiClient,
 * NeuralAudioPlayer e OrbChatGateway.
 */
@Singleton
class AuthService @Inject constructor(
    private val firebaseAuth: FirebaseAuth
) {
    private val mutex = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0L

    val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    suspend fun getFreshToken(forceRefresh: Boolean = false): String? = mutex.withLock {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedToken != null && now < (tokenExpiresAt - 60_000)) {
            return cachedToken
        }
        val user = firebaseAuth.currentUser ?: return null
        return try {
            val result = user.getIdToken(forceRefresh).await()
            val token = result.token
            // Firebase token dura 60min; renovamos com margem segura de 50min
            tokenExpiresAt = now + (50 * 60 * 1000)
            cachedToken = token
            token
        } catch (e: Exception) {
            null
        }
    }

    fun clearTokenCache() {
        cachedToken = null
        tokenExpiresAt = 0L
    }

    /**
     * AUD-AN34: leitura NÃO bloqueante do token em cache. Retorna null quando
     * não há token válido (ainda não logado, ou expirou). Feita para o
     * AuthInterceptor não precisar de runBlocking na thread do OkHttp — o
     * refresh Firebase pode levar segundos e entupir o dispatcher.
     */
    fun cachedTokenNow(): String? {
        val now = System.currentTimeMillis()
        return if (cachedToken != null && now < (tokenExpiresAt - 60_000)) {
            cachedToken
        } else {
            null
        }
    }
}