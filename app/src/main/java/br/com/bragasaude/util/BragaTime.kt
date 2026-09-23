package br.com.bragasaude.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Date

/**
 * Regra inegociável do Care OS: TODA operação de data/hora do app opera no fuso
 * de São Paulo (America/Sao_Paulo, UTC-3), independente do fuso do aparelho.
 * O gateway PostgreSQL grava timestamptz; o app always fala UTC-3 pontualmente.
 */
object BragaTime {

    val ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")

    private val isoFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")

    /** Agora, pontualmente em São Paulo. */
    fun now(): ZonedDateTime = ZonedDateTime.now(ZONE)

    /** epoch-millis do instante atual. */
    fun nowMillis(): Long = Instant.now().toEpochMilli()

    /** Formata um epoch-millis em ISO-8601 com offset UTC-3 (o que o gateway aceita). */
    fun toIso(epochMillis: Long): String =
        isoFormatter.format(Instant.ofEpochMilli(epochMillis).atZone(ZONE))

    /** Formata um Date em ISO-8601 UTC-3. */
    fun toIso(date: Date): String = toIso(date.time)

    /** Início do dia atual (meia-noite, São Paulo) como epoch-millis. */
    fun startOfTodayMillis(): Long =
        now().toLocalDate().atStartOfDay(ZONE).toInstant().toEpochMilli()

    /** Data local (São Paulo) formatada como yyyy-MM-dd. */
    fun todayDate(): String = now().toLocalDate().toString()

    /**
     * Calcula os dias restantes de estoque: unidades atuais / doses por dia.
     * Nunca arredonda para cima — reposição é alertada por antecipação.
     */
    fun daysRemaining(currentUnits: Int, dosesPerDay: Double): Double {
        if (dosesPerDay <= 0.0) return Double.POSITIVE_INFINITY
        return currentUnits / dosesPerDay
    }

    /** Compõe um LocalDateTime São Paulo a partir de data + horário "HH:mm". */
    fun atTime(date: LocalDate, time: LocalTime): LocalDateTime =
        LocalDateTime.of(date, time).atZone(ZONE).toLocalDateTime()

    /** ISO-8601 UTC-3 de uma data + horário "HH:mm" (camada de agendamento). */
    fun toIso(date: LocalDate, time: LocalTime): String =
        isoFormatter.format(LocalDateTime.of(date, time).atZone(ZONE))
}
