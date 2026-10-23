package br.com.bragasaude.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.remote.model.RemoteBiometry
import br.com.bragasaude.data.remote.model.RemoteVitalSign
import br.com.bragasaude.data.util.parseDate
import java.text.SimpleDateFormat
import java.util.Locale

internal data class ReportReading(val label: String, val value: String, val measuredAt: String?)

/** Seleção independente por sinal: a última hidratação não esconde uma pressão anterior. */
internal fun latestReportReadings(vitals: List<RemoteVitalSign>): List<ReportReading> {
    val ordered = vitals.sortedByDescending { it.measuredAt?.let(::parseDate)?.time ?: Long.MIN_VALUE }
    fun latest(label: String, value: (RemoteVitalSign) -> String?): ReportReading {
        val found = ordered.firstNotNullOfOrNull { vital -> value(vital)?.let { it to vital.measuredAt } }
        return ReportReading(label, found?.first ?: "Sem registro", found?.second)
    }
    return listOf(
        latest("Pressão arterial") { v ->
            if (v.systolicPressure != null && v.diastolicPressure != null) "${v.systolicPressure}/${v.diastolicPressure} mmHg" else null
        },
        latest("Glicemia") { it.glucoseLevel?.let { value -> "$value mg/dL" } },
        latest("Batimentos") { it.heartRate?.let { value -> "$value bpm" } },
        latest("Hidratação (último registro)") { it.hydrationMl?.let { value -> "$value ml" } }
    )
}

internal fun reportRecordedAt(value: String?): String = value?.let(::parseDate)?.let {
    "Registrado em " + SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR")).format(it)
} ?: "Data não informada"

@Composable
internal fun ReportVitalsSummary(vitals: List<RemoteVitalSign>) {
    ChartCard("Últimos registros vitais") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            latestReportReadings(vitals).forEach { reading ->
                Column {
                    Text(reading.label, style = MaterialTheme.typography.bodyMedium)
                    Text(reading.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (reading.value != "Sem registro") Text(reportRecordedAt(reading.measuredAt),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("A hidratação mostra um registro individual, não o total do dia.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ReportBodySummary(biometry: RemoteBiometry?) {
    ChartCard("Resumo corporal") {
        if (biometry == null) {
            Text("Nenhuma medida corporal registrada. Seus próximos registros aparecerão aqui.", style = MaterialTheme.typography.bodyMedium)
        } else {
            val locale = Locale.forLanguageTag("pt-BR")
            val values = listOf(
                "Peso" to biometry.weight.takeIf { it.isFinite() && it > 0 }?.let { String.format(locale, "%.1f kg", it) },
                "Altura" to biometry.height.takeIf { it.isFinite() && it > 0 }?.let { String.format(locale, "%.0f cm", it) },
                "IMC registrado" to biometry.imc.takeIf { it.isFinite() && it > 0 }?.let { String.format(locale, "%.1f kg/m²", it) }
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { (label, value) ->
                    Text("$label: ${value ?: "Sem registro"}", style = MaterialTheme.typography.bodyLarge)
                }
                Text(reportRecordedAt(biometry.measuredAt), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
