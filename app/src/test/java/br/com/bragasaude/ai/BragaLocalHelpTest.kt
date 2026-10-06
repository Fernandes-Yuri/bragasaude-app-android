package br.com.bragasaude.ai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class BragaLocalHelpTest {
    private fun orchestrator() = BragaHybridOrchestrator(
        GroqStreamSource { _, _ -> error("Ajuda local não deve chamar a nuvem") }, GroqDynamicPrompt())

    @Test fun `duvidas sobre funcoes do app sao atendidas em texto e voz sem transporte`() = runTest {
        val examples = mapOf(
            "Como medir minha pressão?" to "ajuda_medicao_pressao",
            "Onde vejo meu histórico?" to "ajuda_historico_app",
            "Como gerar um relatório em PDF?" to "ajuda_relatorio_app",
            "Como mudo minha meta de água?" to "ajuda_meta_agua",
            "Onde altero o horário do remédio?" to "ajuda_horario_remedio",
            "Por que não aparece meu registro?" to "ajuda_registro_sem_dados",
            "Entendi, mas por que minha pressão subiu?" to "explicacao_variacao_pressao",
            "Legal, por que minha glicemia baixou?" to "explicacao_variacao_glicemia")
        for (channel in InputChannel.entries) examples.forEach { (text, intent) ->
            val event = orchestrator().respond(text, emptyList(), channel).toList().single()
            assertTrue(text, event is BragaHybridEvent.Local)
            assertEquals(text, intent, (event as BragaHybridEvent.Local).output.intent)
            assertNull(event.output.healthQuery)
        }
    }

    @Test fun `pergunta curta usa somente contexto recente do dono e da conversa`() {
        var now = 1_000L
        val session = HealthQuerySession { now }
        val hybrid = orchestrator()
        fun ask(user: String = "u", conversation: String = "c") =
            hybrid.analyze("Entendi, mas por que subiu?", InputChannel.TEXT, session, user, conversation)
        session.advanceTurn("u", "c")
        session.remember(HealthQuery(HealthMetric.PRESSURE), "u", "c")
        assertEquals("explicacao_variacao_pressao", ask().intent)
        assertEquals("conversa_confirmacao_compreensao",
            hybrid.analyze("entendi, legal", InputChannel.TEXT, session, "u", "c").intent)
        assertEquals("entrada_variacao_sem_referencia", ask("outro").intent)
        session.advanceTurn("u", "c")
        session.remember(HealthQuery(HealthMetric.GLUCOSE), "u", "c")
        assertEquals("explicacao_variacao_glicemia", ask().intent)
        assertEquals("entrada_variacao_sem_referencia", ask(conversation = "nova").intent)
        session.advanceTurn("u", "c")
        session.remember(HealthQuery(HealthMetric.PRESSURE), "u", "c")
        now += 5 * 60_000L
        assertEquals("entrada_variacao_sem_referencia", ask().intent)
        session.remember(HealthQuery(HealthMetric.PRESSURE), "u", "c")
        repeat(3) { session.advanceTurn("u", "c") }
        assertEquals("entrada_variacao_sem_referencia", ask().intent)
    }

    @Test fun `escopo fechado preserva emergencias consultas e perguntas complexas`() {
        val hybrid = orchestrator()
        val session = HealthQuerySession { 1_000L }
        session.advanceTurn("u", "c")
        session.remember(HealthQuery(HealthMetric.PRESSURE), "u", "c")
        fun ask(text: String) = hybrid.analyze(text, InputChannel.TEXT, session, "u", "c")
        assertTrue(ask("Entendi, mas estou com dor no peito").isEmergencia)
        assertEquals(BragaRoute.CLOUD, ask("Por que minha pressão afeta os rins?").route)
        assertEquals(BragaRoute.HEALTH_MEMORY, ask("Qual foi minha pressão ontem?").route)
        assertEquals(BragaRoute.HEALTH_MEMORY, ask("E ontem?").route)
        assertNotEquals("explicacao_variacao_pressao", ask("Por que a glicemia subiu?").intent)
        assertNull(BragaLocalHelp.answer("Por que a bolsa subiu?", HealthQuery(HealthMetric.PRESSURE)))
        assertNull(BragaLocalHelp.answer("Não entendi por que subiu", HealthQuery(HealthMetric.PRESSURE)))
        assertNull(BragaLocalHelp.answer("Por que minha pressão subiu depois de dobrar meu remédio?"))
    }
}
