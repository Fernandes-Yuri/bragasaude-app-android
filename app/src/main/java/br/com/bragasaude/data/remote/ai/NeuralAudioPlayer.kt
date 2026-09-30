package br.com.bragasaude.data.remote.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import br.com.bragasaude.R
import br.com.bragasaude.data.local.voice.AndroidSystemTtsFallback
import br.com.bragasaude.data.local.voice.PiperOnDeviceEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Player de áudio de alta fidelidade para Vozes Neurais do Braga Saúde.
 *
 * Arquitetura 100% On-Device:
 * 1. Reprodução instantânea (0ms) de áudios clínicos embutidos no APK (100% offline).
 * 2. Síntese local On-Device com streaming de baixa latência via Piper/Sherpa-ONNX.
 * 3. Fallback imediato para o motor TextToSpeech nativo do sistema Android (Google Speech Engine).
 * Zero dependência de processamento de áudio ou binários na nuvem.
 */
@Singleton
class NeuralAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val piperOnDeviceEngine: PiperOnDeviceEngine,
    private val androidSystemTtsFallback: AndroidSystemTtsFallback
) {
    companion object {
        private const val TAG = "NeuralAudioPlayer"
    }

    private var mediaPlayer: MediaPlayer? = null
    private val playbackGeneration = AtomicLong()

    /**
     * Tenta reproduzir o áudio neural correspondente ao [text].
     */
    suspend fun playSpeech(
        text: String,
        isMale: Boolean = true,
        onStart: () -> Unit,
        onDone: () -> Unit
    ): Boolean {
        val ticket = playbackGeneration.incrementAndGet()
        return withContext(Dispatchers.IO) {
            val sanitized = br.com.bragasaude.util.PortuguesePhoneticHelper.cleanTextForTts(text)
            if (sanitized.isBlank()) return@withContext false
            try {
                // 1. Áudios clínicos pré-gravados embutidos no APK (0ms, 100% offline)
                val resource = getBundledAudioResId(sanitized)
                if (resource != null) {
                    return@withContext withContext(Dispatchers.Main) {
                        playSource(ticket, onStart, onDone) { player ->
                            player.setDataSource(context, Uri.parse("android.resource://${context.packageName}/$resource"))
                        }
                    }
                }

                // 2. Síntese neural On-Device com streaming de baixa latência via Piper
                if (piperOnDeviceEngine.isAvailable || piperOnDeviceEngine.hasModelFiles()) {
                    val playedLocal = piperOnDeviceEngine.playStream(
                        text = sanitized,
                        onStart = onStart,
                        onDone = onDone
                    )
                    if (playedLocal) return@withContext true
                }

                // 3. Fallback prioritário 100% local: TTS nativo do sistema Android
                if (androidSystemTtsFallback.isAvailable) {
                    val playedFallback = androidSystemTtsFallback.speak(
                        text = sanitized,
                        onStart = onStart,
                        onDone = onDone
                    )
                    if (playedFallback) return@withContext true
                }

                false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.i(TAG, "Falha na reprodução de voz: ${e.message}")
                false
            }
        }
    }

    suspend fun playRawResource(resId: Int, onStart: () -> Unit, onDone: () -> Unit): Boolean {
        val ticket = playbackGeneration.incrementAndGet()
        return withContext(Dispatchers.Main) {
            playSource(ticket, onStart, onDone) {
                it.setDataSource(context, Uri.parse("android.resource://${context.packageName}/$resId"))
            }
        }
    }

    /** Somente a requisição ainda vigente pode começar ou concluir uma reprodução. */
    private fun playSource(ticket: Long, onStart: () -> Unit, onDone: () -> Unit, source: (MediaPlayer) -> Unit): Boolean {
        if (ticket != playbackGeneration.get()) return false
        releasePlayer()
        val player = MediaPlayer()
        mediaPlayer = player
        fun ownsPlayer() = ticket == playbackGeneration.get() && mediaPlayer === player
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build()
            )
            source(player)
            player.setOnPreparedListener {
                if (ownsPlayer()) { it.start(); onStart() }
            }
            player.setOnCompletionListener {
                if (ownsPlayer()) { releasePlayer(); onDone() }
            }
            player.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer error: what=$what extra=$extra")
                if (ownsPlayer()) { releasePlayer(); onDone() }
                true
            }
            player.prepareAsync()
            return true
        } catch (e: Exception) {
            if (ownsPlayer()) releasePlayer()
            Log.w(TAG, "Falha ao preparar áudio: ${e.message}")
            return false
        }
    }

    private fun getBundledAudioResId(text: String): Int? {
        val lower = text.lowercase()
        return when {
            lower.contains("já preenchi") && lower.contains("pressão") -> R.raw.braga_bp_male
            (lower.contains("já anotei") && lower.contains("glicemia")) || lower.contains("glicose") -> R.raw.braga_glucose_male
            lower.contains("registrei a sua água") || lower.contains("manter-se hidratado") -> R.raw.braga_hydration_male
            lower.contains("não entendi bem") || lower.contains("minha pressão é 12 por 8") -> R.raw.braga_help_male
            else -> null
        }
    }

    fun stop() {
        playbackGeneration.incrementAndGet()
        piperOnDeviceEngine.stop()
        androidSystemTtsFallback.stop()
        releasePlayer()
    }

    private fun releasePlayer() {
        val player = mediaPlayer
        mediaPlayer = null
        try {
            player?.release()
        } catch (_: Exception) { }
    }

    enum class MicroInterjection(val resId: Int) {
        ANOTADO(R.raw.braga_micro_anotado),
        PERFEITO(R.raw.braga_micro_perfeito),
        CERTO(R.raw.braga_micro_certo),
        ENTENDI(R.raw.braga_micro_entendi),
        COMBINADO(R.raw.braga_micro_combinado)
    }

    suspend fun playMicroInterjection(
        type: MicroInterjection,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {}
    ): Boolean = playRawResource(type.resId, onStart, onDone)

    val isPlaying: Boolean
        get() = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }
}
