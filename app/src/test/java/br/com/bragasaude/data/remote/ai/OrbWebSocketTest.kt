package br.com.bragasaude.data.remote.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.atomic.AtomicInteger

class OrbWebSocketTest {
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private lateinit var session: OrbWebSocket.Session
    private lateinit var client: OkHttpClient
    private val requests = Channel<JSONObject>(Channel.UNLIMITED)
    private val content = """{"fala":"Olá!","acao":"CONVERSA","parametros":{}}"""

    @Before fun setup() { server = MockWebServer(); server.start(); scope = CoroutineScope(SupervisorJob() + Dispatchers.Default); client = OkHttpClient() }
    @After fun teardown() {
        if (::session.isInitialized) session.close()
        scope.cancel()
        try { server.close() } catch (_: Exception) {}
        if (::client.isInitialized) {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }
    private fun connect() {
        session = OrbWebSocket.Session(scope, server.url("/").toString().trimEnd('/'), { "valid" },
            client, retryDelay = 10, heartbeatMillis = 1000)
    }
    private fun response(complete: Boolean = true) = MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            val req = JSONObject(text)
            requests.trySend(req)
            val id = req.optString("id")
            when (req.optString("type")) {
                "chat" -> {
                    webSocket.send(JSONObject().put("type", "typing").put("id", id).toString())
                    if (complete) {
                        webSocket.send(JSONObject().put("type", "chunk").put("id", id).put("content", content).toString())
                        webSocket.send(JSONObject().put("type", "done").put("id", id).put("fullContent", content).toString())
                    }
                }
                "cancel" -> webSocket.send(JSONObject().put("type", "cancelled").put("id", id).toString())
                "ping" -> webSocket.send("""{"type":"pong"}""")
            }
        }
    })
    @Test fun test_connect_success(): Unit = runBlocking {
        server.enqueue(response())
        connect()
        withTimeout(5000) { session.state.first { it == OrbConnectionState.CONNECTED } }
        assertEquals("Bearer valid", server.takeRequest().getHeader("Authorization"))
        Unit
    }
    @Test fun test_connect_invalidToken_fails(): Unit = runBlocking {
        repeat(6) { server.enqueue(MockResponse().setResponseCode(403)) }
        connect()
        withTimeout(5000) { session.state.first { it == OrbConnectionState.FAILED } }
        Unit
    }
    @Test fun test_sendMessage_receivesChunks(): Unit = runBlocking {
        server.enqueue(response())
        connect()
        var partial = ""
        val reply = withTimeout(5000) { session.chat(listOf("user" to "Oi")) { partial = it } }
        assertEquals("Olá!", partial)
        assertEquals(content, reply.content)
        withTimeout(5000) { session.chat(listOf("user" to "Outra")) {} }
        assertEquals(1, server.requestCount)
        Unit
    }
    @Test fun test_cancel_sendsCancelMessage(): Unit = runBlocking {
        server.enqueue(response(false))
        connect()
        val generation = launch { session.chat(listOf("user" to "Oi")) {} }
        withTimeout(5000) { while (requests.receive().optString("type") != "chat") {} }
        generation.cancelAndJoin()
        val cancel = withTimeout(5000) { requests.receive() }
        assertEquals("cancel", cancel.getString("type"))
        assertEquals(OrbConnectionState.CONNECTED, session.state.value)
        Unit
    }
    @Test fun test_reconnect_afterDisconnect(): Unit = runBlocking {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { webSocket.close(1001, "restart") }
        }))
        server.enqueue(response())
        connect()
        withTimeout(5000) { while (server.requestCount < 2 || session.state.value != OrbConnectionState.CONNECTED) delay(10) }
        assertEquals(content, withTimeout(5000) { session.chat(listOf("user" to "Oi")) {} }.content)
        Unit
    }
}
