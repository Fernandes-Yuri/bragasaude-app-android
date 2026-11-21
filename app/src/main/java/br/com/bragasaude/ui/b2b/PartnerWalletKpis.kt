package br.com.bragasaude.ui.b2b

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.b2b.PartnerWalletKpisDto
import java.util.Locale

private enum class WalletMetric(val label: String) {
    MEMBERS("Participantes"), ACTIVITY("Atividade diária"), RETENTION("Retenção"),
    READS("Lembretes lidos"), APPOINTMENTS("Agendamentos confirmados")
}

@Composable
fun PartnerWalletKpis(kpis: PartnerWalletKpisDto?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Carteira global", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        if (kpis == null) {
            Text("Indicadores indisponíveis. Atualize para tentar novamente.")
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
            PartnerRing(kpis.activeUsers, kpis.activeUsers + kpis.inactiveUsers)
            val active = B2BTextFormatters.countLabel(kpis.activeUsers, "ativo", "ativos")
            val inactive = B2BTextFormatters.countLabel(kpis.inactiveUsers, "inativo", "inativos")
            Text("$active · $inactive")
        }
        WalletMetric.ACTIVITY -> {
            val daily = kpis.dailyActiveUsers
            if (daily.isNullOrEmpty()) Text("Série indisponível") else {
                PartnerSparkline(daily)
                Text("${B2BTextFormatters.countLabel(daily.last(), "ativo", "ativos")} no último dia")
            }
        }
        WalletMetric.RETENTION -> {
            val rate = kpis.retentionRate
            if (rate == null) Text("Sem base na semana anterior") else {
                Text(String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", rate))
                PartnerProgress((rate / 100).toFloat())
            }
        }
        WalletMetric.READS -> Text(kpis.remindersRead.toString(), style = MaterialTheme.typography.headlineSmall)
        WalletMetric.APPOINTMENTS -> {
            Text(kpis.appointmentsConfirmed.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(when (val delta = kpis.appointmentsDelta) {
                null -> "Comparação indisponível"
                0 -> "Igual à semana anterior"
                else -> "${if (delta > 0) "+" else ""}$delta em relação à semana anterior"
            })
        }
    }
}
