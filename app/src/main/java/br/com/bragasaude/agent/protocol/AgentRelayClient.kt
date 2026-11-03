package br.com.bragasaude.agent.protocol

import android.util.Log
import br.com.bragasaude.BuildConfig
import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.data.remote.network.GatewayTlsPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cliente de transporte para o Relay Agêntico (/api/ai/agent-relay).
 * Envia o turno de diálogo e ferramentas declaradas autenticado com JWT Firebase.
 */
@Singleton
class AgentRelayClient @Inject constructor(
    private val authService: AuthService,
    private val httpClient: OkHttpClient = GatewayTlsPolicy.defaultClient()
        .newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "AgentRelayClient"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            explicitNulls = false
        }
    }

    suspend fun sendTurn(request: AgentRelayRequest): AgentRelayResponse? = withContext(Dispatchers.IO) {
        val token = authService.getFreshToken()
        val url = "${BuildConfig.BASE_URL}/api/ai/agent-relay"

        try {
            val requestBodyStr = json.encodeToString(AgentRelayRequest.serializer(), request)
            val reqBuilder = Request.Builder()
                .url(url)
                .post(requestBodyStr.toRequestBody(JSON_MEDIA_TYPE))
                .header("Accept", "application/json")

            if (!token.isNullOrBlank()) {
                reqBuilder.header("Authorization", "Bearer $token")
            }

            httpClient.newCall(reqBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string()
                    Log.w(TAG, "Agent Relay HTTP ${response.code}: $errBody")
                    return@withContext null
                }
                val respBody = response.body?.string() ?: return@withContext null
                return@withContext json.decodeFromString(AgentRelayResponse.serializer(), respBody)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Falha na comunicação com Agent Relay: ${e.message}")
            null
        }
    }
}
