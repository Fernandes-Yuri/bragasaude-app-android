package br.com.bragasaude.domain

import java.time.format.DateTimeParseException
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Test

class SocialPostTimeTest {
    @Test fun `formatos do gateway preservam o instante real da publicacao`() {
        val expected = parseSocialPostDate("2026-09-30T15:20:10.123456Z")
        for (value in listOf(
            "2026-09-30T15:20:10.123456+00:00",
            "2026-09-30T12:20:10.123456-03:00",
            "2026-09-30T15:20:10.123456"
        )) {
            assertEquals(expected, parseSocialPostDate(value))
        }
        assertEquals(parseSocialPostDate("2026-09-30T15:20:10Z"), parseSocialPostDate("2026-09-30T15:20:10+00:00"))
    }

    @Test(expected = DateTimeParseException::class)
    fun `data invalida nao transforma postagem antiga em postagem recente`() {
        parseSocialPostDate("data-invalida")
    }

    @Test fun `idade usa dias e semanas a partir da data original`() {
        val now = parseSocialPostDate("2026-10-01T15:00:00Z").time
        val day = 86_400_000L
        for ((days, label) in listOf(
            1 to "Há 1 dia", 2 to "Há 2 dias", 6 to "Há 6 dias",
            7 to "Há 1 semana", 13 to "Há 1 semana", 14 to "Há 2 semanas",
            21 to "Há 3 semanas", 28 to "Há 4 semanas", 30 to "Há 1 mês"
        )) {
            assertEquals(label, formatSocialPostTime(Date(now - days * day), now))
        }
        val post = Date(now - 6 * day)
        assertEquals("Há 1 semana", formatSocialPostTime(post, now + day))
    }

    @Test fun `minutos horas e pequeno desvio de relogio tem rotulos legiveis`() {
        val now = 1_000_000_000L
        assertEquals("Agora mesmo", formatSocialPostTime(Date(now + 60_000), now))
        assertEquals("Há 1 minuto", formatSocialPostTime(Date(now - 60_000), now))
        assertEquals("Há 2 minutos", formatSocialPostTime(Date(now - 120_000), now))
        assertEquals("Há 1 hora", formatSocialPostTime(Date(now - 3_600_000), now))
        assertEquals("Há 2 horas", formatSocialPostTime(Date(now - 7_200_000), now))
    }
}
