package br.com.bragasaude.data.remote.family

import android.util.Log
import br.com.bragasaude.BuildConfig
import br.com.bragasaude.data.local.FAMILY_MESSAGE_TTL_MS
import br.com.bragasaude.data.local.FamilyMessageEntity
import br.com.bragasaude.data.remote.network.GatewayTlsPolicy
import br.com.bragasaude.di.BaseUrl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

enum class FamilyWsStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

interface FamilyWsListener {
    fun onMessageReceived(msg: FamilyMessageEntity)
    fun onMessageDeleted(messageId: String)
    fun onMessagesRead(patientId: String, readerId: String, messageIds: List<String>) {}
}

/**
 * Cliente WebSocket de alta performance para Chat Familiar em tempo real (/ws/family).
 * Gerencia conexão assíncrona, keepalive (pings a cada 20s), reconexão exponencial com jitter
 * e despacho de eventos ponta a ponta.
 */
@Singleton
class FamilyChatWebSocketClient @Inject constructor(
    @BaseUrl private val baseUrl: String
) {
    // Construtor auxiliar sem argumentos para testes e instanciação manual
    constructor() : this(BuildConfig.BASE_URL)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var connectionJob: Job? = null

    private val client: OkHttpClient = GatewayTlsPolicy.configure(OkHttpClient.Builder(), !BuildConfig.DEBUG)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var webSocket: WebSocket? = null

    private val _status = MutableStateFlow(FamilyWsStatus.DISCONNECTED)
    val status: StateFlow<FamilyWsStatus> = _status.asStateFlow()

    @Volatile
    private var activePatientId: String? = null

    @Volatile
    private var activeListener: FamilyWsListener? = null

    companion object {
        private const val TAG = "FamilyWsClient"
        private const val INITIAL_BACKOFF_MS = 1000L
        private const val MAX_BACKOFF_MS = 30000L
    }

    /**
     * Inicia a sessão de WebSocket para a conversa de [patientId].
     */
    fun startSession(
        patientId: String,
        tokenProvider: suspend () -> String?,
        listener: FamilyWsListener
    ) {
        if (!BuildConfig.ENABLE_FAMILY_WEBSOCKET) {
            Log.d(TAG, "WebSocket de família desativado neste build. Mantendo transporte HTTP.")
            return
        }

        if (patientId.isBlank()) {
            Log.w(TAG, "startSession chamado com patientId vazio. Abortando conexão WebSocket.")
            return
        }

        activePatientId = patientId
        activeListener = listener

        // A sessão foi solicitada: o estado CONNECTING precisa ser visível
        // IMEDIATAMENTE, antes do launch na thread IO. Sem isso, um coletor
        // recém-assinado em `status` lê o valor antigo (DISCONNECTED) e a UI
        // pisca "desconectado" antes de conectar (corrida observada no
        // FamilySessionTest.strictWebSocketMode... do CI da fase 8B).
        _status.value = FamilyWsStatus.CONNECTING

        connectionJob?.cancel()
        connectionJob = scope.launch {
            var attempt = 0
            while (isActive) {
                try {
                    _status.value = if (attempt == 0) FamilyWsStatus.CONNECTING else FamilyWsStatus.RECONNECTING
                    val token = runCatching { tokenProvider() }.getOrNull()
                    if (token.isNullOrBlank()) {
                        Log.w(TAG, "Token indisponível para conexão WebSocket do chat. Aguardando 3s...")
                        delay(3000)
                        attempt++
                        continue
                    }

                    val wsUrl = buildWsUrl(baseUrl, token, patientId)
                    val request = Request.Builder()
                        .url(wsUrl)
                        .header("Authorization", "Bearer $token")
                        .build()

                    val sessionClosed = CompletableDeferred<Unit>()

                    val listenerInternal = object : WebSocketListener() {
                        override fun onOpen(ws: WebSocket, response: Response) {
                            Log.i(TAG, "WebSocket do chat familiar conectado com sucesso!")
                            webSocket = ws
                            _status.value = FamilyWsStatus.CONNECTED
                            attempt = 0
                        }

                        override fun onMessage(ws: WebSocket, text: String) {
                            handleIncomingJson(text)
                        }

                        override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                            Log.d(TAG, "WebSocket encerrando: code=$code reason=$reason")
                            ws.close(code, reason)
                        }

                        override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                            Log.d(TAG, "WebSocket fechado: code=$code reason=$reason")
                            webSocket = null
                            sessionClosed.complete(Unit)
                        }

                        override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                            Log.w(TAG, "Falha no WebSocket do chat: ${t.message} (response=${response?.code})")
                            webSocket = null
                            sessionClosed.completeExceptionally(t)
                        }
                    }

                    val ws = client.newWebSocket(request, listenerInternal)
                    webSocket = ws

                    try {
                        sessionClosed.await()
                    } catch (_: Exception) {
                        // Conexão caiu ou falhou; o loop externo tentará reconectar
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Erro durante conexão do WebSocket: ${e.message}")
                } finally {
                    webSocket?.close(1000, "Normal closure")
                    webSocket = null
                }

                if (isActive) {
                    attempt++
                    val backoff = (INITIAL_BACKOFF_MS * (1L shl attempt.coerceAtMost(5))).coerceAtMost(MAX_BACKOFF_MS)
                    val jitter = (backoff * 0.15 * Math.random()).toLong()
                    val totalDelay = backoff + jitter
                    Log.d(TAG, "Tentando reconectar WebSocket em ${totalDelay}ms (tentativa $attempt)...")
                    _status.value = FamilyWsStatus.RECONNECTING
                    delay(totalDelay)
                }
            }
        }
    }

    /**
     * Encerra a conexão WebSocket e limpa listeners.
     */
    fun stopSession() {
        connectionJob?.cancel()
        connectionJob = null
        webSocket?.close(1000, "Session stopped by user")
        webSocket = null
        activePatientId = null
        activeListener = null
        _status.value = FamilyWsStatus.DISCONNECTED
        Log.d(TAG, "Sessão WebSocket encerrada.")
    }

    /**
     * Envia confirmação de leitura em tempo real para a família.
     */
    fun sendReadReceipt(patientId: String) {
        val ws = webSocket ?: return
        try {
            val json = JSONObject().apply {
                put("type", "MESSAGE_READ")
                put("data", JSONObject().apply {
                    put("patientId", patientId)
                })
            }
            ws.send(json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao enviar recibo de leitura pelo WebSocket: ${e.message}")
        }
    }

    internal fun buildWsUrl(httpBaseUrl: String, token: String, patientId: String): String {
        val base = httpBaseUrl.trimEnd('/')
        val wsScheme = if (base.startsWith("https://")) "wss://" else "ws://"
        val hostPart = base.removePrefix("https://").removePrefix("http://")
        val encodedToken = java.net.URLEncoder.encode(token, "UTF-8")
        val encodedPatientId = java.net.URLEncoder.encode(patientId, "UTF-8")
        return "$wsScheme$hostPart/ws/family?token=$encodedToken&patientId=$encodedPatientId"
    }

    internal fun handleIncomingJson(jsonStr: String) {
        try {
            val root = JSONObject(jsonStr)
            val type = root.optString("type")
            when (type) {
                "FAMILY_MESSAGE" -> {
                    val data = root.optJSONObject("data") ?: return
                    val patientId = data.optString("patient_user_id", data.optString("patientId", data.optString("patient_id", "")))
                    val currentFilter = activePatientId
                    if (currentFilter != null && currentFilter.isNotBlank() && patientId.isNotBlank() && currentFilter != patientId) {
                        return
                    }

                    val sentAt = parseTimestamp(data, "created_at", "createdAt", "sent_at", "sentAt") ?: System.currentTimeMillis()
                    val expiresAt = parseTimestamp(data, "expires_at", "expiresAt") ?: (sentAt + FAMILY_MESSAGE_TTL_MS)
                    val deletedAt = parseTimestamp(data, "deleted_at", "deletedAt")

                    val bodyEnc = if (data.isNull("body_encrypted")) data.optString("bodyEncrypted", "") else data.optString("body_encrypted", "")
                    val rawMsgText = if (data.isNull("message_text")) data.optString("messageText", "") else data.optString("message_text", "")
                    val finalMsgText = bodyEnc.ifBlank { rawMsgText }

                    val msg = FamilyMessageEntity(
                        id = data.getString("id"),
                        remoteId = data.getString("id"),
                        patientUserId = if (patientId.isNotBlank()) patientId else (currentFilter ?: ""),
                        senderUserId = data.optString("sender_user_id", data.optString("senderId", data.optString("sender_id", ""))),
                        senderName = data.optString("sender_name", data.optString("senderName", "Familiar")),
                        messageText = finalMsgText,
                        iconType = data.optString("icon_type", data.optString("iconType", "LOVE")),
                        isRead = data.optBoolean("is_read", data.optBoolean("isRead", false)),
                        sentAt = sentAt,
                        pendingSync = false,
                        expiresAt = expiresAt,
                        deletedAt = deletedAt
                    )
                    activeListener?.onMessageReceived(msg)
                }
                "MESSAGE_DELETED" -> {
                    val data = root.optJSONObject("data")
                    val messageId = data?.optString("message_id", data.optString("messageId", data.optString("id", ""))) ?: root.optString("data")
                    if (messageId.isNotBlank()) {
                        activeListener?.onMessageDeleted(messageId)
                    }
                }
                "MESSAGE_READ" -> {
                    val data = root.optJSONObject("data")
                    val patientId = data?.optString("patient_id", data.optString("patientId", "")) ?: root.optString("data")
                    if (patientId.isNotBlank()) {
                        val readerId = data?.optString("reader_id", data.optString("readerId", "")) ?: ""
                        val ids = data?.optJSONArray("messageIds") ?: data?.optJSONArray("message_ids")
                        val messageIds = if (ids == null) emptyList() else (0 until ids.length()).map { ids.getString(it) }
                        if (patientId == activePatientId && readerId.isNotBlank()) {
                            activeListener?.onMessagesRead(patientId, readerId, messageIds)
                        }
                    }
                }
                "ping" -> {
                    webSocket?.send("{\"type\":\"pong\"}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao processar mensagem JSON do WebSocket: ${e.message}")
        }
    }

    internal fun parseTimestamp(obj: JSONObject, vararg keys: String): Long? {
        for (k in keys) {
            if (!obj.has(k) || obj.isNull(k)) continue
            val num = obj.optLong(k, -1L)
            if (num > 0L) {
                return if (num < 100_000_000_000L) num * 1000L else num
            }
            val str = obj.optString(k, "")
            if (str.isNotBlank()) {
                val parsed = parseIsoMillis(str)
                if (parsed != null) return parsed
            }
        }
        return null
    }

    internal fun parseIsoMillis(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try {
            java.time.Instant.parse(raw.trim()).toEpochMilli()
        } catch (_: Exception) {
            try {
                val match = Regex("""^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?(Z|[+-]\d{2}:?\d{2})?$""")
                    .find(raw.trim()) ?: return null
                val base = match.groupValues[1]
                val fraction = match.groupValues.getOrNull(2)?.take(3)?.padEnd(3, '0') ?: "000"
                val offset = match.groupValues.getOrNull(3).let { if (it == "Z" || it.isNullOrEmpty()) "+0000" else it.replace(":", "") }
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", java.util.Locale.US).apply {
                    timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                sdf.parse("$base.$fraction$offset")?.time
            } catch (_: Exception) {
                null
            }
        }
    }
}
