package br.com.bragasaude.domain

import br.com.bragasaude.data.local.WearableReading
import java.time.Instant
import java.time.ZoneId

data class WearableDaySummary(
    val date: String, val metric: String, val source: String,
    val count: Int, val minimum: Double, val average: Double, val maximum: Double
)

fun summarizeWearableReadings(readings: List<WearableReading>, zone: ZoneId = ZoneId.systemDefault()): List<WearableDaySummary> =
    readings.groupBy { Triple(Instant.ofEpochMilli(it.measuredAt).atZone(zone).toLocalDate().toString(), it.metric, it.sourcePackage) }
        .map { (key, rows) ->
            WearableDaySummary(key.first, key.second, key.third, rows.size, rows.minOf { it.value }, rows.map { it.value }.average(), rows.maxOf { it.value })
        }.sortedWith(compareByDescending<WearableDaySummary> { it.date }.thenBy { it.metric }.thenBy { it.source })
