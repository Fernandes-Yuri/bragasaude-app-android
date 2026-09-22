package br.com.bragasaude.data.remote.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

class OrbRejectedException(message: String) : IOException(message)
class OrbAuthExpiredException : IOException("Sessão expirada")
enum class OrbConnectionState { CONNECTING, CONNECTED, RECONNECTING, FAILED, CLOSED }
data class OrbReply(val content: String, val metrics: String? = null)

@Singleton
class OrbWebSocket @Inject constructor() {
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(10, TimeUnit.SECONDS).build()

    fun openSession(scope: CoroutineScope, baseUrl: String,
                    tokenProvider: suspend (Boolean) -> String): Session =
        Session(scope, baseUrl, tokenProvider, client, maxFailures = 12)

    fun openSession(scope: CoroutineScope, baseUrl: String,
                    maxFailures: Int,
                    tokenProvider: suspend (Boolean) -> String): Session =
        Session(scope, baseUrl, tokenProvider, client, maxFailures = maxFailures)

    // Compatibility for the voice flow outside the dedicated chat.
    suspend fun chat(baseUrl: String, token: String, speech: String, onPartial: (String) -> Unit,
                     history: List<Pair<String, String>> = emptyList()): String = coroutineScope {
        val session = openSession(this, baseUrl) { token }
        try {
            session.chat(history.ifEmpty { listOf("user" to speech) },
                         onPartial = onPartial).content
        } finally { session.close() }
    }

