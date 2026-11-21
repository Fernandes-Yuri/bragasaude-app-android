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
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.graphics.vector.ImageVector
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

/** "1 pendente para você" / "2 pendentes para você". */
internal fun pendingAppointmentsLabel(pending: Int): String =
    B2BTextFormatters.countLabel(pending, "pendente para você", "pendentes para você")

/** "1 mensagem não lida" / "3 mensagens não lidas". */
internal fun unreadMessagesLabel(unread: Int): String =
    B2BTextFormatters.countLabel(unread, "mensagem não lida", "mensagens não lidas")

/**
 * Lembretes lidos nunca passam do total enviado: o gateway pode agregar leituras
 * duplicadas, então clampamos antes de calcular o percentual.
 */
internal fun remindersReadClamped(read: Int, total: Int): Int =
    if (total <= 0) 0 else read.coerceIn(0, total)

/** Percentual inteiro de lembretes lidos; null quando não houve envio no período. */
internal fun remindersReadPercent(read: Int, total: Int): Int? =
    if (total <= 0) null else Math.round(remindersReadClamped(read, total) * 100f / total)

/** Texto visível do card: "4 de 10 lidos" ou o motivo de não haver percentual. */
internal fun remindersReadLabel(read: Int, total: Int): String =
    if (total <= 0) {
        "Sem lembretes enviados no período"
    } else {
        val shown = remindersReadClamped(read, total)
        "$shown de $total ${B2BTextFormatters.plural(total, "lido", "lidos")}"
    }

/**
 * Leitura falada dos cards. Ficam fora do Composable para poderem ser testadas sem
 * instrumentação e para o TalkBack nunca depender de glifos/setas ou de "(s)".
 */
internal fun walletMembersSpoken(active: Int, inactive: Int, unreadMessages: Int = 0): String {
    val base = "Participantes: ${B2BTextFormatters.countLabel(active, "ativo", "ativos")} e " +
        "${B2BTextFormatters.countLabel(inactive, "inativo", "inativos")}."
    return if (unreadMessages > 0) "$base ${unreadMessagesLabel(unreadMessages)}." else base
}

internal fun walletRetentionSpoken(rate: Double?): String =
    if (rate == null) {
        "Retenção: sem base na semana anterior."
    } else {
        "Retenção: ${retentionPercentLabel(rate)}."
    }

/**
 * Leitura falada dos lembretes. Com total enviado, fala parte/total e percentual
 * ("12 de 20 lembretes lidos, 60%"); sem total, mantém a contagem antiga e explica
 * por que não há percentual, em vez de anunciar um 0% falso.
 */
internal fun walletRemindersSpoken(read: Int, total: Int = 0): String {
    if (total <= 0) {
        return "Lembretes lidos: ${B2BTextFormatters.countLabel(read, "leitura", "leituras")}. " +
            "Sem lembretes enviados no período."
    }
    val shown = remindersReadClamped(read, total)
    val percent = remindersReadPercent(read, total)
    return "$shown de $total ${B2BTextFormatters.plural(total, "lembrete lido", "lembretes lidos")}, $percent%"
}

internal fun walletAppointmentsSpoken(confirmed: Int, delta: Int?, pending: Int = 0): String {
    val base = "Agendamentos confirmados: $confirmed. ${appointmentsDeltaSpoken(delta)}"
    return if (pending > 0) "$base ${pendingAppointmentsLabel(pending)}." else base
}

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
/**
 * Rótulo do período da carteira no fuso de Brasília (mesma base usada pelo gateway
 * para os buckets diários desde a migration da Sprint C). O `asOf` chega em ISO/UTC.
 */
internal fun walletPeriodLabel(asOf: String?, periodDays: Int): String {
    val date = asOf?.let {
        runCatching {
            java.time.OffsetDateTime.parse(it)
                .atZoneSameInstant(java.time.ZoneId.of("America/Sao_Paulo"))
                .toLocalDate()
                .toString()
        }.getOrNull()
    }
    return listOfNotNull(
        "Atividade no programa · ${B2BTextFormatters.days(periodDays)}",
        date?.let { "até ${B2BTextFormatters.formatIsoDate(it)} (horário de Brasília)" }
    ).joinToString(" ")
}

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
                Text(walletPeriodLabel(kpis.asOf, kpis.periodDays))

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
        WalletMetric.MEMBERS ->
            walletMembersSpoken(kpis.activeUsers, kpis.inactiveUsers, kpis.unreadMessagesTotal)
        WalletMetric.ACTIVITY -> walletActivitySpoken(kpis.dailyActiveUsers, kpis.periodDays)
        WalletMetric.RETENTION -> walletRetentionSpoken(kpis.retentionRate)
        WalletMetric.READS -> walletRemindersSpoken(kpis.remindersRead, kpis.remindersTotal)
        WalletMetric.APPOINTMENTS ->
            walletAppointmentsSpoken(
                kpis.appointmentsConfirmed,
                kpis.appointmentsDelta,
                kpis.pendingAppointmentsTotal
            )
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
            if (kpis.unreadMessagesTotal > 0) {
                WalletAttentionRow(
                    icon = Icons.Default.MailOutline,
                    text = unreadMessagesLabel(kpis.unreadMessagesTotal),
                    color = MaterialTheme.colorScheme.primary
                )
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
        // Com o total enviado, o card mostra progresso real; sem total, mantém a
        // contagem honesta e explica a ausência de percentual em vez de exibir 0%.
        WalletMetric.READS -> {
            val total = kpis.remindersTotal
            if (total > 0) {
                val shownReads by animateIntAsState(
                    targetValue = remindersReadClamped(kpis.remindersRead, total),
                    animationSpec = tween(durationMillis = 450),
                    label = "walletRemindersRead"
                )
                val percent = remindersReadPercent(shownReads, total)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PartnerRing(
                        part = shownReads,
                        total = total,
                        centerLabel = percent?.let { "$it%" },
                        contentDescription = walletRemindersSpoken(kpis.remindersRead, total)
                    )
                    Text(
                        text = remindersReadLabel(shownReads, total),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                val shownReads by animateIntAsState(
                    targetValue = kpis.remindersRead,
                    animationSpec = tween(durationMillis = 450),
                    label = "walletRemindersRead"
                )
                Text(shownReads.toString(), style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = remindersReadLabel(kpis.remindersRead, total),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
            if (kpis.pendingAppointmentsTotal > 0) {
                WalletAttentionRow(
                    icon = Icons.Default.Warning,
                    text = pendingAppointmentsLabel(kpis.pendingAppointmentsTotal),
                    color = MaterialTheme.colorScheme.error
                )
            }
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

/** Linha curta de atenção dentro de um card; o texto já chega pluralizado. */
@Composable
private fun WalletAttentionRow(icon: ImageVector, text: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color
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
