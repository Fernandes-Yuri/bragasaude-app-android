package br.com.bragasaude.data.remote.network

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Autentica apenas fotos protegidas na origem do gateway; imagens externas não recebem credenciais. */
class AvatarAuthInterceptor(
    private val gateway: HttpUrl,
    private val tokenProvider: (Boolean) -> String?
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url
        val isAvatar = url.scheme == gateway.scheme && url.host == gateway.host && url.port == gateway.port
            && url.encodedPath.startsWith("/api/files/avatars/")
        if (!isAvatar) return chain.proceed(original)
        val token = tokenProvider(false)
        val request = if (token.isNullOrBlank()) original else original.newBuilder()
            .header("Authorization", "Bearer $token").build()
        val response = chain.proceed(request)
        if (response.code != 401) return response
        val freshToken = tokenProvider(true)?.takeIf { it.isNotBlank() } ?: return response
        response.close()
        return chain.proceed(original.newBuilder().header("Authorization", "Bearer $freshToken").build())
    }
}
