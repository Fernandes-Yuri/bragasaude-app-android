package br.com.bragasaude.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Horizonte de consumo individual, prevenindo desperdício de perecíveis e excesso de estoque. */
object GroceryPlanningPeriod {
    const val DEFAULT_DAYS = 7
    val options = listOf(7, 14, 30)

    fun validated(days: Int): Int {
        require(days in options) { "Escolha 7, 14 ou 30 dias." }
        return days
    }

    fun daysRemaining(start: String, days: Int, today: LocalDate = LocalDate.now()): Int {
        validated(days)
        return (days - ChronoUnit.DAYS.between(LocalDate.parse(start), today).toInt()).coerceIn(0, days)
    }

    fun labelFor(days: Int): String = when (days) {
        7 -> "7 dias (Semanal)"
        14 -> "14 dias (Quinzenal)"
        30 -> "30 dias (Mensal)"
        else -> "$days dias"
    }

    fun badgeFor(days: Int): String? = when (days) {
        7 -> "Recomendado"
        14 -> "Recomendado"
        else -> null
    }

    fun descriptionFor(days: Int): String = when (days) {
        7 -> "Ideal para frutas e verduras frescas, mantendo o consumo na validade e a despensa leve."
        14 -> "Compras a cada duas semanas. Foco em hortaliças duráveis, congelamento seguro e sem acúmulo."
        30 -> "Gera maior volume de despensa e exige reposição semanal separada para hortifruti fresco."
        else -> "Planejamento para $days dias."
    }

    fun quickBudgetsFor(days: Int): List<String> = when (days) {
        7 -> listOf("80", "120", "160", "200")
        14 -> listOf("160", "240", "320", "400")
        else -> listOf("350", "500", "700", "900")
    }
}
