package br.com.bragasaude.domain

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import br.com.bragasaude.data.remote.ai.NeuralAudioPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cena C38 — Celebração positiva por voz.
 *
 * Ao confirmar a ingestão do medicamento, o Braga celebra em pt-BR:
 *   "Muito bem, [Nome]! Remédio registrado com sucesso. Continuar firme no
 *    seu tratamento é a sua melhor escolha!"
 *
 * Usa a voz neural quando disponível e cai no TextToSpeech nativo do Android
 * (também pt-BR) como fallback — a celebração nunca pode ficar muda.
 */
@Singleton
class CareCelebrationTts @Inject constructor(
    @ApplicationContext private val context: Context,
    private val neuralAudioPlayer: NeuralAudioPlayer
) {
    companion object {
        private const val TAG = "CareCelebrationTts"
    }

    private var tts: TextToSpeech? = null
    private val ttsReady = AtomicBoolean(false)
    private var ttsInitialization: CompletableDeferred<Boolean>? = null

    /**
     * Fala a mensagem de celebração. Recebe o nome do paciente para
     * personalizar; usa o nome de batismo se houver.
     */
    suspend fun celebrate(patientName: String?, onStart: () -> Unit = {}, onDone: () -> Unit = {}) {
        val name = patientName?.trim()?.takeIf { it.isNotEmpty() }?.split(" ")?.firstOrNull()
        val text = if (name.isNullOrEmpty()) {
            "Muito bem! Remédio registrado com sucesso. Continuar firme no seu tratamento é a sua melhor escolha!"
        } else {
            "Muito bem, $name! Remédio registrado com sucesso. Continuar firme no seu tratamento é a sua melhor escolha!"
        }
        speak(text, onStart, onDone)
    }

    /** Fala um texto livre em pt-BR (voz neural com fallback nativo). */
    suspend fun speak(text: String, onStart: () -> Unit = {}, onDone: () -> Unit = {}) {
        if (text.isBlank()) return
        val played = neuralAudioPlayer.playSpeech(text, isMale = true, onStart = onStart, onDone = onDone)
        if (!played) fallbackToSystemTts(text, onStart, onDone)
    }

    private suspend fun fallbackToSystemTts(text: String, onStart: () -> Unit, onDone: () -> Unit) =
        withContext(Dispatchers.Main) {
            try {
                if (!ensureSystemTts()) {
                    Log.w(TAG, "TextToSpeech nativo indisponível em pt-BR.")
                    onDone()
                    return@withContext
                }
                val id = UUID.randomUUID().toString()
                tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { onStart() }
                    override fun onDone(utteranceId: String?) { onDone() }
                    @Deprecated("Legacy")
                    override fun onError(utteranceId: String?) { onDone() }
                })
                val params = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) }
                if (tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) == TextToSpeech.ERROR) {
                    Log.w(TAG, "TextToSpeech nativo falhou; celebração silenciosa.")
                    onDone()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallback TTS falhou: ${e.message}")
                onDone()
            }
        }

    /** Aguarda a inicialização assíncrona antes de chamar speak(). */
    private suspend fun ensureSystemTts(): Boolean {
        if (ttsReady.get()) return true
        val initialization = ttsInitialization ?: CompletableDeferred<Boolean>().also { pending ->
            ttsInitialization = pending
            tts = TextToSpeech(context.applicationContext) { status ->
                val ready = if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale("pt", "BR"))
                    result != TextToSpeech.LANG_MISSING_DATA &&
                        result != TextToSpeech.LANG_NOT_SUPPORTED
                } else {
                    false
                }
                ttsReady.set(ready)
                pending.complete(ready)
            }
        }
        return withTimeoutOrNull(5_000L) { initialization.await() } ?: false
    }

    private fun resetSystemTts() {
        ttsInitialization = null
        ttsReady.set(false)
    }

    /** Libera o sintetizador (chamar em onCleared do ViewModel). */
    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) { /* best-effort */ }
        tts = null
        resetSystemTts()
    }
}
