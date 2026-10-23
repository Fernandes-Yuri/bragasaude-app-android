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
internal fun ReportVitalsSummary(vitals: List<RemoteVitalSign>, birthDate: String? = null) {
    ChartCard("Últimos registros vitais") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            latestReportReadings(vitals).forEach { reading ->
                Column {
                    Text(reading.label, style = MaterialTheme.typography.bodyMedium)
                    Text(reading.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (reading.label == "Pressão arterial") {
                        val pressure = vitals.filter { it.systolicPressure != null && it.diastolicPressure != null }
                            .maxByOrNull { it.measuredAt?.let(::parseDate)?.time ?: Long.MIN_VALUE }
                        pressure?.let { vital ->
                            if ((reportAge(birthDate, vital.measuredAt) ?: 0) >= 18) {
                                val band = pressureReference(vital.systolicPressure!!, vital.diastolicPressure!!)
                                Text(band, style = MaterialTheme.typography.labelMedium,
                                    color = if (band == "Dentro da referência geral") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    if (reading.value != "Sem registro") Text(reportRecordedAt(reading.measuredAt),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Referências de pressão de consultório para adultos; uma leitura não estabelece diagnóstico. A hidratação é um registro individual.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ReportBodySummary(biometry: RemoteBiometry?, birthDate: String? = null) {
    ChartCard("Resumo corporal") {
        if (biometry == null) {
            Text("Nenhuma medida corporal registrada. Seus próximos registros aparecerão aqui.", style = MaterialTheme.typography.bodyMedium)
        } else {
            val locale = Locale.forLanguageTag("pt-BR")
            val values = listOf(
                "Peso" to biometry.weight.takeIf { it.isFinite() && it > 0 }?.let { String.format(locale, "%.1f kg", it) },
                "Altura" to biometry.height.takeIf { it.isFinite() && it > 0 }?.let { String.format(locale, "%.0f cm", it) },
                "IMC calculado" to reportBmi(biometry.weight, biometry.height)?.let { String.format(locale, "%.1f kg/m²", it) }
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                values.forEach { (label, value) ->
                    Text("$label: ${value ?: "Sem registro"}", style = MaterialTheme.typography.bodyLarge)
                }
                reportBmi(biometry.weight, biometry.height)?.let { bmi ->
                    val age = reportAge(birthDate, biometry.measuredAt)
                    Text(if (age != null && age in 20..59) "Referência de IMC: ${adultBmiReference(bmi)}"
                        else "A classificação do IMC depende da idade e da avaliação profissional.", style = MaterialTheme.typography.labelMedium)
                    if (age != null && age in 20..59) Text("Referência geral para adultos de 20 a 59 anos; não se aplica à gestação.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(reportRecordedAt(biometry.measuredAt), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}


internal fun reportBmi(weight: Float, heightCm: Float): Double? =
    if (weight.isFinite() && heightCm.isFinite() && weight > 0 && heightCm > 0)
        weight.toDouble() / (heightCm / 100.0).let { it * it } else null

internal fun reportAge(birthDate: String?, measuredAt: String?): Int? = try {
    val birth = java.time.LocalDate.parse(birthDate)
    val date = measuredAt?.let(::parseDate)?.toInstant()?.atZone(java.time.ZoneId.systemDefault())?.toLocalDate()
    date?.let { java.time.Period.between(birth, it).years }?.takeIf { it in 0..130 }
} catch (_: Exception) { null }

/** SISVAN: referência geral, exibida apenas no grupo adulto de 20 a 59 anos.
 * https://www.gov.br/saude/pt-br/composicao/saps/vigilancia-alimentar-e-nutricional/arquivos/protocolos-do-sistema-de-vigilancia-alimentar-e-nutricional-sisvan
 */
internal fun adultBmiReference(bmi: Double): String = when {
    bmi < 18.5 -> "Baixo peso"
    bmi < 25.0 -> "Peso adequado"
    bmi < 30.0 -> "Sobrepeso"
    else -> "Obesidade"
}

/** DBHA 2025: referência de consultório para adultos, sem afirmar diagnóstico.
 * https://abccardiol.org/wp-content/uploads/articles_xml/0066-782X-abc-122-09-e20250624/0066-782X-abc-122-09-e20250624.pdf
 */
internal fun pressureReference(systolic: Int, diastolic: Int): String = when {
    systolic < 90 || diastolic < 60 -> "Conferir leitura baixa"
    systolic < 120 && diastolic < 80 -> "Dentro da referência geral"
    systolic < 140 && diastolic < 90 -> "Faixa de atenção"
    else -> "Acima da referência geral"
}
