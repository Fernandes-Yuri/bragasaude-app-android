package br.com.bragasaude.ui.b2b

import br.com.bragasaude.domain.b2b.PartnerApprovalStatus
import br.com.bragasaude.domain.b2b.PartnerParticipantItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Copy/estado da aba Empresa e totais dos badges da barra. Helpers puros para
 * rodar no JVM, sem Compose/instrumentação.
 */
class PartnerCompanyKybBadgeTest {

    private fun participant(id: String, pending: Int, unread: Int) =
        PartnerParticipantItemDto(
            patientUserId = id,
            displayName = "Paciente $id",
            pendingAppointmentsCount = pending,
            unreadDirectMessagesCount = unread
        )

    @Test
    fun `kyb verde conclui e demais estados seguem em andamento`() {
        assertEquals("Verificação KYB concluída", companyKybSummaryLine(2, "GREEN"))
        assertEquals("Verificação KYB concluída", companyKybSummaryLine(0, " green "))
        assertEquals(
            "Documentos: 0 de 2 enviados · verificação KYB em andamento",
            companyKybSummaryLine(0, null)
        )
        assertEquals(
            "Documentos: 1 de 2 enviados · verificação KYB em andamento",
            companyKybSummaryLine(1, "PENDING")
        )
    }

    @Test
    fun `aprovado sem documentos explica que kyb nao bloqueia convite qr nem pdf`() {
        assertEquals(
            "Empresa aprovada sem comprovantes anexados: a verificação KYB não bloqueia convite, QR Code nem placa PDF.",
            companyKybSupportingLine(0, null, PartnerApprovalStatus.APPROVED)
        )
        assertEquals(
            "A verificação KYB não bloqueia convite, QR Code nem placa PDF.",
            companyKybSupportingLine(2, "MANUAL_REVIEW", PartnerApprovalStatus.APPROVED)
        )
        assertNull(companyKybSupportingLine(0, "GREEN", PartnerApprovalStatus.APPROVED))
        assertNull(companyKybSupportingLine(0, null, PartnerApprovalStatus.PENDING_REVIEW))
    }

    @Test
    fun `badge de agendamentos prioriza total global e cai na soma atual`() {
        // O total global do gateway (PENDING + RESCHEDULE_REQUESTED) prevalece.
        assertEquals(7, pendingAppointmentsBadgeTotal(7, fallbackTotal = 3))
        // Payload antigo/sem pendência chega como 0: mantém exatamente o total atual.
        assertEquals(3, pendingAppointmentsBadgeTotal(0, fallbackTotal = 3))
        assertEquals(3, pendingAppointmentsBadgeTotal(null, fallbackTotal = 3))
        // Contador inválido não esconde a pendência nem expõe número negativo.
        assertEquals(3, pendingAppointmentsBadgeTotal(-1, fallbackTotal = 3))
        assertEquals(0, pendingAppointmentsBadgeTotal(null, fallbackTotal = -2))
    }

    @Test
    fun `badge de mensagens prioriza total global e cai na soma da pagina`() {
        val participants = listOf(
            participant("p1", pending = 0, unread = 2),
            participant("p2", pending = 0, unread = 4)
        )
        assertEquals(9, unreadMessagesBadgeTotal(9, participants))
        assertEquals(6, unreadMessagesBadgeTotal(0, participants))
        assertEquals(6, unreadMessagesBadgeTotal(null, participants))
        assertEquals(6, unreadMessagesBadgeTotal(-2, participants))
    }
}
