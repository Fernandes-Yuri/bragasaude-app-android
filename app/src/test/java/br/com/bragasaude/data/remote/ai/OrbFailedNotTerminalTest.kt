package br.com.bragasaude.data.remote.ai

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.*
import org.junit.Assert.*
import org.junit.Test

/**
 * APP-7: FAILED deixou de ser um estado terminal. A sessão anuncia FAILED
 * (para a UI mostrar a falha) e em seguida retoma o ciclo de reconexão com
 * backoff, em vez de encerrar a coroutine de conexão.
 */
class OrbFailedNotTerminalTest {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test fun failed_isFollowedByReconnectionAttempt() = runTest {
        val client = OkHttpClient()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var connects = 0
        val server = okhttp3.mockwebserver.MockWebServer().apply { start() }
        // Primeira conexão: 401 (auth expirada) → falha.
        server.enqueue(okhttp3.mockwebserver.MockResponse().setResponseCode(401))
        // Segunda: upgrade aceito → a sessão se reconecta sozinha.
        server.enqueue(okhttp3.mockwebserver.MockResponse().withWebSocketUpgrade(object : WebSocketListener() {}))

        val session = OrbWebSocket.Session(scope, server.url("/").toString().trimEnd('/'), { "valid" },
            client, retryDelay = 1, heartbeatMillis = 60000)

        try {
            // A sessão passa por FAILED (anunciado) e depois volta a tentar.
            withTimeout(5000) { session.state.first { it == OrbConnectionState.FAILED } }
            // A reconexão efetiva acontece: volta a conectar.
            withTimeout(5000) { session.state.first { it == OrbConnectionState.CONNECTED } }
        } finally {
            session.close()
            scope.cancel()
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            server.close()
        }
    }
}
