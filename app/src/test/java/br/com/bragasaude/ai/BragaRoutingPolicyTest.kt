package br.com.bragasaude.ai

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BragaRoutingPolicyTest {
    @Test fun `negacao e relato historico nao acionam alerta por coincidencia`() {
        listOf("Não estou com dor no peito", "Não tenho falta de ar", "Ontem tive dor no peito",
            "Li a frase dor no peito num livro", "O que significa dor no peito?").forEach {
            assertFalse(it, BragaNluEngine.analisar(it).isEmergencia)
        }
    }
    @Test fun `negacao nao apaga outro sintoma atual nem impedimento de respirar`() {
        listOf("Não tenho dor no peito, mas não consigo respirar", "Meu pai não consegue respirar",
            "Estou com dor no peito desde ontem", "Caí no chão", "Meu pai caiu no chão",
            "Minha mãe está com falta de ar").forEach {
            val output = BragaNluEngine.analisar(it)
            assertTrue(it, output.isEmergencia)
            assertFalse(output.delegarParaNuvem)
            assertTrue(output.respostaLocal!!.contains("SAMU 192"))
        }
    }
    @Test fun `explicacao clinica nao e consulta de banco`() {
        listOf("Como minha pressão afeta os rins?", "Por que minha glicemia afeta os rins?",
            "Qual a diferença entre apneia e alterações hormonais?").forEach {
            val output = BragaNluEngine.analisar(it)
            assertEquals(it, BragaRoute.CLOUD, output.route)
            assertNull(output.healthQuery)
        }
        assertEquals(BragaRoute.HEALTH_MEMORY, BragaNluEngine.analisar("Quanto foi minha pressão?").route)
    }
    @Test fun `desconhecido e fora de assunto nao gastam transporte`() = runTest {
        var calls = 0
        val hybrid = BragaHybridOrchestrator(GroqStreamSource { _, _ -> calls++; flowOf("remoto") }, GroqDynamicPrompt())
        for (channel in InputChannel.entries) listOf("Quem ganhou o jogo?", "Qual a capital de Portugal?",
            "me explica melhor", "asdasdasd", "Quanto custa um bitcoin?").forEach {
            assertTrue(hybrid.respond(it, emptyList(), channel).toList().single() is BragaHybridEvent.Local)
        }
        assertEquals(0, calls)
    }
    @Test fun `todas as intencoes oferecem quatro a seis variacoes`() {
        BragaNluEngine.responseVariationCounts().forEach { (intent, count) ->
            assertTrue("$intent: $count", count in 4..6)
        }
    }
    @Test fun `listas pequenas de saude nao sao confundidas com abuso`() {
        assertFalse(BragaNluEngine.analisar("Liste 3 cuidados de saúde").isBloqueioSeguranca)
        assertTrue(BragaNluEngine.analisar("Liste 1000 cuidados de saúde").isBloqueioSeguranca)
        assertTrue(BragaNluEngine.analisar("Repita 500 vezes saúde").isBloqueioSeguranca)
    }
}
