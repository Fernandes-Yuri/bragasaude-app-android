package br.com.bragasaude.data.local.voice

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gerenciador de download em segundo plano do modelo neural Piper On-Device.
 *
 * Permite que o APK continue com tamanho reduzido na instalação. Na primeira
 * inicialização, realiza o download e extração em background dos arquivos de inferência
 * (model.onnx, tokens.txt e regras fonéticas espeak-ng-data).
 * Enquanto o download ocorre, o aplicativo utiliza o AndroidSystemTtsFallback de forma transparente.
 */
@Singleton
class PiperModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val piperEngine: PiperOnDeviceEngine,
    private val okHttpClient: OkHttpClient
) {
    companion object {
        private const val TAG = "PiperModelDownloader"
        const val MODEL_ZIP_URL =
            "https://github.com/Fernandes-Yuri/bragasaude-app-android/releases/download/v1.3.0-build14/piper-pt_BR-faber.zip"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isDownloading = AtomicBoolean(false)

    private val _downloadStatus = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadStatus: StateFlow<DownloadState> = _downloadStatus.asStateFlow()

    sealed class DownloadState {
        object Idle : DownloadState()
        object Downloading : DownloadState()
        object Ready : DownloadState()
        data class Error(val message: String) : DownloadState()
    }

    init {
        if (piperEngine.hasModelFiles()) {
            _downloadStatus.value = DownloadState.Ready
        }
    }

    /**
     * Inicia o download e extração assíncrona se o modelo ainda não estiver presente.
     * Retorna imediatamente sem bloquear a UI nem a síntese vocal de fallback.
     */
    fun startDownloadInBackground(onComplete: ((Boolean) -> Unit)? = null) {
        if (piperEngine.hasModelFiles()) {
            _downloadStatus.value = DownloadState.Ready
            scope.launch {
                piperEngine.initialize()
                onComplete?.invoke(true)
            }
            return
        }

        if (isDownloading.getAndSet(true)) {
            Log.d(TAG, "Download do modelo Piper já está em andamento.")
            return
        }

        _downloadStatus.value = DownloadState.Downloading

        scope.launch {
            val success = downloadAndExtractModel()
            isDownloading.set(false)
            if (success) {
                _downloadStatus.value = DownloadState.Ready
                Log.i(TAG, "Modelo Piper pronto. Inicializando motor de inferência...")
                val initOk = piperEngine.initialize()
                Log.i(TAG, "Motor Piper inicializado após download: $initOk")
                onComplete?.invoke(true)
            } else {
                _downloadStatus.value = DownloadState.Error("Falha no download/extração do modelo Piper")
                onComplete?.invoke(false)
            }
        }
    }

    suspend fun downloadAndExtractModel(): Boolean = withContext(Dispatchers.IO) {
        val targetDir = piperEngine.modelDir
        val tempZipFile = File(context.cacheDir, "piper_model_temp.zip")

        try {
            Log.i(TAG, "Iniciando download do modelo Piper: $MODEL_ZIP_URL")
            val request = Request.Builder()
                .url(MODEL_ZIP_URL)
                .build()

            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Falha na resposta HTTP do download do modelo: ${response.code}")
                return@withContext false
            }

            val body = response.body ?: run {
                Log.w(TAG, "Corpo de resposta nulo ao baixar modelo")
                return@withContext false
            }

            // Grava o arquivo ZIP em disco
            body.byteStream().use { input ->
                FileOutputStream(tempZipFile).use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            }

            Log.i(TAG, "Download do ZIP concluído (${tempZipFile.length()} bytes). Extraindo para $targetDir...")

            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }

            // Descompacta os arquivos
            ZipInputStream(tempZipFile.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryFile = File(targetDir, entry.name)

                    // Proteção Zip Slip
                    val canonicalPath = entryFile.canonicalPath
                    if (!canonicalPath.startsWith(targetDir.canonicalPath)) {
                        throw SecurityException("Entrada ZIP maliciosa detectada: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { fos ->
                            zis.copyTo(fos, bufferSize = 32 * 1024)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            Log.i(TAG, "Extração do modelo Piper concluída com sucesso.")

            // Valida integridade básica
            val ok = piperEngine.hasModelFiles()
            if (!ok) {
                Log.w(TAG, "Arquivos do modelo incompletos após extração.")
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "Erro durante download/extração do modelo Piper: ${e.message}", e)
            false
        } finally {
            if (tempZipFile.exists()) {
                tempZipFile.delete()
            }
        }
    }
}
