package br.com.bragasaude.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Horizonte de consumo, independente da validade de uma cotação de supermercado. */
object GroceryPlanningPeriod {
    val options = listOf(7, 14, 30)
    fun validated(days: Int): Int {
        require(days in options) { "Escolha 7, 14 ou 30 dias." }
        return days
    }
    fun daysRemaining(start: String, days: Int, today: LocalDate = LocalDate.now()): Int {
        validated(days)
        return (days - ChronoUnit.DAYS.between(LocalDate.parse(start), today).toInt()).coerceIn(0, days)
    }
}
