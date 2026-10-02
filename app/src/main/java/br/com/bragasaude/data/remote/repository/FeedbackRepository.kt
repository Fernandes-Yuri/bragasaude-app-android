package br.com.bragasaude.data.remote.repository

import android.content.Context
import br.com.bragasaude.BuildConfig
import br.com.bragasaude.data.local.FeedbackDao
import br.com.bragasaude.data.local.FeedbackEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.feedbackPayload
import br.com.bragasaude.data.remote.sync.SyncScheduler
import br.com.bragasaude.data.util.FeedbackTelemetryHelper
import br.com.bragasaude.data.util.LgpdSanitizer
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val feedbackDao: FeedbackDao,
    private val auth: FirebaseAuth,
    private val syncScheduler: SyncScheduler,
    private val apiClient: BragaApiClient
) {
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
        var savedLocally = false
        try {
            val user = auth.currentUser ?: return@withContext Result.failure(IllegalStateException("Entre na sua conta para enviar o feedback."))
            val feedbackId = UUID.randomUUID().toString()
            
            // 1. Sanitização LGPD Automática (Mascara CPFs e Telefones)
            val sanitizedMessage = LgpdSanitizer.sanitize(message.trim())
            val sanitizedTitle = title?.let { LgpdSanitizer.sanitize(it.trim()) }

            // 2. Coleta de Telemetria Oculta (Aparelho, Android API, Bateria, Rede, Permissões)
            val telemetry = FeedbackTelemetryHelper.collectDiagnosticTelemetry(context)

            // 3. Persistência Offline no Room Database (Fila Local Garantida)
            val localEntity = FeedbackEntity(
                id = feedbackId,
                userId = user.uid,
                userEmail = user.email,
                userName = user.displayName ?: "Usuário Braga Saúde",
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
            savedLocally = true

            // 4. Mesmo contrato autenticado usado na retentativa em background
            val isUploaded = uploadToGateway(localEntity)
            if (isUploaded) {
                feedbackDao.markAsSynced(feedbackId)
                Result.success("Feedback enviado com sucesso para a equipe de desenvolvimento!")
            } else {
                // Se estiver sem sinal ou em falha de conexão, aciona WorkManager para retentativa
                triggerSync()
                Result.success("Feedback gravado com sucesso! Será sincronizado assim que a conexão for restabelecida.")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("FeedbackRepo", "Falha no envio de feedback", e)
            if (savedLocally) {
                triggerSync()
                Result.success("Mensagem guardada no aparelho. A entrega à equipe ainda está pendente.")
            } else {
                Result.failure(IllegalStateException("Não foi possível guardar a mensagem. Tente novamente.", e))
            }
        }
    }

    suspend fun syncPendingFeedbacks() = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        var deliveryFailed = false
        for (item in feedbackDao.getPendingSync().filter { it.userId == uid }) {
            if (uploadToGateway(item)) {
                feedbackDao.markAsSynced(item.id)
            } else {
                deliveryFailed = true
            }
        }
        check(!deliveryFailed) { "Há feedbacks aguardando confirmação de persistência no gateway." }
    }

    private suspend fun uploadToGateway(entity: FeedbackEntity): Boolean {
        if (auth.currentUser?.uid != entity.userId) return false
        return apiClient.syncFeedback(feedbackPayload(entity)) != null
    }

    private fun triggerSync() {
        runCatching { syncScheduler.scheduleSync() }
            .onFailure { android.util.Log.w("FeedbackRepo", "Retentativa ainda não agendada", it) }
    }
}
