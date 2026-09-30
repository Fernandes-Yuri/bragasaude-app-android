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
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gerenciador de download em segundo plano do modelo neural Piper On-Device.
 *
 * Utiliza conexão HTTP isolada via HttpURLConnection (livre de interceptores
 * de autenticação e independente de certificados de API interna) para baixar
 * e extrair o modelo neural hospedado como asset de release.
 */
@Singleton
class PiperModelDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val piperEngine: PiperOnDeviceEngine
) {
    companion object {
        private const val TAG = "PiperModelDownloader"
        const val MODEL_ZIP_URL =
            "https://github.com/Fernandes-Yuri/bragasaude-app-android/releases/download/v1.3.0-build14/piper-pt_BR-faber.zip"
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
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
        try {
            if (piperEngine.hasModelFiles()) {
                _downloadStatus.value = DownloadState.Ready
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Erro ao verificar arquivos de modelo no init: ${t.message}")
        }
    }

    /**
     * Inicia o download e extração assíncrona se o modelo ainda não estiver presente.
     * Retorna imediatamente sem bloquear a UI nem a síntese vocal de fallback.
     */
    fun startDownloadInBackground(onComplete: ((Boolean) -> Unit)? = null) {
        try {
            if (piperEngine.hasModelFiles()) {
                _downloadStatus.value = DownloadState.Ready
                scope.launch {
                    val initOk = piperEngine.initialize()
                    onComplete?.invoke(initOk)
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
                    onComplete?.invoke(initOk)
                } else {
                    _downloadStatus.value = DownloadState.Error("Falha no download/extração do modelo Piper")
                    onComplete?.invoke(false)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Falha ao disparar download em segundo plano: ${t.message}", t)
            isDownloading.set(false)
            onComplete?.invoke(false)
        }
    }

    suspend fun downloadAndExtractModel(): Boolean = withContext(Dispatchers.IO) {
        val targetDir = piperEngine.modelDir
        val tempZipFile = File(context.cacheDir, "piper_model_temp.zip")
        val stagingDir = File(context.cacheDir, "piper_staging_${System.currentTimeMillis()}")

        try {
            Log.i(TAG, "Iniciando download do modelo Piper: $MODEL_ZIP_URL")
            val downloaded = downloadFileWithRedirects(MODEL_ZIP_URL, tempZipFile)
            if (!downloaded || !tempZipFile.exists() || tempZipFile.length() < 1024) {
                Log.w(TAG, "Falha no download do arquivo ZIP do modelo Piper.")
                return@withContext false
            }

            Log.i(TAG, "Download do ZIP concluído (${tempZipFile.length()} bytes). Extraindo para staging...")

            if (stagingDir.exists()) stagingDir.deleteRecursively()
            stagingDir.mkdirs()

            ZipInputStream(tempZipFile.inputStream().buffered()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryFile = File(stagingDir, entry.name)

                    // Proteção contra Zip Slip
                    val canonicalPath = entryFile.canonicalPath
                    if (!canonicalPath.startsWith(stagingDir.canonicalPath)) {
                        throw SecurityException("Entrada ZIP maliciosa detectada: ${entry.name}")
                    }

                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { fos ->
                            zis.copyTo(fos, bufferSize = 64 * 1024)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // Move da pasta staging para o diretório final
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            stagingDir.copyRecursively(targetDir, overwrite = true)
            Log.i(TAG, "Extração do modelo Piper concluída com sucesso.")

            val ok = piperEngine.hasModelFiles()
            if (!ok) {
                Log.w(TAG, "Arquivos do modelo incompletos após extração.")
            }
            ok
        } catch (e: Throwable) {
            Log.e(TAG, "Erro durante download/extração do modelo Piper: ${e.message}", e)
            false
        } finally {
            try {
                if (tempZipFile.exists()) tempZipFile.delete()
                if (stagingDir.exists()) stagingDir.deleteRecursively()
            } catch (_: Exception) {}
        }
    }

    private fun downloadFileWithRedirects(urlStr: String, destination: File, maxRedirects: Int = 5): Boolean {
        var currentUrl = urlStr
        var redirects = 0

        while (redirects < maxRedirects) {
            var connection: HttpURLConnection? = null
            try {
                val url = URL(currentUrl)
                connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "BragaSaudeApp/1.3")
                    setRequestProperty("Accept", "*/*")
                }

                val status = connection.responseCode
                if (status in 300..399) {
                    val newUrl = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (newUrl.isNullOrBlank()) return false
                    currentUrl = newUrl
                    redirects++
                    continue
                }

                if (status == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { input ->
                        FileOutputStream(destination).use { output ->
                            input.copyTo(output, bufferSize = 64 * 1024)
                        }
                    }
                    connection.disconnect()
                    return true
                }

                Log.w(TAG, "Código HTTP inesperado ao baixar modelo: $status")
                connection.disconnect()
                return false
            } catch (e: Exception) {
                Log.e(TAG, "Erro de rede no download: ${e.message}")
                try { connection?.disconnect() } catch (_: Exception) {}
                return false
            }
        }
        return false
    }
}

