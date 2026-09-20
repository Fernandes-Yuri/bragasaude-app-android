package br.com.bragasaude.data.remote.repository

import android.content.Context
import br.com.bragasaude.BuildConfig
import br.com.bragasaude.data.local.FeedbackDao
import br.com.bragasaude.data.local.FeedbackEntity
import br.com.bragasaude.data.remote.model.RemoteFeedback
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.data.util.FeedbackTelemetryHelper
import br.com.bragasaude.data.util.LgpdSanitizer
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val feedbackDao: FeedbackDao,
    private val auth: FirebaseAuth,
    private val syncScheduler: SyncScheduler
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    // AUD-AN33: antes hardcoded em "https://braga-saude.web.app" — o Firebase
    // Hosting da LANDING PAGE, não o portal de operações. O feedback nunca
    // chegava ao painel (falha silenciosa). Agora vem do BuildConfig, e a
    // versão do app também é a real (era travada em "1.2.0").
    private val webserviceBaseUrl = BuildConfig.WEBSERVICE_BASE_URL

    fun getUserFeedbacks(userId: String): Flow<List<FeedbackEntity>> {
        return feedbackDao.getFeedbacksByUser(userId)
    }

    suspend fun sendFeedback(
        category: String,
        message: String,
        title: String? = null,
        inputMethod: String = "text",
        screenshotBase64: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val user = auth.currentUser
            val feedbackId = "fb-${UUID.randomUUID()}"
            
            // 1. Sanitização LGPD Automática (Mascara CPFs e Telefones)
            val sanitizedMessage = LgpdSanitizer.sanitize(message.trim())
            val sanitizedTitle = title?.let { LgpdSanitizer.sanitize(it.trim()) }

            // 2. Coleta de Telemetria Oculta (Aparelho, Android API, Bateria, Rede, Permissões)
            val telemetry = FeedbackTelemetryHelper.collectDiagnosticTelemetry(context)

            // 3. Persistência Offline no Room Database (Fila Local Garantida)
            val localEntity = FeedbackEntity(
                id = feedbackId,
                userId = user?.uid,
                userEmail = user?.email,
                userName = user?.displayName ?: "Usuário Braga Saúde",
                category = category,
                title = sanitizedTitle,
                message = sanitizedMessage,
                inputMethod = inputMethod,
                appVersion = BuildConfig.VERSION_NAME, // AUD-AN33: era "1.2.0" travado
                deviceInfo = telemetry,
                screenshotBase64 = screenshotBase64,
                status = "pending",
                createdAt = Date(),
                pendingSync = true
            )
            feedbackDao.insert(localEntity)

            // 4. Tentativa de Envio Imediato para o Web Service
            val isUploaded = uploadToWebService(localEntity)
            if (isUploaded) {
                feedbackDao.markAsSynced(feedbackId)
                Result.success("Feedback enviado com sucesso para a equipe de desenvolvimento!")
            } else {
                // Se estiver sem sinal ou em falha de conexão, aciona WorkManager para retentativa
                triggerSync()
                Result.success("Feedback gravado com sucesso! Será sincronizado assim que a conexão for restabelecida.")
            }
        } catch (e: Exception) {
            android.util.Log.e("FeedbackRepo", "Erro ao enviar feedback: ${e.message}")
            triggerSync()
            Result.success("Mensagem guardada no aparelho. Sincronizaremos automaticamente.")
        }
    }

    suspend fun syncPendingFeedbacks() = withContext(Dispatchers.IO) {
        val pendingList = feedbackDao.getPendingSync()
        for (item in pendingList) {
            val success = uploadToWebService(item)
            if (success) {
                feedbackDao.markAsSynced(item.id)
            }
        }
    }

    private fun uploadToWebService(entity: FeedbackEntity): Boolean {
        return try {
            val remote = RemoteFeedback(
                id = entity.id,
                userId = entity.userId,
                userEmail = entity.userEmail,
                userName = entity.userName,
                category = entity.category,
                title = entity.title,
                message = entity.message,
                inputMethod = entity.inputMethod,
                appVersion = entity.appVersion,
                deviceInfo = entity.deviceInfo,
                screenshotBase64 = entity.screenshotBase64,
                status = entity.status,
                createdAt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(entity.createdAt)
            )

            val endpoint = URL("$webserviceBaseUrl/api/feedback")
            val conn = endpoint.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.setRequestProperty("Accept", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.doOutput = true

            val payload = json.encodeToString(remote)
            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(payload)
                writer.flush()
            }

            conn.responseCode in 200..299
        } catch (e: Exception) {
            false
        }
    }

    private fun triggerSync() {
        syncScheduler.scheduleSync()
    }
}
