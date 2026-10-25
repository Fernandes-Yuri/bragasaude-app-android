package br.com.bragasaude.data.remote.network

import br.com.bragasaude.BuildConfig
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient

/** Política única do gateway. Os pins são SPKI públicos, não credenciais. */
object GatewayTlsPolicy {
    val releasePins = listOf(
        "sha256/ecNVE7mHYinKMatGJOuHa88LThGMOavenDSjR2ZqZjk=", // Leaf atual api.bragasaude.online
        "sha256/s/tdAOmUzd8syaTuqfgGvFcn6DzA5Cmb+Vby1ST+U3Y=", // CA intermediária YE2
        "sha256/sCkq5UWXjg+7mKu9lMhhYF5bGLsy7VI/UNW3tccdR7w=", // CA intermediária Root YE
        "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI=", // CA raiz/intermediária ISRG Root X2
        "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=", // CA raiz ISRG Root X1
        "sha256/u5RhQ4d7GV6gMQo1uHKCPtHTCSOQn6UZYiQilLwCi8I=", // Backup legado Let'\''s Encrypt
        "sha256/brzvtCELCIZUo4sD/qPX0ccRtPsd3DY6RfmxpOU9oB4="  // Backup legado Let'\''s Encrypt
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
