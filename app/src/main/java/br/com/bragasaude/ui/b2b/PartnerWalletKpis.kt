package br.com.bragasaude.ui.b2b

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.b2b.PartnerWalletKpisDto
import java.util.Locale

private data class WalletKpiCard(
    val label: String,
    val value: String?,
    val caption: String? = null
)

@Composable
fun PartnerWalletKpis(kpis: PartnerWalletKpisDto?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Carteira global", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        // Janela vem do backend (periodDays); 7 é o fallback histórico para quando
        // os KPIs ainda não carregaram, preservando o texto anterior.
        val periodDays = kpis?.periodDays ?: 7
        val periodLabel = if (periodDays == 1) "último dia" else "últimos $periodDays dias"
        Text("Atividade no programa · $periodLabel", style = MaterialTheme.typography.labelSmall)
        val retentionRate = kpis?.retentionRate
        val entries = listOf(
            WalletKpiCard("Ativos", kpis?.activeUsers?.toString()),
            WalletKpiCard("Inativos", kpis?.inactiveUsers?.toString()),
            WalletKpiCard(
                label = "Retenção",
                value = retentionRate?.let { String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", it) },
                // Semana sem base comparativa: o traço fica autoexplicativo dentro do card,
                // sem linha solta abaixo da grade.
                caption = if (kpis != null && retentionRate == null) "sem base na semana anterior" else null
            ),
            WalletKpiCard("Lembretes lidos", kpis?.remindersRead?.toString()),
            WalletKpiCard("Agendamentos confirmados", kpis?.appointmentsConfirmed?.toString())
        )
        entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { entry ->
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(entry.value ?: "—", style = MaterialTheme.typography.headlineSmall)
                            Text(entry.label, style = MaterialTheme.typography.labelSmall)
                            entry.caption?.let { caption ->
                                Text(
                                    caption,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
        if (kpis == null) Text("Indicadores indisponíveis. Atualize para tentar novamente.", style = MaterialTheme.typography.labelSmall)
    }
}
