package br.com.bragasaude.di

import br.com.bragasaude.BuildConfig
import br.com.bragasaude.data.remote.network.AuthInterceptor
import br.com.bragasaude.data.remote.network.BragaAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        authInterceptor: AuthInterceptor,
        tokenAuthenticator: BragaAuthenticator
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)
            .authenticator(tokenAuthenticator) // AUD-AN34: recupera 401 forçando refresh do token

        if (!BuildConfig.DEBUG) {
            // Em RELEASE, aplica Certificate Pinning rígido contra interceptação (MITM).
            // AUD-AN15: antes havia dois hashes PLACEHOLDER ("GenPlaceholder111…") — falsa
            // segurança que, no instante em que o BragaApiService entrasse em uso, faria
            // TODO o tráfego de release morrer com SSLPeerUnverifiedException.
            // Pins reais extraídos da cadeia ao vivo de api.bragasaude.online (20/09/2026):
            //   primário  = certificado leaf (bragasaude.online, Let's Encrypt)
            //   backup    = CA intermediária (Let's Encrypt YE1) — mantém tráfego se o
            //                leaf for renovado, mas não se a CA raiz mudar.
            // RENOVAÇÃO: o leaf Let's Encrypt expira em ~90 dias. Sempre que trocar,
            // atualize o pin primário; o de backup cobre a transição.
            val certificatePinner = CertificatePinner.Builder()
                .add("api.bragasaude.online", "sha256/u5RhQ4d7GV6gMQo1uHKCPtHTCSOQn6UZYiQilLwCi8I=")
                .add("api.bragasaude.online", "sha256/brzvtCELCIZUo4sD/qPX0ccRtPsd3DY6RfmxpOU9oB4=")
                .build()
            builder.certificatePinner(certificatePinner)
        } else {
            // AUD-AN35: nível BODY loga o header Authorization (Bearer token) e
            // corpos clínicos no logcat. Builds debug rodam em aparelhos de
            // testadores com contas e PHI reais — um logcat vazado expõe o
            // token de sessão. Redire as headers e mantém o corpo em HEADERS.
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.HEADERS
            }
            builder.addInterceptor(logger)
        }

        return builder.build()
    }

    // AUD-AN32: provideRetrofit / provideBragaApiService removidos — cadeia
    // Retrofit morta (BragaApiService tinha contrato incompatível com o gateway
    // real: "api/profile/sync" vs "api/sync/profile", e DTOs com campos
    // inexistentes). O app usa BragaApiClient (HttpURLConnection). Se a
    // migração Retrofit for retomada, reconstrua a interface a partir do
    // contrato real do gateway; NÃO confie nas rotas de BragaApiService.kt.

    // AUD-AN40: BragaApiClient agora recebe baseUrl no construtor (imutavel).
    // Producao pega do BuildConfig; testes injetam a URL do MockWebServer.
    @Provides
    @Singleton
    @BaseUrl
    fun provideBaseUrl(): String = BuildConfig.BASE_URL
}