package br.com.bragasaude.ui.b2b

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import br.com.bragasaude.R
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
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private enum class ParticipantDetailSubTab(val title: String) {
    METRICS("Métricas"),
    QUICK_ACTIONS("Ações rápidas")
}

private fun participantSubTabIcon(tab: ParticipantDetailSubTab): ImageVector = when (tab) {
    ParticipantDetailSubTab.METRICS -> Icons.Default.BarChart
    ParticipantDetailSubTab.QUICK_ACTIONS -> Icons.Default.Bolt
}

/** Normaliza o status de consentimento vindo do DTO (ou derivado das categorias). */
internal fun normalizedConsentStatus(
    consentStatus: String?,
    sharedCategories: List<String>
): String = when (consentStatus?.trim()?.uppercase(Locale.US)) {
    "COMPLETE" -> "COMPLETE"
    "PARTIAL" -> "PARTIAL"
    "NONE" -> "NONE"
    else -> if (sharedCategories.isEmpty()) "NONE" else "UNKNOWN"
}

/** Rótulo único usado pelo chip do detalhe e pela carteira (nunca só a cor). */
internal fun participantConsentStatusLabel(
    consentStatus: String?,
    sharedCategories: List<String>
): String = when (normalizedConsentStatus(consentStatus, sharedCategories)) {
    "COMPLETE" -> "Consentimento completo"
    "PARTIAL" -> "Consentimento parcial"
    "NONE" -> "Sem categorias autorizadas"
    else -> "Consentimento a confirmar"
}

private fun participantConsentIcon(consentStatus: String?, sharedCategories: List<String>): ImageVector =
    when (normalizedConsentStatus(consentStatus, sharedCategories)) {
        "COMPLETE" -> Icons.Default.CheckCircle
        "PARTIAL" -> Icons.Default.Info
        "NONE" -> Icons.Default.Block
        else -> Icons.Default.Info
    }


@OptIn(ExperimentalLayoutApi::class)
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
    // Resumo legível do estado real dos filtros (nunca um texto genérico).
    val activeCategoryLabel = selectedCategory?.let { wireKey ->
        (categoryOptions.firstOrNull { it.wireKey == wireKey }
            ?: SharedCategoryOption.entries.firstOrNull { it.wireKey == wireKey })
            ?.let { shortCategoryFilterLabel(it) }
    }
    val activeFilterParts = buildList {
        if (query.isNotBlank()) add("Busca: \"${query.trim()}\"")
        if (attentionOnly) add("Com atenção")
        activeCategoryLabel?.let { add("Categoria: $it") }
    }
    val activeFilterSummary = if (activeFilterParts.isEmpty()) {
        "Nenhum filtro ativo"
    } else {
        activeFilterParts.joinToString(" · ")
    }

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

        Text(
            text = "Prioridade",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = attentionOnly,
                onClick = onToggleAttentionOnly,
                label = { Text("Com atenção") },
                leadingIcon = {
                    Icon(
                        imageVector = if (attentionOnly) Icons.Default.Check else Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (attentionOnly) {
                            MaterialTheme.colorScheme.onError
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.error,
                    selectedLabelColor = MaterialTheme.colorScheme.onError
                )
            )
        }
        Text(
            text = "Categoria",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { onSelectCategory(null) },
                label = { Text("Todos") },
                leadingIcon = {
                    Icon(
                        imageVector = if (selectedCategory == null) {
                            Icons.Default.Check
                        } else {
                            Icons.AutoMirrored.Filled.List
                        },
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (selectedCategory == null) brandOnPrimary else brandAccent
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = brandPrimary,
                    selectedLabelColor = brandOnPrimary
                )
            )
            categoryOptions.forEach { opt ->
                FilterChip(
                    selected = selectedCategory == opt.wireKey,
                    onClick = { onSelectCategory(opt.wireKey) },
                    leadingIcon = if (selectedCategory == opt.wireKey) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    },
                    label = { Text(shortCategoryFilterLabel(opt)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = brandPrimary,
                        selectedLabelColor = brandOnPrimary
                    )
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Filtros ativos na carteira",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = activeFilterSummary,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasActiveFilter) brandAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (hasActiveFilter) {
                    TextButton(onClick = onClearFilters) {
                        Text("Limpar filtros", color = brandAccent)
                    }
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = softSurface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.displayName,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Abrir detalhes de ${item.displayName}",
                    tint = brandAccent
                )
            }
            Text(
                text = "Atividade no programa · últimos 7 dias",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = item.activityDatesLast7Days?.let { "${it.size} de 7 dias ativos" }
                    ?: "Atividade indisponível",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = brandAccent
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Lembretes: ${
                        item.pendingRemindersCount?.let {
                            B2BTextFormatters.countLabel(it, "pendente", "pendentes")
                        } ?: "—"
                    }",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = "Agenda: ${item.upcomingAppointmentsCount?.toString() ?: "—"} em 7 dias",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            ConsentStatusChip(
                consentStatus = item.consentStatus,
                sharedCategories = item.sharedCategories,
                brandAccent = brandAccent
            )
        }
    }
}

