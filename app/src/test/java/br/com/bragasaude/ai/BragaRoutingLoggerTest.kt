package br.com.bragasaude.ai

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class BragaRoutingLoggerTest {

    @Before
    fun setup() {
        BragaRoutingLogger.clear()
    }

    @Test
    fun `registra eventos de roteamento e gera relatorio estruturado`() {
        assertEquals(0, BragaRoutingLogger.logs.value.size)

        BragaRoutingLogger.record(
            channel = InputChannel.TEXT,
            input = "qual foi minha pressão",
            decision = RoutingLogEntry.RoutingDecision.LOCAL_NLU,
            intent = "consulta_historico_pressao",
            reason = "Consulta Room local",
            durationMs = 2L,
            previewResponse = "Sua última pressão foi 12 por 8"
        )

        BragaRoutingLogger.record(
            channel = InputChannel.VOICE,
            input = "o que é hipertensão arterial",
            decision = RoutingLogEntry.RoutingDecision.CLOUD_LLM,
            intent = "conversa_incompreendida_fallback",
            reason = "Roteado para Groq Cloud",
            durationMs = 350L,
            previewResponse = "Hipertensão é a elevação persistente..."
        )

        val logs = BragaRoutingLogger.logs.value
        assertEquals(2, logs.size)
        assertEquals("o que é hipertensão arterial", logs[0].input)
        assertEquals(RoutingLogEntry.RoutingDecision.CLOUD_LLM, logs[0].decision)
        assertEquals("qual foi minha pressão", logs[1].input)
        assertEquals(RoutingLogEntry.RoutingDecision.LOCAL_NLU, logs[1].decision)

        val report = BragaRoutingLogger.exportReport()
        assertTrue(report.contains("Total de Interações: 2"))
        assertTrue(report.contains("Locais (NLU On-Device): 1 (50.0%)"))
        assertTrue(report.contains("Nuvem (Groq LLM): 1"))
        assertTrue(report.contains("qual foi minha pressão"))
        assertTrue(report.contains("o que é hipertensão arterial"))
    }

    @Test
    fun `perguntas simples sociais sao atendidas pelo NLU local sem cair no LLM`() {
        val obrigado = BragaNluEngine.analisar("muito obrigado braga")
        assertEquals("conversa_agradecimento", obrigado.intent)
        assertFalse(obrigado.delegarParaNuvem)
        assertNotNull(obrigado.respostaLocal)

        val quem = BragaNluEngine.analisar("quem é você?")
        assertEquals("conversa_apresentacao_assistente", quem.intent)
        assertFalse(quem.delegarParaNuvem)

        val comoEsta = BragaNluEngine.analisar("como você tá?")
        assertEquals("conversa_como_esta_assistente", comoEsta.intent)
        assertFalse(comoEsta.delegarParaNuvem)

        val ok = BragaNluEngine.analisar("ok")
        assertEquals("conversa_confirmacao_compreensao", ok.intent)
        assertFalse(ok.delegarParaNuvem)
    }
}
