package br.com.bragasaude.di

import br.com.bragasaude.BuildConfig
import br.com.bragasaude.data.remote.api.BragaApiService
import br.com.bragasaude.data.remote.network.AuthInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.CertificatePinner
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
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
    fun provideOkHttpClient(authInterceptor: AuthInterceptor): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(authInterceptor)

        if (!BuildConfig.DEBUG) {
            // Em RELEASE, aplica Certificate Pinning rígido contra interceptação (MITM)
            val certificatePinner = CertificatePinner.Builder()
                // Pin primário do domínio e pin de backup
                .add("api.bragasaude.online", "sha256/k2oTQLGenPlaceholder1111111111111111111111111=")
                .add("api.bragasaude.online", "sha256/k2oTQLGenBackupCA222222222222222222222222222=")
                .build()
            builder.certificatePinner(certificatePinner)
        } else {
            val logger = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }
            builder.addInterceptor(logger)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL.ensureTrailingSlash())
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideBragaApiService(retrofit: Retrofit): BragaApiService {
        return retrofit.create(BragaApiService::class.java)
    }

    private fun String.ensureTrailingSlash(): String {
        return if (endsWith("/")) this else "$this/"
    }
}