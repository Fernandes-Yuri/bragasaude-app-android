package br.com.bragasaude.data.remote.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AvatarAuthInterceptorTest {
    @Test fun autenticaFotoDoGatewayERenovaUmaVez() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(401))
            server.enqueue(MockResponse().setResponseCode(200))
            val client = OkHttpClient.Builder().addInterceptor(AvatarAuthInterceptor(server.url("/")) {
                if (it) "renovado" else "inicial"
            }).build()
            client.newCall(Request.Builder().url(server.url("/api/files/avatars/autor/foto.jpg")).build()).execute().use {
                assertEquals(200, it.code)
            }
            assertEquals("Bearer inicial", server.takeRequest().getHeader("Authorization"))
            assertEquals("Bearer renovado", server.takeRequest().getHeader("Authorization"))
        }
    }
    @Test fun naoEnviaCredencialParaOutraOrigemNemRedirecionamento() {
        MockWebServer().use { gateway ->
            MockWebServer().use { external ->
                gateway.start(); external.start()
                var calls = 0
                val client = OkHttpClient.Builder().addInterceptor(AvatarAuthInterceptor(gateway.url("/")) {
                    calls++; "sessao"
                }).build()
                external.enqueue(MockResponse().setResponseCode(200))
                client.newCall(Request.Builder().url(external.url("/api/files/avatars/foto.jpg")).build()).execute().close()
                assertNull(external.takeRequest().getHeader("Authorization"))
                assertEquals(0, calls)
                gateway.enqueue(MockResponse().setResponseCode(302).addHeader("Location", external.url("/foto.jpg")))
                external.enqueue(MockResponse().setResponseCode(200))
                client.newCall(Request.Builder().url(gateway.url("/api/files/avatars/foto.jpg")).build()).execute().close()
                assertEquals("Bearer sessao", gateway.takeRequest().getHeader("Authorization"))
                assertNull(external.takeRequest().getHeader("Authorization"))
            }
        }
    }
}
