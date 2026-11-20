package br.com.bragasaude.ui.b2b

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.b2b.B2BOnboardingValidator
import br.com.bragasaude.domain.b2b.B2BThemeEngine
import br.com.bragasaude.domain.b2b.ParticipantRoutineSummaryDto
import br.com.bragasaude.domain.b2b.PartnerParticipantItemDto
import br.com.bragasaude.domain.b2b.PartnerReminderType
import br.com.bragasaude.domain.b2b.ProgramExperienceConfigDto
import br.com.bragasaude.domain.b2b.ProgramReminderDto
import br.com.bragasaude.domain.b2b.SharedCategoryOption
import br.com.bragasaude.ui.components.BragaBottomSheet
import java.util.Locale

private enum class ParticipantDetailSubTab(val title: String) {
    METRICS("Métricas"),
    QUICK_ACTIONS("Ações rápidas")
}

@Composable
fun PartnerWalletFiltersSection(
    query: String,
    selectedCategory: String?,
    attentionOnly: Boolean,
    enabledProgramMetrics: List<String>,
    isFiltering: Boolean,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    onQueryChange: (String) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onToggleAttentionOnly: () -> Unit,
    onClearFilters: () -> Unit
) {
    val categoryOptions = if (enabledProgramMetrics.isEmpty()) {
        SharedCategoryOption.entries
    } else {
        SharedCategoryOption.entries.filter { it.wireKey in enabledProgramMetrics }
    }
    val hasActiveFilter = query.isNotBlank() || selectedCategory != null || attentionOnly

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            placeholder = { Text("Buscar participante por nome...") },
            leadingIcon = {
                Icon(imageVector = Icons.Default.Search, contentDescription = "Buscar participante")
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Limpar busca")
                    }
                }
            }
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 2.dp)
        ) {
            item {
                FilterChip(
                    selected = attentionOnly,
                    onClick = onToggleAttentionOnly,
                    label = { Text("Com atenção") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = brandPrimary,
                        selectedLabelColor = brandOnPrimary
                    )
                )
            }
            item {
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onSelectCategory(null) },
                    label = { Text("Todos") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = brandPrimary,
                        selectedLabelColor = brandOnPrimary
                    )
                )
            }
            items(categoryOptions, key = { it.wireKey }) { opt ->
                FilterChip(
                    selected = selectedCategory == opt.wireKey,
                    onClick = { onSelectCategory(opt.wireKey) },
                    label = { Text(shortCategoryFilterLabel(opt)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = brandPrimary,
                        selectedLabelColor = brandOnPrimary
                    )
                )
            }
        }

        if (hasActiveFilter) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filtros ativos na carteira",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClearFilters) {
                    Text("Limpar filtros", color = brandAccent)
                }
            }
        }

        if (isFiltering) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = brandAccent)
        }
    }
}

