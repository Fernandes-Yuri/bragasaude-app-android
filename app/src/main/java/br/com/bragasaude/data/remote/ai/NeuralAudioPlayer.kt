package br.com.bragasaude.data.remote.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import br.com.bragasaude.R
import br.com.bragasaude.data.local.voice.VoiceProfileManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/** Reproduz falas com a voz selecionada e mantém o controle de cancelamento da conversa. */
@Singleton
class NeuralAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val voiceProfileManager: VoiceProfileManager
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
                voiceProfileManager.playSpeech(sanitized,
                    onStart = { if (ticket == playbackGeneration.get()) onStart() },
                    onDone = { if (ticket == playbackGeneration.get()) onDone() })
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

    fun stop() {
        playbackGeneration.incrementAndGet()
        voiceProfileManager.stop()
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
    ): Boolean = playSpeech(when (type) {
        MicroInterjection.ANOTADO -> "Anotado."
        MicroInterjection.PERFEITO -> "Perfeito."
        MicroInterjection.CERTO -> "Certo."
        MicroInterjection.ENTENDI -> "Entendi."
        MicroInterjection.COMBINADO -> "Combinado."
    }, onStart = onStart, onDone = onDone)

    val isPlaying: Boolean
        get() = try { mediaPlayer?.isPlaying == true } catch (_: Exception) { false }
}
