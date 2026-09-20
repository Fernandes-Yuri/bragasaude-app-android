package br.com.bragasaude.data.remote.network

import br.com.bragasaude.data.remote.auth.AuthService
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AUD-AN34: recuperação de 401. O AuthInterceptor só lê o token em cache (sem
 * bloquear a thread); quando o token expira entre dois refreshes, a request sai
 * sem Authorization válida e o gateway devolve 401. Antes esse 401 era
 * engolido silenciosamente. O OkHttp chama este Authenticator UMA vez por
 * request; forçamos o refresh e refazemos a chamada. Se o refresh falhar ou o
 * usuário deslogou (token null), devolvemos null e o 401 segue para a camada
 * de UI tratar — sem loop infinito.
 */
@Singleton
class BragaAuthenticator @Inject constructor(
    private val authService: AuthService
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Evita loop: se já tentamos esta request, desiste.
        if (responseCount(response) >= 2) return null

        // Força refresh (ignora o cache expirado).
        val newToken = authService.cachedTokenNow()
            ?: kotlinx.coroutines.runBlocking { authService.getFreshToken(forceRefresh = true) }

        return if (newToken.isNullOrBlank()) {
            null // sem sessão — deixa o 401 subir para a UI
        } else {
            response.request.newBuilder()
                .header("Authorization", "Bearer $newToken")
                .build()
        }
    }

    private fun responseCount(response: Response): Int {
        var current: Response? = response
        var count = 1
        while (current?.priorResponse != null) {
            count++
            current = current.priorResponse
        }
        return count
    }
}
