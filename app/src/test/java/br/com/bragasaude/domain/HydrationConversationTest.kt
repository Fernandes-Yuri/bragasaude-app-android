package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test

class HydrationConversationTest {
    private val chat = HydrationConversation()
    private fun say(text: String) = chat.respond(text, "test-user")
    private fun speech(text: String) = (say(text) as HydrationConversation.Reply.Say).text
    private fun amount(text: String) = (say(text) as HydrationConversation.Reply.Review).amountMl

    @Test fun `asks quantity then cup size without guessing`() {
        assertTrue(speech("Bebi água").contains("Quanto"))
        assertTrue(speech("Dois copos").contains("quantos ml"))
        assertTrue(speech("Trezentos").contains("600 ml"))
        assertEquals(600, amount("sim"))
    }

    @Test fun `correction before review changes count and does not create another action`() {
        assertTrue(speech("bebi dois copos de 300 ml").contains("600 ml"))
        assertTrue(speech("espera, foram três").contains("900 ml"))
        assertEquals(900, amount("pode preparar"))
        assertTrue(say("sim") is HydrationConversation.Reply.Say)
    }

    @Test fun `correction in same sentence uses final count`() {
        assertTrue(speech("bebi dois copos de 300 ml, não, foram três").contains("900 ml"))
        assertEquals(900, amount("sim"))
    }

    @Test fun `correction after review directs to existing form instead of creating another`() {
        speech("bebi 500 ml de água")
        amount("sim")
        assertTrue(speech("não, eram 300 ml").contains("Corrija"))
        assertTrue(say("sim") is HydrationConversation.Reply.Say)
    }

    @Test fun `cancel clears pending confirmation`() {
        speech("bebi dois copos")
        assertTrue(speech("cancela").contains("descartei"))
        assertNull(say("300"))
        assertNull(say("sim"))
    }

    @Test fun `topic change invalidates previous question`() {
        speech("bebi dois copos")
        assertNull(say("que horas são"))
        assertNull(say("sim"))
        assertNull(say("300"))
    }

    @Test fun `account change and exclusive caregiver clear draft`() {
        speech("bebi dois copos")
        assertNull(chat.respond("300", "another-user"))
        speech("bebi dois copos")
        assertNull(chat.respond("300", "test-user", allowed = false))
        assertNull(say("sim"))
    }

    @Test fun `session timeout discards draft`() {
        var time = 0L
        val expiring = HydrationConversation { time }
        expiring.respond("bebi dois copos", "u")
        time = 120_001
        assertNull(expiring.respond("300", "u"))
    }

    @Test fun `reset discards pending review`() {
        speech("bebi 500 ml")
        chat.reset()
        assertNull(say("sim"))
    }

    @Test fun `decimal liters and spoken hundreds retain value`() {
        assertTrue(speech("bebi 0,5 litro de água").contains("500 ml"))
        assertEquals(500, amount("sim"))
        assertTrue(speech("bebi duzentos e cinquenta ml de água").contains("250 ml"))
        assertEquals(250, amount("sim"))
    }

    @Test fun `half liter works`() {
        assertTrue(speech("bebi meio litro de água").contains("500 ml"))
        assertEquals(500, amount("sim"))
    }

    @Test fun `conversation queries and other beverages do not start water draft`() {
        listOf("quanto de água registrei hoje", "por que devo beber água", "não bebi água", "tomei dois copos de leite", "tomei 100 ml de xarope", "meu pai bebeu dois copos", "estou com dor no peito").forEach {
            assertNull(it, say(it))
        }
    }

    @Test fun `confirmation while fields missing does not produce action`() {
        speech("bebi água")
        assertTrue(speech("sim").contains("Quanto"))
        speech("dois copos")
        assertTrue(speech("sim").contains("quantos ml"))
    }

    @Test fun `out of range does not generate review`() {
        assertTrue(speech("bebi 9000 ml de água").contains("conferir"))
        assertTrue(say("sim") is HydrationConversation.Reply.Say)
    }

    @Test fun `negative volume and multiple volumes require clarification`() {
        assertTrue(speech("bebi -500 ml de água").contains("positiva"))
        assertTrue(say("sim") is HydrationConversation.Reply.Say)
        assertTrue(speech("bebi 300 ml e 200 ml de água").contains("total"))
        assertTrue(say("sim") is HydrationConversation.Reply.Say)
    }

    @Test fun `bare number needs unit when quantity is unknown`() {
        speech("bebi água")
        assertTrue(speech("500").contains("unidade"))
        assertTrue(speech("500 ml").contains("500 ml"))
        assertEquals(500, amount("sim"))
    }

    @Test fun `short correction changes count`() {
        speech("bebi dois copos de 300 ml")
        assertTrue(speech("foram três").contains("900 ml"))
        assertEquals(900, amount("sim"))
    }

    @Test fun `bare cups still require actual size`() {
        assertTrue(speech("dois copos de água").contains("quantos ml"))
        assertTrue(speech("200 ml").contains("400 ml"))
        assertEquals(400, amount("sim"))
    }
}
