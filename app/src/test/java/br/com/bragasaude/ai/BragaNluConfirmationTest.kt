package br.com.bragasaude.ai

import org.junit.Assert.*
import org.junit.Test

class BragaNluConfirmationTest {
    @Test fun `confirmacoes simples e compostas encerram a explicacao localmente`() {
        for (channel in InputChannel.entries) {
            listOf("entendi", "legal", "Entendi, legal!", "ok, entendi", "entendi e legal",
                "agora entendi", "beleza, ficou claro", "ótimo", "tá bom", "jóia", "faz sentido",
                "entendi. Legal!", "certo; combinado").forEach { text ->
                val output = BragaNluEngine.analisar(text, channel)
                assertEquals(text, "conversa_confirmacao_compreensao", output.intent)
                assertEquals(text, BragaRoute.LOCAL_CONVERSATION, output.route)
                assertFalse(text, output.delegarParaNuvem)
                assertNull(text, output.healthQuery)
                assertFalse(text, output.respostaLocal!!.contains("?"))
            }
        }
    }

    @Test fun `confirmacao nao oculta pergunta consulta ou sintoma`() {
        assertEquals(BragaRoute.CLOUD,
            BragaNluEngine.analisar("Entendi, mas por que minha glicemia afeta os rins?").route)
        assertEquals(BragaRoute.HEALTH_MEMORY,
            BragaNluEngine.analisar("Legal, quanto foi minha pressão?").route)
        assertTrue(BragaNluEngine.analisar("Entendi, mas estou com dor no peito").isEmergencia)
        listOf("não entendi", "não ficou claro", "legal?", "entendi?", "legal, mas tenho uma dúvida",
            "meu dia foi legal", "entendi, anota isso").forEach { text ->
            assertNotEquals(text, "conversa_confirmacao_compreensao", BragaNluEngine.analisar(text).intent)
        }
    }
}
