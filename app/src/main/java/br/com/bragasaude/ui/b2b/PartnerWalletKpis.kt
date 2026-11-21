package br.com.bragasaude.ui.b2b

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.b2b.PartnerWalletKpisDto
import java.util.Locale

private val ptBr = Locale.forLanguageTag("pt-BR")

private enum class WalletMetric(val label: String) {
    MEMBERS("Participantes"),
    ACTIVITY("Atividade diária"),
    RETENTION("Retenção"),
    READS("Lembretes lidos"),
    APPOINTMENTS("Agendamentos confirmados")
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
    null -> "Sem base na semana anterior."
    0 -> "Igual à semana anterior."
    else -> if (delta > 0) "Aumento de $delta em relação à semana anterior."
    else "Redução de ${-delta} em relação à semana anterior."
}

/** Percentual de retenção no padrão pt-BR ("70,0%"). */
internal fun retentionPercentLabel(rate: Double): String = String.format(ptBr, "%.1f%%", rate)

/**
 * Leitura falada dos cards. Ficam fora do Composable para poderem ser testadas sem
 * instrumentação e para o TalkBack nunca depender de glifos/setas ou de "(s)".
 */
internal fun walletMembersSpoken(active: Int, inactive: Int): String =
    "Participantes: ${B2BTextFormatters.countLabel(active, "ativo", "ativos")} e " +
        "${B2BTextFormatters.countLabel(inactive, "inativo", "inativos")}."

internal fun walletRetentionSpoken(rate: Double?): String =
    if (rate == null) {
        "Retenção: sem base na semana anterior."
    } else {
        "Retenção: ${retentionPercentLabel(rate)}."
    }

internal fun walletRemindersSpoken(read: Int): String =
    "Lembretes lidos: ${B2BTextFormatters.countLabel(read, "leitura", "leituras")}."

internal fun walletAppointmentsSpoken(confirmed: Int, delta: Int?): String =
    "Agendamentos confirmados: $confirmed. ${appointmentsDeltaSpoken(delta)}"

internal fun walletActivitySpoken(daily: List<Int>?, periodDays: Int): String {
    if (daily.isNullOrEmpty()) return "Atividade diária: série indisponível."
    return "Atividade diária: pico de ${daily.max()} e ${daily.last()} no último dia, " +
        "em ${B2BTextFormatters.days(periodDays)}."
}

/**
 * KPIs da carteira em grade equilibrada: dois pares (Participantes/Atividade,
 * Retenção/Lembretes) e o card de Agendamentos ocupando a linha inteira. Cada linha
 * usa `IntrinsicSize.Min` para as alturas ficarem iguais sem cortar texto; com fonte
 * grande ou tela estreita tudo vira uma coluna.
 *
 * Os cards com ação útil recebem `role = Button` e uma descrição falada própria.
 * A leitura visível continua igual; o texto falado usa os helpers acima.
 */
