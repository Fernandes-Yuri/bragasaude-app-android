package br.com.bragasaude.data.local.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Fallback definitivo de síntese vocal utilizando o TextToSpeech nativo do Android.
 *
 * Garante que se o modelo neural on-device estiver ausente ou indisponível,
 * o dispositivo sintetize a fala localmente com o motor do sistema (Google Speech Engine),
 * eliminando 100% a dependência de servidores remotos para áudio.
 */
@Singleton
class AndroidSystemTtsFallback @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AndroidSystemTtsFallback"
    }

    private var tts: TextToSpeech? = null
    private val isReady = AtomicBoolean(false)
    private var pending: kotlinx.coroutines.CancellableContinuation<Boolean>? = null

    init {
        try {
            tts = TextToSpeech(context, this)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao instanciar TTS do sistema operacional: ${e.message}")
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("pt", "BR"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isReady.set(true)
            } else {
                Log.w(TAG, "Idioma pt-BR nao suportado no TTS do sistema")
            }
        }
    }

    suspend fun speak(
        text: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {}
    ): Boolean = withContext(Dispatchers.Main) {
        val engine = tts ?: return@withContext false
        if (!isReady.get()) return@withContext false

        stop()
        suspendCancellableCoroutine { continuation ->
            pending = continuation
            val utteranceId = "fallback_${System.currentTimeMillis()}"
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    if (id == utteranceId && continuation.isActive) onStart()
                }

                override fun onDone(id: String?) {
                    if (id == utteranceId && continuation.isActive) {
                        onDone()
                        if (continuation.isActive) continuation.resume(true)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && continuation.isActive) {
                        onDone()
                        if (continuation.isActive) continuation.resume(false)
                    }
                }
            })

            continuation.invokeOnCancellation {
                try {
                    engine.stop()
                } catch (_: Exception) {}
            }

            val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
            if (result != TextToSpeech.SUCCESS) {
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }

    fun stop() {
        val previous = pending
        pending = null
        if (previous?.isActive == true) previous.cancel()
        try {
            tts?.stop()
        } catch (_: Exception) {}
    }

    val isAvailable: Boolean
        get() = isReady.get()
}
