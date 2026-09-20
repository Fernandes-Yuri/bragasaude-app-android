package br.com.bragasaude.domain

import org.junit.Assert.*
import org.junit.Test

class HydrationPreferenceTest {
    // D-VOZ1: valor determinado é entregue direto na tela (Review), sem turno
    // intermediário de confirmação.

    @Test fun `confirmed cup preference computes total and fills the form directly`() {
        val dialog = HydrationConversation()
        assertEquals(HydrationConversation.Reply.Review(600), dialog.respond("bebi dois copos", "a", defaultCupMl = 300))
    }
    @Test fun `explicit volume takes precedence and does not change preference`() {
        val dialog = HydrationConversation()
        assertEquals(HydrationConversation.Reply.Review(400), dialog.respond("bebi dois copos de 200 ml", "a", defaultCupMl = 300))
    }
    @Test fun `preference does not apply to bottles or leak after account switch`() {
        val dialog = HydrationConversation()
        assertTrue((dialog.respond("bebi duas garrafas", "a", defaultCupMl = 300) as HydrationConversation.Reply.Say).text.contains("De quantos ml"))
        dialog.respond("bebi dois copos", "a", defaultCupMl = 300)
        assertTrue((dialog.respond("bebi dois copos", "b") as HydrationConversation.Reply.Say).text.contains("De quantos ml"))
    }
    @Test fun `correction after direct review does not reuse habitual cup volume`() {
        val dialog = HydrationConversation()
        dialog.respond("bebi dois copos", "a", defaultCupMl = 300)
        assertTrue((dialog.respond("na verdade foram duas garrafas", "a") as HydrationConversation.Reply.Say).text.contains("Corrija"))
    }
    @Test fun `memory request is not a hydration record`() {
        val parser = VoiceHealthParser()
        listOf("guarde que meu copo tem 300 ml", "meu copo tem 300 ml", "lembre que uso um copo de 300 ml").forEach {
            assertTrue(it, parser.parse(it) is VoiceHealthIntent.Unknown)
        }
    }
}
