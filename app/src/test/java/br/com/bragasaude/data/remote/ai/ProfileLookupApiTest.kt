package br.com.bragasaude.data.remote.ai

import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.ProfileLookup
import br.com.bragasaude.data.remote.auth.AuthService
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import org.junit.*
import org.junit.Assert.*

class ProfileLookupApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: BragaApiClient
    private lateinit var authService: AuthService
    @Before fun setup() {
        mockkStatic(FirebaseAuth::class)
        val auth = mockk<FirebaseAuth>()
        every { FirebaseAuth.getInstance() } returns auth
        every { auth.currentUser } returns null
        server = MockWebServer()
        server.start()
        authService = mockk()
        every { authService.getTokenBlocking(any(), any()) } returns "test-token"
        // AUD-AN40: baseUrl agora imutavel — injeta a URL do mock no construtor.
        api = BragaApiClient(mockk(relaxed = true), server.url("/").toString().trimEnd('/'), authService)
    }
    @After fun cleanup() { server.shutdown(); unmockkStatic(FirebaseAuth::class) }
    @Test fun only404MeansMissing() = runBlocking {
        for (code in listOf(404, 401, 403, 500, 503)) {
            server.enqueue(MockResponse().setResponseCode(code))
            val result = api.getProfileLookup("owner")
            if (code == 404) assertEquals(ProfileLookup.NotFound, result)
            else assertEquals(ProfileLookup.Unavailable(code), result)
        }
    }
    @Test fun validResponsePreservesExplicitCompletionFlags() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"owner","basic_profile_complete":true,"self_care_complete":true,"self_care_setup_pending":false}"""))
        val result = api.getProfileLookup("owner") as ProfileLookup.Found
        assertTrue(result.profile.basicProfileComplete)
        assertTrue(result.profile.selfCareComplete)
    }
    @Test fun malformedResponseIsUnavailable() = runBlocking {
        server.enqueue(MockResponse().setBody("{}"))
        assertTrue(api.getProfileLookup("owner") is ProfileLookup.Unavailable)
    }
    @Test fun refusedConnectionIsUnavailable() = runBlocking {
        val closedServer = MockWebServer()
        closedServer.start()
        val deadApi = BragaApiClient(mockk(relaxed = true), closedServer.url("/").toString().trimEnd('/'), authService)
        closedServer.shutdown()
        assertTrue(deadApi.getProfileLookup("owner") is ProfileLookup.Unavailable)
    }
    @Test fun legacyPointsAreNotSentWhenSyncingHealthProfile() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"success"}"""))
        val result = api.syncProfile(br.com.bragasaude.data.local.ProfileEntity(
            userId = "owner", isSmoker = true, hasDiabetes = true,
            pointsDiscipline = 100, currentXp = 200, totalXp = 300,
            currentLevel = 8, currentStreak = 7, lastXpAt = java.util.Date(1)
        ))
        assertTrue(result.success)
        val request = server.takeRequest()
        assertEquals("/api/sync/profile", request.path)
        val body = org.json.JSONObject(request.body.readUtf8())
        assertTrue(body.getBoolean("isSmoker"))
        assertTrue(body.getBoolean("hasDiabetes"))
        for (key in listOf("pointsDiscipline", "currentXp", "totalXp", "currentLevel", "currentStreak", "lastXpAt")) {
            assertFalse("Campo legado enviado: $key", body.has(key))
        }
    }
    @Test fun legacyPointsInServerResponseDoNotAffectHealthProfile() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"id":"owner","is_smoker":true,"has_diabetes":true,"current_xp":500,"current_level":8,"points_discipline":100,"basic_profile_complete":true,"self_care_complete":true,"self_care_setup_pending":false}"""))
        val result = api.getProfileLookup("owner") as ProfileLookup.Found
        assertTrue(result.profile.isSmoker)
        assertTrue(result.profile.hasDiabetes)
        val encoded = kotlinx.serialization.json.Json.encodeToString(
            br.com.bragasaude.data.remote.model.RemoteProfile.serializer(), result.profile
        )
        val body = org.json.JSONObject(encoded)
        for (key in listOf("points_discipline", "current_xp", "total_xp", "current_level", "current_streak", "last_xp_at")) {
            assertFalse("Campo legado exposto: $key", body.has(key))
        }
    }

}
