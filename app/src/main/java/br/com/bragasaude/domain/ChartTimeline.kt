package br.com.bragasaude.domain

import java.time.*
import java.time.format.DateTimeFormatter

/** Fronteira única para datas ISO e offsets legados do Room/relatórios. */
object ChartTimeline {
    fun instant(value: String, zone: ZoneId = ZoneId.systemDefault()): Instant? {
        val normalized = value.replace(Regex("([+-]\\d{2})(\\d{2})$"), "$1:$2")
        return runCatching { Instant.parse(normalized) }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value).toInstant() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(value, DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssZ")).toInstant() }.getOrNull()
            ?: runCatching { LocalDate.parse(value).atStartOfDay(zone).toInstant() }.getOrNull()
    }

    fun indices(data: List<Double>, timestamps: List<String>, days: Int, today: LocalDate, zone: ZoneId): List<Int> {
        val dated = timestamps.isNotEmpty()
        val start = today.minusDays(days.toLong() - 1).atStartOfDay(zone).toInstant()
        val end = today.plusDays(1).atStartOfDay(zone).toInstant()
        return data.indices.filter { i ->
            data[i].isFinite() && (!dated || timestamps.getOrNull(i)?.let { instant(it, zone) }?.let { it >= start && it < end } == true)
        }.let { if (dated) it else it.takeLast(days) }
    }

    fun fractions(timestamps: List<String>): List<Float> {
        val instants = timestamps.map { instant(it)?.toEpochMilli() }
        val first = instants.filterNotNull().minOrNull()
        val last = instants.filterNotNull().maxOrNull()
        return instants.mapIndexed { index, point ->
            when {
                timestamps.size == 1 -> .5f
                point != null && first != null && last != null && last > first -> (point - first).toFloat() / (last - first)
                else -> index.toFloat() / (timestamps.size - 1).coerceAtLeast(1)
            }
        }
    }
}