@Composable
private fun ConsentStatusChip(
    consentStatus: String?,
    sharedCategories: List<String>,
    brandAccent: Color
) {
    val normalized = normalizedConsentStatus(consentStatus, sharedCategories)
    val label = participantConsentStatusLabel(consentStatus, sharedCategories)
    val container = when (normalized) {
        "COMPLETE" -> brandAccent.copy(alpha = 0.14f)
        "NONE" -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }
    val content = when (normalized) {
        "NONE" -> MaterialTheme.colorScheme.onErrorContainer
        "COMPLETE" -> brandAccent
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier.semantics(mergeDescendants = true) {
            contentDescription = "Status de consentimento: $label"
        },
        shape = RoundedCornerShape(50),
        color = container
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = participantConsentIcon(consentStatus, sharedCategories),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = content
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = content
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    onClinicalDetail: (String?) -> Unit,
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(brandPrimary)
                        .clearAndSetSemantics {},
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (summary.participantName.firstOrNull() ?: 'P').uppercaseChar().toString(),
                        color = brandOnPrimary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = summary.participantName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${summary.programName.ifBlank { B2BThemeEngine.PARTNER_AREA_TITLE }} · Últimos ${B2BTextFormatters.days(summary.periodDays)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ConsentStatusChip(
                        consentStatus = participantItem?.consentStatus,
                        sharedCategories = summary.sharedCategories,
                        brandAccent = brandAccent
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Fechar", color = brandAccent)
                }
            }

            val categoryLabels = summary.sharedCategories.filter {
                it in enabledMetrics && (it == "steps" || it == "hydration" || it in summary.availableDetails)
            }.mapNotNull { SharedCategoryOption.fromWire(it)?.titlePtBr }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (categoryLabels.isEmpty()) {
                        "Nenhuma categoria autorizada neste programa"
                    } else {
                        "Categorias autorizadas: ${categoryLabels.joinToString()}"
                    },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val tooltip = rememberTooltipState(isPersistent = true)
                val scope = rememberCoroutineScope()
                TooltipBox(state = tooltip, positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text("Você está na visão do gestor deste programa. O participante escolhe as categorias e pode revogar o compartilhamento.") } }) {
                    IconButton(onClick = { scope.launch { tooltip.show() } }) {
                        Icon(Icons.Default.VerifiedUser, contentDescription = "Sobre o acesso do gestor")
                    }
                }
            }

            // P2: quando o participante não autorizou detalhes clínicos, o gestor
            // precisa saber o próximo passo em vez de encarar um beco sem saída.
            val clinicalCategoriesInScope = listOf(
                SharedCategoryOption.MEDICATIONS.wireKey,
                SharedCategoryOption.AUTHORIZED_RECORDS.wireKey
            ).filter { it in enabledMetrics && it in summary.sharedCategories }
            val missingClinicalDetails =
                clinicalCategoriesInScope.any { it !in summary.availableDetails }
            if (missingClinicalDetails) {
                val features = experienceConfig.features
                val clinicalGuidance = buildString {
                    append("O participante ainda não autorizou detalhes clínicos. Peça para ele reabrir o app e confirmar o compartilhamento.")
                    if (features.directPartnerChatEnabled) {
                        append(" Se preferir, use o chat direto nas Ações rápidas para orientá-lo.")
                    } else if (features.partnerRemindersEnabled) {
                        append(" Você também pode enviar um lembrete de rotina pelas Ações rápidas.")
                    }
                }
                ClinicalDetailsGuidance(message = clinicalGuidance, accent = brandAccent)
            }

            TabRow(
                selectedTabIndex = ParticipantDetailSubTab.entries.indexOf(selectedSubTab),
                containerColor = Color.Transparent,
                contentColor = brandAccent
            ) {
                ParticipantDetailSubTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedSubTab == tab,
                        onClick = {
                            if (tab != selectedSubTab) onClinicalDetail(null)
                            selectedSubTab = tab
                        },
                        text = {
                            Text(
                                text = tab.title,
                                fontWeight = if (selectedSubTab == tab) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = participantSubTabIcon(tab),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (selectedSubTab) {
                    ParticipantDetailSubTab.METRICS -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7, 14, 30).forEach { days ->
                                FilterChip(
                                    selected = selectedDays == days,
                                    enabled = !isLoading,
                                    onClick = { onChangeDays(days) },
                                    leadingIcon = if (selectedDays == days) { { Icon(Icons.Default.Check, null) } } else null,
                                    label = { Text(B2BTextFormatters.days(days)) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = brandPrimary,
                                        selectedLabelColor = brandOnPrimary
                                    )
                                )
                            }
                        }

                        if (isLoading) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .semantics { liveRegion = LiveRegionMode.Polite },
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LinearProgressIndicator(
                                    modifier = Modifier.weight(1f),
                                    color = brandAccent
                                )
                                Text(
                                    text = "Atualizando últimos ${B2BTextFormatters.days(selectedDays)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        MetricSlotSection(
                            metricKey = SharedCategoryOption.STEPS.wireKey,
                            enabledMetrics = enabledMetrics,
                            sharedCategories = summary.sharedCategories,
                            disabledNote = "Passos desativados na configuração deste programa.",
                            unsharedNote = "Passos não compartilhados por este participante."
                        ) {
                            val steps = summary.stepsSection ?: return@MetricSlotSection
                            DailyMetricCard(
                                title = "Passos",
                                icon = Icons.Default.DirectionsWalk,
                                unit = "passos",
                                source = steps.source,
                                participant = summary.patientUserId,
                                period = summary.periodDays,
                                periodEnd = summary.periodEnd,
                                brandAccent = brandAccent,
                                readings = steps.days.map { d ->
                                    DailyReading(
                                        d.date,
                                        d.steps.toLong(),
                                        "${d.steps} ${B2BTextFormatters.plural(d.steps, "passo", "passos")} · " +
                                            B2BTextFormatters.countLabel(d.activeMinutes, "min ativo", "min ativos")
                                    )
                                }
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
                            DailyMetricCard(
                                title = "Hidratação",
                                icon = Icons.Default.LocalDrink,
                                unit = "ml",
                                source = hyd.source,
                                participant = summary.patientUserId,
                                period = summary.periodDays,
                                periodEnd = summary.periodEnd,
                                brandAccent = brandAccent,
                                readings = hyd.days.map { d ->
                                    DailyReading(d.date, d.totalMl, "${d.totalMl} ml")
                                }
                            )
                        }

                        MetricSlotSection(
                            metricKey = SharedCategoryOption.MEDICATIONS.wireKey,
                            enabledMetrics = enabledMetrics,
                            sharedCategories = summary.sharedCategories,
                            disabledNote = "Lembretes de medicamentos desativados neste programa.",
                            unsharedNote = "Lembretes de medicamentos não compartilhados pelo participante."
                        ) {
                            ClinicalDetailControl("medicação", "medications", summary, isLoading, onClinicalDetail)
                            val meds = summary.medicationsSection ?: return@MetricSlotSection
                            val listText = meds.items.take(5).joinToString("\n") { m ->
                                "• ${m.name} (${m.schedule.ifBlank { "sem horário fixo" }})"
                            }
                            SummaryMetricCard(
                                icon = Icons.Default.Medication,
                                title = "Adesão a lembretes",
                                source = meds.source,
                                body = "${B2BTextFormatters.countLabel(meds.userConfirmationsInPeriod, "confirmação", "confirmações")} no período. ${meds.note}" +
                                    if (listText.isNotBlank()) "\n$listText" else "",
                                brandAccent = brandAccent
                            )
                        }

                        MetricSlotSection(
                            metricKey = SharedCategoryOption.AUTHORIZED_RECORDS.wireKey,
                            enabledMetrics = enabledMetrics,
                            sharedCategories = summary.sharedCategories,
                            disabledNote = "Registros de sinais e biometria desativados neste programa.",
                            unsharedNote = "Registros de sinais e biometria não compartilhados pelo participante."
                        ) {
                            ClinicalDetailControl("sinais e biometria", "authorized_records", summary, isLoading, onClinicalDetail)
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
                                "• ${B2BTextFormatters.formatIsoDate(r.measuredAt)} (${r.origin}): $parts"
                            }
                            val bioLines = recs.biometries.take(6).map { b ->
                                val parts = buildList {
                                    add("Peso ${String.format(Locale.US, "%.1f", b.weightKg)} kg")
                                    b.heightM?.let { add("Altura ${String.format(Locale.US, "%.2f", it)} m") }
                                    b.imc?.let { add("IMC ${String.format(Locale.US, "%.1f", it)}") }
                                }.joinToString(" · ")
                                "• ${B2BTextFormatters.formatIsoDate(b.measuredAt)} (Biometria): $parts"
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
                                icon = Icons.Default.MonitorHeart,
                                title = B2BThemeEngine.PARTNER_RECORDS_TITLE,
                                source = recs.source,
                                body = combined,
                                brandAccent = brandAccent
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
                                "Envie uma orientação de rotina por push."
                            } else {
                                "Desativado nas configurações do programa."
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
                            title = "Ver agendamentos",
                            subtitle = if (features.appointmentsEnabled) {
                                if (pendingAppt > 0) {
                                    "${B2BTextFormatters.countLabel(pendingAppt, "solicitação", "solicitações")} aguardando confirmação."
                                } else {
                                    "Consulte os horários deste participante."
                                }
                            } else {
                                "Desativado nas configurações do programa."
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
                            title = "Abrir chat direto",
                            subtitle = if (features.directPartnerChatEnabled) {
                                if (unreadMsgs > 0) {
                                    B2BTextFormatters.countLabel(
                                        unreadMsgs,
                                        "mensagem não lida",
                                        "mensagens não lidas"
                                    ) + " no canal direto."
                                } else {
                                    "Canal assíncrono com ${summary.participantName}."
                                }
                            } else {
                                "Desativado nas configurações do programa."
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (isRemoving) "Encerrando vínculo..." else "Encerrar vínculo deste participante",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
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
private fun ClinicalDetailControl(label: String, category: String, summary: ParticipantRoutineSummaryDto,
    loading: Boolean, onChange: (String?) -> Unit) {
    if (category !in summary.availableDetails) {
        MutedMetricNote("Detalhes de $label sem autorização específica vigente.")
        return
    }
    val expanded = summary.detailCategory == category
    TextButton(
        enabled = !loading,
        onClick = { onChange(if (expanded) null else category) },
        modifier = Modifier.heightIn(min = 48.dp)
    ) {
        Icon(
            imageVector = if (expanded) Icons.Default.Close else Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(if (expanded) "Ocultar detalhes de $label" else "Consultar detalhes de $label")
    }
}

private data class DailyReading(val date: String, val value: Long, val detail: String)

@Composable
private fun DailyMetricCard(
    title: String,
    icon: ImageVector,
    unit: String,
    source: String,
    participant: String,
    period: Int,
    periodEnd: String?,
    brandAccent: Color,
    readings: List<DailyReading>
) {
    val end = remember(participant, period, periodEnd) {
        runCatching { LocalDate.parse(periodEnd) }.getOrElse { LocalDate.now(ZoneId.of("America/Sao_Paulo")) }
    }
    val dates = (period.coerceIn(1, 90) - 1 downTo 0).map { end.minusDays(it.toLong()).toString() }
    val byDate = readings.associateBy { it.date }
    val values = dates.map { byDate[it]?.value }
    val recorded = values.filterNotNull()
    var expanded by remember(participant, end, period) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(14.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = brandAccent
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Fonte: $source",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = brandAccent.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = "$unit/dia",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = brandAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (recorded.isEmpty()) {
                MutedMetricNote("Sem registros nos últimos ${B2BTextFormatters.days(period)}.")
            } else {
                val mean = recorded.average()
                Text(
                    text = "Média de ${String.format(Locale.forLanguageTag("pt-BR"), "%.1f", mean)} $unit/dia · " +
                        "${recorded.size} de ${dates.size} dias com registro",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                PartnerDailyBars(values, mean)
                Text(
                    text = "${B2BTextFormatters.formatIsoDate(dates.first())} a ${B2BTextFormatters.formatIsoDate(dates.last())} · linha tracejada indica a média",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (expanded) "Ocultar detalhes diários" else "Ver detalhes diários de $title")
                }
                AnimatedVisibility(visible = expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        dates.forEach { date ->
                            Text(
                                text = "${B2BTextFormatters.formatIsoDate(date)} · ${byDate[date]?.detail ?: "Sem registro"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryMetricCard(
    icon: ImageVector,
    title: String,
    source: String,
    body: String,
    brandAccent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = brandAccent
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Fonte: $source",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(text = body, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ClinicalDetailsGuidance(message: String, accent: Color) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = null,
                tint = accent
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MutedMetricNote(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
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
    val stateDescriptionText = if (enabled) {
        "Disponível"
    } else {
        "Desativado nas configurações do programa"
    }
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        shape = RoundedCornerShape(12.dp),
        color = if (enabled) {
            MaterialTheme.colorScheme.surface
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(
            1.dp,
            if (enabled) brandAccent.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .semantics { stateDescription = stateDescriptionText },
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (enabled) {
                            brandAccent.copy(alpha = 0.12f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (enabled) brandAccent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
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
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = if (enabled) brandAccent else MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    var showAllReminders by rememberSaveable { mutableStateOf(false) }
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
                            text = "Canal educativo separado dos alarmes pessoais de medicação (sem diagnóstico nem prescrição).",
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
                val visibleReminders = if (showAllReminders) reminders else reminders.take(3)
                visibleReminders.forEach { rem ->
                    val typeLabel = PartnerReminderType.fromWire(rem.reminderType).labelPtBr
                    val targetLabel = if (rem.targetPatientId == null) "Envio em lote" else "Envio individual"
                    val sentDate = B2BTextFormatters.formatIsoDate(rem.createdAt).orEmpty()
                    val ackLabel = B2BTextFormatters.countLabelAgreement(rem.acknowledgedCount, "leitura", "leituras", "confirmada", "confirmadas")
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "${rem.title} · $typeLabel",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = listOfNotNull(sentDate.ifBlank { null }, targetLabel)
                                    .joinToString(" · "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val total = rem.recipientCount ?: if (rem.targetPatientId != null) 1 else null
                            val showRing = total != null && total > 0 && rem.acknowledgedCount in 0..total
                            val ringPercent = if (showRing && total != null) {
                                (rem.acknowledgedCount * 100.0 / total).toInt()
                            } else {
                                0
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = if (showRing) {
                                    // O anel é desenho puro: expõe a proporção em texto
                                    // para leitores de tela em vez de depender só da cor.
                                    Modifier.clearAndSetSemantics {
                                        contentDescription =
                                            "Leituras confirmadas: ${rem.acknowledgedCount} de ${total} (${ringPercent}%)."
                                    }
                                } else {
                                    Modifier
                                }
                            ) {
                                if (showRing) {
                                    PartnerRing(rem.acknowledgedCount, total ?: 0)
                                    Text("${rem.acknowledgedCount} de $total com leitura confirmada")
                                } else Text(ackLabel)
                            }
                        }
                    }
                }
                if (reminders.size > 3) {
                    TextButton(
                        onClick = { showAllReminders = !showAllReminders },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                    ) {
                        Text(
                            if (showAllReminders) "Mostrar menos" else "Ver todos (${reminders.size})"
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
                        text = pluralStringResource(R.plurals.b2b_individual_reminder_limit, 3, 3) + " " +
                            pluralStringResource(R.plurals.b2b_batch_reminder_limit, 20, 20),
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PartnerReminderType.entries.forEach { opt ->
                    FilterChip(
                        selected = selectedType == opt,
                        onClick = { selectedType = opt },
                        leadingIcon = if (selectedType == opt) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
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
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        leadingIcon = if (selectedCategory == null) {
                            { Icon(Icons.Default.Check, contentDescription = null) }
                        } else {
                            null
                        },
                        label = { Text("Todos ativos") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = brandPrimary,
                            selectedLabelColor = brandOnPrimary
                        )
                    )
                    categoryOptions.forEach { opt ->
                        FilterChip(
                            selected = selectedCategory == opt.wireKey,
                            onClick = {
                                selectedCategory = if (selectedCategory == opt.wireKey) null else opt.wireKey
                            },
                            leadingIcon = if (selectedCategory == opt.wireKey) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else {
                                null
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
                label = { Text("Título do lembrete") },
                placeholder = { Text("Ex.: Meta de hidratação da tarde") },
                supportingText = { Text("${title.length}/140 caracteres") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = body,
                onValueChange = { if (it.length <= 500) body = it },
                label = { Text("Mensagem de rotina") },
                placeholder = { Text("Ex.: Beba 300 ml de água e registre seus passos antes do retorno.") },
                supportingText = { Text("${body.length}/500 caracteres") },
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
