package br.com.bragasaude.data.remote.api

import br.com.bragasaude.data.local.security.FamilyE2eeManager
import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.data.remote.b2b.ActiveContextStore
import br.com.bragasaude.data.remote.network.AuthInterceptor
import br.com.bragasaude.domain.b2b.B2BContextDescriptor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Suite B2B-13: Retrocompatibilidade HTTP (v1.9.6 build 43), sanitizacao no AuthInterceptor
 * e tolerancia de contratos B2B no BragaApiClient.
 */
class BragaB2BBackwardCompatTest {

    private fun stubAuthService(): AuthService {
        val auth = mockk<AuthService>()
        every { auth.cachedTokenNow() } returns "jwt-rs256-token"
        every { auth.getTokenBlocking(any(), any()) } returns "jwt-rs256-token"
        return auth
    }

    @Test
    fun `modo PERSONAL preserva contratos da v1_9_6 sem enviar headers B2B e impede spoofing manual`() {
        val auth = stubAuthService()
        val store = ActiveContextStore()
        val client = OkHttpClient.Builder()
            .addInterceptor(AuthInterceptor(auth, store))
            .build()

        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

            // Tenta enviar headers B2B manualmente enquanto em PERSONAL: interceptor deve remover ambos
            val req = Request.Builder()
                .url(server.url("/api/vitals/patient-1"))
                .header(ActiveContextStore.HEADER_PROGRAM_ID, "spoofed-program")
                .header(ActiveContextStore.HEADER_CONTEXT_MODE, "PARTNER_OWNER")
                .build()

            client.newCall(req).execute().close()
            val recorded = server.takeRequest()
            assertEquals("Bearer jwt-rs256-token", recorded.getHeader("Authorization"))
            assertNull(recorded.getHeader(ActiveContextStore.HEADER_PROGRAM_ID))
            assertNull(recorded.getHeader(ActiveContextStore.HEADER_CONTEXT_MODE))

            // Agora ativa contexto autorizado para o mesmo usuario e depois retorna ao PERSONAL
            val partnerCtx = B2BContextDescriptor(
                mode = "PARTNER_PARTICIPANT",
                userId = "patient-1",
                programId = "11111111-1111-1111-1111-111111111111",
                programName = "Clinica Piloto",
                approvalStatus = "APPROVED",
                sharedCategories = listOf("steps")
            )
            store.updateAvailableContexts("patient-1", listOf(partnerCtx))
            assertTrue(store.selectContext("patient-1", partnerCtx))

            server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))
            client.newCall(Request.Builder().url(server.url("/api/b2b/contexts")).build()).execute().close()
            val partnerReq = server.takeRequest()
            assertEquals("11111111-1111-1111-1111-111111111111", partnerReq.getHeader(ActiveContextStore.HEADER_PROGRAM_ID))
            assertEquals("PARTNER_PARTICIPANT", partnerReq.getHeader(ActiveContextStore.HEADER_CONTEXT_MODE))

            store.resetToPersonal()
            server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
            client.newCall(Request.Builder().url(server.url("/api/metrics/daily/patient-1")).build()).execute().close()
            val restoredReq = server.takeRequest()
            assertNull(restoredReq.getHeader(ActiveContextStore.HEADER_PROGRAM_ID))
            assertNull(restoredReq.getHeader(ActiveContextStore.HEADER_CONTEXT_MODE))
        }
    }

    @Test
    fun `BragaApiClient desserializa participantes e resumo granular com campos extras futuros`() = runBlocking {
        val auth = stubAuthService()
        MockWebServer().use { server ->
            server.start()
            val api = BragaApiClient(
                mockk(relaxed = true),
                server.url("/").toString().trimEnd('/'),
                auth,
                OkHttpClient(),
                FamilyE2eeManager()
            )

            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "futureEnvelopeField": true,
                      "program": {
                        "programId": "11111111-1111-1111-1111-111111111111",
                        "ownerUserId": "owner-a",
                        "companyName": "Clinica Horizonte",
                        "programName": "Cuidado Cardio",
                        "primaryColor": "#0F4C81",
                        "approvalStatus": "APPROVED",
                        "inviteCode": "CARD-4F9A",
                        "invitePaused": false,
                        "seatLimit": 15,
                        "activeParticipants": 1,
                        "canPromote": true
                      },
                      "participants": [
                        {
                          "patientUserId": "patient-1",
                          "displayName": "Maria Silva",
                          "sharedCategories": ["steps", "hydration"],
                          "consentedAt": "2026-11-19T01:00:00Z",
                          "extraField": "ignored"
                        }
                      ],
                      "nextCursor": "2026-11-19T01:00:00Z",
                      "hasMore": false
                    }
                    """.trimIndent()
                )
            )

            val page = api.getMyPartnerParticipants(cursor = null, limit = 20)
            assertNotNull(page)
            assertEquals(1, page?.participants?.size)
            assertEquals("Maria Silva", page?.participants?.first()?.displayName)
            assertEquals(listOf("steps", "hydration"), page?.participants?.first()?.sharedCategories)
            val listReq = server.takeRequest()
            assertTrue(listReq.path.orEmpty().startsWith("/api/b2b/program/me/participants"))

            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "summary": {
                        "programId": "11111111-1111-1111-1111-111111111111",
                        "programName": "Cuidado Cardio",
                        "companyName": "Clinica Horizonte",
                        "patientUserId": "patient-1",
                        "participantName": "Maria Silva",
                        "periodDays": 30,
                        "sharedCategories": ["steps"],
                        "stepsSection": {
                          "source": "Braga Saúde • Passos",
                          "averageSteps": 5400,
                          "daysRecorded": 10,
                          "days": []
                        },
                        "hydrationSection": null,
                        "medicationsSection": null,
                        "authorizedRecordsSection": null
                      }
                    }
                    """.trimIndent()
                )
            )

            val summary = api.getPartnerParticipantSummary("patient-1", days = 30)
            assertNotNull(summary)
            assertEquals(5400L, summary?.stepsSection?.averageSteps)
            assertNull(summary?.hydrationSection)
            assertNull(summary?.authorizedRecordsSection)
            val summaryReq = server.takeRequest()
            assertEquals("/api/b2b/program/me/participants/patient-1/summary?days=30", summaryReq.path)

            // Resposta 403/IDOR em acesso cruzado deve retornar null sem lancar excecao na UI
            server.enqueue(MockResponse().setResponseCode(403).setBody("""{"detail":"Participante sem vínculo ativo."}"""))
            assertNull(api.getPartnerParticipantSummary("patient-outro", days = 30))
            val summary403Req = server.takeRequest()
            assertEquals("/api/b2b/program/me/participants/patient-outro/summary?days=30", summary403Req.path)

            // B2B-17B: remocao de participante libera vaga e propaga erro 404/403 com seguranca
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "success": true,
                      "removedPatientUserId": "patient-1",
                      "program": {
                        "programId": "11111111-1111-1111-1111-111111111111",
                        "ownerUserId": "owner-a",
                        "companyName": "Clinica Horizonte",
                        "programName": "Cuidado Cardio",
                        "primaryColor": "#0F4C81",
                        "approvalStatus": "APPROVED",
                        "inviteCode": "CARD-4F9A",
                        "invitePaused": false,
                        "seatLimit": 15,
                        "activeParticipants": 0,
                        "canPromote": true
                      }
                    }
                    """.trimIndent()
                )
            )
            val removedOk = api.removePartnerParticipant("patient-1")
            assertTrue(removedOk.isSuccess)
            assertEquals("patient-1", removedOk.removedPatientUserId)
            assertEquals(0, removedOk.program?.activeParticipants)

            val removeCall = server.takeRequest()
            assertEquals("/api/b2b/program/me/participants/patient-1/remove", removeCall.path)
            assertEquals("POST", removeCall.method)

            server.enqueue(MockResponse().setResponseCode(404).setBody("""{"detail":"Vínculo ativo não encontrado."}"""))
            val removedFail = api.removePartnerParticipant("patient-inexistente")
            assertTrue(!removedFail.isSuccess)
            assertEquals("Vínculo ativo não encontrado.", removedFail.errorDetail)
            val removeFailReq = server.takeRequest()
            assertEquals("/api/b2b/program/me/participants/patient-inexistente/remove", removeFailReq.path)

            // B2B-16B: upload multipart da logo B2B para bucket S3/R2 e tratamento de formato invalido
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "logoUrl": "https://api.bragasaude.online/api/files/b2b-logos/11111111-1111-1111-1111-111111111111/logo-abc.png",
                      "program": {
                        "programId": "11111111-1111-1111-1111-111111111111",
                        "ownerUserId": "owner-a",
                        "companyName": "Clinica Horizonte",
                        "programName": "Cuidado Cardio",
                        "primaryColor": "#0F4C81",
                        "logoUrl": "https://api.bragasaude.online/api/files/b2b-logos/11111111-1111-1111-1111-111111111111/logo-abc.png",
                        "approvalStatus": "APPROVED",
                        "inviteCode": "CARD-4F9A",
                        "invitePaused": false,
                        "seatLimit": 15,
                        "activeParticipants": 0,
                        "canPromote": true
                      }
                    }
                    """.trimIndent()
                )
            )
            val uploadOk = api.uploadPartnerLogo("logo.png", "image/png", byteArrayOf(1, 2, 3, 4))
            assertTrue(uploadOk.isSuccess)
            assertEquals(
                "https://api.bragasaude.online/api/files/b2b-logos/11111111-1111-1111-1111-111111111111/logo-abc.png",
                uploadOk.logoUrl
            )
            assertEquals(uploadOk.logoUrl, uploadOk.program?.logoUrl)
            val uploadReq = server.takeRequest()
            assertEquals("/api/b2b/program/me/logo", uploadReq.path)
            assertEquals("POST", uploadReq.method)
            assertTrue(uploadReq.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data"))

            server.enqueue(
                MockResponse().setResponseCode(415).setBody("""{"detail":"SVG não é permitido."}""")
            )
            val uploadFail = api.uploadPartnerLogo("bad.svg", "image/svg+xml", "<svg/>".toByteArray())
            assertTrue(!uploadFail.isSuccess)
            assertEquals("SVG não é permitido.", uploadFail.errorDetail)
        }
    }
}
