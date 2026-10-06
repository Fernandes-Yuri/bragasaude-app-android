package br.com.bragasaude.ai

import org.junit.Assert.*
import org.junit.Test

class BragaManualLogRegressionTest {
    @Test fun `pedidos de orientacao encontrados nos logs chegam a nuvem`() {
        for (channel in InputChannel.entries) {
            listOf("posso tomar um comprimido de pressão?", "posso tomar um remédio pra pressão",
                "posso tomar remédio pra pressão", "posso tomar remédio pra dor de cabeça",
                "O que posso almoçar?").forEach { text ->
                val output = BragaNluEngine.analisar(text, channel)
                assertEquals(text, BragaRoute.CLOUD, output.route)
                assertTrue(text, output.delegarParaNuvem)
                assertNull(output.healthQuery)
            }
        }
    }

    @Test fun `erro mais apos confirmacao conserva a pergunta e o contexto`() {
        val recent = HealthQuery(HealthMetric.PRESSURE)
        listOf("entendi, mais porque subiu?", "quero saber porque subiu",
            "entendi, mas por que subiu?").forEach { text ->
            assertEquals(text, "explicacao_variacao_pressao", BragaLocalHelp.answer(text, recent)?.intent)
        }
        assertEquals("quanto mais agua", BragaDialogueRequest.main("quanto mais água"))
    }

    @Test fun `pergunta sobre dose sem nome solicita medicamento registrado`() {
        val output = BragaNluEngine.analisar("tomei o meu remédio hoje ?")
        assertEquals("entrada_medicamento_sem_nome", output.intent)
        assertFalse(output.delegarParaNuvem)
        assertNull(output.healthQuery)
        assertEquals("ajuda_horario_remedio", BragaNluEngine.analisar("Lembrete de remédio").intent)
    }
}
