package br.com.bragasaude.data.remote.ai

import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.ProfileLookup
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
    @Before fun setup() {
        mockkStatic(FirebaseAuth::class)
        val auth = mockk<FirebaseAuth>()
        every { FirebaseAuth.getInstance() } returns auth
        every { auth.currentUser } returns null
        server = MockWebServer()
        server.start()
        // AUD-AN40: baseUrl agora imutavel — injeta a URL do mock no construtor.
        api = BragaApiClient(mockk(relaxed = true), server.url("/").toString().trimEnd('/'))
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
        val deadApi = BragaApiClient(mockk(relaxed = true), closedServer.url("/").toString().trimEnd('/'))
        closedServer.shutdown()
        assertTrue(deadApi.getProfileLookup("owner") is ProfileLookup.Unavailable)
    }
}
