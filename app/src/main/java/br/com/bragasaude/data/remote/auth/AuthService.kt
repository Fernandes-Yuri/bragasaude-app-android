package br.com.bragasaude.data.remote.auth

import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
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
    private val cacheLock = Any()
    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedUserId: String? = null
    @Volatile private var tokenExpiresAt: Long = 0L

    val currentUserId: String?
        get() = firebaseAuth.currentUser?.uid

    suspend fun getFreshToken(forceRefresh: Boolean = false): String? {
        val user = firebaseAuth.currentUser ?: return null
        cachedTokenFor(user.uid, forceRefresh)?.let { return it }
        return try {
            val result = user.getIdToken(forceRefresh).await()
            val token = result.token
            cache(user.uid, token)
            token
        } catch (_: Exception) {
            null
        }
    }

    /** Versão bloqueante para os clientes HttpURLConnection legados, sempre fora da main thread. */
    fun getTokenBlocking(forceRefresh: Boolean = false, timeoutSeconds: Long = 15): String? {
        val user = firebaseAuth.currentUser ?: return null
        cachedTokenFor(user.uid, forceRefresh)?.let { return it }
        return try {
            val token = Tasks.await(user.getIdToken(forceRefresh), timeoutSeconds, TimeUnit.SECONDS).token
            cache(user.uid, token)
            token
        } catch (_: Exception) {
            null
        }
    }

    private fun cachedTokenFor(userId: String, forceRefresh: Boolean): String? = synchronized(cacheLock) {
        val valid = cachedUserId == userId && System.currentTimeMillis() < tokenExpiresAt
        if (!forceRefresh && valid) cachedToken else null
    }

    private fun cache(userId: String, token: String?) = synchronized(cacheLock) {
        cachedUserId = userId
        cachedToken = token
        tokenExpiresAt = if (token == null) 0L else System.currentTimeMillis() + (50 * 60 * 1000)
    }

    fun clearTokenCache() {
        synchronized(cacheLock) {
            cachedToken = null
            cachedUserId = null
            tokenExpiresAt = 0L
        }
    }

    /**
     * AUD-AN34: leitura NÃO bloqueante do token em cache. Retorna null quando
     * não há token válido (ainda não logado, ou expirou). Feita para o
     * AuthInterceptor não precisar de runBlocking na thread do OkHttp — o
     * refresh Firebase pode levar segundos e entupir o dispatcher.
     */
    fun cachedTokenNow(): String? {
        val userId = firebaseAuth.currentUser?.uid ?: return null
        return cachedTokenFor(userId, forceRefresh = false)
    }
}
