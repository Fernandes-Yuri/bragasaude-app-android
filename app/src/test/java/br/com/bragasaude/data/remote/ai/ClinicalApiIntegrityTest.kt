package br.com.bragasaude.data.remote.ai

import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.data.local.security.FamilyE2eeManager
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.auth.AuthService
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*

class ClinicalApiIntegrityTest {
    private lateinit var server: MockWebServer
    private lateinit var api: BragaApiClient
    private val id = "00000000-0000-0000-0000-000000000123"
    @Before fun setup() {
        mockkStatic(FirebaseAuth::class)
        val auth = mockk<FirebaseAuth>()
        every { FirebaseAuth.getInstance() } returns auth
        every { auth.currentUser } returns null
        val tokens = mockk<AuthService>()
        every { tokens.getTokenBlocking(any(), any()) } returns "isolated-test"
        server = MockWebServer().also { it.start() }
        api = BragaApiClient(mockk(relaxed = true), server.url("/").toString().trimEnd('/'), tokens, OkHttpClient(), FamilyE2eeManager())
    }
    @After fun close() { server.shutdown(); unmockkStatic(FirebaseAuth::class) }

    @Test fun uploadPreservesStableIdAndRequiresPersistedResponse() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"success","id":"$id"}"""))
        assertEquals(id, api.syncVitalSign(VitalSignEntity(remoteId = id, userId = "patient", hydrationMl = 250)))
        val request = server.takeRequest()
        val json = JSONObject(request.body.readUtf8())
        assertEquals(id, json.getString("id"))
        assertEquals(250, json.getInt("hydrationMl"))
    }
    @Test fun offlineModeAndWrongIdentityNeverAcknowledgeUpload() = runBlocking {
        val vital = VitalSignEntity(remoteId = id, userId = "patient", hydrationMl = 250)
        for (body in listOf("""{"status":"offline_mode","id":"local"}""",
            """{"status":"success","id":"00000000-0000-0000-0000-000000000999"}""")) {
            server.enqueue(MockResponse().setBody(body))
            assertNull(api.syncVitalSign(vital))
        }
    }
    @Test fun undoPendingCannotBeSentAsAnotherReading() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"status":"success","id":"$id"}"""))
        assertEquals(id, api.syncVitalSign(VitalSignEntity(remoteId = id, userId = "patient", hydrationUndoPending = true, pendingSync = true)))
        assertEquals("/api/vitals/$id/undo-hydration", server.takeRequest().path)
    }
    @Test fun failedHistoryReadPreservesCursorRatherThanReturningEmptyData() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        assertNull(api.getVitalChanges("patient", 41))
        assertEquals("/api/vitals/patient/changes?after=41", server.takeRequest().path)
    }
    @Test fun changesKeepClinicalTimestampAndDeletion() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"changes":[{"revision":42,"vital":{
            "id":"$id","user_id":"patient","hydration_ml":null,
            "measured_at":"2026-11-01T08:00:00+00:00","deleted_at":"2026-11-07T18:00:00+00:00"}}],
            "nextCursor":42,"hasMore":false}"""))
        val page = api.getVitalChanges("patient", 41)!!
        assertEquals(42L, page.nextCursor)
        assertNull(page.records.single().hydrationMl)
        assertNotNull(page.records.single().deletedAt)
        assertTrue(page.records.single().measuredAt.time < page.records.single().deletedAt!!)
    }
}
