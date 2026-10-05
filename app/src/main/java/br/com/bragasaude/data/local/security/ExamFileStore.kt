package br.com.bragasaude.data.local.security

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Originais cifrados; cópias em claro existem apenas durante leitura/exportação. */
@Singleton
class ExamFileStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val directory get() = File(context.filesDir, "exams").apply { mkdirs() }

    private fun encrypted(file: File): EncryptedFile {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedFile.Builder(context, file, key, EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB).build()
    }

    suspend fun store(bytes: ByteArray, extension: String): String = withContext(Dispatchers.IO) {
        require(bytes.size <= 35 * 1024 * 1024) { "O exame deve ter no máximo 35 MB." }
        val safeExtension = extension.lowercase().takeIf { it in setOf("pdf", "jpg", "jpeg", "png", "webp") } ?: "jpg"
        val file = File(directory, "${UUID.randomUUID()}.$safeExtension.enc")
        try {
            encrypted(file).openFileOutput().use { it.write(bytes) }
            file.absolutePath
        } catch (e: Exception) { file.delete(); throw e }
    }

    private fun owned(path: String): File {
        val file = File(path).canonicalFile
        require(file.parentFile == directory.canonicalFile) { "Original fora da pasta de exames." }
        return file
    }

    suspend fun readBytes(path: String): ByteArray = withContext(Dispatchers.IO) {
        val file = owned(path)
        require(file.length() <= 36 * 1024 * 1024) { "Original muito grande." }
        if (file.extension == "enc") encrypted(file).openFileInput().use { it.readBytes() } else file.readBytes()
    }

    suspend fun materialize(path: String, share: Boolean = false): File = withContext(Dispatchers.IO) {
        val source = owned(path)
        val extension = source.name.removeSuffix(".enc").substringAfterLast('.', "pdf")
        val cache = File(context.cacheDir, if (share) "shared_pdfs" else "exam_originals").apply { mkdirs() }
        cache.listFiles()?.filter { it.isFile && it.lastModified() < System.currentTimeMillis() - 24 * 60 * 60 * 1000L }?.forEach { it.delete() }
        val file = File.createTempFile("exame_", ".$extension", cache)
        try { file.writeBytes(readBytes(path)); file } catch (e: Exception) { file.delete(); throw e }
    }

    fun delete(path: String?) { if (path != null) owned(path).delete() }
}
