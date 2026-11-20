package br.com.bragasaude.data.backup

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class DriveBackupFileMeta(
    val fileId: String,
    val name: String,
    val modifiedTime: String? = null,
    val sizeBytes: Long? = null
)

/**
 * Cliente REST enxuto para a pasta oculta `appDataFolder` do Google Drive (`drive.appdata`),
 * isolado do OkHttpClient do gateway para nao vazar tokens Firebase nem aplicar pinning de servidor Braga.
 */
@Singleton
class GoogleDriveAppDataClient(
    private val httpClient: OkHttpClient,
    private val driveApiBaseUrl: String = DEFAULT_DRIVE_API_BASE,
    private val driveUploadBaseUrl: String = DEFAULT_DRIVE_UPLOAD_BASE
) {
    @Inject
    constructor() : this(
        httpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build(),
        driveApiBaseUrl = DEFAULT_DRIVE_API_BASE,
        driveUploadBaseUrl = DEFAULT_DRIVE_UPLOAD_BASE
    )

    suspend fun findLatestBackupFile(accessToken: String): DriveBackupFileMeta? =
        withContext(Dispatchers.IO) {
            val query = URLEncoder.encode(
                "name = '$BACKUP_FILE_NAME' and trashed = false",
                Charsets.UTF_8.name()
            )
            val fields = URLEncoder.encode(
                "files(id,name,modifiedTime,size)",
                Charsets.UTF_8.name()
            )
            val url = "${driveApiBaseUrl.trimEnd('/')}/drive/v3/files" +
                "?spaces=appDataFolder&q=$query&fields=$fields&orderBy=modifiedTime%20desc&pageSize=1"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    throw SecurityException("Permissão ao Google Drive expirada ou negada (${resp.code}).")
                }
                if (!resp.isSuccessful) {
                    throw IOException("Falha ao consultar backups no Google Drive (HTTP ${resp.code}).")
                }
                val raw = resp.body?.string().orEmpty()
                val files = JSONObject(raw).optJSONArray("files") ?: return@withContext null
                if (files.length() == 0) return@withContext null
                parseFileMeta(files.getJSONObject(0))
            }
        }

    suspend fun uploadEncryptedBackup(
        accessToken: String,
        encryptedBytes: ByteArray,
        existingFileId: String? = null
    ): DriveBackupFileMeta = withContext(Dispatchers.IO) {
        val boundary = "braga_bkp_${System.currentTimeMillis()}"
        val metadata = JSONObject().apply {
            put("name", BACKUP_FILE_NAME)
            put("mimeType", "application/octet-stream")
            if (existingFileId.isNullOrBlank()) {
                put("parents", org.json.JSONArray(listOf("appDataFolder")))
            }
        }.toString()

        val bodyOut = ByteArrayOutputStream()
        bodyOut.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
        bodyOut.write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray(Charsets.UTF_8))
        bodyOut.write(metadata.toByteArray(Charsets.UTF_8))
        bodyOut.write("\r\n--$boundary\r\n".toByteArray(Charsets.UTF_8))
        bodyOut.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray(Charsets.UTF_8))
        bodyOut.write(encryptedBytes)
        bodyOut.write("\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8))

        val requestBody = bodyOut.toByteArray()
            .toRequestBody("multipart/related; boundary=$boundary".toMediaType())

        val base = driveUploadBaseUrl.trimEnd('/')
        val builder = Request.Builder()
            .header("Authorization", "Bearer $accessToken")

        val request = if (existingFileId.isNullOrBlank()) {
            builder
                .url("$base/upload/drive/v3/files?uploadType=multipart&fields=id,name,modifiedTime,size")
                .post(requestBody)
                .build()
        } else {
            val safeId = URLEncoder.encode(existingFileId.trim(), Charsets.UTF_8.name())
            builder
                .url("$base/upload/drive/v3/files/$safeId?uploadType=multipart&fields=id,name,modifiedTime,size")
                .patch(requestBody)
                .build()
        }

        httpClient.newCall(request).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403) {
                throw SecurityException("Permissão ao Google Drive expirada ou negada (${resp.code}).")
            }
            if (!resp.isSuccessful) {
                throw IOException("Falha ao enviar backup ao Google Drive (HTTP ${resp.code}).")
            }
            val raw = resp.body?.string().orEmpty()
            parseFileMeta(JSONObject(raw))
                ?: throw IOException("Resposta inesperada do Google Drive após envio do backup.")
        }
    }

    suspend fun downloadBackup(accessToken: String, fileId: String): ByteArray =
        withContext(Dispatchers.IO) {
            val safeId = URLEncoder.encode(fileId.trim(), Charsets.UTF_8.name())
            val url = "${driveApiBaseUrl.trimEnd('/')}/drive/v3/files/$safeId?alt=media"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()

            httpClient.newCall(request).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    throw SecurityException("Permissão ao Google Drive expirada ou negada (${resp.code}).")
                }
                if (!resp.isSuccessful) {
                    throw IOException("Falha ao baixar backup do Google Drive (HTTP ${resp.code}).")
                }
                resp.body?.bytes() ?: throw IOException("Backup baixado está vazio.")
            }
        }

    private fun parseFileMeta(obj: JSONObject): DriveBackupFileMeta? {
        val id = obj.optString("id").trim()
        if (id.isEmpty()) return null
        val name = obj.optString("name", BACKUP_FILE_NAME)
        val mod = obj.optString("modifiedTime").trim().ifEmpty { null }
        val size = obj.optString("size").trim().toLongOrNull()
        return DriveBackupFileMeta(
            fileId = id,
            name = name,
            modifiedTime = mod,
            sizeBytes = size
        )
    }

    companion object {
        const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        const val BACKUP_FILE_NAME = "braga-chat-backup-v1.bragabkp"
        const val DEFAULT_DRIVE_API_BASE = "https://www.googleapis.com"
        const val DEFAULT_DRIVE_UPLOAD_BASE = "https://www.googleapis.com"
    }
}
