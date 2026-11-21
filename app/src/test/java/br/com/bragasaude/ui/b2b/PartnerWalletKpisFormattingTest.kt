package br.com.bragasaude.ui.b2b

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

/** Textos dos cards de KPI: delta nulo nunca vira "0" nem "comparação indisponível". */
class PartnerWalletKpisFormattingTest {

    @Test
    fun `delta ausente fala sem base na semana anterior`() {
        assertEquals("Sem base na semana anterior", appointmentsDeltaLabel(null))
        assertEquals("Sem base na semana anterior.", appointmentsDeltaSpoken(null))
    }

    @Test
    fun `delta zero indica igualdade`() {
        assertEquals("Igual à semana anterior", appointmentsDeltaLabel(0))
        assertEquals("Igual à semana anterior.", appointmentsDeltaSpoken(0))
    }

    @Test
    fun `delta positivo usa seta para cima e leitura de aumento`() {
        assertEquals("▲ +2 em relação à semana anterior", appointmentsDeltaLabel(2))
        assertEquals("Aumento de 2 em relação à semana anterior.", appointmentsDeltaSpoken(2))
    }

    @Test
    fun `delta negativo usa seta para baixo e leitura de reducao`() {
        assertEquals("▼ -2 em relação à semana anterior", appointmentsDeltaLabel(-2))
        assertEquals("Redução de 2 em relação à semana anterior.", appointmentsDeltaSpoken(-2))
    }

    @Test
    fun `lembretes sem total enviado mantem contagem e sem percentual`() {
        assertNull(remindersReadPercent(2, 0))
        assertEquals("Sem lembretes enviados no período", remindersReadLabel(2, 0))
        assertEquals(
            "Lembretes lidos: 2 leituras. Sem lembretes enviados no período.",
            walletRemindersSpoken(2, 0)
        )
    }

    @Test
    fun `lembretes com total enviado mostram progresso e percentual falado`() {
        assertEquals("12 de 20 lidos", remindersReadLabel(12, 20))
        assertEquals(60, remindersReadPercent(12, 20))
        assertEquals(
            "12 de 20 lembretes lidos, 60%",
            walletRemindersSpoken(12, 20)
        )
    }

    @Test
    fun `leitura acima do total e clampada antes do percentual`() {
        assertEquals(4, remindersReadClamped(9, 4))
        assertEquals(100, remindersReadPercent(9, 4))
        assertEquals("4 de 4 lidos", remindersReadLabel(9, 4))
        assertEquals(
            "4 de 4 lembretes lidos, 100%",
            walletRemindersSpoken(9, 4)
        )
    }

    @Test
    fun `total unitario usa singular no texto e na leitura falada`() {
        assertEquals("1 de 1 lido", remindersReadLabel(1, 1))
        assertEquals(
            "1 de 1 lembrete lido, 100%",
            walletRemindersSpoken(1, 1)
        )
    }

    @Test
    fun `pendentes para voce usam plural real`() {
        assertEquals("1 pendente para você", pendingAppointmentsLabel(1))
        assertEquals("2 pendentes para você", pendingAppointmentsLabel(2))
        assertEquals(
            "Agendamentos confirmados: 3. Sem base na semana anterior. 2 pendentes para você.",
            walletAppointmentsSpoken(3, null, 2)
        )
    }

    @Test
    fun `mensagens nao lidas usam plural e entram na leitura de participantes`() {
        assertEquals("1 mensagem não lida", unreadMessagesLabel(1))
        assertEquals("3 mensagens não lidas", unreadMessagesLabel(3))
        assertEquals(
            "Participantes: 2 ativos e 1 inativo. 3 mensagens não lidas.",
            walletMembersSpoken(2, 1, 3)
        )
    }

    @Test
    fun `periodo da carteira usa fuso de Brasilia e rotulo local`() {
        val label = walletPeriodLabel("2026-11-21T02:11:31Z", 7)
        assertTrue(label.contains("Atividade no programa · 7 dias"))
        assertTrue(label.contains("20/11/2026"))
        assertTrue(label.contains("horário de Brasília"))
        assertEquals("Atividade no programa · 7 dias", walletPeriodLabel(null, 7))
    }
}
