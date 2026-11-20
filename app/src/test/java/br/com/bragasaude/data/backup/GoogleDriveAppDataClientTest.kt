package br.com.bragasaude.data.backup

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GoogleDriveAppDataClientTest {

    private lateinit var server: MockWebServer
    private lateinit var client: GoogleDriveAppDataClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val base = server.url("/").toString().trimEnd('/')
        client = GoogleDriveAppDataClient(
            httpClient = OkHttpClient(),
            driveApiBaseUrl = base,
            driveUploadBaseUrl = base
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `findLatestBackupFile queries appDataFolder scope and parses metadata`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"files":[{"id":"drv-file-1","name":"braga-chat-backup-v1.bragabkp","modifiedTime":"2026-11-19T18:00:00Z","size":"512"}]}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"files":[]}""")
        )

        val found = client.findLatestBackupFile("ya29.test-token")
        assertNotNull(found)
        assertEquals("drv-file-1", found?.fileId)
        assertEquals(512L, found?.sizeBytes)

        val req1 = server.takeRequest()
        assertEquals("Bearer ya29.test-token", req1.getHeader("Authorization"))
        assertTrue(req1.path!!.contains("spaces=appDataFolder"))

        assertNull(client.findLatestBackupFile("ya29.test-token"))
    }

    @Test
    fun `uploadEncryptedBackup uses POST on create and PATCH on overwrite plus downloads exact bytes`() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"drv-new","name":"braga-chat-backup-v1.bragabkp"}""")
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"drv-new","name":"braga-chat-backup-v1.bragabkp"}""")
        )
        val expectedBytes = "encrypted-payload-bytes".toByteArray(Charsets.UTF_8)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("encrypted-payload-bytes")
        )

        val created = client.uploadEncryptedBackup("ya29.tok", expectedBytes, existingFileId = null)
        assertEquals("drv-new", created.fileId)
        val postReq = server.takeRequest()
        assertEquals("POST", postReq.method)
        assertTrue(postReq.body.readUtf8().contains("appDataFolder"))

        val updated = client.uploadEncryptedBackup("ya29.tok", expectedBytes, existingFileId = "drv-new")
        assertEquals("drv-new", updated.fileId)
        val patchReq = server.takeRequest()
        assertEquals("PATCH", patchReq.method)
        assertTrue(patchReq.path!!.contains("/upload/drive/v3/files/drv-new"))

        val downloaded = client.downloadBackup("ya29.tok", "drv-new")
        assertArrayEquals(expectedBytes, downloaded)
    }

    @Test
    fun `revoked Drive token throws SecurityException`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"unauthorized"}"""))
        val err = runCatching { client.findLatestBackupFile("expired-token") }.exceptionOrNull()
        assertTrue(err is SecurityException)
    }
}
