package br.com.bragasaude.data.local.voice

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PiperModelDownloader @Inject constructor() {
    /** Não altera a instalação ativa. Progresso separado de download e extração. */
    suspend fun prepare(option: VoiceOption, staging: File, progress: (String, Float?) -> Unit) =
        withContext(Dispatchers.IO) {
            val archive = File(staging, "download.tar.bz2")
            try {
                download(option, archive) { progress("Baixando voz", it) }
                progress("Verificando integridade", null)
                verifyChecksum(archive, requireNotNull(option.sha256))
                extract(archive, staging, requireNotNull(option.archiveName)) {
                    progress("Extraindo voz", it)
                }
                check(VoiceModelStore.valid(staging)) { "Modelo incompleto" }
            } finally { archive.delete() }
        }

    private suspend fun download(option: VoiceOption, destination: File, progress: (Float) -> Unit) {
        var url = URL(requireNotNull(option.downloadUrl))
        repeat(6) {
            require(url.protocol == "https")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 30_000
                instanceFollowRedirects = false
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                if (connection.responseCode in 300..399) {
                    url = URL(url, requireNotNull(connection.getHeaderField("Location")))
                } else {
                    check(connection.responseCode == 200) { "Download indisponível" }
                    var bytes = 0L
                    connection.inputStream.use { input -> destination.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            bytes += count
                            check(bytes <= option.downloadBytes) { "Tamanho de download inesperado" }
                            output.write(buffer, 0, count)
                            progress(bytes.toFloat() / option.downloadBytes)
                        }
                    } }
                    check(bytes == option.downloadBytes) { "Download incompleto" }
                    return
                }
            } finally { connection.disconnect() }
        }
        error("Redirecionamentos excessivos")
    }

    internal fun verifyChecksum(archive: File, expected: String) {
        val digest = MessageDigest.getInstance("SHA-256")
        archive.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        check(digest.digest().joinToString("") { "%02x".format(it) } == expected) { "Integridade inválida" }
    }

    internal suspend fun extract(archive: File, target: File, prefix: String, progress: (Float) -> Unit) {
        fun entries() = TarArchiveInputStream(BZip2CompressorInputStream(archive.inputStream().buffered()))
        var total = 0L
        var count = 0
        entries().use { tar ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = tar.nextTarEntry ?: break
                check(++count <= 10_000 && entry.size >= 0)
                total += entry.size
                check(total <= 250_000_000) { "Modelo excede o tamanho permitido" }
            }
        }
        var written = 0L
        entries().use { tar ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = tar.nextTarEntry ?: break
                check(entry.name == prefix || entry.name.startsWith("$prefix/"))
                check(entry.isDirectory || (entry.isFile && !entry.isLink && !entry.isSymbolicLink))
                val relative = entry.name.removePrefix(prefix).removePrefix("/")
                if (relative.isEmpty()) continue
                val file = File(target, relative)
                check(file.canonicalPath.startsWith(target.canonicalPath + File.separator)) { "Caminho inválido" }
                if (entry.isDirectory) { file.mkdirs(); continue }
                file.parentFile?.mkdirs()
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val size = tar.read(buffer)
                        if (size < 0) break
                        output.write(buffer, 0, size)
                        written += size
                        progress(if (total > 0) written.toFloat() / total else 1f)
                    }
                }
            }
        }
        val models = target.listFiles().orEmpty().filter { it.extension == "onnx" }
        check(models.size == 1) { "Modelo ambíguo" }
        if (models.single().name != "model.onnx") check(models.single().renameTo(File(target, "model.onnx")))
    }
}
