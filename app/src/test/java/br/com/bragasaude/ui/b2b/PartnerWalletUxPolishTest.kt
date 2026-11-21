package br.com.bragasaude.ui.b2b

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Copy e acessibilidade novas do polimento da carteira/operação. Os helpers são
 * puros para rodar no JVM, sem Compose/instrumentação.
 */
class PartnerWalletUxPolishTest {

    @Test
    fun `resumo falado de participantes usa plural real`() {
        assertEquals("Participantes: 1 ativo e 2 inativos.", walletMembersSpoken(1, 2))
        assertEquals("Participantes: 0 ativos e 0 inativos.", walletMembersSpoken(0, 0))
        assertEquals("Participantes: 2 ativos e 1 inativo.", walletMembersSpoken(2, 1))
    }

    @Test
    fun `resumo falado de agendamentos usa leitura do delta sem seta`() {
        assertEquals(
            "Agendamentos confirmados: 3. Sem base na semana anterior.",
            walletAppointmentsSpoken(3, null)
        )
        assertTrue(walletAppointmentsSpoken(5, -2).contains("Redução de 2"))
        assertTrue(walletAppointmentsSpoken(5, 2).contains("Aumento de 2"))
    }

    @Test
    fun `retencao falada cobre ausencia de base e percentual pt br`() {
        assertEquals("Retenção: sem base na semana anterior.", walletRetentionSpoken(null))
        assertEquals("Retenção: 70,0%.", walletRetentionSpoken(70.0))
    }

    @Test
    fun `lembretes lidos falam leitura no singular e plural`() {
        assertEquals("Lembretes lidos: 1 leitura.", walletRemindersSpoken(1))
        assertEquals("Lembretes lidos: 0 leituras.", walletRemindersSpoken(0))
    }

    @Test
    fun `atividade falada nomeia pico ultimo dia e periodo`() {
        val spoken = walletActivitySpoken(listOf(1, 4, 2), 7)
        assertTrue(spoken.contains("pico de 4"))
        assertTrue(spoken.contains("2 no último dia"))
        assertTrue(spoken.contains("7 dias"))
        assertEquals("Atividade diária: série indisponível.", walletActivitySpoken(null, 7))
    }

    @Test
    fun `badge da barra fica nulo sem pendencia e limita em 99 mais`() {
        assertNull(navBadgeLabel(0))
        assertNull(navBadgeLabel(-3))
        assertEquals("1", navBadgeLabel(1))
        assertEquals("99", navBadgeLabel(99))
        assertEquals("99+", navBadgeLabel(100))
    }

    @Test
    fun `descricao da sparkline inclui periodo e serie completa`() {
        val spoken = sparklineAccessibilityDescription(listOf(1, 2, 3), 7)
        assertTrue(spoken.contains("no período de 7 dias"))
        assertTrue(spoken.contains("1, 2, 3"))
        assertEquals("Sem atividade no período.", sparklineAccessibilityDescription(emptyList(), 7))
    }
}
