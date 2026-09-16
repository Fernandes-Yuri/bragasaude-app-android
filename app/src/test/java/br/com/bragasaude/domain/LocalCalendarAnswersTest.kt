package br.com.bragasaude.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class LocalCalendarAnswersTest {
    private val clock = Clock.fixed(Instant.parse("2026-09-15T09:44:00Z"), ZoneId.of("America/Sao_Paulo"))

    @Test fun `current day month and year come from injected clock`() {
        assertEquals("Estamos em 2026.", LocalCalendarAnswers.answer("Braga, em que ano estamos?", clock))
        assertEquals("Estamos em setembro de 2026.", LocalCalendarAnswers.answer("Qual é o mês atual?", clock))
        assertEquals("Hoje é terça-feira, 15 de setembro de 2026.", LocalCalendarAnswers.answer("Você pode me dizer qual é a data de hoje?", clock))
        assertEquals("São 6 horas e 44 minutos.", LocalCalendarAnswers.answer("Que horas são agora?", clock))
    }

    @Test fun `relative dates and time zones handle year rollover`() {
        val boundary = Clock.fixed(Instant.parse("2027-01-01T01:00:00Z"), ZoneId.of("America/Sao_Paulo"))
        assertTrue(LocalCalendarAnswers.answer("que dia é hoje", boundary)!!.contains("31 de dezembro de 2026"))
        assertTrue(LocalCalendarAnswers.answer("que dia será amanhã", boundary)!!.contains("1 de janeiro de 2027"))
        assertTrue(LocalCalendarAnswers.answer("que dia foi ontem", boundary)!!.contains("30 de dezembro de 2026"))
        assertEquals("Estamos em 2027.", LocalCalendarAnswers.answer("qual o ano atual", boundary.withZone(ZoneId.of("UTC"))))
    }

    @Test fun `appointments historical questions and dosages are not answered as current date`() {
        listOf("que dia é minha consulta", "como era o calendário em maio de 2024", "tomei o remédio hoje", "qual é a data do meu exame").forEach {
            assertNull(it, LocalCalendarAnswers.answer(it, clock))
        }
    }
}
