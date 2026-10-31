package br.com.bragasaude.ai

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BragaRoutingPolicyTest {
    @Test fun `crise emocional recebe acolhimento local nos dois canais`() {
        for (channel in InputChannel.entries) listOf(
            "estou me sentindo muito mal quero morrer", "não aguento mais viver",
            "não quero mais viver", "vontade de sumir", "pensando em morrer",
            "quero tirar minha vida", "quero acabar com a minha vida", "preferia estar morta",
            "desespero total", "socorro não aguento mais", "quero me machucar",
            "automutilação", "quero cortar meus pulsos", "estou pensando em suicídio",
            "Não tenho dor no peito, mas quero morrer", "não consigo dormir, quero morrer",
            "não aguento mais, quero morrer"
        ).forEach { text ->
            val output = BragaNluEngine.analisar(text, channel)
            assertEquals(text, BragaRoutingPolicy.MENTAL_HEALTH_EMERGENCY, output.intent)
            assertEquals(text, BragaRoute.EMERGENCY, output.route)
            assertFalse(output.delegarParaNuvem)
            assertTrue(output.respostaLocal!!.contains("CVV 188"))
            assertTrue(output.respostaLocal.contains("SAMU 192"))
        }
    }

    @Test fun `quedas no infinitivo e trauma atual acionam socorro local`() {
        listOf("acabei de cair passando mal", "meu pai acabou de cair", "estou caindo",
            "me machuquei agora", "levei um tombo", "escorreguei no banheiro").forEach { text ->
            val output = BragaNluEngine.analisar(text)
            assertEquals(text, "emergencia_queda_trauma", output.intent)
            assertTrue(output.isEmergencia)
            assertFalse(output.delegarParaNuvem)
        }
    }

    @Test fun `negacao historico e prevencao nao viram crise atual`() {
        listOf("não quero morrer", "não tenho vontade de sumir", "não quero me machucar",
            "ontem tive vontade de sumir", "não acabei de cair", "ontem levei um tombo",
            "o que é automutilação?", "como prevenir suicídio?", "li a frase quero morrer no livro",
            "tenho medo de cair", "como evitar cair?", "tenho receio de me machucar"
        ).forEach { assertFalse(it, BragaNluEngine.analisar(it).isEmergencia) }
        assertTrue(BragaNluEngine.analisar("ontem tive vontade de sumir, mas agora voltou").isEmergencia)
    }

    @Test fun `crise emocional e queda nao consultam rede para texto ou voz`() = runTest {
        val hybrid = BragaHybridOrchestrator(GroqStreamSource { _, _ -> error("Emergência deve ser local") }, GroqDynamicPrompt())
        for (channel in InputChannel.entries) listOf("quero morrer", "acabei de cair passando mal").forEach {
            assertTrue(hybrid.respond(it, emptyList(), channel).toList().single() is BragaHybridEvent.Local)
        }
    }

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
