package br.com.bragasaude.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

/** Horários diários prescritos; nunca calcula dose, intervalo ou frequência. */
object MedicationSchedule {
    private val timePattern = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")
    fun parse(input: String?): List<String>? {
        val values = input?.split(',', ';')?.map { it.trim() } ?: return null
        if (values.isEmpty() || values.any { !timePattern.matches(it) }) return null
        return values.distinct().sorted()
    }
    fun times(multiple: String?, legacy: String?): List<String> =
        parse(multiple) ?: parse(legacy?.take(5)) ?: emptyList()
    fun key(date: LocalDate, time: String): String = "${date}T$time"
    fun logId(user: String, medication: String, scheduledFor: String): String =
        UUID.nameUUIDFromBytes("$user|$medication|$scheduledFor".toByteArray(Charsets.UTF_8)).toString()
    fun available(date: LocalDate, time: String, now: LocalDateTime): Boolean =
        !now.isBefore(date.atTime(LocalTime.parse(time)).minusHours(2))
}
