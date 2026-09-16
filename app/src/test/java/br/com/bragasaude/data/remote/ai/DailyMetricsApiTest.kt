package br.com.bragasaude.data.remote.ai

import br.com.bragasaude.data.remote.api.BragaApiClient
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.*
import org.junit.Assert.*

class DailyMetricsApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: BragaApiClient

    @Before fun setup() {
        mockkStatic(FirebaseAuth::class)
        val auth = mockk<FirebaseAuth>()
        every { FirebaseAuth.getInstance() } returns auth
        every { auth.currentUser } returns null
        server = MockWebServer()
        server.start()
        api = BragaApiClient(mockk(relaxed = true))
        api.baseUrl = server.url("/").toString().trimEnd('/')
    }

    @After fun cleanup() {
        server.shutdown()
        unmockkStatic(FirebaseAuth::class)
    }

    @Test fun testGetDailyMetricsParsesCorrectly() = runBlocking {
        val json = """
            [
              {
                "id": "1",
                "user_id": "user123",
                "date": "2026-09-10",
                "steps": 1125,
                "distance_meters": 825.9,
                "distance_gps_meters": 0.0,
                "distance_steps_meters": 825.9,
                "distance_final_meters": 825.9,
                "reliability_score": 0.9,
                "calories_burned": 2061.3,
                "active_minutes": 2
              }
            ]
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(json))
        val metrics = api.getDailyMetrics("user123")
        assertEquals(1, metrics.size)
        val m = metrics.first()
        assertEquals("user123", m.userId)
        assertEquals("2026-09-10", m.date)
        assertEquals(1125, m.steps)
        assertEquals(2061.3f, m.caloriesBurned, 0.1f)
        assertEquals(2, m.activeMinutes)
        assertFalse(m.pendingSync)
    }

    @Test fun testGetDailyMetricsEmptyOnError() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500))
        val metrics = api.getDailyMetrics("user123")
        assertTrue(metrics.isEmpty())
    }
}
