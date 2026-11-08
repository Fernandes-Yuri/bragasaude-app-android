package br.com.bragasaude.data.remote.ai

import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.data.remote.service.NotificationClient
import io.mockk.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.Assert.*
import org.junit.Test

class DeviceRegistrationTest {
    @Test fun duplicateConcurrentRegistrationsAreConsolidatedButTokenAndLogoutInvalidateThem() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            repeat(5) { server.enqueue(MockResponse().setBody("{}")) }
            val auth = mockk<AuthService>()
            every { auth.getTokenBlocking(any(), any()) } returns "synthetic-fixture-token"
            val client = NotificationClient(auth, OkHttpClient()).apply { serverBaseUrl = server.url("/").toString().trimEnd('/') }
            coroutineScope { (1..10).map { async(Dispatchers.Default) { client.registerDevice("fixture", "synthetic-device-token") } }.awaitAll().forEach { assertTrue(it) } }
            assertEquals(1, server.requestCount)
            assertTrue(client.registerDevice("fixture", "synthetic-renewed-token"))
            assertEquals(2, server.requestCount)
            assertTrue(client.unregisterDeviceToken("fixture", "synthetic-renewed-token"))
            assertTrue(client.registerDevice("fixture", "synthetic-renewed-token"))
            assertEquals(4, server.requestCount)
            assertTrue(client.registerDevice("other-fixture", "synthetic-renewed-token"))
            assertEquals(5, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun failedRegistrationIsRetried() = runBlocking {
        val server = MockWebServer(); server.start()
        try {
            server.enqueue(MockResponse().setResponseCode(503)); server.enqueue(MockResponse().setBody("{}"))
            val auth = mockk<AuthService>()
            every { auth.getTokenBlocking(any(), any()) } returns "synthetic-fixture-token"
            val client = NotificationClient(auth, OkHttpClient()).apply { serverBaseUrl = server.url("/").toString().trimEnd('/') }
            assertFalse(client.registerDevice("fixture", "synthetic-device-token"))
            assertTrue(client.registerDevice("fixture", "synthetic-device-token"))
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }
}
