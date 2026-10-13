package br.com.bragasaude.data.remote.api

import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.data.remote.network.GatewayConnection
import br.com.bragasaude.data.remote.network.GatewayTlsPolicy
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.CertificatePinner
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.URL

class GatewayTransportTlsTest {
    private val root = HeldCertificate.Builder().certificateAuthority(1).build()
    private val leaf = HeldCertificate.Builder().commonName("localhost")
        .addSubjectAlternativeName("localhost").signedBy(root).build()

    private fun server(): MockWebServer = MockWebServer().apply {
        val tls = HandshakeCertificates.Builder().heldCertificate(leaf, root.certificate).build()
        useHttps(tls.sslSocketFactory(), false)
        start()
    }

    private fun client(pin: String): OkHttpClient {
        val tls = HandshakeCertificates.Builder().addTrustedCertificate(root.certificate).build()
        return GatewayTlsPolicy.configure(OkHttpClient.Builder()
            .sslSocketFactory(tls.sslSocketFactory(), tls.trustManager),
            release = true, host = "localhost", pins = listOf(pin)).build()
    }

    @Test fun canonicalMethodsRejectValidTrustedChainWithWrongPin() = runBlocking {
        val wrong = CertificatePinner.pin(HeldCertificate.Builder().build().certificate)
        server().use { server ->
            val auth = mockk<AuthService>()
            every { auth.getTokenBlocking() } returns "synthetic-session"
            val api = BragaApiClient(mockk(relaxed = true), server.url("/").toString().trimEnd('/'), auth, client(wrong))
            for (method in listOf("GET", "POST", "PATCH", "DELETE")) {
                val result = api.doseRequest(method, "/api/synthetic", if (method in listOf("POST", "PATCH")) JSONObject().put("value", 1) else null)
                assertEquals(method, 0, result.code)
            }
            assertEquals(0, server.requestCount)
        }
    }

    @Test fun canonicalMethodsUseInjectedClientWithLeafAndBackupPins() = runBlocking {
        for (pin in listOf(CertificatePinner.pin(leaf.certificate), CertificatePinner.pin(root.certificate))) {
            server().use { server ->
                val auth = mockk<AuthService>()
                every { auth.getTokenBlocking() } returns "synthetic-session"
                val api = BragaApiClient(mockk(relaxed = true), server.url("/").toString().trimEnd('/'), auth, client(pin))
                for (method in listOf("GET", "POST", "PATCH", "DELETE")) {
                    server.enqueue(MockResponse().setBody("{\"ok\":true}"))
                    val result = api.doseRequest(method, "/api/synthetic", if (method in listOf("POST", "PATCH")) JSONObject().put("value", 1) else null)
                    assertEquals(method, 200, result.code)
                    assertEquals(method, server.takeRequest().method)
                }
            }
        }
    }

    @Test fun multipartAndPdfUseSameTlsPolicyAndCloseResponses() {
        server().use { server ->
            val client = client(CertificatePinner.pin(leaf.certificate))
            server.enqueue(MockResponse().setBody("accepted"))
            val upload = GatewayConnection(URL(server.url("/api/upload").toString()), client)
            upload.requestMethod = "POST"
            upload.setRequestProperty("Content-Type", "multipart/form-data; boundary=test")
            upload.outputStream.use { it.write("--test\r\nsynthetic\r\n--test--".toByteArray()) }
            assertEquals(200, upload.responseCode)
            assertTrue(server.takeRequest().body.readUtf8().contains("synthetic"))
            upload.disconnect()
            server.enqueue(MockResponse().setHeader("Content-Type", "application/pdf").setBody("%PDF-synthetic"))
            val download = GatewayConnection(URL(server.url("/api/report").toString()), client)
            assertEquals("application/pdf", download.contentType)
            assertTrue(download.inputStream.use { it.readBytes().decodeToString() }.startsWith("%PDF"))
            download.disconnect()
        }
    }

    @Test fun redirectsDoNotTransferAuthenticatedRequestsToAnotherOrigin() {
        server().use { server ->
            MockWebServer().use { other ->
                other.start()
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", other.url("/stolen")))
                val connection = GatewayConnection(URL(server.url("/api/private").toString()), client(CertificatePinner.pin(leaf.certificate)))
                connection.setRequestProperty("Authorization", "Bearer synthetic-session")
                connection.instanceFollowRedirects = true
                assertEquals(302, connection.responseCode)
                assertEquals(0, other.requestCount)
                connection.disconnect()
            }
        }
    }
}
