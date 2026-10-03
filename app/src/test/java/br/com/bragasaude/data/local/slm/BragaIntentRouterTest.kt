package br.com.bragasaude.data.local.slm

import br.com.bragasaude.domain.VoiceHealthIntent
import br.com.bragasaude.domain.VoiceHealthParser
import org.junit.Assert.*
import org.junit.Test

class BragaIntentRouterTest {
    private val router = BragaIntentRouter(VoiceHealthParser())
    @Test fun extractsValidatedMeasurements() {
        val water = router.route("bebi 300ml de água")
        assertEquals(BragaIntent.REGISTRO_AGUA, water.type)
        assertEquals(300, (water.draft as VoiceHealthIntent.Hydration).amountMl)
        val pressure = router.route("minha pressão deu 12 por 8")
        assertEquals(120, (pressure.draft as VoiceHealthIntent.BloodPressure).systolic)
        assertEquals(80, (pressure.draft as VoiceHealthIntent.BloodPressure).diastolic)
        assertEquals(110, (router.route("glicose deu 110 em jejum").draft as VoiceHealthIntent.Glucose).glucoseMgDl)
    }
    @Test fun questionsAreResolvedBeforeMeasurementParsing() {
        val request = router.route("quanto deu minha pressão ontem?")
        assertEquals(BragaIntent.CONSULTA_HISTORICO, request.type)
        assertEquals(1L, request.dayOffset)
        assertNull(request.draft)
    }
    @Test fun negationFutureAndSmallTalkNeverPrepareRecords() {
        listOf("não bebi 300ml de água", "vou tomar meu remédio", "devo tomar Losartana?", "estou com saudade", "como foi seu dia?", "qual é a pressão normal?", "quanta água devo beber hoje?", "tomei um café").forEach {
            assertEquals(it, BragaIntent.CONVERSA_LIVRE, router.route(it).type)
        }
    }
    @Test fun dangerHasPriorityEvenWhenWaterIsMentioned() {
        listOf("dor forte no peito depois que bebi água", "minha mãe caiu e bateu a cabeça", "não consigo respirar").forEach {
            assertEquals(it, BragaIntent.EMERGENCIA, router.route(it).type)
        }
        assertEquals(BragaIntent.CONVERSA_LIVRE, router.route("não tenho dor no peito").type)
        assertEquals(BragaIntent.CONVERSA_LIVRE, router.route("o que é infarto?").type)
    }
}
