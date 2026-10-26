package br.com.bragasaude.data.local.organizer

import android.content.Context
import android.content.Intent
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Disponibilidade da leitura assistida (OCR), que depende do Google Play Services no aparelho. */
sealed interface OcrAvailability {
    object Ready : OcrAvailability
    data class NeedsUpdate(val resolution: Intent?) : OcrAvailability
    object Unavailable : OcrAvailability
}

@Singleton
class OcrCapability @Inject constructor(@ApplicationContext private val context: Context) {
    /**
     * Sonda leve da infraestrutura de OCR. Nunca lança: sem Google Play Services,
     * ou em qualquer falha da verificação, degrada para indisponível e a área segue
     * utilizável com título, data e tipo informados manualmente.
     */
    fun probe(): OcrAvailability = runCatching {
        val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context)
        when {
            code == ConnectionResult.SUCCESS -> OcrAvailability.Ready
            GoogleApiAvailability.getInstance().isUserResolvableError(code) ->
                OcrAvailability.NeedsUpdate(GoogleApiAvailability.getInstance().getErrorResolutionIntent(context, code, null))
            else -> OcrAvailability.Unavailable
        }
    }.getOrDefault(OcrAvailability.Unavailable)
}
