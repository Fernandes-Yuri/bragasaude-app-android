package br.com.bragasaude.domain.b2b

import br.com.bragasaude.data.local.b2b.B2BContextCacheEntity
import br.com.bragasaude.data.remote.b2b.ActiveContextStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class B2BContextStoreTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `ActiveContextStore inicia em modo PERSONAL sem emitir headers B2B`() {
        val store = ActiveContextStore()
        assertEquals(AppContextMode.PERSONAL, store.activeContext.value.parsedMode)
        assertNull(store.currentHeaderProgramId())
        assertNull(store.currentHeaderMode())
    }

    @Test
    fun `selectContext exige que o contexto pertenca ao usuario autenticado e esteja autorizado`() {
        val store = ActiveContextStore()
        val ownerCtx = B2BContextDescriptor(
            mode = "PARTNER_OWNER",
            userId = "user-1",
            programId = "11111111-1111-1111-1111-111111111111",
            programName = "Programa Cardio",
            companyName = "Clinica Cardio",
            primaryColor = "#1A6B5C",
            approvalStatus = "APPROVED"
        )
        val participantCtx = B2BContextDescriptor(
            mode = "PARTNER_PARTICIPANT",
            userId = "user-1",
            programId = "22222222-2222-2222-2222-222222222222",
            programName = "Rotina Bem-Estar",
            companyName = "Clinica Bem",
            primaryColor = "#0F4C81",
            approvalStatus = "APPROVED",
            sharedCategories = listOf("steps", "hydration")
        )

        store.updateAvailableContexts("user-1", listOf(ownerCtx, participantCtx))

        // Tentativa de ativar contexto de outro usuario deve falhar
        val crossUserAttempt = ownerCtx.copy(userId = "user-2")
        assertFalse(store.selectContext("user-1", crossUserAttempt))
        assertNull(store.currentHeaderProgramId())

        // Tentativa de ativar programa nao autorizado deve falhar
        val unauthorizedAttempt = ownerCtx.copy(programId = "99999999-9999-9999-9999-999999999999")
        assertFalse(store.selectContext("user-1", unauthorizedAttempt))
        assertNull(store.currentHeaderProgramId())

        // Selecao autorizada ativa o header do programa
        assertTrue(store.selectContext("user-1", participantCtx))
        assertEquals("22222222-2222-2222-2222-222222222222", store.currentHeaderProgramId())
        assertEquals("PARTNER_PARTICIPANT", store.currentHeaderMode())

        // Retorno ao modo pessoal limpa os headers B2B
        assertTrue(store.selectContext("user-1", B2BContextDescriptor.personal("user-1")))
        assertNull(store.currentHeaderProgramId())
        assertNull(store.currentHeaderMode())
    }

    @Test
    fun `resetToPersonal limpa contextos B2B no logout`() {
        val store = ActiveContextStore()
        val ownerCtx = B2BContextDescriptor(
            mode = "PARTNER_OWNER",
            userId = "user-1",
            programId = "11111111-1111-1111-1111-111111111111",
            programName = "Programa Cardio"
        )
        store.updateAvailableContexts("user-1", listOf(ownerCtx))
        assertTrue(store.selectContext("user-1", ownerCtx))
        assertEquals("11111111-1111-1111-1111-111111111111", store.currentHeaderProgramId())

        store.resetToPersonal()
        assertEquals(AppContextMode.PERSONAL, store.activeContext.value.parsedMode)
        assertEquals(1, store.availableContexts.value.size)
        assertNull(store.currentHeaderProgramId())
        assertNull(store.currentHeaderMode())
    }

    @Test
    fun `serializacao Kotlin pura e conversao de entidade preservam categorias compartilhadas`() {
        val payload = """
            {
              "defaultMode": "PERSONAL",
              "contexts": [
                {"mode": "PERSONAL", "userId": "u1"},
                {
                  "mode": "PARTNER_PARTICIPANT",
                  "userId": "u1",
                  "programId": "22222222-2222-2222-2222-222222222222",
                  "programName": "Rotina",
                  "sharedCategories": ["steps", "medications"]
                }
              ]
            }
        """.trimIndent()
        val decoded = json.decodeFromString<B2BContextsEnvelope>(payload)
        assertEquals(2, decoded.contexts.size)
        val partner = decoded.contexts[1]
        val entity = B2BContextCacheEntity.fromDescriptor("u1", partner, isActive = true, json = json)
        val roundTrip = entity.toDescriptor(json)
        assertEquals(listOf("steps", "medications"), roundTrip.sharedCategories)
        assertEquals(AppContextMode.PARTNER_PARTICIPANT, roundTrip.parsedMode)
        assertTrue(json.encodeToString(roundTrip).contains("PARTNER_PARTICIPANT"))
    }

    @Test
    fun `B2BOnboardingValidator valida cor hexadecimal, CNPJ e criterios de submissao`() {
        assertTrue(B2BOnboardingValidator.isValidHexColor("#1A6B5C"))
        assertTrue(B2BOnboardingValidator.isValidHexColor("#0f4c81"))
        assertFalse(B2BOnboardingValidator.isValidHexColor("1A6B5C"))
        assertFalse(B2BOnboardingValidator.isValidHexColor("#GGGGGG"))

        assertEquals(
            "12.345.678/0001-95",
            B2BOnboardingValidator.formatCnpjOrNull("12345678000195")
        )
        assertNull(B2BOnboardingValidator.formatCnpjOrNull("11111111111111"))

        // Rascunho permite CNPJ vazio, mas envio para analise exige CNPJ valido
        assertTrue(
            B2BOnboardingValidator.canSaveDraft(
                companyName = "Clinica Horizonte",
                programName = "Horizonte Cuidado",
                primaryColor = "#1A6B5C",
                cnpj = null
            )
        )
        assertFalse(
            B2BOnboardingValidator.canSubmitForReview(
                companyName = "Clinica Horizonte",
                programName = "Horizonte Cuidado",
                cnpj = null,
                primaryColor = "#1A6B5C"
            )
        )
        assertTrue(
            B2BOnboardingValidator.canSubmitForReview(
                companyName = "Clinica Horizonte",
                programName = "Horizonte Cuidado",
                cnpj = "12.345.678/0001-95",
                primaryColor = "#1A6B5C"
            )
        )
        assertEquals("Em análise", PartnerApprovalStatus.fromWire("PENDING_REVIEW").labelPtBr)
        assertEquals("Empresa aprovada", PartnerApprovalStatus.fromWire("APPROVED").labelPtBr)
    }
}
