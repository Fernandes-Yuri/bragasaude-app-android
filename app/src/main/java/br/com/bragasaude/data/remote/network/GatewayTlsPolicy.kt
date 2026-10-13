package br.com.bragasaude.data.remote.network

import br.com.bragasaude.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

/** Política única do gateway. Os pins são SPKI públicos, não credenciais. */
object GatewayTlsPolicy {
    val releasePins = listOf(
        "sha256/u5RhQ4d7GV6gMQo1uHKCPtHTCSOQn6UZYiQilLwCi8I=",
        "sha256/brzvtCELCIZUo4sD/qPX0ccRtPsd3DY6RfmxpOU9oB4="
    )

    fun configure(builder: OkHttpClient.Builder, release: Boolean, host: String = "api.bragasaude.online",
                  pins: List<String> = releasePins): OkHttpClient.Builder {
        builder.followRedirects(false).followSslRedirects(false)
        if (release) {
            require(pins.isNotEmpty())
            builder.certificatePinner(CertificatePinner.Builder().add(host, *pins.toTypedArray()).build())
        }
        return builder
    }

    fun defaultClient(): OkHttpClient = configure(OkHttpClient.Builder(), !BuildConfig.DEBUG).build()
}