@Composable
fun WalletParticipantCard(
    item: PartnerParticipantItemDto,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softSurface: Color,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = softSurface)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(item.displayName, modifier = Modifier.weight(1f), maxLines = 1,
                    overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Abrir detalhes", tint = brandAccent)
            }
            Text("Atividade no programa · 7 dias", style = MaterialTheme.typography.labelSmall)
            Text(item.activityDatesLast7Days?.let { "${it.size}/7 dias ativos" } ?: "Atividade indisponível",
                style = MaterialTheme.typography.bodyMedium, color = brandAccent)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Lembretes: ${item.pendingRemindersCount?.toString() ?: "—"} pendentes",
                    style = MaterialTheme.typography.labelSmall)
                Text("Agenda: ${item.upcomingAppointmentsCount?.toString() ?: "—"} em 7 dias",
                    style = MaterialTheme.typography.labelSmall)
            }
            val consent = when (item.consentStatus) {
                "COMPLETE" -> "Completo"
                "PARTIAL" -> "Parcial"
                "NONE" -> "Sem categorias autorizadas"
                else -> "Indisponível"
            }
            Text("Consentimento: $consent", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantDetailBottomSheet(
    summary: ParticipantRoutineSummaryDto,
    participantItem: PartnerParticipantItemDto?,
    experienceConfig: ProgramExperienceConfigDto,
    selectedDays: Int,
    isLoading: Boolean,
    isRemoving: Boolean,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softSurface: Color,
    onChangeDays: (Int) -> Unit,
    onActionFeedback: (String) -> Unit,
    onOpenSendReminder: (String, String) -> Unit,
    onOpenManageAppointments: (String, String) -> Unit = { _, _ -> },
    onOpenDirectChat: (String, String) -> Unit = { _, _ -> },
    onRequestRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectedSubTab by rememberSaveable(summary.patientUserId) {
        mutableStateOf(ParticipantDetailSubTab.METRICS)
    }
    val enabledMetrics = experienceConfig.enabledMetrics.ifEmpty {
        SharedCategoryOption.entries.map { it.wireKey }
    }

    BragaBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(brandPrimary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (summary.participantName.firstOrNull() ?: 'P').uppercaseChar().toString(),
                            color = brandOnPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = summary.participantName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${summary.programName.ifBlank { B2BThemeEngine.PARTNER_AREA_TITLE }} · Últimos ${summary.periodDays}d",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Fechar", color = brandAccent)
                }
            }

            if (summary.familySupportActive) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = softSurface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = null,
                            tint = brandAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = summary.familySupportSummary?.takeIf { it.isNotBlank() }
                                ?: "Rede familiar ativa neste programa (mensagens familiares permanecem privadas).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            TabRow(
                selectedTabIndex = ParticipantDetailSubTab.entries.indexOf(selectedSubTab),
                containerColor = Color.Transparent,
                contentColor = brandAccent
            ) {
                ParticipantDetailSubTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedSubTab == tab,
                        onClick = { selectedSubTab = tab },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedSubTab == tab) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    )
                }
            }

            when (selectedSubTab) {
                ParticipantDetailSubTab.METRICS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(7, 14, 30).forEach { days ->
                            FilterChip(
                                selected = selectedDays == days,
                                onClick = { if (!isLoading) onChangeDays(days) },
                                label = { Text("${days}d") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = brandPrimary,
                                    selectedLabelColor = brandOnPrimary
                                )
                            )
                        }
                    }

                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = brandAccent)
                    }

                    MetricSlotSection(
                        metricKey = SharedCategoryOption.STEPS.wireKey,
                        enabledMetrics = enabledMetrics,
                        sharedCategories = summary.sharedCategories,
                        disabledNote = "Passos desativados na configuração deste programa.",
                        unsharedNote = "Passos não compartilhados por este participante."
                    ) {
                        val steps = summary.stepsSection ?: return@MetricSlotSection
                        val recent = steps.days.take(5).joinToString("\n") { d ->
                            "• ${d.date}: ${d.steps} passos (${d.activeMinutes} min ativos)"
                        }
                        SummaryMetricCard(
                            title = "Passos (${steps.source})",
                            body = "Média de ${steps.averageSteps} passos/dia em ${steps.daysRecorded} dia(s)." +
                                if (recent.isNotBlank()) "\n$recent" else ""
                        )
                    }

                    MetricSlotSection(
                        metricKey = SharedCategoryOption.HYDRATION.wireKey,
                        enabledMetrics = enabledMetrics,
                        sharedCategories = summary.sharedCategories,
                        disabledNote = "Hidratação desativada na configuração deste programa.",
                        unsharedNote = "Hidratação não compartilhada por este participante."
                    ) {
                        val hyd = summary.hydrationSection ?: return@MetricSlotSection
                        val recent = hyd.days.take(5).joinToString("\n") { d ->
                            "• ${d.date}: ${d.totalMl} ml"
                        }
                        SummaryMetricCard(
                            title = "Hidratação (${hyd.source})",
                            body = "Média diária de ${hyd.averageMl} ml em ${hyd.daysRecorded} dia(s)." +
                                if (recent.isNotBlank()) "\n$recent" else ""
                        )
                    }

                    MetricSlotSection(
                        metricKey = SharedCategoryOption.MEDICATIONS.wireKey,
                        enabledMetrics = enabledMetrics,
                        sharedCategories = summary.sharedCategories,
                        disabledNote = "Lembretes de medicamentos desativados neste programa.",
                        unsharedNote = "Lembretes de medicamentos não compartilhados pelo participante."
                    ) {
                        val meds = summary.medicationsSection ?: return@MetricSlotSection
                        val listText = meds.items.take(5).joinToString("\n") { m ->
                            "• ${m.name} (${m.schedule.ifBlank { "sem horário fixo" }})"
                        }
                        SummaryMetricCard(
                            title = "Adesão a lembretes (${meds.source})",
                            body = "${meds.userConfirmationsInPeriod} confirmação(ões) no período. ${meds.note}" +
                                if (listText.isNotBlank()) "\n$listText" else ""
                        )
                    }

                    MetricSlotSection(
                        metricKey = SharedCategoryOption.AUTHORIZED_RECORDS.wireKey,
                        enabledMetrics = enabledMetrics,
                        sharedCategories = summary.sharedCategories,
                        disabledNote = "Registros de sinais e biometria desativados neste programa.",
                        unsharedNote = "Registros de sinais e biometria não compartilhados pelo participante."
                    ) {
                        val recs = summary.authorizedRecordsSection ?: return@MetricSlotSection
                        val vitalLines = recs.records.take(6).map { r ->
                            val parts = buildList {
                                if (r.systolicPressure != null || r.diastolicPressure != null) {
                                    add("PA ${r.systolicPressure ?: "--"}/${r.diastolicPressure ?: "--"} mmHg")
                                }
                                r.glucoseLevel?.let { add("Glicose $it mg/dL") }
                                r.heartRate?.let { add("FC $it bpm") }
                                r.oxygenSaturation?.let {
                                    add("SpO2 ${String.format(Locale.US, "%.0f", it)}%")
                                }
                            }.joinToString(" · ")
                            "• ${r.measuredAt.take(10)} (${r.origin}): $parts"
                        }
                        val bioLines = recs.biometries.take(6).map { b ->
                            val parts = buildList {
                                add("Peso ${String.format(Locale.US, "%.1f", b.weightKg)} kg")
                                b.heightM?.let { add("Altura ${String.format(Locale.US, "%.2f", it)} m") }
                                b.imc?.let { add("IMC ${String.format(Locale.US, "%.1f", it)}") }
                            }.joinToString(" · ")
                            "• ${b.measuredAt.take(10)} (Biometria): $parts"
                        }
                        val combined = buildList {
                            if (bioLines.isNotEmpty()) {
                                add("Composição & Peso:")
                                addAll(bioLines)
                            }
                            if (vitalLines.isNotEmpty()) {
                                add("Sinais Vitais:")
                                addAll(vitalLines)
                            }
                            if (bioLines.isEmpty() && vitalLines.isEmpty()) {
                                add("Sem medições ou biometrias autorizadas registradas no período.")
                            }
                            add(recs.zeroFileEgressNotice)
                        }.joinToString("\n")

                        SummaryMetricCard(
                            title = "${B2BThemeEngine.PARTNER_RECORDS_TITLE} (${recs.source})",
                            body = combined
                        )
                    }
                }

                ParticipantDetailSubTab.QUICK_ACTIONS -> {
                    val features = experienceConfig.features
                    val pendingAppt = participantItem?.pendingAppointmentsCount ?: 0
                    val unreadMsgs = participantItem?.unreadDirectMessagesCount ?: 0

                    ParticipantQuickActionCard(
                        icon = Icons.Default.NotificationsActive,
                        title = "Enviar lembrete de rotina",
                        subtitle = if (features.partnerRemindersEnabled) {
                            "Toque para enviar orientação de rotina ou hidratação por Push."
                        } else {
                            "Módulo desligado nas configurações do programa."
                        },
                        enabled = features.partnerRemindersEnabled,
                        brandAccent = brandAccent,
                        onClick = {
                            if (features.partnerRemindersEnabled) {
                                onOpenSendReminder(summary.patientUserId, summary.participantName)
                            } else {
                                onActionFeedback(
                                    "Ative Lembretes de Rotina na aba Estúdio antes de disparar orientações."
                                )
                            }
                        }
                    )

                    ParticipantQuickActionCard(
                        icon = Icons.Default.CalendarMonth,
                        title = if (pendingAppt > 0) {
                            "Ver agendamentos ($pendingAppt pendente(s))"
                        } else {
                            "Ver agendamentos"
                        },
                        subtitle = if (features.appointmentsEnabled) {
                            if (pendingAppt > 0) {
                                "$pendingAppt solicitação(ões) aguardando confirmação. Toque para decidir."
                            } else {
                                "Agenda do programa ativa. Toque para conferir os pedidos deste participante."
                            }
                        } else {
                            "Agendamento desativado nas configurações do programa."
                        },
                        enabled = features.appointmentsEnabled,
                        brandAccent = brandAccent,
                        onClick = {
                            if (features.appointmentsEnabled) {
                                onOpenManageAppointments(summary.patientUserId, summary.participantName)
                            } else {
                                onActionFeedback(
                                    "Ative Agendamento no Estúdio para receber solicitações de horários."
                                )
                            }
                        }
                    )

                    ParticipantQuickActionCard(
                        icon = Icons.Default.SupportAgent,
                        title = if (unreadMsgs > 0) {
                            "Abrir chat direto ($unreadMsgs não lida(s))"
                        } else {
                            "Abrir chat direto"
                        },
                        subtitle = if (features.directPartnerChatEnabled) {
                            if (unreadMsgs > 0) {
                                "$unreadMsgs mensagem(ns) aguardando leitura no canal direto."
                            } else {
                                "Canal assíncrono direto com ${summary.participantName}."
                            }
                        } else {
                            "Chat direto desativado nas configurações do programa."
                        },
                        enabled = features.directPartnerChatEnabled,
                        brandAccent = brandAccent,
                        onClick = {
                            if (features.directPartnerChatEnabled) {
                                onOpenDirectChat(summary.patientUserId, summary.participantName)
                            } else {
                                onActionFeedback(
                                    "Ative o Chat Direto na aba Estúdio para conversar com participantes."
                                )
                            }
                        }
                    )

                    HorizontalDivider()

                    OutlinedButton(
                        onClick = onRequestRemove,
                        enabled = !isRemoving,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Text(
                            text = if (isRemoving) "Encerrando vínculo..." else "Encerrar vínculo deste participante",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun MetricSlotSection(
    metricKey: String,
    enabledMetrics: List<String>,
    sharedCategories: List<String>,
    disabledNote: String,
    unsharedNote: String,
    content: @Composable () -> Unit
) {
    when {
        metricKey !in enabledMetrics -> {
            MutedMetricNote(disabledNote)
        }
        metricKey !in sharedCategories -> {
            MutedMetricNote(unsharedNote)
        }
        else -> content()
    }
}

@Composable
private fun SummaryMetricCard(title: String, body: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(text = body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MutedMetricNote(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ParticipantQuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    brandAccent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) brandAccent else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun shortCategoryFilterLabel(opt: SharedCategoryOption): String = when (opt) {
    SharedCategoryOption.STEPS -> "Passos"
    SharedCategoryOption.HYDRATION -> "Hidratação"
    SharedCategoryOption.MEDICATIONS -> "Medicações"
    SharedCategoryOption.AUTHORIZED_RECORDS -> "Sinais & Peso"
}

@Composable
fun OwnerSentRemindersCard(
    reminders: List<ProgramReminderDto>,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softBorder: BorderStroke,
    onOpenBatchComposer: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = softBorder
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = brandAccent
                    )
                    Column {
                        Text(
                            text = "Lembretes e orientações de rotina",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = brandAccent
                        )
                        Text(
                            text = "Canal Não-SaMD separado dos alarmes pessoais de medicação.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = onOpenBatchComposer,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandPrimary,
                    contentColor = brandOnPrimary
                )
            ) {
                Text("Enviar lembrete em lote para a carteira", fontWeight = FontWeight.SemiBold)
            }

            if (reminders.isNotEmpty()) {
                HorizontalDivider()
                Text(
                    text = "Últimos envios (${reminders.size})",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                reminders.take(4).forEach { rem ->
                    val typeLabel = PartnerReminderType.fromWire(rem.reminderType).labelPtBr
                    val targetLabel = rem.targetPatientName ?: "Lote de participantes"
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${rem.title} · $typeLabel",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = rem.body,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$targetLabel · ${rem.acknowledgedCount} leitura(s) confirmada(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerSendReminderBottomSheet(
    targetPatientId: String?,
    targetPatientName: String,
    enabledProgramMetrics: List<String>,
    isSending: Boolean,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    onSend: (targetCategory: String?, reminderType: String, title: String, body: String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedType by rememberSaveable(targetPatientId) {
        mutableStateOf(PartnerReminderType.ROUTINE)
    }
    var selectedCategory by rememberSaveable(targetPatientId) {
        mutableStateOf<String?>(null)
    }
    var title by rememberSaveable(targetPatientId) { mutableStateOf("") }
    var body by rememberSaveable(targetPatientId) { mutableStateOf("") }

    val posologyHint = B2BOnboardingValidator.findUnsafeReminderPosologyHint("$title $body")
    val categoryOptions = if (enabledProgramMetrics.isEmpty()) {
        SharedCategoryOption.entries
    } else {
        SharedCategoryOption.entries.filter { it.wireKey in enabledProgramMetrics }
    }
    val canSubmit = !isSending &&
        title.trim().isNotEmpty() &&
        body.trim().isNotEmpty() &&
        posologyHint == null

    BragaBottomSheet(onDismissRequest = { if (!isSending) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (targetPatientId == null) {
                            "Novo lembrete em lote"
                        } else {
                            "Lembrete para $targetPatientName"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Limites: até 3/paciente/dia ou 20 lotes/dia. Nunca altera medicações.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isSending
                ) {
                    Text("Fechar", color = brandAccent)
                }
            }

            Text(
                text = "Tipo de orientação",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PartnerReminderType.entries) { opt ->
                    FilterChip(
                        selected = selectedType == opt,
                        onClick = { selectedType = opt },
                        label = { Text(opt.labelPtBr) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = brandPrimary,
                            selectedLabelColor = brandOnPrimary
                        )
                    )
                }
            }

            if (targetPatientId == null && categoryOptions.isNotEmpty()) {
                Text(
                    text = "Filtrar destinatários que autorizaram:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("Todos ativos") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = brandPrimary,
                                selectedLabelColor = brandOnPrimary
                            )
                        )
                    }
                    items(categoryOptions, key = { it.wireKey }) { opt ->
                        FilterChip(
                            selected = selectedCategory == opt.wireKey,
                            onClick = {
                                selectedCategory = if (selectedCategory == opt.wireKey) null else opt.wireKey
                            },
                            label = { Text(shortCategoryFilterLabel(opt)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = brandPrimary,
                                selectedLabelColor = brandOnPrimary
                            )
                        )
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { if (it.length <= 140) title = it },
                label = { Text("Título curto (máx. 140 caracteres)") },
                placeholder = { Text("Ex.: Meta de hidratação da tarde") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = body,
                onValueChange = { if (it.length <= 500) body = it },
                label = { Text("Mensagem de rotina (máx. 500 caracteres)") },
                placeholder = { Text("Ex.: Beba 300 ml de água e registre seus passos antes do retorno.") },
                minLines = 3,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )

            if (posologyHint != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = posologyHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            Button(
                onClick = {
                    onSend(
                        selectedCategory,
                        selectedType.wireValue,
                        title.trim(),
                        body.trim()
                    )
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandPrimary,
                    contentColor = brandOnPrimary
                )
            ) {
                Text(
                    text = if (isSending) "Enviando orientação..." else "Disparar lembrete agora",
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}
