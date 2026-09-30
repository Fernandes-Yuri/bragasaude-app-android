package br.com.bragasaude.data.local.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reprodutor de áudio PCM de baixa latência em modo streaming contínuo.
 *
 * Utiliza AudioTrack no modo MODE_STREAM para reproduzir chunks de áudio
 * sintetizados em tempo real pelo motor Piper On-Device antes do término
 * da geração da frase completa.
 */
@Singleton
class PcmStreamAudioPlayer @Inject constructor() {

    companion object {
        private const val TAG = "PcmStreamAudioPlayer"
        const val DEFAULT_SAMPLE_RATE = 22050
    }

    private var audioTrack: AudioTrack? = null
    private val isStreamingActive = AtomicBoolean(false)
    private var currentSampleRate = DEFAULT_SAMPLE_RATE

    @Synchronized
    fun prepare(sampleRate: Int = DEFAULT_SAMPLE_RATE): Boolean {
        stop()
        currentSampleRate = sampleRate
        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT
        )

        val bufferSize = if (minBufferSize > 0) minBufferSize * 2 else 4096

        val attributes = AudioAttributes.Builder()
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .build()

        val format = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .build()

        return try {
            audioTrack = AudioTrack(
                attributes,
                format,
                bufferSize,
                AudioTrack.MODE_STREAM,
                AudioManager.AUDIO_SESSION_ID_GENERATE
            )
            audioTrack?.play()
            isStreamingActive.set(true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao inicializar AudioTrack: ${e.message}")
            false
        }
    }

    @Synchronized
    fun writeSamples(samples: FloatArray): Int {
        val track = audioTrack ?: return 0
        if (!isStreamingActive.get()) return 0
        return try {
            track.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao escrever amostras no AudioTrack: ${e.message}")
            0
        }
    }

    @Synchronized
    fun stop() {
        isStreamingActive.set(false)
        try {
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    pause()
                    flush()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao parar AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
        }
    }

    val isPlaying: Boolean
        get() = audioTrack?.playState == AudioTrack.PLAYSTATE_PLAYING && isStreamingActive.get()
}
