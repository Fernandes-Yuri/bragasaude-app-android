package br.com.bragasaude.ai

import org.junit.Assert.*
import org.junit.Test

class BragaTurnMeaningTest {
    @Test fun denialCancellationAndOtherDrinksKeepMeaningLocally() {
        for (raw in listOf("não bebi 500 ml de água", "não anota 500 ml de água", "bebi 500 ml de suco")) {
            val output = BragaNluEngine.analisar(raw, InputChannel.TEXT)
            assertFalse(output.delegarParaNuvem)
            assertFalse(output.isEmergencia)
            assertEquals("conversa_bebida_sem_registro", output.intent)
            assertFalse(BragaActionGate.canParse(raw, output))
            assertFalse(output.respostaLocal.orEmpty().contains("Boa!"))
        }
        val denied = BragaTurnMeaning.beverages("não bebi 500 ml de água").single()
        assertTrue(denied.negated)
        assertEquals("agua", denied.entity)
        assertEquals(500, denied.quantityMl)
        assertEquals("cancellation", BragaTurnMeaning.beverages("não anota 500 ml de água").single().modality)
        assertEquals("suco", BragaTurnMeaning.beverages("bebi 500 ml de suco").single().entity)
    }

    @Test fun unknownDrinkClarifiesAndDecimalsPreserveQuantity() {
        assertTrue(BragaTurnMeaning.localReply("bebi 500 ml").orEmpty().contains("Qual bebida"))
        assertEquals(500, BragaTurnMeaning.beverages("bebi 0,5 litro de água").single().quantityMl)
        assertNull(BragaTurnMeaning.localReply("bebi 500 ml de água"))
        assertNull(BragaTurnMeaning.localReply("quanto devo beber de água?"))
    }

    @Test fun realEmergencyPrecedesBeverageAndEducationalMention() {
        for (raw in listOf("não bebi 500 ml de água; estou com dor no peito",
                "por que estou com dor no peito?", "por que não consigo respirar?",
                "não tenho dor no peito, mas não consigo respirar", "ontem tive dor no peito e agora voltou",
                "como excluo minha conta? Estou com dor no peito")) {
            assertTrue(raw, BragaNluEngine.analisar(raw, InputChannel.TEXT).isEmergencia)
        }
        assertFalse(BragaNluEngine.analisar("o que significa dor no peito?", InputChannel.TEXT).isEmergencia)
        assertFalse(BragaNluEngine.analisar("não estou com dor no peito", InputChannel.TEXT).isEmergencia)
    }
    @Test fun negationAndRecurrenceDoNotLeakIntoOtherClauses() {
        assertNull(BragaTurnMeaning.localReply("Naum bebi 500 ml de água"))
        assertEquals("entrada_linguagem_ambigua", BragaNluEngine.analisar("Naum bebi 500 ml de água", InputChannel.TEXT).intent)
        assertTrue(BragaNluEngine.analisar("Ontem tive dor no peito e agora voltou a doer forte", InputChannel.TEXT).isEmergencia)
        assertTrue(BragaNluEngine.analisar("não tenho dor no peito, estou com falta de ar", InputChannel.TEXT).isEmergencia)
        assertFalse(BragaNluEngine.analisar("o que significa dor no peito? Minha dor de cabeça voltou", InputChannel.TEXT).isEmergencia)
        val mentions = BragaTurnMeaning.beverages("não bebi 500 ml de água, bebi 250 ml de suco")
        assertEquals(2, mentions.size)
        assertTrue(mentions[0].negated)
        assertFalse(mentions[1].negated)
        assertEquals("suco", mentions[1].entity)
        assertEquals(250, mentions[1].quantityMl)
        assertNull(BragaTurnMeaning.localReply("não bebi água, mas por que minha pressão subiu?"))
    }
}
