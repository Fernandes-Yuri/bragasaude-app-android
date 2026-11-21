package br.com.bragasaude.ui.b2b

import br.com.bragasaude.domain.b2b.DirectChatMessageDto
import br.com.bragasaude.domain.b2b.ProgramAppointmentStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Helpers puros do polimento UX B2B (chat, consentimento e agenda), sem instrumentação. */
class PartnerUxPolishFormattingTest {

    private val saoPaulo: ZoneId = ZoneId.of("America/Sao_Paulo")

    private fun message(id: String, body: String, createdAt: String) = DirectChatMessageDto(
        messageId = id,
        programId = "program-1",
        patientUserId = "patient-1",
        senderUserId = "sender-1",
        senderRole = "PARTICIPANT",
        body = body,
        createdAt = createdAt
    )

    @Test
    fun `separador do chat usa hoje ontem e data completa`() {
        val today = LocalDate.of(2026, 11, 21)
        assertEquals("Hoje", directChatDayLabel(today, today))
        assertEquals("Ontem", directChatDayLabel(today.minusDays(1), today))
        assertEquals("18/11/2026", directChatDayLabel(today.minusDays(3), today))
    }

    @Test
    fun `timeline do chat agrupa mensagens por dia na ordem cronologica`() {
        val today = LocalDate.of(2026, 11, 21)
        val timeline = buildDirectChatTimeline(
            messages = listOf(
                message("m1", "Bom dia", "2026-11-20T10:00:00Z"),
                message("m2", "Tudo bem?", "2026-11-20T13:00:00Z"),
                message("m3", "Hoje é dia de caminhada", "2026-11-21T11:00:00Z")
            ),
            today = today,
            zone = saoPaulo
        )

        assertEquals(5, timeline.size)
        assertEquals(
            "Ontem",
            (timeline[0] as DirectChatTimelineItem.DaySeparator).label
        )
        assertEquals("m1", (timeline[1] as DirectChatTimelineItem.Message).message.messageId)
        assertEquals("m2", (timeline[2] as DirectChatTimelineItem.Message).message.messageId)
        assertEquals(
            "Hoje",
            (timeline[3] as DirectChatTimelineItem.DaySeparator).label
        )
        assertEquals("m3", (timeline[4] as DirectChatTimelineItem.Message).message.messageId)
    }

    @Test
    fun `timeline do chat preserva mensagens sem timestamp legivel`() {
        val timeline = buildDirectChatTimeline(
            messages = listOf(
                message("m1", "Sem data", ""),
                message("m2", "Com data", "2026-11-21T11:00:00Z")
            ),
            today = LocalDate.of(2026, 11, 21),
            zone = saoPaulo
        )

        assertEquals(4, timeline.size)
        assertEquals(
            "Mensagens sem data",
            (timeline[0] as DirectChatTimelineItem.DaySeparator).label
        )
        assertEquals("Hoje", (timeline[2] as DirectChatTimelineItem.DaySeparator).label)
    }

    @Test
    fun `status da agenda tem rotulo curto e visual`() {
        assertEquals("Pendente", appointmentStatusVisualLabel(ProgramAppointmentStatus.PENDING))
        assertEquals("Confirmado", appointmentStatusVisualLabel(ProgramAppointmentStatus.CONFIRMED))
        assertEquals("Reagendar", appointmentStatusVisualLabel(ProgramAppointmentStatus.RESCHEDULE_REQUESTED))
        assertEquals("Cancelado", appointmentStatusVisualLabel(ProgramAppointmentStatus.CANCELLED))
        assertEquals("Concluído", appointmentStatusVisualLabel(ProgramAppointmentStatus.COMPLETED))
    }

    @Test
    fun `horario confirmado nao se confunde com preferencia informada`() {
        assertEquals(
            "Confirmado para 22/11/2026 às 14:00",
            appointmentScheduleHeadline(
                ProgramAppointmentStatus.CONFIRMED,
                "22/11/2026 às 14:00"
            )
        )
        assertEquals(
            "Novo horário sugerido: 23/11/2026 às 09:00",
            appointmentScheduleHeadline(
                ProgramAppointmentStatus.RESCHEDULE_REQUESTED,
                "23/11/2026 às 09:00"
            )
        )
        assertEquals(
            "Aguardando confirmação do programa",
            appointmentScheduleHeadline(ProgramAppointmentStatus.PENDING, null)
        )
        assertEquals(
            "Atendimento cancelado",
            appointmentScheduleHeadline(ProgramAppointmentStatus.CANCELLED, null)
        )
    }

    @Test
    fun `preferencia informada sempre vem rotulada`() {
        assertEquals(
            "Preferência informada: quinta pela manhã",
            appointmentPreferenceLine("  quinta pela manhã  ")
        )
        assertNull(appointmentPreferenceLine("   "))
        assertNull(appointmentPreferenceLine(null))
        assertEquals("Duração a combinar", appointmentDurationLabel())
    }

    @Test
    fun `periodo solicitado vira texto humano e cai no texto livre como fallback`() {
        assertEquals(
            "Próxima semana · manhã",
            appointmentPeriodHumanLabel("PROXIMA_SEMANA_MANHA")
        )
        // O gateway normaliza preservando acentos; o mapeador aceita as duas grafias.
        assertEquals(
            "Próxima semana · manhã",
            appointmentPeriodHumanLabel("próxima semana manhã")
        )
        assertEquals("Próxima semana", appointmentPeriodHumanLabel("PRÓXIMA_SEMANA"))
        assertEquals("Esta semana · tarde", appointmentPeriodHumanLabel("esta-semana-tarde"))
        assertEquals("Qualquer horário", appointmentPeriodHumanLabel("INDIFERENTE"))
        assertNull(appointmentPeriodHumanLabel("CODIGO_DESCONHECIDO"))
        assertNull(appointmentPeriodHumanLabel("   "))

        assertEquals(
            "Preferência informada: Próxima semana · tarde",
            appointmentPreferenceLine("quinta à tarde", "PROXIMA_SEMANA_TARDE")
        )
        assertEquals(
            "Preferência informada: quinta à tarde",
            appointmentPreferenceLine("  quinta à tarde  ", "CODIGO_DESCONHECIDO")
        )
        assertEquals(
            "Preferência informada: Próxima semana",
            appointmentPreferenceLine(null, "PROXIMA_SEMANA")
        )
        assertNull(appointmentPreferenceLine(null, null))
    }

    @Test
    fun `status de consentimento tem rotulo humano e fallback pelas categorias`() {
        assertEquals(
            "Consentimento completo",
            participantConsentStatusLabel("COMPLETE", listOf("steps", "hydration"))
        )
        assertEquals(
            "Consentimento parcial",
            participantConsentStatusLabel("PARTIAL", listOf("steps"))
        )
        assertEquals(
            "Sem categorias autorizadas",
            participantConsentStatusLabel("NONE", emptyList())
        )
        assertEquals(
            "Sem categorias autorizadas",
            participantConsentStatusLabel(null, emptyList())
        )
        assertEquals(
            "Consentimento a confirmar",
            participantConsentStatusLabel(null, listOf("steps"))
        )
    }

    @Test
    fun `normalizacao de consentimento reconcilia status e categorias`() {
        assertEquals("NONE", normalizedConsentStatus("desconhecido", emptyList()))
        assertEquals("COMPLETE", normalizedConsentStatus(" complete ", emptyList()))
        assertTrue(
            normalizedConsentStatus(null, listOf("hydration")) == "UNKNOWN"
        )
    }
}
