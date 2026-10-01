package br.com.bragasaude.domain

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Date

/** Datas legadas sem offset são UTC, assim como o contrato de sincronização. */
fun parseSocialPostDate(value: String): Date {
    val instant = try {
        OffsetDateTime.parse(value).toInstant()
    } catch (_: DateTimeParseException) {
        LocalDateTime.parse(value).toInstant(ZoneOffset.UTC)
    }
    return Date.from(instant)
}

fun formatSocialPostTime(date: Date, nowMillis: Long = System.currentTimeMillis()): String {
    val minutes = (nowMillis - date.time).coerceAtLeast(0) / 60_000L
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "Agora mesmo"
        minutes == 1L -> "Há 1 minuto"
        minutes < 60 -> "Há $minutes minutos"
        hours == 1L -> "Há 1 hora"
        hours < 24 -> "Há $hours horas"
        days == 1L -> "Há 1 dia"
        days < 7 -> "Há $days dias"
        days < 30 -> if (days / 7 == 1L) "Há 1 semana" else "Há ${days / 7} semanas"
        days < 365 -> if (days / 30 == 1L) "Há 1 mês" else "Há ${days / 30} meses"
        else -> DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(java.time.ZoneId.systemDefault()).format(Instant.ofEpochMilli(date.time))
    }
}
