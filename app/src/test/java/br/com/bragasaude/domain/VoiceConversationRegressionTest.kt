package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test

class VoiceConversationRegressionTest {
    private val parser = VoiceHealthParser()

    @Test fun `greeting does not hide a measurement or emotional disclosure`() {
        assertTrue(parser.parse("bom dia minha pressão deu 12 por 8") is VoiceHealthIntent.BloodPressure)
        assertTrue(parser.parse("oi hoje foi um dia difícil") is VoiceHealthIntent.Unknown)
        assertTrue(parser.parse("obrigado mas preciso conversar sobre meu dia") is VoiceHealthIntent.Unknown)
    }

    @Test fun `clock and date variants are recognized without matching unrelated requests`() {
        listOf("que horas são", "Braga, me diz as horas", "qual é a hora agora", "que dia é hoje", "qual a data de hoje").forEach {
            assertTrue(it, parser.parse(it) is VoiceHealthIntent.ConversationalReply)
        }
        assertTrue(parser.parse("me fale a história do jardim") is VoiceHealthIntent.Unknown)
        assertTrue(parser.parse("qual dia você prefere") is VoiceHealthIntent.Unknown)
    }

    @Test fun `food actions are distinct from conversation`() {
        listOf("comi arroz no almoço", "você gosta de maçã", "o que posso comer hoje").forEach {
            assertTrue(it, parser.parse(it) is VoiceHealthIntent.Unknown)
        }
        listOf("registre meu almoço", "adicione chia na lista de compras").forEach {
            val reply = parser.parse(it) as VoiceHealthIntent.ConversationalReply
            assertTrue(reply.message.contains("Alimentação"))
            assertFalse(reply.message.contains("registrei", ignoreCase = true))
        }
    }
}
