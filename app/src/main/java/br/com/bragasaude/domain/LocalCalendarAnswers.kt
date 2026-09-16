package br.com.bragasaude.domain

import java.text.Normalizer
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Respostas temporais vêm do relógio e fuso atuais do aparelho, nunca do modelo. */
object LocalCalendarAnswers {
    fun answer(raw: String, clock: Clock = Clock.systemDefaultZone()): String? {
        val text = Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\bbraga\\b"), "").replace(Regex("\\s+"), " ").trim()
            .removePrefix("por favor ").removeSuffix(" por favor").trim()
            .replace(Regex("^(?:(?:voce )?pode me (?:dizer|falar|informar)|me (?:diz|diga|fale|informe)) "), "")
        val now = ZonedDateTime.now(clock)
        val locale = Locale.forLanguageTag("pt-BR")
        val fullDate = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", locale)
        return when {
            text.matches(Regex("(?:que horas (?:sao|sao agora)|qual (?:e )?a hora(?: atual| agora)?|as horas|a hora|hora exata)")) -> {
                val hour = if (now.hour == 1) "É 1 hora" else "São ${now.hour} horas"
                if (now.minute == 0) "$hour." else "$hour e ${now.minute} ${if (now.minute == 1) "minuto" else "minutos"}."
            }
            text.matches(Regex("(?:que dia (?:e )?hoje|qual (?:e )?(?:o dia|a data)(?: de hoje| hoje| atual)?|a data de hoje|data de hoje|dia de hoje|em que dia estamos|que dia estamos|qual (?:e )?o dia da semana(?: hoje)?|hoje e que dia(?: da semana)?)")) -> "Hoje é ${now.format(fullDate)}."
            text.matches(Regex("(?:(?:em )?que ano (?:estamos|e hoje)|qual (?:e )?o ano(?: atual| hoje)?|estamos em que ano)")) -> "Estamos em ${now.year}."
            text.matches(Regex("(?:(?:em )?que mes (?:estamos|e hoje)|qual (?:e )?o mes(?: atual| hoje)?|estamos em que mes)")) -> "Estamos em ${now.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", locale))}."
            text.matches(Regex("(?:que dia (?:e|sera) amanha|qual (?:e|sera) a data de amanha)")) -> "Amanhã será ${now.plusDays(1).format(fullDate)}."
            text.matches(Regex("(?:que dia (?:era|foi) ontem|qual (?:era|foi) a data de ontem)")) -> "Ontem foi ${now.minusDays(1).format(fullDate)}."
            else -> null
        }
    }
}
