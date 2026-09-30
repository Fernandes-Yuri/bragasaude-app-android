package br.com.bragasaude.data.local.voice

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Motor de síntese neural Piper rodando On-Device via Sherpa-ONNX.
 *
 * Elimina a dependência de chamadas remotas de rede para síntese de voz,
 * permitindo execução em tempo real, streaming contínuo via AudioTrack
 * e funcionamento 100% offline.
 */
@Singleton
class PiperOnDeviceEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pcmStreamAudioPlayer: PcmStreamAudioPlayer
) {
    companion object {
        private const val TAG = "PiperOnDeviceEngine"
        private const val MODEL_FOLDER = "piper_voice"
        private const val DEFAULT_SAMPLE_RATE = 22050

        init {
            try {
                System.loadLibrary("onnxruntime")
            } catch (t: Throwable) {
                Log.d(TAG, "onnxruntime loadLibrary: ${t.message}")
            }
            try {
                System.loadLibrary("sherpa-onnx-c-api")
            } catch (t: Throwable) {
                Log.d(TAG, "sherpa-onnx-c-api loadLibrary: ${t.message}")
            }
            try {
                System.loadLibrary("sherpa-onnx-cxx-api")
            } catch (t: Throwable) {
                Log.d(TAG, "sherpa-onnx-cxx-api loadLibrary: ${t.message}")
            }
            try {
                System.loadLibrary("sherpa-onnx-jni")
            } catch (t: Throwable) {
                Log.d(TAG, "sherpa-onnx-jni loadLibrary: ${t.message}")
            }
        }
    }

    private var sherpaTts: Any? = null
    private val isInitialized = AtomicBoolean(false)
    private val isInitializing = AtomicBoolean(false)

    var customModelDir: File? = null

    /**
     * Diretório onde os arquivos do modelo (ONNX, tokens e regras fonéticas)
     * residem no armazenamento interno do aplicativo.
     */
    val modelDir: File
        get() = customModelDir ?: File(context.filesDir, MODEL_FOLDER).apply { if (!exists()) mkdirs() }

    /**
     * Verifica se os arquivos essenciais do modelo Piper estão presentes.
     */
    fun hasModelFiles(): Boolean {
        val modelFile = File(modelDir, "model.onnx")
        val tokensFile = File(modelDir, "tokens.txt")
        return modelFile.exists() && modelFile.length() > 100_000 && tokensFile.exists() && tokensFile.length() > 50
    }

    /**
     * Inicializa o motor de inferência C++ em segundo plano.
     * Captura erros de carga de biblioteca nativa defensivamente (ex: testes de JVM).
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized.get()) return@withContext true
        if (isInitializing.getAndSet(true)) return@withContext false

        try {
            if (!hasModelFiles()) {
                Log.i(TAG, "Arquivos de modelo Piper ainda nao extraidos ou baixados.")
                isInitializing.set(false)
                return@withContext false
            }

            val modelPath = File(modelDir, "model.onnx").absolutePath
            val tokensPath = File(modelDir, "tokens.txt").absolutePath
            val dataDir = File(modelDir, "espeak-ng-data").absolutePath

            val vitsConfig = com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig(
                model = modelPath,
                tokens = tokensPath,
                dataDir = if (File(dataDir).exists()) dataDir else "",
                noiseScale = 0.667f,
                noiseScaleW = 0.8f,
                lengthScale = 1.0f
            )

            val modelConfig = com.k2fsa.sherpa.onnx.OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )

            val ttsConfig = com.k2fsa.sherpa.onnx.OfflineTtsConfig(
                model = modelConfig
            )

            sherpaTts = com.k2fsa.sherpa.onnx.OfflineTts(
                config = ttsConfig
            )

            isInitialized.set(true)
            Log.i(TAG, "Motor Piper On-Device inicializado com sucesso.")
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Falha ao inicializar motor Piper On-Device: ${t.message}")
            false
        } finally {
            isInitializing.set(false)
        }
    }

    /**
     * Sintetiza o texto em tempo real e toca os chunks diretamente no AudioTrack.
     * Retorna true se sintetizou e reproduziu com sucesso, false em caso de falha
     * para ativar o fallback.
     */
    suspend fun playStream(
        text: String,
        speed: Float = 1.0f,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {}
    ): Boolean = withContext(Dispatchers.Default) {
        if (!isInitialized.get()) {
            val ok = initialize()
            if (!ok) return@withContext false
        }

        val engine = sherpaTts as? com.k2fsa.sherpa.onnx.OfflineTts ?: return@withContext false

        try {
            val prepared = pcmStreamAudioPlayer.prepare(DEFAULT_SAMPLE_RATE)
            if (!prepared) return@withContext false

            var started = false

            // Executa a sintese via sherpa-onnx
            val audio = engine.generate(
                text = text,
                sid = 0,
                speed = speed
            )

            if (audio.samples.isEmpty()) {
                pcmStreamAudioPlayer.stop()
                return@withContext false
            }

            if (!started) {
                started = true
                withContext(Dispatchers.Main) { onStart() }
            }

            // Escreve os samples diretamente no buffer do AudioTrack
            pcmStreamAudioPlayer.writeSamples(audio.samples)

            withContext(Dispatchers.Main) { onDone() }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Erro durante sintese streaming: ${e.message}")
            pcmStreamAudioPlayer.stop()
            false
        }
    }

    fun stop() {
        pcmStreamAudioPlayer.stop()
    }

    val isAvailable: Boolean
        get() = isInitialized.get()
}
