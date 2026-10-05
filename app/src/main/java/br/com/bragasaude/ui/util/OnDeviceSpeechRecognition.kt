package br.com.bragasaude.ui.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class OnDeviceRecognitionUnavailable(message: String) : IllegalStateException(message)

/** Reconhecimento explicitamente local. Nenhum serviço remoto ou download automático. */
object OnDeviceSpeechRecognition {
    const val LANGUAGE = "pt-BR"

    fun create(context: Context): SpeechRecognizer {
        if (Build.VERSION.SDK_INT < 31) {
            throw OnDeviceRecognitionUnavailable(unavailableReason(Build.VERSION.SDK_INT, false)!!)
        }
        unavailableReason(Build.VERSION.SDK_INT, SpeechRecognizer.isOnDeviceRecognitionAvailable(context))
            ?.let { throw OnDeviceRecognitionUnavailable(it) }
        return SpeechRecognizer.createOnDeviceSpeechRecognizer(context.applicationContext)
    }

    internal fun unavailableReason(apiLevel: Int, available: Boolean): String? = when {
        apiLevel < 31 -> "O reconhecimento de voz no dispositivo exige Android 12 ou superior. Você pode continuar pelo chat de texto."
        !available -> "O reconhecimento de voz no dispositivo não está disponível neste aparelho. Você pode continuar pelo chat de texto."
        else -> null
    }

    fun intent(partialResults: Boolean = true): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
    }

    /** API 31/32 informam ausência de idioma pelo listener; API 33+ permite consulta prévia. */
    suspend fun checkPortugueseSupport(context: Context, recognizer: SpeechRecognizer, intent: Intent) {
        if (Build.VERSION.SDK_INT < 33) return
        val reason = withTimeoutOrNull(2_000) {
            suspendCancellableCoroutine<String?> { continuation ->
                recognizer.checkRecognitionSupport(intent, context.mainExecutor, object : RecognitionSupportCallback {
                    override fun onSupportResult(recognitionSupport: RecognitionSupport) {
                        if (continuation.isActive) continuation.resume(
                            languageUnavailableReason(recognitionSupport.installedOnDeviceLanguages)
                        )
                    }
                    override fun onError(error: Int) {
                        if (continuation.isActive) continuation.resume(
                            // Alguns serviços locais não implementam a consulta. O listener trata o idioma.
                            if (error == SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT) null else errorMessage(error)
                        )
                    }
                })
            }
        }
        // Timeout da consulta não muda o tipo do reconhecedor: ainda é apenas on-device.
        reason?.let { throw OnDeviceRecognitionUnavailable(it) }
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
