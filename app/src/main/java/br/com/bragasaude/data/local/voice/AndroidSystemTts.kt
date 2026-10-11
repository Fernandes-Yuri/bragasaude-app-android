package br.com.bragasaude.data.local.voice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Motor definitivo de síntese vocal utilizando o TextToSpeech nativo do Android.
 *
 * Suporta reconexão automática resiliente caso o processo seja suspenso pelo sistema
 * ou a conexão com o mecanismo seja interrompida, resolução proativa de motores públicos
 * (evitando motores privados de fabricantes como com.samsung.SMT) e sincronização assíncrona.
 */
@Singleton
class AndroidSystemTts @Inject constructor(
    @ApplicationContext private val context: Context
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AndroidSystemTts"
        private const val INIT_TIMEOUT_MS = 4_000L
        private const val GOOGLE_TTS_PKG = "com.google.android.tts"
    }

    private val mutex = Mutex()
    private var tts: TextToSpeech? = null
    private val isReady = AtomicBoolean(false)
    private var initDeferred: CompletableDeferred<Boolean>? = null
    private var pending: kotlinx.coroutines.CancellableContinuation<Boolean>? = null

    init {
        initTtsInstance()
    }

    private fun resolvePreferredEngine(ctx: Context): String? {
        return try {
            val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
            val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ctx.packageManager?.queryIntentServices(
                    intent,
                    PackageManager.ResolveInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                ctx.packageManager?.queryIntentServices(intent, 0)
            } ?: emptyList()
            val packages = services.mapNotNull { it.serviceInfo?.packageName }

            if (packages.contains(GOOGLE_TTS_PKG)) {
                GOOGLE_TTS_PKG
            } else {
                packages.firstOrNull { it != "com.samsung.SMT" }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao resolver motor TTS público: ${e.message}")
            null
        }
    }

    private fun initTtsInstance() {
        isReady.set(false)
        val deferred = CompletableDeferred<Boolean>()
        initDeferred = deferred
        try {
            val preferredEngine = resolvePreferredEngine(context)
            tts = if (preferredEngine != null) {
                TextToSpeech(context, this, preferredEngine)
            } else {
                TextToSpeech(context, this)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao instanciar TTS do sistema: ${e.message}")
            deferred.complete(false)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("pt", "BR"))
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setSpeechRate(0.95f)
                isReady.set(true)
                initDeferred?.complete(true)
            } else {
                Log.w(TAG, "Idioma pt-BR nao suportado no TTS do sistema")
                isReady.set(false)
                initDeferred?.complete(false)
            }
        } else {
            Log.w(TAG, "Falha na inicializacao do TTS (status=$status)")
            isReady.set(false)
            initDeferred?.complete(false)
        }
    }

    /** Garante que o motor esteja inicializado e pronto para falar, reconectando se necessário. */
    suspend fun ensureReady(): Boolean = mutex.withLock {
        if (isReady.get() && tts != null) return true
        val deferred = initDeferred
        if (deferred != null && deferred.isActive) {
            return withTimeoutOrNull(INIT_TIMEOUT_MS) { deferred.await() } ?: false
        }
        tearDownTts()
        initTtsInstance()
        return withTimeoutOrNull(INIT_TIMEOUT_MS) { initDeferred?.await() } ?: false
    }

    private fun tearDownTts() {
        val oldTts = tts
        tts = null
        isReady.set(false)
        try {
            oldTts?.stop()
            oldTts?.shutdown()
        } catch (_: Exception) {}
    }

    suspend fun speak(
        text: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {}
    ): Boolean = withContext(Dispatchers.Main) {
        if (!ensureReady()) {
            Log.w(TAG, "TTS nao esta pronto para reproducao")
            return@withContext false
        }

        val success = executeSpeak(text, onStart, onDone)
        if (!success) {
            Log.i(TAG, "Primeira tentativa de speak falhou, tentando reconectar ao servico TTS...")
            tearDownTts()
            if (ensureReady()) {
                return@withContext executeSpeak(text, onStart, onDone)
            }
        }
        return@withContext success
    }

    private suspend fun executeSpeak(
        text: String,
        onStart: () -> Unit,
        onDone: () -> Unit
    ): Boolean {
        val engine = tts ?: return false
        stop()

        return suspendCancellableCoroutine { continuation ->
            pending = continuation
            val utteranceId = "braga_tts_${System.currentTimeMillis()}"

            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {
                    if (id == utteranceId && continuation.isActive) onStart()
                }

                override fun onDone(id: String?) {
                    if (id == utteranceId && continuation.isActive) {
                        onDone()
                        continuation.resume(true)
                    }
                }

                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    if (id == utteranceId && continuation.isActive) {
                        onDone()
                        continuation.resume(false)
                    }
                }
            })

            continuation.invokeOnCancellation {
                try {
                    engine.stop()
                } catch (_: Exception) {}
            }

            try {
                val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
                if (result != TextToSpeech.SUCCESS) {
                    if (continuation.isActive) continuation.resume(false)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Excecao ao chamar engine.speak: ${e.message}")
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

    fun shutdown() {
        stop()
        tearDownTts()
    }

    val isAvailable: Boolean
        get() = isReady.get()
}
