package br.com.bragasaude.data.remote.service

import br.com.bragasaude.data.remote.auth.AuthService
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class FcmTokenConsolidationTest {

    @Test
    fun `concurrent profile emissions share a single token fetch and gateway registration`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            repeat(5) { server.enqueue(MockResponse().setBody("{}")) }
            val auth = mockk<AuthService>()
            every { auth.getTokenBlocking(any(), any()) } returns "synthetic-fixture-jwt"

            val tokenFetchCount = AtomicInteger(0)
            val nowMs = AtomicLong(1_000_000L)
            val tokenProvider = FcmTokenProvider {
                tokenFetchCount.incrementAndGet()
                "synthetic-fcm-device-token-1"
            }

            val client = NotificationClient(
                authService = auth,
                httpClient = OkHttpClient(),
                tokenProvider = tokenProvider,
                clock = { nowMs.get() }
            ).apply {
                serverBaseUrl = server.url("/").toString().trimEnd('/')
            }

            val results = coroutineScope {
                (1..10).map {
                    async(Dispatchers.Default) {
                        client.ensureSessionTokenRegistered(
                            userId = "mariana-uid",
                            role = "USER",
                            name = "Mariana"
                        )
                    }
                }.awaitAll()
            }

            results.forEach { assertTrue(it) }
            assertEquals("A busca de token FCM deve ocorrer apenas 1 vez por sessão", 1, tokenFetchCount.get())
            assertEquals("O registro no gateway deve ocorrer apenas 1 vez para emissões equivalentes", 1, server.requestCount)

            // Emissões equivalentes subsequentes dentro do TTL não chamam fetchToken nem HTTP
            repeat(5) {
                assertTrue(
                    client.ensureSessionTokenRegistered(
                        userId = "mariana-uid",
                        role = "USER",
                        name = "Mariana"
                    )
                )
            }
            assertEquals(1, tokenFetchCount.get())
            assertEquals(1, server.requestCount)

            // Mudança de papel/nome reutiliza o token em cache sem nova chamada ao FirebaseMessaging
            assertTrue(
                client.ensureSessionTokenRegistered(
                    userId = "mariana-uid",
                    role = "CAREGIVER",
                    name = "Mariana Cuidadora"
                )
            )
            assertEquals("Mudança de papel deve reutilizar o token em cache", 1, tokenFetchCount.get())
            assertEquals(2, server.requestCount)
            assertNull(client.lastDiagnostic.value)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `restricted environment failure records safe diagnostic enforces backoff and retries after window`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("{}"))
            val auth = mockk<AuthService>()
            every { auth.getTokenBlocking(any(), any()) } returns "synthetic-fixture-jwt"

            val tokenFetchCount = AtomicInteger(0)
            val nowMs = AtomicLong(10_000L)
            var shouldFail = true

            val tokenProvider = FcmTokenProvider {
                tokenFetchCount.incrementAndGet()
                if (shouldFail) {
                    throw RuntimeException(
                        "java.io.IOException: FIS_AUTH_ERROR",
                        IOException("SERVICE_NOT_AVAILABLE")
                    )
                }
                "synthetic-fcm-recovered-token"
            }

            val client = NotificationClient(
                authService = auth,
                httpClient = OkHttpClient(),
                tokenProvider = tokenProvider,
                clock = { nowMs.get() }
            ).apply {
                serverBaseUrl = server.url("/").toString().trimEnd('/')
            }

            assertFalse(
                client.ensureSessionTokenRegistered(
                    userId = "secret-patient-uid-123",
                    role = "USER",
                    name = "Mariana"
                )
            )
            assertEquals(1, tokenFetchCount.get())
            assertEquals(0, server.requestCount)

            val diag = client.lastDiagnostic.value
            assertNotNull("Diagnóstico estruturado deve ser registrado", diag)
            assertEquals(FcmFailureReason.FIS_OR_INSTANCE_ID_RESTRICTED, diag!!.reason)
            assertTrue("Falha de FIS/Play Services deve ser classificada como externa ao app", diag.externalEnvironmentFailure)
            assertEquals("SERVICE_NOT_AVAILABLE", diag.errorCode)
            assertEquals(NotificationClient.BASE_BACKOFF_MS, diag.nextRetryDelayMs)

            val summary = diag.toSafeLogSummary()
            assertFalse("Log seguro não pode expor UID em claro", summary.contains("secret-patient-uid-123"))
            assertFalse("Log seguro não pode expor nome clínico", summary.contains("Mariana"))

            // Dentro da janela de backoff, novas emissões do perfil são suprimidas sem repetir fetchToken()
            nowMs.addAndGet(5_000L)
            repeat(6) {
                assertFalse(
                    client.ensureSessionTokenRegistered(
                        userId = "secret-patient-uid-123",
                        role = "USER",
                        name = "Mariana"
                    )
                )
            }
            assertEquals("Nenhuma nova tentativa deve ocorrer durante a janela de backoff", 1, tokenFetchCount.get())

            // Após o término do backoff, a retomada controlada funciona normalmente
            shouldFail = false
            nowMs.addAndGet(NotificationClient.BASE_BACKOFF_MS + 1_000L)
            assertTrue(
                client.ensureSessionTokenRegistered(
                    userId = "secret-patient-uid-123",
                    role = "USER",
                    name = "Mariana"
                )
            )
            assertEquals(2, tokenFetchCount.get())
            assertEquals(1, server.requestCount)
            assertNull("Diagnóstico de falha é limpo após registro bem-sucedido", client.lastDiagnostic.value)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `account switch during in flight token fetch prevents previous user registration`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            repeat(3) { server.enqueue(MockResponse().setBody("{}")) }
            val auth = mockk<AuthService>()
            every { auth.getTokenBlocking(any(), any()) } returns "synthetic-fixture-jwt"

            val activeUser = AtomicReference("mariana-uid")
            val tokenGate = CompletableDeferred<String>()
            val fetchCount = AtomicInteger(0)

            val tokenProvider = FcmTokenProvider {
                val callIndex = fetchCount.incrementAndGet()
                if (callIndex == 1) {
                    tokenGate.await()
                } else {
                    "synthetic-token-fred"
                }
            }

            val client = NotificationClient(
                authService = auth,
                httpClient = OkHttpClient(),
                tokenProvider = tokenProvider
            ).apply {
                serverBaseUrl = server.url("/").toString().trimEnd('/')
            }

            val marianaJob = async(Dispatchers.Default) {
                client.ensureSessionTokenRegistered(
                    userId = "mariana-uid",
                    role = "USER",
                    name = "Mariana",
                    currentUserIdProvider = { activeUser.get() }
                )
            }

            // Aguarda a primeira tentativa entrar em voo
            while (fetchCount.get() == 0) {
                kotlinx.coroutines.yield()
            }

            // Troca de conta antes do token da Mariana retornar
            activeUser.set("fred-uid")
            client.onSessionChanged("fred-uid")
            tokenGate.complete("synthetic-token-mariana")

            assertFalse("Tentativa da conta anterior deve ser descartada na troca de sessão", marianaJob.await())
            assertEquals("Nenhum POST deve ser feito para a sessão encerrada", 0, server.requestCount)

            // Agora a nova sessão (Fred) registra normalmente
            assertTrue(
                client.ensureSessionTokenRegistered(
                    userId = "fred-uid",
                    role = "CAREGIVER",
                    name = "Fred",
                    currentUserIdProvider = { activeUser.get() }
                )
            )
            assertEquals(1, server.requestCount)
            val requestBody = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("fred-uid", requestBody.getString("userId"))
            assertEquals("synthetic-token-fred", requestBody.getString("token"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `onNewToken updates cached token for authenticated user and ignores anonymous`() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            repeat(3) { server.enqueue(MockResponse().setBody("{}")) }
            val auth = mockk<AuthService>()
            every { auth.getTokenBlocking(any(), any()) } returns "synthetic-fixture-jwt"

            val fetchCount = AtomicInteger(0)
            val client = NotificationClient(
                authService = auth,
                httpClient = OkHttpClient(),
                tokenProvider = FcmTokenProvider {
                    fetchCount.incrementAndGet()
                    "synthetic-initial-token"
                }
            ).apply {
                serverBaseUrl = server.url("/").toString().trimEnd('/')
            }

            assertFalse("onNewToken nunca deve registrar 'anonymous'", client.onNewToken("synthetic- rotated-token ", "anonymous"))
            assertEquals(0, server.requestCount)

            assertTrue(
                client.onNewToken(
                    newToken = "synthetic-rotated-token-v2",
                    userId = "mariana-uid",
                    role = "USER",
                    name = "Mariana"
                )
            )
            assertEquals("onNewToken já recebe o token novo e não precisa chamar fetchToken()", 0, fetchCount.get())
            assertEquals(1, server.requestCount)
            val recorded = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals("mariana-uid", recorded.getString("userId"))
            assertEquals("synthetic-rotated-token-v2", recorded.getString("token"))
        } finally {
            server.shutdown()
        }
    }
}
