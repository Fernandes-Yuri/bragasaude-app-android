package br.com.bragasaude.data.remote.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test

/**
 * APP-7: FAILED deixou de ser um estado terminal. A sessão anuncia FAILED
 * (para a UI mostrar a falha) e em seguida retoma o ciclo de reconexão com
 * backoff, em vez de encerrar a coroutine de conexão.
 *
 * Modelo do OrbWebSocketTest existente: runBlocking + Dispatchers.Default
 * (tempo real), evitando o congelamento de tempo virtual do runTest com
 * sockets reais.
 */
class OrbFailedNotTerminalTest {

    @Test fun failed_isFollowedByReconnectionAttempt(): Unit = runBlocking {
        val client = OkHttpClient()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = MockWebServer().apply { start() }
        // Dispatcher determinístico: falha as primeiras N tentativas com 401
        // (auth expirada) e a partir daí aceita o upgrade. Com maxFailures
        // padrão (5), a sessão anuncia FAILED quando failures o excede — e a
        // reconexão seguinte precisa encontrar o servidor "recuperado".
        var failuresServed = 0
        server.dispatcher = object : QueueDispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                // Heartbeats/pings não são HTTP; só conta tentativas de /ws/orb.
                return if (failuresServed < 7) {
                    failuresServed++
                    MockResponse().setResponseCode(401)
                } else {
                    MockResponse().withWebSocketUpgrade(object : WebSocketListener() {})
                }
            }
        }

        val session = OrbWebSocket.Session(scope, server.url("/").toString().trimEnd('/'), { "valid" },
            client, retryDelay = 10, heartbeatMillis = 60000)

        try {
            // 1. A sessão anuncia FAILED ao esgotar maxFailures.
            withTimeout(10000) { session.state.first { it == OrbConnectionState.FAILED } }
            // 2. ...mas não é terminal: a reconexão acontece e volta a conectar.
            withTimeout(10000) { session.state.first { it == OrbConnectionState.CONNECTED } }
        } finally {
            session.close()
            scope.cancel()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            try { server.close() } catch (_: Exception) {}
        }
    }
}
