package br.com.bragasaude.domain

import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.data.local.WearableReading

/** Histórico de leitura, sem copiar registros entre tabelas nem mudar sua origem. */
object HealthReadingHistory {
    const val WHATSAPP = "braga.whatsapp"
    const val SYNC = "braga.sync"

    fun merge(userId: String, metric: String, wearable: List<WearableReading>, vitals: List<VitalSignEntity>): List<WearableReading> {
        if (userId.isBlank()) return emptyList()
        val synced = vitals.filter { it.userId == userId }.mapNotNull { vital ->
            val value = when (metric) {
                HealthReadingInput.HEART -> vital.heartRate
                HealthReadingInput.OXYGEN -> vital.oxygenSaturation
                else -> null
            } ?: return@mapNotNull null
            if (HealthReadingInput.value(metric, value.toString()) == null) return@mapNotNull null
            WearableReading(userId, "vital:" + (vital.remoteId ?: vital.localId.toString()), metric,
                value.toDouble(), vital.measuredAt.time,
                if (vital.confirmedVia.equals("whatsapp", ignoreCase = true)) WHATSAPP else SYNC, null)
        }
        return (wearable.filter { it.userId == userId && it.metric == metric } + synced)
            .distinctBy { it.recordKey }
            .sortedWith(compareByDescending<WearableReading> { it.measuredAt }.thenBy { it.recordKey })
            .take(200)
    }
}
