package br.com.bragasaude.domain

import br.com.bragasaude.data.remote.model.RemoteVitalSign
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class HomeReadingSummary(
    val userId: String = "",
    val loaded: Boolean = false,
    val pressure: RemoteVitalSign? = null,
    val glucose: RemoteVitalSign? = null
) {
    companion object {
        fun from(userId: String, rows: List<RemoteVitalSign>): HomeReadingSummary {
            val own = rows.filter { it.userId == userId }.sortedByDescending {
                it.measuredAt?.let { stamp -> ChartTimeline.instant(stamp) } ?: Instant.MIN
            }
            return HomeReadingSummary(userId, true,
                own.firstOrNull { it.systolicPressure != null && it.diastolicPressure != null },
                own.firstOrNull { it.glucoseLevel != null })
        }

        fun date(value: String?): String = value?.let { ChartTimeline.instant(it) }?.let {
            DateTimeFormatter.ofPattern("dd/MM HH:mm").withZone(ZoneId.systemDefault()).format(it)
        } ?: "Data não informada"
    }
}