@Composable
fun PartnerWalletKpis(
    kpis: PartnerWalletKpisDto?,
    isLoading: Boolean = false,
    onOpenParticipants: (() -> Unit)? = null,
    onOpenOperations: (() -> Unit)? = null,
    onOpenStudio: (() -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Carteira global", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
        when {
            isLoading && kpis == null -> WalletKpisSkeleton()
            kpis == null -> WalletKpisEmptyState(onOpenStudio = onOpenStudio)
            else -> {
                val endDate = kpis.asOf?.let {
                    runCatching {
                        java.time.OffsetDateTime.parse(it)
                            .withOffsetSameInstant(java.time.ZoneOffset.UTC)
                            .toLocalDate()
                            .toString()
                    }.getOrNull()
                }
                Text(
                    listOfNotNull(
                        "Atividade no programa · ${B2BTextFormatters.days(kpis.periodDays)}",
                        endDate?.let { "até ${B2BTextFormatters.formatIsoDate(it)} (UTC)" }
                    ).joinToString(" ")
                )

                BoxWithConstraints(Modifier.fillMaxWidth().semantics { isTraversalGroup = true }) {
                    val columns = if (maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.5f) 1 else 2
                    val rows = if (columns == 1) {
                        WalletMetric.entries.map { listOf(it) }
                    } else {
                        listOf(
                            listOf(WalletMetric.MEMBERS, WalletMetric.ACTIVITY),
                            listOf(WalletMetric.RETENTION, WalletMetric.READS),
                            listOf(WalletMetric.APPOINTMENTS)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        rows.forEach { row ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .height(IntrinsicSize.Min),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                row.forEach { metric ->
                                    val action = when (metric) {
                                        WalletMetric.MEMBERS -> onOpenParticipants
                                        WalletMetric.READS, WalletMetric.APPOINTMENTS -> onOpenOperations
                                        else -> null
                                    }
                                    WalletMetricCard(
                                        metric = metric,
                                        kpis = kpis,
                                        modifier = Modifier.weight(1f)
                                            .heightIn(min = if (metric == WalletMetric.APPOINTMENTS) 112.dp else 132.dp)
                                            .fillMaxHeight(),
                                        onCardClick = action,
                                        actionHint = when (metric) {
                                            WalletMetric.MEMBERS ->
                                                "Toque para ver na lista os participantes que precisam de atenção."
                                            WalletMetric.READS ->
                                                "Toque para abrir a aba Operação e enviar lembretes."
                                            WalletMetric.APPOINTMENTS ->
                                                "Toque para abrir a aba Operação e ver todos os agendamentos."
                                            else -> null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletMetricCard(
    metric: WalletMetric,
    kpis: PartnerWalletKpisDto,
    modifier: Modifier,
    onCardClick: (() -> Unit)?,
    actionHint: String?
) {
    val clickModifier = if (onCardClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onCardClick)
    } else {
        Modifier
    }
    Card(
        modifier = modifier
            .then(clickModifier)
            .semantics(mergeDescendants = onCardClick == null) {
                traversalIndex = metric.ordinal.toFloat()
            },
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .then(
                    if (onCardClick != null) {
                        Modifier.clearAndSetSemantics {
                            role = Role.Button
                            contentDescription = walletCardDescription(metric, kpis, actionHint)
                        }
                    } else {
                        Modifier
                    }
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(metric.label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            WalletMetricValue(metric, kpis)
            if (metric == WalletMetric.APPOINTMENTS && onCardClick != null) {
                Text(
                    text = "Abrir Operação",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun walletCardDescription(
    metric: WalletMetric,
    kpis: PartnerWalletKpisDto,
    actionHint: String?
): String {
    val summary = when (metric) {
        WalletMetric.MEMBERS -> walletMembersSpoken(kpis.activeUsers, kpis.inactiveUsers)
        WalletMetric.ACTIVITY -> walletActivitySpoken(kpis.dailyActiveUsers, kpis.periodDays)
        WalletMetric.RETENTION -> walletRetentionSpoken(kpis.retentionRate)
        WalletMetric.READS -> walletRemindersSpoken(kpis.remindersRead)
        WalletMetric.APPOINTMENTS -> walletAppointmentsSpoken(kpis.appointmentsConfirmed, kpis.appointmentsDelta)
    }
    return if (actionHint.isNullOrBlank()) summary else "$summary $actionHint"
}

@Composable
private fun WalletMetricValue(metric: WalletMetric, kpis: PartnerWalletKpisDto) {
    when (metric) {
        WalletMetric.MEMBERS -> {
            val total = kpis.activeUsers + kpis.inactiveUsers
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PartnerRing(
                    part = kpis.activeUsers,
                    total = total,
                    centerLabel = kpis.activeUsers.toString(),
                    contentDescription = "${kpis.activeUsers} de $total ativos, ${ringPercentLabel(kpis.activeUsers, total)}"
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    WalletLegendRow(MaterialTheme.colorScheme.primary, kpis.activeUsers, "Ativos")
                    WalletLegendRow(
                        trackColorFor(
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        ),
                        kpis.inactiveUsers,
                        "Inativos"
                    )
                }
            }
        }
        WalletMetric.ACTIVITY -> {
            val daily = kpis.dailyActiveUsers
            if (daily.isNullOrEmpty()) {
                Text("Série indisponível")
            } else {
                PartnerSparkline(daily, periodDays = kpis.periodDays)
                if (daily.size >= 31 && kpis.periodDays > daily.size) {
                    Text(
                        text = "Série limitada aos últimos 31 dias.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        WalletMetric.RETENTION -> {
            val rate = kpis.retentionRate
            if (rate == null) {
                Text("Sem base na semana anterior")
            } else {
                Text(retentionPercentLabel(rate))
                PartnerProgress((rate / 100).toFloat())
            }
        }
        // Sem o total de lembretes enviados no contrato atual não há percentual confiável:
        // o número é a leitura honesta e fica visível no card.
        WalletMetric.READS -> {
            val shownReads by animateIntAsState(
                targetValue = kpis.remindersRead,
                animationSpec = tween(durationMillis = 450),
                label = "walletRemindersRead"
            )
            Text(shownReads.toString(), style = MaterialTheme.typography.headlineSmall)
        }
        WalletMetric.APPOINTMENTS -> {
            val shownAppointments by animateIntAsState(
                targetValue = kpis.appointmentsConfirmed,
                animationSpec = tween(durationMillis = 450),
                label = "walletAppointmentsConfirmed"
            )
            Text(shownAppointments.toString(), style = MaterialTheme.typography.headlineSmall)
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
                modifier = if (spoken == label) {
                    Modifier
                } else {
                    Modifier.clearAndSetSemantics { this.contentDescription = spoken }
                }
            )
        }
    }
}

@Composable
private fun WalletLegendRow(color: Color, count: Int, name: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(
            text = "$name: $count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clearAndSetSemantics { contentDescription = "$name: $count" }
        )
    }
}

@Composable
private fun WalletKpisEmptyState(onOpenStudio: (() -> Unit)?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Ainda não há indicadores",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "Os números aparecem quando o programa receber participantes e houver atividade no período.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onOpenStudio != null) {
                Button(
                    onClick = onOpenStudio,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Abrir Estúdio", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Placeholder da carteira: evita o vazio textual enquanto a primeira carga acontece. */
@Composable
private fun WalletKpisSkeleton() {
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = "Carregando indicadores da carteira." }
    ) {
        val columns = if (maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.5f) 1 else 2
        val rowSizes = if (columns == 1) List(5) { 1 } else listOf(2, 2, 1)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rowSizes.forEachIndexed { index, size ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(size) {
                        WalletSkeletonBlock(
                            Modifier
                                .weight(1f)
                                .heightIn(min = if (index == rowSizes.lastIndex) 112.dp else 132.dp)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletSkeletonBlock(modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
    )
}
