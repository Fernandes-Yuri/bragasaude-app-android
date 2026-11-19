package br.com.bragasaude.data.remote.api

import br.com.bragasaude.data.local.security.FamilyE2eeManager
import br.com.bragasaude.data.remote.auth.AuthService
import br.com.bragasaude.data.remote.b2b.ActiveContextStore
import br.com.bragasaude.data.remote.network.AuthInterceptor
import br.com.bragasaude.domain.b2b.B2BContextDescriptor
import br.com.bragasaude.domain.b2b.CreateProgramReminderRequest
import br.com.bragasaude.domain.b2b.ProgramExperienceConfigDto
import br.com.bragasaude.domain.b2b.ReportProgramAbuseRequest
import br.com.bragasaude.domain.b2b.UpdateExperienceRequest
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
            assertEquals("/api/b2b/program/me/logo", server.takeRequest().path)

            // B2B-09B: submit Auto-KYB Verde, bloqueio 422 Vermelho, upload e remocao de comprovante
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "kybDecision": "GREEN",
                      "flags": [],
                      "requiresManualProof": false,
                      "program": {
                        "programId": "11111111-1111-1111-1111-111111111111",
                        "ownerUserId": "owner-a",
                        "companyName": "Clinica Horizonte Ltda",
                        "programName": "Cuidado Cardio",
                        "cnpj": "12.345.678/0001-95",
                        "primaryColor": "#0F4C81",
                        "approvalStatus": "APPROVED",
                        "inviteCode": "CARD-4F9A",
                        "responsibleCpfMasked": "***.982.247-**",
                        "autoKybStatus": "GREEN"
                      }
                    }
                    """.trimIndent()
                )
            )
            val submitOk = api.submitMyPartnerProgramKyb(
                termsAccepted = true,
                termsVersion = "B2B-V1-2026",
                responsibleCpf = "529.982.247-25"
            )
            assertTrue(submitOk.isSuccess)
            assertEquals("GREEN", submitOk.kybDecision)
            assertEquals("APPROVED", submitOk.program?.approvalStatus)
            assertEquals("***.982.247-**", submitOk.program?.responsibleCpfMasked)
            val submitReq = server.takeRequest()
            assertEquals("/api/b2b/program/me/submit", submitReq.path)
            assertTrue(submitReq.body.readUtf8().contains("B2B-V1-2026"))

            server.enqueue(
                MockResponse().setResponseCode(422).setBody(
                    """
                    {
                      "detail": "Cadastro bloqueado: CNPJ BAIXADA na Receita Federal.",
                      "kybDecision": "RED",
                      "flags": ["CNPJ_INACTIVE:BAIXADA"]
                    }
                    """.trimIndent()
                )
            )
            val submitRed = api.submitMyPartnerProgramKyb()
            assertTrue(!submitRed.isSuccess)
            assertEquals("RED", submitRed.kybDecision)
            assertEquals(listOf("CNPJ_INACTIVE:BAIXADA"), submitRed.flags)
            assertEquals("/api/b2b/program/me/submit", server.takeRequest().path)

            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "proofId": "proof-1",
                      "program": {
                        "programId": "11111111-1111-1111-1111-111111111111",
                        "ownerUserId": "owner-a",
                        "companyName": "Clinica Horizonte Ltda",
                        "programName": "Cuidado Cardio",
                        "approvalStatus": "PENDING_REVIEW",
                        "proofDocuments": [
                          {
                            "id": "proof-1",
                            "docType": "CARTAO_CNPJ_CCMEI",
                            "fileName": "comprovante.pdf",
                            "contentType": "application/pdf",
                            "sizeBytes": 1024,
                            "uploadedAt": "2026-11-19T18:00:00Z"
                          }
                        ]
                      }
                    }
                    """.trimIndent()
                )
            )
            val proofUp = api.uploadPartnerProof(
                docType = "CARTAO_CNPJ_CCMEI",
                fileName = "comprovante.pdf",
                mimeType = "application/pdf",
                bytes = "%PDF-1.4\n...".toByteArray()
            )
            assertTrue(proofUp.isSuccess)
            assertEquals("proof-1", proofUp.proofId)
            assertEquals(1, proofUp.program?.proofDocuments?.size)
            val proofUpReq = server.takeRequest()
            assertEquals("/api/b2b/program/me/proofs", proofUpReq.path)
            assertTrue(proofUpReq.body.readUtf8().contains("CARTAO_CNPJ_CCMEI"))

            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "program": {
                        "programId": "11111111-1111-1111-1111-111111111111",
                        "ownerUserId": "owner-a",
                        "companyName": "Clinica Horizonte Ltda",
                        "programName": "Cuidado Cardio",
                        "approvalStatus": "PENDING_REVIEW",
                        "proofDocuments": []
                      }
                    }
                    """.trimIndent()
                )
            )
            val proofDel = api.deletePartnerProof("proof-1")
            assertTrue(proofDel.isSuccess)
            assertEquals(0, proofDel.program?.proofDocuments?.size)
            val proofDelReq = server.takeRequest()
            assertEquals("/api/b2b/program/me/proofs/proof-1", proofDelReq.path)
            assertEquals("DELETE", proofDelReq.method)

            // B2B-16: templates No-Code, PUT experience e parsing de experienceConfig em contexts
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "templates": [
                        {
                          "templateKey": "NUTRICAO_BEM_ESTAR",
                          "title": "Nutricao e Bem-Estar",
                          "subtitle": "Habitos e hidratacao",
                          "sampleSafeReminder": "Lembrete de agua",
                          "defaultConfig": {
                            "schemaVersion": 1,
                            "templateKey": "NUTRICAO_BEM_ESTAR",
                            "welcomeHeadline": "Habitos diarios",
                            "welcomeMessage": "Acompanhe sua rotina.",
                            "enabledMetrics": ["steps", "hydration"],
                            "features": {
                              "assistantChatEnabled": true,
                              "directPartnerChatEnabled": true,
                              "aiReceptionistEnabled": true,
                              "familyLinkEnabled": false,
                              "partnerRemindersEnabled": true,
                              "appointmentsEnabled": false,
                              "preConsultationPdfEnabled": true
                            }
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val templates = api.getProgramTemplates()
            assertEquals(1, templates.size)
            assertEquals("NUTRICAO_BEM_ESTAR", templates[0].templateKey)
            assertTrue(templates[0].defaultConfig.features.aiReceptionistEnabled)
            assertEquals("/api/b2b/templates", server.takeRequest().path)

            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "program": {
                        "programId": "11111111-1111-1111-1111-111111111111",
                        "ownerUserId": "owner-a",
                        "companyName": "Clinica Horizonte Ltda",
                        "programName": "Cuidado Cardio",
                        "approvalStatus": "APPROVED",
                        "templateKey": "NUTRICAO_BEM_ESTAR",
                        "experienceConfig": {
                          "schemaVersion": 1,
                          "templateKey": "NUTRICAO_BEM_ESTAR",
                          "welcomeHeadline": "Rotina Personalizada",
                          "welcomeMessage": "Bem-vindo ao nosso programa.",
                          "enabledMetrics": ["steps", "hydration"],
                          "features": {
                            "directPartnerChatEnabled": true,
                            "aiReceptionistEnabled": true
                          }
                        }
                      }
                    }
                    """.trimIndent()
                )
            )
            val customCfg = ProgramExperienceConfigDto.defaultForTemplate("NUTRICAO_BEM_ESTAR").copy(
                welcomeHeadline = "Rotina Personalizada",
                welcomeMessage = "Bem-vindo ao nosso programa."
            )
            val expRes = api.updateProgramExperience(
                UpdateExperienceRequest(
                    templateKey = "NUTRICAO_BEM_ESTAR",
                    applyTemplateDefaults = false,
                    experienceConfig = customCfg
                )
            )
            assertTrue(expRes.isSuccess)
            assertEquals("Rotina Personalizada", expRes.program?.experienceConfig?.welcomeHeadline)
            val expReq = server.takeRequest()
            assertEquals("/api/b2b/program/me/experience", expReq.path)
            assertEquals("PUT", expReq.method)

            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "defaultMode": "PERSONAL",
                      "contexts": [
                        {
                          "mode": "PARTNER_PARTICIPANT",
                          "userId": "patient-1",
                          "programId": "11111111-1111-1111-1111-111111111111",
                          "programName": "Cuidado Cardio",
                          "companyName": "Clinica Horizonte",
                          "templateKey": "NUTRICAO_BEM_ESTAR",
                          "familyShareEnabled": true,
                          "sharedCategories": ["steps"],
                          "experienceConfig": {
                            "templateKey": "NUTRICAO_BEM_ESTAR",
                            "welcomeHeadline": "Rotina Personalizada",
                            "welcomeMessage": "Bem-vindo ao nosso programa.",
                            "enabledMetrics": ["steps", "hydration"],
                            "features": { "partnerRemindersEnabled": true, "appointmentsEnabled": false }
                          }
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val parsedCtxs = api.getB2BContexts()
            assertEquals(1, parsedCtxs.size)
            assertTrue(parsedCtxs[0].familyShareEnabled)
            assertEquals("Rotina Personalizada", parsedCtxs[0].experienceConfig?.welcomeHeadline)
            assertTrue(!parsedCtxs[0].resolvedExperienceConfig().features.appointmentsEnabled)
            assertEquals("/api/b2b/contexts", server.takeRequest().path)
        }
    }

    @Test
    fun partnerParticipantsFilterCompositeCursorAndBiometriesParseCorrectly() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "participants": [
                        {
                          "patientUserId": "patient-1",
                          "displayName": "Ana Silva",
                          "sharedCategories": ["steps", "authorized_records"],
                          "effectiveCategories": ["steps", "authorized_records"],
                          "missingProgramCategories": ["hydration"],
                          "familySupportActive": true,
                          "familySupportSummary": "Apoio familiar ativo neste programa",
                          "pendingAppointmentsCount": 2,
                          "unreadDirectMessagesCount": 1,
                          "consentedAt": "2026-11-19T12:00:00Z"
                        }
                      ],
                      "nextCursor": "2026-11-19T12:00:00Z|patient-1",
                      "hasMore": true
                    }
                    """.trimIndent()
                )
            )
            val api = BragaApiClient(
                mockk(relaxed = true),
                server.url("/").toString().trimEnd('/'),
                stubAuthService(),
                OkHttpClient(),
                FamilyE2eeManager()
            )

            val page = api.getMyPartnerParticipants(
                cursor = "2026-11-19T12:00:00Z|patient-0",
                limit = 20,
                query = "Ana Silva",
                category = "authorized_records",
                attentionOnly = true
            )
            assertNotNull(page)
            assertEquals(1, page!!.participants.size)
            val p = page.participants[0]
            assertEquals(listOf("steps", "authorized_records"), p.effectiveCategories)
            assertEquals(listOf("hydration"), p.missingProgramCategories)
            assertTrue(p.familySupportActive)
            assertEquals("Apoio familiar ativo neste programa", p.familySupportSummary)
            assertEquals(2, p.pendingAppointmentsCount)
            assertEquals(1, p.unreadDirectMessagesCount)
            assertEquals("2026-11-19T12:00:00Z|patient-1", page.nextCursor)
            assertTrue(page.hasMore)

            val listReqPath = server.takeRequest().path!!
            assertTrue(listReqPath.contains("/api/b2b/program/me/participants?"))
            assertTrue(listReqPath.contains("q=Ana+Silva") || listReqPath.contains("q=Ana%20Silva"))
            assertTrue(listReqPath.contains("category=authorized_records"))
            assertTrue(listReqPath.contains("attentionOnly=true"))
            assertTrue(listReqPath.contains("cursor="))

            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "summary": {
                        "programId": "prog-1",
                        "programName": "Cuidado Cardio",
                        "companyName": "Clinica Horizonte",
                        "patientUserId": "patient-1",
                        "participantName": "Ana Silva",
                        "periodDays": 14,
                        "sharedCategories": ["authorized_records"],
                        "familySupportActive": true,
                        "familySupportSummary": "Apoio familiar ativo",
                        "stepsSection": null,
                        "hydrationSection": null,
                        "medicationsSection": null,
                        "authorizedRecordsSection": {
                          "source": "Braga Saúde • Registros autorizados",
                          "zeroFileEgressNotice": "Zero File Egress",
                          "records": [
                            {
                              "measuredAt": "2026-11-19T10:00:00Z",
                              "origin": "Braga Saúde",
                              "systolicPressure": 120,
                              "diastolicPressure": 80,
                              "heartRate": 72
                            }
                          ],
                          "biometries": [
                            {
                              "measuredAt": "2026-11-18T09:00:00Z",
                              "weightKg": 71.4,
                              "heightM": 1.68,
                              "imc": 25.3
                            }
                          ]
                        }
                      }
                    }
                    """.trimIndent()
                )
            )

            val summary = api.getPartnerParticipantSummary("patient-1", days = 14)
            assertNotNull(summary)
            assertEquals(14, summary!!.periodDays)
            assertTrue(summary.familySupportActive)
            assertNull(summary.stepsSection)
            assertNotNull(summary.authorizedRecordsSection)
            assertEquals(1, summary.authorizedRecordsSection!!.records.size)
            assertEquals(1, summary.authorizedRecordsSection!!.biometries.size)
            assertEquals(71.4, summary.authorizedRecordsSection!!.biometries[0].weightKg, 0.01)
            assertEquals(25.3, summary.authorizedRecordsSection!!.biometries[0].imc!!, 0.01)
            assertEquals("/api/b2b/program/me/participants/patient-1/summary?days=14", server.takeRequest().path)
        }
    }

    @Test
    fun partnerRemindersSendListAckAndMuteParseAndPropagateNonSaMDErrors() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = BragaApiClient(
                mockk(relaxed = true),
                server.url("/").toString().trimEnd('/'),
                stubAuthService(),
                OkHttpClient(),
                FamilyE2eeManager()
            )

            // Envio com sucesso (201)
            server.enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """
                    {
                      "recipientCount": 4,
                      "reminder": {
                        "reminderId": "rem-1",
                        "programId": "prog-1",
                        "targetPatientId": null,
                        "targetPatientName": null,
                        "targetCategory": "hydration",
                        "reminderType": "HYDRATION",
                        "title": "Hidratacao da tarde",
                        "body": "Beba 300 ml de agua agora.",
                        "createdAt": "2026-11-19T18:00:00Z",
                        "acknowledgedCount": 0
                      }
                    }
                    """.trimIndent()
                )
            )
            val sent = api.sendPartnerReminder(
                CreateProgramReminderRequest(
                    targetPatientId = null,
                    targetCategory = "hydration",
                    reminderType = "HYDRATION",
                    title = "Hidratacao da tarde",
                    body = "Beba 300 ml de agua agora."
                )
            )
            assertTrue(sent.isSuccess)
            assertEquals(4, sent.recipientCount)
            assertEquals("rem-1", sent.reminder?.reminderId)
            val sendReq = server.takeRequest()
            assertEquals("/api/b2b/program/me/reminders", sendReq.path)
            assertEquals("POST", sendReq.method)

            // Erro 422 posologia propagado ao usuário
            server.enqueue(
                MockResponse().setResponseCode(422).setBody(
                    """{"detail":"Orientação de medicação não é permitida neste canal."}"""
                )
            )
            val blocked = api.sendPartnerReminder(
                CreateProgramReminderRequest(
                    targetPatientId = "patient-1",
                    reminderType = "ROUTINE",
                    title = "Remedio",
                    body = "Tomar 50 mg"
                )
            )
            assertTrue(!blocked.isSuccess)
            assertEquals("Orientação de medicação não é permitida neste canal.", blocked.errorDetail)
            server.takeRequest()

            // Participante lista, confirma leitura (ack) e silencia (mute)
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "programId": "prog-1",
                      "programName": "Cuidado Cardio",
                      "primaryColor": "#1A6B5C",
                      "muted": false,
                      "reminders": [
                        {
                          "reminderId": "rem-1",
                          "programId": "prog-1",
                          "reminderType": "HYDRATION",
                          "title": "Hidratacao da tarde",
                          "body": "Beba 300 ml de agua agora.",
                          "createdAt": "2026-11-19T18:00:00Z",
                          "acknowledgedCount": 0
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val feed = api.getEnrollmentReminders("prog-1", limit = 20)
            assertNotNull(feed)
            assertEquals(1, feed!!.reminders.size)
            assertTrue(!feed.muted)
            assertEquals("/api/b2b/enrollments/prog-1/reminders?limit=20", server.takeRequest().path)

            server.enqueue(MockResponse().setBody("""{"reminderId":"rem-1","acknowledgedAt":"2026-11-19T18:05:00Z"}"""))
            val ack = api.acknowledgeEnrollmentReminder("prog-1", "rem-1")
            assertEquals("2026-11-19T18:05:00Z", ack)
            assertEquals("/api/b2b/enrollments/prog-1/reminders/rem-1/ack", server.takeRequest().path)

            server.enqueue(MockResponse().setBody("""{"programId":"prog-1","muted":true}"""))
            val mutedRes = api.setEnrollmentMuted("prog-1", muted = true)
            assertEquals(true, mutedRes)
            assertEquals("/api/b2b/enrollments/prog-1/mute", server.takeRequest().path)
        }
    }

    @Test
    fun parseAiPausedFlagAndReportProgramAbuseRoute() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = BragaApiClient(
                mockk(relaxed = true),
                server.url("/").toString().trimEnd('/'),
                stubAuthService(),
                OkHttpClient(),
                FamilyE2eeManager()
            )

            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "program": {
                        "programId": "prog-1",
                        "ownerUserId": "owner-1",
                        "companyName": "Clinica Cardio",
                        "programName": "Cuidado Cardio",
                        "primaryColor": "#1A6B5C",
                        "approvalStatus": "APPROVED",
                        "aiPaused": true,
                        "seatLimit": 5,
                        "activeParticipants": 1
                      }
                    }
                    """.trimIndent()
                )
            )
            val prog = api.getMyPartnerProgram()
            assertNotNull(prog)
            assertEquals(true, prog!!.aiPaused)
            assertEquals("/api/b2b/program/me", server.takeRequest().path)

            server.enqueue(
                MockResponse()
                    .setResponseCode(201)
                    .setBody("""{"reportId":"rep-1","programId":"prog-1","muted":true}""")
            )
            val report = api.reportPartnerProgramAbuse(
                programId = "prog-1",
                request = ReportProgramAbuseRequest(
                    reason = "SPAM_OU_ASSEDIO",
                    details = "Mensagens repetidas fora do horário.",
                    muteAlso = true
                )
            )
            assertTrue(report.isSuccess)
            assertEquals("rep-1", report.reportId)
            assertEquals("prog-1", report.programId)
            assertTrue(report.muted)
            val req = server.takeRequest()
            assertEquals("POST", req.method)
            assertEquals("/api/b2b/enrollments/prog-1/report", req.path)
            assertTrue(req.body.readUtf8().contains("SPAM_OU_ASSEDIO"))
        }
    }

    @Test
    fun programAppointmentsListRequestConfirmOverlapConflictAndCancelWorkEndToEnd() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = BragaApiClient(
                mockk(relaxed = true),
                server.url("/").toString().trimEnd('/'),
                stubAuthService(),
                OkHttpClient(),
                FamilyE2eeManager()
            )

            // 1. Participante lista e solicita horario (201)
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "programId": "prog-1",
                      "programName": "Cuidado Cardio",
                      "appointmentsPolicy": {
                        "allowedModalities": ["IN_PERSON","REMOTE"],
                        "bookingMode": "REQUEST_CONFIRMATION",
                        "availabilitiesSummary": "Seg a Sex 09h-17h"
                      },
                      "pendingCount": 0,
                      "appointments": []
                    }
                    """.trimIndent()
                )
            )
            val emptyFeed = api.getEnrollmentAppointments("prog-1")
            assertNotNull(emptyFeed)
            assertEquals("REQUEST_CONFIRMATION", emptyFeed!!.appointmentsPolicy.bookingMode)
            assertEquals("/api/b2b/enrollments/prog-1/appointments", server.takeRequest().path)

            server.enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """
                    {
                      "appointment": {
                        "appointmentId": "appt-1",
                        "programId": "prog-1",
                        "patientUserId": "patient-1",
                        "modality": "REMOTE",
                        "requestedSlotText": "Quinta 14h",
                        "status": "PENDING",
                        "createdAt": "2026-11-19T19:00:00Z",
                        "updatedAt": "2026-11-19T19:00:00Z"
                      }
                    }
                    """.trimIndent()
                )
            )
            val created = api.requestEnrollmentAppointment(
                programId = "prog-1",
                modality = "REMOTE",
                requestedSlotText = "Quinta 14h",
                patientNote = "Videochamada"
            )
            assertTrue(created.isSuccess)
            assertEquals("PENDING", created.appointment!!.status)
            assertEquals("/api/b2b/enrollments/prog-1/appointments", server.takeRequest().path)

            // 2. Dono lista agenda e trata colisao 409 ao tentar confirmar no mesmo slot
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "programId": "prog-1",
                      "programName": "Cuidado Cardio",
                      "appointmentsPolicy": {
                        "allowedModalities": ["IN_PERSON","REMOTE"],
                        "bookingMode": "REQUEST_CONFIRMATION",
                        "availabilitiesSummary": "Seg a Sex 09h-17h"
                      },
                      "appointments": [
                        {
                          "appointmentId": "appt-1",
                          "programId": "prog-1",
                          "patientUserId": "patient-1",
                          "patientName": "Maria",
                          "modality": "REMOTE",
                          "requestedSlotText": "Quinta 14h",
                          "status": "PENDING",
                          "createdAt": "2026-11-19T19:00:00Z",
                          "updatedAt": "2026-11-19T19:00:00Z"
                        }
                      ]
                    }
                    """.trimIndent()
                )
            )
            val ownerFeed = api.getOwnerProgramAppointments(limit = 50)
            assertNotNull(ownerFeed)
            assertEquals(1, ownerFeed!!.appointments.size)
            assertEquals("/api/b2b/program/me/appointments?limit=50", server.takeRequest().path)

            server.enqueue(
                MockResponse().setResponseCode(409).setBody(
                    """{"detail":"Já existe outro agendamento confirmado na janela de 30 minutos para este programa."}"""
                )
            )
            val conflict = api.updateOwnerAppointment(
                appointmentId = "appt-1",
                status = "CONFIRMED",
                scheduledAtIso = "2026-11-21T17:00:00Z",
                allowOverlap = false
            )
            assertTrue(!conflict.isSuccess)
            assertEquals(
                "Já existe outro agendamento confirmado na janela de 30 minutos para este programa.",
                conflict.errorDetail
            )
            val patchReq = server.takeRequest()
            assertEquals("PATCH", patchReq.method)
            assertEquals("/api/b2b/program/me/appointments/appt-1", patchReq.path)

            // 3. Participante cancela agendamento pendente
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "appointment": {
                        "appointmentId": "appt-1",
                        "programId": "prog-1",
                        "patientUserId": "patient-1",
                        "modality": "REMOTE",
                        "requestedSlotText": "Quinta 14h",
                        "status": "CANCELLED",
                        "createdAt": "2026-11-19T19:00:00Z",
                        "updatedAt": "2026-11-19T19:10:00Z"
                      }
                    }
                    """.trimIndent()
                )
            )
            val cancelled = api.cancelEnrollmentAppointment("prog-1", "appt-1", "Imprevisto")
            assertTrue(cancelled.isSuccess)
            assertEquals("CANCELLED", cancelled.appointment!!.status)
            assertEquals("/api/b2b/enrollments/prog-1/appointments/appt-1/cancel", server.takeRequest().path)
        }
    }

    @Test
    fun programDirectChatEnrollmentAndOwnerRoutesHandleCursorOffHoursAndReadReceipts() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val api = BragaApiClient(
                mockk(relaxed = true),
                server.url("/").toString().trimEnd('/'),
                stubAuthService(),
                OkHttpClient(),
                FamilyE2eeManager()
            )

            // 1. Participante carrega chat direto e verifica assistantChatEnabled=false
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "programId": "prog-1",
                      "programName": "Cuidado Cardio",
                      "companyName": "Clinica Cardio",
                      "assistantChatEnabled": false,
                      "directChatPolicy": {
                        "serviceHoursLabel": "Seg a Sex, 08h às 18h",
                        "responseSlaLabel": "Resposta em até 1 dia útil",
                        "allowOffHoursQueue": true
                      },
                      "isOffHoursNow": true,
                      "thread": {
                        "programId": "prog-1",
                        "patientUserId": "patient-1",
                        "handlingMode": "AI_ACTIVE",
                        "updatedAt": "2026-11-19T20:00:00Z"
                      },
                      "messages": [
                        {
                          "messageId": "msg-1",
                          "programId": "prog-1",
                          "patientUserId": "patient-1",
                          "senderUserId": "owner-1",
                          "senderRole": "PARTNER",
                          "body": "Olá! Como podemos ajudar hoje?",
                          "createdAt": "2026-11-19T19:50:00Z"
                        }
                      ],
                      "nextCursor": null,
                      "hasMore": false
                    }
                    """.trimIndent()
                )
            )
            val page = api.getEnrollmentDirectMessages("prog-1", limit = 30)
            assertNotNull(page)
            assertTrue(!page!!.assistantChatEnabled)
            assertTrue(page.isOffHoursNow)
            assertEquals(1, page.messages.size)
            assertEquals("/api/b2b/enrollments/prog-1/messages?limit=30", server.takeRequest().path)

            // 2. Participante envia mensagem (201) + marca lido
            server.enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """
                    {
                      "sentOffHours": true,
                      "message": {
                        "messageId": "msg-2",
                        "programId": "prog-1",
                        "patientUserId": "patient-1",
                        "senderUserId": "patient-1",
                        "senderRole": "PATIENT",
                        "body": "Gostaria de confirmar meu horário.",
                        "createdAt": "2026-11-19T20:01:00Z"
                      }
                    }
                    """.trimIndent()
                )
            )
            val sent = api.sendEnrollmentDirectMessage("prog-1", "Gostaria de confirmar meu horário.")
            assertTrue(sent.isSuccess)
            assertTrue(sent.sentOffHours)
            assertEquals("/api/b2b/enrollments/prog-1/messages", server.takeRequest().path)

            server.enqueue(MockResponse().setBody("""{"programId":"prog-1","markedReadCount":1}"""))
            assertEquals(1, api.markEnrollmentDirectMessagesRead("prog-1"))
            assertEquals("/api/b2b/enrollments/prog-1/messages/read", server.takeRequest().path)

            // 3. Dono lista, responde (201) e marca leitura + rejeita 415 de anexo
            server.enqueue(
                MockResponse().setBody(
                    """
                    {
                      "programId": "prog-1",
                      "programName": "Cuidado Cardio",
                      "companyName": "Clinica Cardio",
                      "directChatPolicy": {
                        "serviceHoursLabel": "Seg a Sex, 08h às 18h",
                        "responseSlaLabel": "Resposta em até 1 dia útil",
                        "allowOffHoursQueue": true
                      },
                      "messages": [],
                      "nextCursor": null,
                      "hasMore": false
                    }
                    """.trimIndent()
                )
            )
            val ownerPage = api.getOwnerDirectMessages("patient-1", cursor = "2026-11-19T20:00:00Z|m1", limit = 20)
            assertNotNull(ownerPage)
            assertTrue(server.takeRequest().path!!.startsWith("/api/b2b/program/me/participants/patient-1/messages?limit=20&cursor="))

            server.enqueue(MockResponse().setBody("""{"programId":"prog-1","patientUserId":"patient-1","markedReadCount":2}"""))
            assertEquals(2, api.markOwnerDirectMessagesRead("patient-1"))
            assertEquals("/api/b2b/program/me/participants/patient-1/messages/read", server.takeRequest().path)

            server.enqueue(
                MockResponse().setResponseCode(415).setBody(
                    """{"detail":"Anexos e arquivos não são permitidos no chat direto."}"""
                )
            )
            val blocked = api.sendOwnerDirectMessage("patient-1", "data:image/png;base64,AAA")
            assertTrue(!blocked.isSuccess)
            assertEquals("Anexos e arquivos não são permitidos no chat direto.", blocked.errorDetail)
            assertEquals("/api/b2b/program/me/participants/patient-1/messages", server.takeRequest().path)
        }
    }
}
