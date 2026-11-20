package br.com.bragasaude.ui.b2b

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.b2b.PartnerWalletKpisDto
import java.util.Locale

@Composable
fun PartnerWalletKpis(kpis: PartnerWalletKpisDto?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Carteira global", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Atividade no programa · últimos 7 dias", style = MaterialTheme.typography.labelSmall)
        val entries = listOf(
            "Ativos" to kpis?.activeUsers?.toString(),
            "Inativos" to kpis?.inactiveUsers?.toString(),
            "Retenção" to kpis?.retentionRate?.let { String.format(Locale.forLanguageTag("pt-BR"), "%.1f%%", it) },
            "Lembretes lidos" to kpis?.remindersRead?.toString(),
            "Agendamentos confirmados" to kpis?.appointmentsConfirmed?.toString()
        )
        entries.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, value) ->
                    Card(Modifier.weight(1f)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(value ?: "—", style = MaterialTheme.typography.headlineSmall)
                            Text(label, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        if (kpis == null) Text("Indicadores indisponíveis. Atualize para tentar novamente.", style = MaterialTheme.typography.labelSmall)
        else if (kpis.retentionRate == null) Text("Retenção: sem atividade na semana anterior.", style = MaterialTheme.typography.labelSmall)
    }
}
