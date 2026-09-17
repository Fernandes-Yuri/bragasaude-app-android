package br.com.bragasaude.data.remote.network

import br.com.bragasaude.data.remote.auth.AuthService
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injeta o header Authorization: Bearer <token> em todas as requisições REST.
 * Rotas públicas (app/latest, /public/) são bypassadas automaticamente.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authService: AuthService
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Rotas públicas que não precisam de token
        val path = originalRequest.url.encodedPath
        if (path.contains("/api/app/latest") || path.contains("/public/")) {
            return chain.proceed(originalRequest)
        }

        val token = runBlocking { authService.getFreshToken() }
        val requestBuilder = originalRequest.newBuilder()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        return chain.proceed(requestBuilder.build())
    }
}