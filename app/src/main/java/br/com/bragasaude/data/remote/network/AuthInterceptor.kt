package br.com.bragasaude.data.remote.network

import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.data.remote.b2b.ActiveContextStore
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Injeta o header Authorization: Bearer <token> em todas as requisições REST.
 * Rotas públicas (app/latest, /public/) são bypassadas automaticamente.
 * Quando um contexto B2B autorizado está ativo, propaga apenas a preferência
 * `X-Braga-Program-Id` / `X-Braga-Context-Mode` para validação no servidor.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val authService: AuthService,
    private val activeContextStore: ActiveContextStore
) : Interceptor {

    constructor(authService: AuthService) : this(authService, ActiveContextStore())

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Rotas públicas que não precisam de token
        val path = originalRequest.url.encodedPath
        if (path.contains("/api/app/latest") || path.contains("/public/")) {
            return chain.proceed(originalRequest)
        }

        // AUD-AN34: runBlocking na thread do dispatcher do OkHttp bloqueia a
        // thread por segundos na primeira chamada (refresh Firebase). Lemos o
        // cache sem bloquear; só caímos no runBlocking quando não há token
        // válido — caso raro (login) e sempre após um MISS, sem contenção.
        var token = authService.cachedTokenNow()
        if (token == null) {
            token = runBlocking { authService.getFreshToken() }
        }
        val requestBuilder = originalRequest.newBuilder()
            .removeHeader(ActiveContextStore.HEADER_PROGRAM_ID)
            .removeHeader(ActiveContextStore.HEADER_CONTEXT_MODE)
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
            val programId = activeContextStore.currentHeaderProgramId()
            val contextMode = activeContextStore.currentHeaderMode()
            if (!programId.isNullOrBlank() && !contextMode.isNullOrBlank()) {
                requestBuilder.header(ActiveContextStore.HEADER_PROGRAM_ID, programId)
                requestBuilder.header(ActiveContextStore.HEADER_CONTEXT_MODE, contextMode)
            }
        }
        return chain.proceed(requestBuilder.build())
    }
}