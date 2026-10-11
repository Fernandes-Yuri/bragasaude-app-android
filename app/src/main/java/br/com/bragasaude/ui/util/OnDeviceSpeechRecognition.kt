package br.com.bragasaude.ui.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class OnDeviceRecognitionUnavailable(message: String) : IllegalStateException(message)

/** Reconhecimento de fala resiliente: prioriza on-device se disponível, com fallback automático para o sistema. */
object OnDeviceSpeechRecognition {
    private const val TAG = "SpeechRecognition"
    const val LANGUAGE = "pt-BR"

    fun create(context: Context): SpeechRecognizer {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= 31) {
            val hasOnDevice = try {
                SpeechRecognizer.isOnDeviceRecognitionAvailable(appContext)
            } catch (_: Throwable) {
                false
            }
            if (hasOnDevice) {
                try {
                    return SpeechRecognizer.createOnDeviceSpeechRecognizer(appContext)
                } catch (t: Throwable) {
                    Log.w(TAG, "Falha ao criar OnDeviceSpeechRecognizer, usando SpeechRecognizer padrão: ${t.message}")
                }
            }
        }
        return SpeechRecognizer.createSpeechRecognizer(appContext)
    }

    internal fun unavailableReason(apiLevel: Int, available: Boolean): String? = when {
        apiLevel < 31 -> "O reconhecimento de voz no dispositivo exige Android 12 ou superior. Você pode continuar pelo chat de texto."
        !available -> "O reconhecimento de voz no dispositivo não está disponível neste aparelho. Você pode continuar pelo chat de texto."
        else -> null
    }

    fun intent(partialResults: Boolean = true): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LANGUAGE)
        putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES", arrayOf(LANGUAGE))
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    /** Consulta suporte local se disponível; falhas não bloqueiam o reconhecimento resiliente. */
    suspend fun checkPortugueseSupport(context: Context, recognizer: SpeechRecognizer, intent: Intent) {
        if (Build.VERSION.SDK_INT < 33) return
        try {
            withTimeoutOrNull(1_500) {
                suspendCancellableCoroutine<Unit> { continuation ->
                    recognizer.checkRecognitionSupport(intent, context.mainExecutor, object : RecognitionSupportCallback {
                        override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                        override fun onError(error: Int) {
                            if (continuation.isActive) continuation.resume(Unit)
                        }
                    })
                }
            }
        } catch (_: Throwable) {
            // Consulta defensiva: se falhar ou der timeout, a execução prossegue com o reconhecedor ativo.
        }
    }

    internal fun languageUnavailableReason(installedLanguages: List<String>): String? {
        val installed = installedLanguages.any {
            val tag = it.replace('_', '-').lowercase(Locale.ROOT)
            tag == "pt" || tag == "pt-br"
        }
        return if (installed) null else
            "O português brasileiro ainda não está instalado para reconhecimento no dispositivo. Confira o pacote de idioma nas configurações de voz do aparelho ou continue pelo chat de texto."
    }

    fun errorMessage(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> languageUnavailableReason(emptyList())!!
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Permita o microfone para usar a voz no dispositivo, ou continue pelo chat de texto."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        SpeechRecognizer.ERROR_NO_MATCH -> "Não consegui entender a fala desta vez. Você pode tentar novamente ou digitar no chat."
        SpeechRecognizer.ERROR_AUDIO -> "Não consegui acessar o áudio do microfone. Você pode continuar pelo chat de texto."
        else -> "O reconhecimento local de voz não conseguiu concluir agora. Você pode tentar novamente ou continuar pelo chat de texto."
    }
}
