package br.com.bragasaude.ai

import br.com.bragasaude.domain.VoiceHealthIntent
import br.com.bragasaude.domain.VoiceHealthParser
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BragaNluEngineTest {
    private val parser = VoiceHealthParser()

    @Test fun `recursos descontinuados recebem orientacao sem acao nem nuvem nos dois canais`() = runTest {
        var calls = 0
        val hybrid = BragaHybridOrchestrator(GroqStreamSource { _, _ -> calls++; flowOf("remoto") }, GroqDynamicPrompt())
        for (channel in InputChannel.entries) {
            for (text in listOf("Quero uma dieta", "Conte minhas calorias", "Abra meu cardápio",
                "Qual é o WhatsApp do suporte?", "Registrar pressão 12 por 8 pelo WhatsApp",
                "Monte meu plano alimentar", "Quero um plano de alimentação", "Abra a lista de compras", "Anote minha refeição",
                "Obrigado, onde fica o cardapio?", "Me chama no zap", "Suporte via whats app")) {
                val output = hybrid.analyze(text, channel)
                assertEquals(text, BragaProductScope.RETIRED, output.intent)
                assertFalse(text, BragaActionGate.canParse(text, output))
                assertFalse(text, BragaCloudFallback.afterLocal(text, output).delegarParaNuvem)
                assertTrue(output.respostaLocal!!.contains("contato@bragasaude.online"))
                assertTrue(hybrid.respond(text, emptyList(), channel).toList().single() is BragaHybridEvent.Local)
            }
        }
        assertEquals(0, calls)
    }

    @Test fun `contexto de consulta anterior nao substitui guardiao de escopo ou painel de doses`() {
        val hybrid = BragaHybridOrchestrator(GroqStreamSource { _, _ -> flowOf("remoto") }, GroqDynamicPrompt())
        val session = HealthQuerySession()
        session.remember(HealthQuery(HealthMetric.PRESSURE), "dono", "conversa")
        for (channel in InputChannel.entries) {
            assertEquals(BragaProductScope.RETIRED,
                hybrid.analyze("E minha dieta?", channel, session, "dono", "conversa").intent)
            assertEquals(BragaProductScope.DOSES,
                hybrid.analyze("Tomei meu remédio da manhã", channel, session, "dono", "conversa").intent)
        }
    }

    @Test fun `emergencia tem prioridade sobre recurso removido`() {
        for (channel in InputChannel.entries) {
            assertTrue(BragaNluEngine.analisar("Estou com dor no peito, chama no WhatsApp", channel).isEmergencia)
        }
    }

    @Test fun `pressao e agua continuam preparando valores para revisao`() {
        for (channel in InputChannel.entries) {
            for ((text, expected) in listOf("Minha pressão deu 12 por 8" to (120 to 80),
                "12 por 8" to (120 to 80), "130 por 85" to (130 to 85))) {
                val output = BragaNluEngine.analisar(text, channel)
                assertTrue(text, BragaActionGate.canParse(text, output))
                val intent = parser.parse(text) as VoiceHealthIntent.BloodPressure
                assertEquals(expected.first, intent.systolic)
                assertEquals(expected.second, intent.diastolic)
            }
            val text = "Bebi dois copos de água"
            assertTrue(BragaActionGate.canParse(text, BragaNluEngine.analisar(text, channel)))
            assertEquals(500, (parser.parse(text) as VoiceHealthIntent.Hydration).amountMl)
        }
    }

    @Test fun `relatos de medicamento direcionam a revisao sem liberar executor legado`() {
        for (channel in InputChannel.entries) {
            for (period in listOf("manhã", "tarde", "noite")) {
                val text = "Tomei meu remédio da $period"
                val output = BragaNluEngine.analisar(text, channel)
                assertEquals(BragaProductScope.DOSES, output.intent)
                assertTrue(output.respostaLocal!!.contains("da $period"))
                assertFalse(BragaActionGate.canParse(text, output))
                assertFalse(output.delegarParaNuvem)
            }
            for (text in listOf("Não tomei meu remédio da manhã", "Tomei meu remédio da manhã?",
                "Acho que tomei meu remédio", "Ontem tomei meu remédio")) {
                assertNotEquals(text, BragaProductScope.DOSES, BragaNluEngine.analisar(text, channel).intent)
            }
        }
    }

    @Test fun `receita medica e contexto da glicemia continuam ativos`() {
        assertEquals("orientacao_cadastro_medicamento", BragaNluEngine.analisar("Quero cadastrar medicamento pela receita").intent)
        assertNotEquals(BragaProductScope.RETIRED, BragaNluEngine.analisar("Glicemia 105 após a refeição").intent)
        assertTrue(GroqDynamicPrompt().build(emptyList()).contains(BragaProductScope.PROMPT))
    }
}
