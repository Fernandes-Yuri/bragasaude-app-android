package br.com.bragasaude.ai

import br.com.bragasaude.domain.VoiceHealthParser
import br.com.bragasaude.domain.VoiceHealthIntent
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class BragaCloudFallbackTest {
    @Test fun `emergencias confirmacoes ajuda e consultas permanecem locais nos dois canais`() = runTest {
        var calls = 0
        val hybrid = BragaHybridOrchestrator(GroqStreamSource { _, _ -> calls++; flowOf("remota") }, GroqDynamicPrompt())
        for (channel in InputChannel.entries) {
            listOf("estou com dor no peito", "não consigo respirar", "Caí no chão",
                "entendi, legal", "Como está minha pressão?", "Como gerar um relatório em PDF?",
                "eu tô passando mal").forEach { input ->
                val local = hybrid.analyze(input, channel)
                val final = BragaCloudFallback.afterLocal(input, local)
                assertFalse(input, final.delegarParaNuvem)
                assertTrue(hybrid.respond(input, emptyList(), channel, decision = final).toList().single() is BragaHybridEvent.Local)
            }
        }
        assertEquals(0, calls)
    }

    @Test fun `acao reconhecida pelo parser impede fallback e conserva revisao`() {
        val input = "bebi 250 ml de água"
        val parsed = VoiceHealthParser().parse(input)
        assertTrue(parsed is VoiceHealthIntent.Hydration)
        val local = BragaNluEngine.analisar(input)
        assertFalse(BragaCloudFallback.afterLocal(input, local, handledLocally = true).delegarParaNuvem)
    }

    @Test fun `pedido nao resolvido chama api uma vez com contexto nos dois canais`() = runTest {
        for (channel in InputChannel.entries) {
            var calls = 0
            val hybrid = BragaHybridOrchestrator(GroqStreamSource { prompt, message ->
                calls++
                assertEquals("tem certeza disso?", message)
                assertTrue(prompt.contains("A pressão varia"))
                assertTrue(prompt.contains("entrada_sem_clareza"))
                flowOf("Vamos esclarecer a informação anterior.")
            }, GroqDynamicPrompt())
            val final = BragaCloudFallback.afterLocal("tem certeza disso?", hybrid.analyze("tem certeza disso?", channel))
            val events = hybrid.respond("tem certeza disso?", listOf("assistant" to "A pressão varia"), channel, decision = final).toList()
            assertEquals(1, calls)
            assertTrue(events.last() is BragaHybridEvent.Completed)
            assertFalse(events.any { it is BragaHybridEvent.Local })
        }
    }

    @Test fun `reformulacao apos api envia a resposta anterior ao transporte`() = runTest {
        val session = HealthQuerySession()
        val turn = session.advanceTurn("dono", "conversa")
        session.rememberExternalReply("A explicação anterior sobre alimentação", "dono", "conversa", turn)
        session.advanceTurn("dono", "conversa")
        var calls = 0
        val hybrid = BragaHybridOrchestrator(GroqStreamSource { prompt, _ ->
            calls++
            assertTrue(prompt.contains("A explicação anterior sobre alimentação"))
            flowOf("Explicação mais simples.")
        }, GroqDynamicPrompt())
        val local = hybrid.analyze("explica melhor", InputChannel.VOICE, session, "dono", "conversa")
        val final = BragaCloudFallback.afterLocal("explica melhor", local)
        hybrid.respond("explica melhor", listOf("assistant" to "A explicação anterior sobre alimentação"), decision = final).toList()
        assertEquals(1, calls)
    }

    @Test fun `falha da api nao repete pedido nem chama transporte novamente`() = runTest {
        var calls = 0
        val hybrid = BragaHybridOrchestrator(GroqStreamSource { _, _ -> calls++; flow { throw IOException("offline") } }, GroqDynamicPrompt())
        val final = BragaCloudFallback.afterLocal("tem certeza disso?", hybrid.analyze("tem certeza disso?"))
        try {
            hybrid.respond("tem certeza disso?", emptyList(), decision = final).toList()
            fail("Deveria informar falha de transporte")
        } catch (_: IOException) { assertEquals(1, calls) }
    }

    @Test fun `mal estar passado negado ou educativo nao dispara emergencia`() {
        listOf("ontem eu estava passando mal", "não estou passando mal", "o que fazer se alguém passar mal?")
            .forEach { assertFalse(it, BragaNluEngine.analisar(it).isEmergencia) }
        assertEquals("sintoma_mal_estar_atual", BragaNluEngine.analisar("eu tô passando mal").intent)
        assertTrue(BragaNluEngine.analisar("eu tô passando mal e não consigo respirar").isEmergencia)
    }
}
