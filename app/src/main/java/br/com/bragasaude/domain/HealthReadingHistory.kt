package br.com.bragasaude.domain

import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.data.local.WearableReading
import java.util.Locale
import java.util.UUID

/** Histórico de leitura com identidade canônica, preservação de origem e estado de pendência. */
object HealthReadingHistory {
    const val WHATSAPP = "braga.whatsapp"
    const val SYNC = "braga.sync"
    const val PENDING_SYNC_LABEL = "Aguardando envio"

    fun sourceFromConfirmedVia(confirmedVia: String?): String = when (confirmedVia?.trim()?.lowercase(Locale.ROOT)) {
        HealthReadingInput.MANUAL -> HealthReadingInput.MANUAL
        HealthReadingInput.VOICE -> HealthReadingInput.VOICE
        "whatsapp", WHATSAPP -> WHATSAPP
        else -> SYNC
    }

    fun canonicalUuidForLegacyReading(userId: String, metric: String, recordKey: String): String {
        val stripped = recordKey
            .removePrefix("vital:")
            .removePrefix("manual:")
            .removePrefix("voice:")
            .trim()
        val parsed = runCatching { UUID.fromString(stripped).toString() }.getOrNull()
        if (parsed != null) return parsed
        return UUID.nameUUIDFromBytes("$userId:$metric:$stripped".toByteArray(Charsets.UTF_8)).toString()
    }

    fun canonicalRecordId(userId: String, metric: String, recordKey: String, sourcePackage: String): String {
        val hasCanonicalPrefix = recordKey.startsWith("vital:") || recordKey.startsWith("manual:") || recordKey.startsWith("voice:")
        if (hasCanonicalPrefix || sourcePackage == HealthReadingInput.MANUAL || sourcePackage == HealthReadingInput.VOICE) {
            val stripped = recordKey
                .removePrefix("vital:")
                .removePrefix("manual:")
                .removePrefix("voice:")
                .trim()
            val validUuid = runCatching { UUID.fromString(stripped).toString() }.getOrNull()
            if (validUuid != null) return "vital:$validUuid"
            if (sourcePackage == HealthReadingInput.MANUAL || sourcePackage == HealthReadingInput.VOICE) {
                return "vital:" + canonicalUuidForLegacyReading(userId, metric, recordKey)
            }
        }
        return recordKey
    }

    fun merge(userId: String, metric: String, wearable: List<WearableReading>, vitals: List<VitalSignEntity>): List<WearableReading> {
        if (userId.isBlank()) return emptyList()
        val legacyByCanonicalId = wearable
            .filter { it.userId == userId && it.metric == metric }
            .associateBy { canonicalRecordId(userId, metric, it.recordKey, it.sourcePackage) }

        val synced = vitals
            .filter { it.userId == userId && it.deletedAt == null }
            .mapNotNull { vital ->
                val rawValue: Number = when (metric) {
                    HealthReadingInput.HEART -> vital.heartRate
                    HealthReadingInput.OXYGEN -> vital.oxygenSaturation
                    else -> null
                } ?: return@mapNotNull null
                val validated = HealthReadingInput.value(metric, rawValue.toString()) ?: return@mapNotNull null
                val rawKey = "vital:" + (vital.remoteId ?: vital.localId.toString())
                val mappedSource = sourceFromConfirmedVia(vital.confirmedVia)
                val canonicalId = canonicalRecordId(userId, metric, rawKey, mappedSource)
                val fallbackLegacySource = legacyByCanonicalId[canonicalId]?.sourcePackage
                    ?.takeIf { it == HealthReadingInput.MANUAL || it == HealthReadingInput.VOICE }
                val effectiveSource = if (mappedSource == SYNC && fallbackLegacySource != null) fallbackLegacySource else mappedSource
                WearableReading(
                    userId = userId,
                    recordKey = canonicalId,
                    metric = metric,
                    value = validated,
                    measuredAt = vital.measuredAt.time,
                    sourcePackage = effectiveSource,
                    deviceModel = if (vital.pendingSync) PENDING_SYNC_LABEL else null
                )
            }

        val wearableNormalized = wearable
            .filter { it.userId == userId && it.metric == metric }
            .mapNotNull { item ->
                val validated = HealthReadingInput.value(metric, item.value.toString()) ?: return@mapNotNull null
                val isLegacyManualOrVoice = item.sourcePackage == HealthReadingInput.MANUAL || item.sourcePackage == HealthReadingInput.VOICE
                item.copy(
                    recordKey = canonicalRecordId(userId, metric, item.recordKey, item.sourcePackage),
                    value = validated,
                    deviceModel = if (isLegacyManualOrVoice && item.deviceModel == null) PENDING_SYNC_LABEL else item.deviceModel
                )
            }

        return (synced + wearableNormalized)
            .distinctBy { it.recordKey }
            .sortedWith(compareByDescending<WearableReading> { it.measuredAt }.thenBy { it.recordKey })
            .take(200)
    }
}
