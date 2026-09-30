package br.com.bragasaude.data.remote.ai

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import br.com.bragasaude.R
import br.com.bragasaude.data.remote.auth.AuthService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import br.com.bragasaude.data.local.voice.PiperOnDeviceEngine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Player de áudio de alta fidelidade para Vozes Neurais (Faber pt-BR e Edresson pt-BR).
 *
 * Suporta:
 * 1. Reprodução instantânea (0ms) de áudios clínicos embutidos no APK (0ms, 100% offline).
 * 2. Síntese local On-Device com streaming de baixa latência via Piper/Sherpa-ONNX.
 * 3. Fallback para gateway remoto se o modelo local estiver em carga.
 */
@Singleton
class NeuralAudioPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val authService: AuthService,
    private val piperOnDeviceEngine: PiperOnDeviceEngine
) {
    companion object {
        private const val TAG = "NeuralAudioPlayer"
        val DEFAULT_SERVER_URL = br.com.bragasaude.BuildConfig.BASE_URL
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 20000
    }

    var serverBaseUrl: String = DEFAULT_SERVER_URL
    private var mediaPlayer: MediaPlayer? = null
    private val playbackGeneration = AtomicLong()
    private val cacheDir: File by lazy {
        File(context.cacheDir, "neural_tts").apply { if (!exists()) mkdirs() }
    }

    /**
     * Tenta reproduzir o áudio neural correspondente ao [text].
     * 1. Verifica se é uma frase clínica pré-gravada embutida no APK (0ms, 100% offline).
     * 2. Se for texto dinâmico, busca no cache em disco ou no servidor homelab.
     * 3. Retorna false se falhar, acionando o fallback para o sintetizador do sistema.
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
                val resource = getBundledAudioResId(sanitized)
                if (resource != null) {
                    return@withContext withContext(Dispatchers.Main) {
                        playSource(ticket, onStart, onDone) { player ->
                            player.setDataSource(context, Uri.parse("android.resource://${context.packageName}/$resource"))
                        }
                    }
                }

                // Tenta síntese neural On-Device com streaming de baixa latência via Piper
                if (piperOnDeviceEngine.isAvailable || piperOnDeviceEngine.hasModelFiles()) {
                    val playedLocal = piperOnDeviceEngine.playStream(
                        text = sanitized,
                        onStart = onStart,
                        onDone = onDone
                    )
                    if (playedLocal) return@withContext true
                }

                // Fallback: cache em disco ou síntese via gateway remoto
                val file = getOrCreateAudioFile(sanitized)
                currentCoroutineContext().ensureActive()
                if (file == null) return@withContext false
                withContext(Dispatchers.Main) {
                    playSource(ticket, onStart, onDone) { player ->
                        player.setDataSource(file.absolutePath)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.i(TAG, "Falha ao obter voz neural: ${e.message}")
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
            player.setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).build())
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

    private fun getOrCreateAudioFile(text: String): File? {
        val voiceName = "faber"
        val hash = hashMd5("${voiceName}_${text.trim()}")
        val cachedFile = File(cacheDir, "$hash.wav")

        if (cachedFile.exists() && cachedFile.length() > 500) {
            return cachedFile
        }

        var connection: HttpURLConnection? = null
        try {
            // 1. Endpoint nativo POST /synthesize_voice do servidor homelab (Piper TTS)
            val endpoint = URL("$serverBaseUrl/synthesize_voice")
            connection = (endpoint.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                doInput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "audio/wav")
                authService.getTokenBlocking(timeoutSeconds = 5)?.let {
                    setRequestProperty("Authorization", "Bearer $it")
                }
            }

            val payload = org.json.JSONObject().apply {
                put("text", text.trim())
                put("voice", voiceName)
            }.toString()

            java.io.OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload)
                writer.flush()
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val tempFile = File(cacheDir, "$hash.tmp")
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.length() > 500) {
                    tempFile.renameTo(cachedFile)
                    return cachedFile
                } else {
                    tempFile.delete()
                }
            } else {
                Log.w(TAG, "Endpoint /synthesize_voice retornou HTTP ${connection.responseCode}. Tentando fallback /v1/audio/speech.")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Tentativa em /synthesize_voice falhou (${e.message}). Tentando fallback GET.")
        } finally {
            try { connection?.disconnect() } catch (_: Exception) { }
            connection = null
        }

        // 2. Fallback defensivo para GET /v1/audio/speech caso o servidor esteja em versão anterior
        try {
            val encodedText = URLEncoder.encode(text.trim(), "UTF-8")
            val fallbackVoice = "faber"
            val fallbackUrl = URL("$serverBaseUrl/v1/audio/speech?text=$encodedText&voice=$fallbackVoice")

            connection = (fallbackUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doInput = true
                authService.getTokenBlocking(timeoutSeconds = 5)?.let {
                    setRequestProperty("Authorization", "Bearer $it")
                }
            }

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val tempFile = File(cacheDir, "$hash.tmp")
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }
                if (tempFile.length() > 500) {
                    tempFile.renameTo(cachedFile)
                    return cachedFile
                } else {
                    tempFile.delete()
                    return null
                }
            } else {
                Log.w(TAG, "Servidor de áudio retornou HTTP ${connection.responseCode}")
                return null
            }
        } catch (e: Exception) {
            Log.d(TAG, "Erro ao baixar áudio neural: ${e.message}")
            return null
        } finally {
            try { connection?.disconnect() } catch (_: Exception) { }
        }
    }

    private fun hashMd5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