    class Session internal constructor(parent: CoroutineScope, private val baseUrl: String,
                                      private val tokenProvider: suspend (Boolean) -> String,
                                      private val client: OkHttpClient,
                                      private val retryDelay: Long = 1000,
                                      private val heartbeatMillis: Long = 10000,
                                      private val maxFailures: Int = 5) {
        private val job = SupervisorJob(parent.coroutineContext[Job])
        private val scope = CoroutineScope(parent.coroutineContext + job)
        private val mutableState = MutableStateFlow(OrbConnectionState.CONNECTING)
        val state: StateFlow<OrbConnectionState> = mutableState.asStateFlow()
        private val pending = ConcurrentHashMap<String, Channel<JSONObject>>()
        private val requestLock = Mutex()
        @Volatile private var socket: WebSocket? = null
        @Volatile private var connectedToken: String? = null
        private var connectionJob: Job? = null
        @Volatile private var immediateRefresh = false
        @Volatile private var lastFailure: Exception? = null
        init { retry() }

        fun retry() {
            if (!job.isActive || connectionJob?.isActive == true) return
            connectionJob = scope.launch {
                var failures = 0
                // APP-7: o laço só termina quando a sessão é explicitamente
                // encerrada (close()) ou cancelada. Esgotar maxFailures sinaliza
                // FAILED para a UI, mas a reconexão continua — FAILED deixa de
                // ser terminal e passa a ter o mesmo retry+backoff dos outros
                // estados (RECONNECTING).
                while (isActive) {
                    if (failures > 0) {
                        mutableState.value =
                            if (failures > maxFailures) OrbConnectionState.FAILED
                            else OrbConnectionState.RECONNECTING
                        val baseDelay = (retryDelay * (1L shl (failures - 1).coerceAtMost(29))).coerceAtMost(30000)
                        val jitter = if (retryDelay > 50) (baseDelay * 0.15 * Math.random()).toLong() else 0L
                        delay(baseDelay + jitter)
                    }
                    val incoming = Channel<String>(512, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
                    val opened = CompletableDeferred<Unit>()
                    val started = System.nanoTime()
                    var heartbeat: Job? = null
                    try {
                        mutableState.value = if (failures == 0) OrbConnectionState.CONNECTING else OrbConnectionState.RECONNECTING
                        val token = withTimeout(10000) { tokenProvider(failures > 0 || immediateRefresh) }
                        immediateRefresh = false
                        val request = Request.Builder().url(baseUrl.trimEnd('/') + "/ws/orb")
                            .header("Authorization", "Bearer $token")
                            .build()
                        val ws = client.newWebSocket(request, object : WebSocketListener() {
                            override fun onOpen(webSocket: WebSocket, response: Response) { opened.complete(Unit) }
                            override fun onMessage(webSocket: WebSocket, text: String) {
                                if (!incoming.trySend(text).isSuccess) incoming.close(IOException("Fluxo muito rápido."))
                            }
                            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                                webSocket.close(code, reason)
                                incoming.close(if (code == 4001) OrbAuthExpiredException() else IOException("Conexão encerrada."))
                            }
                            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                                val cause = if (response?.code in listOf(401, 403)) OrbAuthExpiredException() else t
                                opened.completeExceptionally(cause)
                                incoming.close(cause)
                            }
                        })
                        socket = ws
                        withTimeout(7000) { opened.await() }
                        connectedToken = token
                        lastFailure = null
                        mutableState.value = OrbConnectionState.CONNECTED
                        val lastReceived = AtomicLong(System.nanoTime())
                        heartbeat = launch {
                            while (isActive) {
                                delay(heartbeatMillis)
                                if ((System.nanoTime() - lastReceived.get()) / 1_000_000 >= heartbeatMillis * 3) {
                                    incoming.close(IOException("Sem resposta aos heartbeats."))
                                    ws.cancel()
                                    break
                                }
                                ws.send(JSONObject().put("type", "ping").toString())
                            }
                        }
                        for (raw in incoming) {
                            lastReceived.set(System.nanoTime())
                            val event = JSONObject(raw)
                            when (event.optString("type")) {
                                "heartbeat" -> ws.send(JSONObject().put("type", "pong").put("id", event.opt("id")).toString())
                                "pong" -> Unit
                                else -> pending[event.optString("id")]?.let { channel ->
                                    channel.trySend(event)
                                }
                            }
                        }
                        throw IOException("Conexão encerrada.")
                    } catch (e: TimeoutCancellationException) {
                        lastFailure = IOException("Tempo de conexão esgotado.")
                        pending.values.forEach { it.close(java.io.IOException("Tempo de conexão esgotado.")) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        lastFailure = e
                        pending.values.forEach { it.close(e) }
                    } finally {
                        heartbeat?.cancel()
                        socket?.cancel()
                        socket = null
                        connectedToken = null
                        incoming.cancel()
                        if (job.isActive) mutableState.value = OrbConnectionState.RECONNECTING
                    }
                    failures = if (immediateRefresh) 0 else if ((System.nanoTime() - started) > 30_000_000_000) 1 else failures + 1
                }
                if (job.isActive) mutableState.value = OrbConnectionState.FAILED
            }
        }

        suspend fun chat(messages: List<Pair<String, String>>,
                         actingAs: String? = null, patientId: String? = null,
                         onPartial: (String) -> Unit): OrbReply {
            check(requestLock.tryLock()) { "Uma resposta já está em andamento." }
            try {
                for (authAttempt in 0..1) {
                    try { return request(messages, actingAs, patientId, onPartial) }
                    catch (e: OrbAuthExpiredException) {
                        if (authAttempt == 1) throw OrbRejectedException("Entre novamente para conversar.")
                        immediateRefresh = true
                        mutableState.value = OrbConnectionState.RECONNECTING
                        socket?.cancel()
                    }
                }
                error("Sessão indisponível")
            } finally { requestLock.unlock() }
        }

        /**
         * Agente B1: envia recibo de executor e aguarda a fala de
         * fechamento montada pelo servidor. Retorna null em falha
         * (melhor-esforço: a navegação local já valeu).
         */
        suspend fun receipt(action: String, params: JSONObject, idempotencyKey: String): String? {
            if (state.value != OrbConnectionState.CONNECTED) return null
            val id = UUID.randomUUID().toString()
            val events = Channel<JSONObject>(8)
            pending[id] = events
            try {
                val payload = JSONObject().put("type", "receipt").put("id", id)
                    .put("action", action).put("params", params)
                    .put("idempotencyKey", idempotencyKey)
                if (socket?.send(payload.toString()) != true) return null
                val ack = withTimeoutOrNull(15000) { events.receive() } ?: return null
                if (ack.optString("type") != "receipt_ack") return null
                return ack.optString("closing").takeIf { it.isNotBlank() }
            } catch (_: Exception) {
                return null
            } finally {
                pending.remove(id)
                events.cancel()
            }
        }

        private suspend fun request(messages: List<Pair<String, String>>,
                                    actingAs: String? = null, patientId: String? = null,
                                    onPartial: (String) -> Unit): OrbReply {
            val freshToken = tokenProvider(false)
            if (connectedToken != null && connectedToken != freshToken) {
                immediateRefresh = true
                mutableState.value = OrbConnectionState.RECONNECTING
                socket?.cancel()
            }
            withTimeout(70000) {
                state.first { it == OrbConnectionState.CONNECTED || it == OrbConnectionState.FAILED || it == OrbConnectionState.CLOSED }
            }
            if (state.value != OrbConnectionState.CONNECTED) {
                if (lastFailure is OrbAuthExpiredException || lastFailure is OrbRejectedException)
                    throw OrbRejectedException("Entre novamente para conversar.")
                throw IOException("Sem conexão. Toque em Tentar novamente.")
            }
            val id = UUID.randomUUID().toString()
            val events = Channel<JSONObject>(512, onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST)
            pending[id] = events
            var terminal = false
            val started = System.nanoTime()
            var firstMs: Long? = null
            try {
                val history = JSONArray()
                messages.takeLast(30).forEach { (role, content) -> history.put(JSONObject().put("role", role).put("content", content)) }
                val payload = JSONObject().put("type", "chat").put("id", id).put("messages", history)
                    .put("max_tokens", 512).put("temperature", 0.1)
                // D50/D51: escopo de papel — modo cuidador agenda consulta por voz
                if (actingAs == "caregiver" && !patientId.isNullOrBlank()) {
                    payload.put("acting_as", "caregiver").put("patient_id", patientId)
                }
                if (socket?.send(payload.toString()) != true) throw IOException("Falha ao enviar.")
                var full = ""
                while (true) {
                    val event = withTimeout(40000) { events.receive() }
                    when (event.getString("type")) {
                        "chunk" -> {
                            if (firstMs == null) firstMs = (System.nanoTime() - started) / 1_000_000
                            full += event.getString("content")
                            if (full.length > 64000) throw IOException("Resposta muito longa.")
                            onPartial(OrbSpeechPreview.extract(full))
                        }
                        "done" -> {
                            terminal = true
                            // AUD-AN21: antes o JSON do "done" era parses sem
                            // try/catch. Se o gateway enviasse um done
                            // malformado (ou o "fullContent" vazio/parcial por
                            // timeout upstream), JSONException escapava e
                            // derrubava a conversa inteira. Agora tratado.
                            try {
                                val content = event.getString("fullContent")
                                if (content.isBlank()) throw IOException("Resposta vazia do servidor.")
                                val parsed = JSONObject(content)
                                require(parsed.getString("fala").isNotBlank())
                                parsed.getString("acao")
                                parsed.getJSONObject("parametros")
                                val metrics = event.optJSONObject("metrics")
                                val total = (System.nanoTime() - started) / 1_000_000
                                android.util.Log.d("OrbWebSocket", "time_to_first_chunk=${firstMs}ms total_generation_time=${total}ms")
                                val tokens = metrics?.takeUnless { it.isNull("tokens") }?.optInt("tokens")
                                val label = "Resposta em %.1fs".format(total / 1000.0) + (tokens?.let { " ($it tokens)" } ?: "")
                                return OrbReply(content, label)
                            } catch (je: org.json.JSONException) {
                                throw IOException("Resposta do servidor malformada.", je)
                            }
                        }
                        "cancelled" -> { terminal = true; throw CancellationException("Geração cancelada") }
                        "error" -> {
                            terminal = true
                            val message = event.optString("message", "Não foi possível concluir.")
                            when (event.optString("code")) {
                                "AUTH_EXPIRED" -> throw OrbAuthExpiredException()
                                "SAFETY", "FORBIDDEN", "INVALID_REQUEST", "BUSY" -> throw OrbRejectedException(message)
                                else -> throw IOException(message)
                            }
                        }
                    }
                }
            } finally {
                if (!terminal) withContext(NonCancellable) {
                    socket?.send(JSONObject().put("type", "cancel").put("id", id).toString())
                    val acknowledged = withTimeoutOrNull(2000) {
                        try { while (events.receive().optString("type") != "cancelled") { }; true }
                        catch (_: Exception) { false }
                    } ?: false
                    if (!acknowledged) socket?.cancel()
                }
                pending.remove(id)
                events.cancel()
            }
        }

        fun close() {
            mutableState.value = OrbConnectionState.CLOSED
            pending.values.forEach { it.close(CancellationException("Tela fechada")) }
            socket?.close(1000, "Tela fechada")
            job.cancel()
        }
    }
}

/** Decode only complete characters in the JSON fala string, never action metadata. */
internal object OrbSpeechPreview {
    fun extract(json: String): String {
        val start = Regex("\"fala\"\\s*:\\s*\"").find(json)?.range?.last?.plus(1) ?: return ""
        val out = StringBuilder()
        var i = start
        while (i < json.length) {
            val c = json[i++]
            if (c == '"') break
            if (c != '\\') { out.append(c); continue }
            if (i >= json.length) break
            when (val escaped = json[i++]) {
                '"', '\\', '/' -> out.append(escaped)
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                'u' -> {
                    if (i + 4 > json.length) break
                    val code = json.substring(i, i + 4).toIntOrNull(16) ?: break
                    out.append(code.toChar())
                    i += 4
                }
                else -> break
            }
        }
        if (out.isNotEmpty() && out.last().isHighSurrogate()) out.deleteCharAt(out.lastIndex)
        return out.toString()
    }
}
