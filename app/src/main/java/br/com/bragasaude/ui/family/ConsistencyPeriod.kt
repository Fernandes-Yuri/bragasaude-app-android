package br.com.bragasaude.ui.family

import java.time.LocalDate

internal data class ConsistencyPeriod(val met: Int, val observed: Int, val missing: Int)

/** Hoje ainda não terminou; ausência de registro não é uma meta fracassada. */
internal fun consistencyPeriod(today: LocalDate, days: Int, totals: Map<String, Int>, target: Int): ConsistencyPeriod {
    val dates = (1..days).map { today.minusDays(it.toLong()).toString() }
    val observed = dates.count { totals.containsKey(it) }
    val met = if (target <= 0) 0 else dates.count { (totals[it] ?: -1) >= target }
    return ConsistencyPeriod(met, observed, days - observed)
}
