package br.com.bragasaude.data.remote.service

import android.util.Log
import br.com.bragasaude.data.remote.auth.AuthService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cliente HTTP para comunicação com o Hub de Notificações no Lenovo G460.
 *
 * Envia registros de tokens FCM, dispara alertas para cuidadores e
 * aciona o canal de emergência familiar com dependência zero e fail-safe silencioso.
 */
@Singleton
class NotificationClient @Inject constructor(
    private val authService: AuthService
) {

    companion object {
        private const val TAG = "NotificationClient"
        val DEFAULT_SERVER_URL = br.com.bragasaude.BuildConfig.BASE_URL
        private const val TIMEOUT_MS = 5000
    }

    var serverBaseUrl: String = DEFAULT_SERVER_URL

    /**
     * Cadastra o dispositivo atual e seu token FCM no servidor Homelab.
     */
    suspend fun registerDevice(
        userId: String,
        token: String,
        role: String = "PATIENT",
        name: String = "Usuário Braga"
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$serverBaseUrl/api/device/register")
            val payload = JSONObject().apply {
                put("userId", userId)
                put("token", token)
                put("role", role)
                put("name", name)
                put("platform", "android")
            }
            return@withContext sendJsonPost(endpoint, payload)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao registrar dispositivo no Hub: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Dispara um alerta clínico ou mensagem para cuidadores/pacientes.
     */
    suspend fun sendAlert(
        type: String,
        title: String,
        message: String,
        sourceUserId: String,
        targetRole: String = "CAREGIVER",
        metricValue: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$serverBaseUrl/api/notify/alert")
            val payload = JSONObject().apply {
                put("type", type)
                put("title", title)
                put("message", message)
                put("sourceUserId", sourceUserId)
                put("targetRole", targetRole)
                if (metricValue != null) {
                    put("metricValue", metricValue)
                }
            }
            return@withContext sendJsonPost(endpoint, payload)
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao enviar alerta clínico para o Hub: ${e.message}")
            return@withContext false
        }
    }

    /**
     * Dispara o canal de emergência de altíssima prioridade para a rede de cuidado familiar.
     */
    suspend fun triggerEmergency(
        userId: String,
        userName: String,
        details: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val endpoint = URL("$serverBaseUrl/api/emergency/trigger")
            val payload = JSONObject().apply {
                put("userId", userId)
                put("userName", userName)
                put("details", details)
            }
            return@withContext sendJsonPost(endpoint, payload)
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao disparar emergência no Hub: ${e.message}")
            return@withContext false
        }
    }

    private fun sendJsonPost(url: URL, json: JSONObject): Boolean {
        var conn: HttpURLConnection? = null
        return try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                val token = authService.getTokenBlocking()
                    ?: throw java.io.IOException("Sessão expirada.")
                setRequestProperty("Authorization", "Bearer $token")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(json.toString())
                writer.flush()
            }
            conn.responseCode in 200..299
        } catch (e: Exception) {
            Log.d(TAG, "Erro na comunicação HTTP com o Hub (${url.path}): ${e.message}")
            false
        } finally {
            try { conn?.disconnect() } catch (_: Exception) {}
        }
    }
}
