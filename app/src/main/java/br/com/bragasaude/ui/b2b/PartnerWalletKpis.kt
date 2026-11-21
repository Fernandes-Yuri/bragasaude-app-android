package br.com.bragasaude.ui.b2b

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.b2b.PartnerWalletKpisDto
import java.util.Locale

private enum class WalletMetric(val label: String) {
    MEMBERS("Participantes"), ACTIVITY("Atividade diária"), RETENTION("Retenção"),
    READS("Lembretes lidos"), APPOINTMENTS("Agendamentos confirmados")
}

/** Texto do delta de agendamentos; null não vira 0 (sem base de comparação). */
internal fun appointmentsDeltaLabel(delta: Int?): String = when (delta) {
    null -> "Sem base na semana anterior"
    0 -> "Igual à semana anterior"
    else -> if (delta > 0) "▲ +$delta em relação à semana anterior"
    else "▼ $delta em relação à semana anterior"
}

/** Leitura falada do delta, sem depender do glifo da seta. */
internal fun appointmentsDeltaSpoken(delta: Int?): String = when (delta) {
    null -> "Sem base na semana anterior"
    0 -> "Igual à semana anterior"
    else -> if (delta > 0) "Aumento de $delta em relação à semana anterior"
    else "Redução de ${-delta} em relação à semana anterior"
}

@Composable
fun PartnerWalletKpis(kpis: PartnerWalletKpisDto?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Carteira global", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        if (kpis == null) {
            Text("Ainda não há indicadores para este programa.")
            return@Column
        }
        val endDate = kpis.asOf?.let { runCatching {
            java.time.OffsetDateTime.parse(it).withOffsetSameInstant(java.time.ZoneOffset.UTC).toLocalDate().toString()
        }.getOrNull() }
        Text(listOfNotNull("Atividade no programa · ${B2BTextFormatters.days(kpis.periodDays)}",
            endDate?.let { "até ${B2BTextFormatters.formatIsoDate(it)} (UTC)" }).joinToString(" "))

        BoxWithConstraints(Modifier.fillMaxWidth().semantics { isTraversalGroup = true }) {
            val columns = if (maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.5f) 1 else 2
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WalletMetric.entries.chunked(columns).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { metric ->
                            Card(
                                modifier = Modifier.weight(1f).heightIn(min = 132.dp).fillMaxHeight()
                                    .semantics(mergeDescendants = true) { traversalIndex = metric.ordinal.toFloat() }
                            ) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(metric.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                    WalletMetricValue(metric, kpis)
                                }
                            }
                        }
                        if (row.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletMetricValue(metric: WalletMetric, kpis: PartnerWalletKpisDto) {
    when (metric) {
        WalletMetric.MEMBERS -> {
            val total = kpis.activeUsers + kpis.inactiveUsers
            val active = B2BTextFormatters.countLabel(kpis.activeUsers, "ativo", "ativos")
            val inactive = B2BTextFormatters.countLabel(kpis.inactiveUsers, "inativo", "inativos")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PartnerRing(
                    part = kpis.activeUsers,
                    total = total,
                    centerLabel = kpis.activeUsers.toString(),
                    contentDescription = "${kpis.activeUsers} de $total ativos, ${ringPercentLabel(kpis.activeUsers, total)}"
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    WalletLegendRow(MaterialTheme.colorScheme.primary, active)
                    WalletLegendRow(MaterialTheme.colorScheme.onSurfaceVariant, inactive)
                }
            }
        }
        WalletMetric.ACTIVITY -> {
            val daily = kpis.dailyActiveUsers
            if (daily.isNullOrEmpty()) Text("Série indisponível") else {
                PartnerSparkline(daily)
                Text("${daily.last()} com atividade no último dia")
                if (daily.size >= 31 && kpis.periodDays > daily.size) {
                    Text(
                        text = "Série limitada aos últimos 31 dias.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        WalletMetric.RETENTION -> {
            val rate = kpis.retentionRate
            if (rate == null) Text("Sem base na semana anterior") else {
                Text(String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", rate))
                PartnerProgress((rate / 100).toFloat())
            }
        }
        // Sem o total de lembretes enviados no contrato atual não há percentual confiável:
        // o número é a leitura honesta e fica visível no card.
        WalletMetric.READS -> Text(kpis.remindersRead.toString(), style = MaterialTheme.typography.headlineSmall)
        WalletMetric.APPOINTMENTS -> {
            Text(kpis.appointmentsConfirmed.toString(), style = MaterialTheme.typography.headlineSmall)
            val delta = kpis.appointmentsDelta
            val label = appointmentsDeltaLabel(delta)
            val spoken = appointmentsDeltaSpoken(delta)
            val color = when (delta) {
                null, 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                else -> if (delta > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            }
            Text(
                text = label,
                color = color,
                modifier = if (spoken == label) Modifier
                else Modifier.clearAndSetSemantics { this.contentDescription = spoken }
            )
        }
    }
}

@Composable
private fun WalletLegendRow(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
