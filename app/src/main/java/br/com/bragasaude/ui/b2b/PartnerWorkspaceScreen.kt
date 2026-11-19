package br.com.bragasaude.ui.b2b

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.domain.b2b.AppContextMode
import br.com.bragasaude.domain.b2b.B2BThemeEngine
import br.com.bragasaude.domain.b2b.ParticipantRoutineSummaryDto
import br.com.bragasaude.domain.b2b.SharedCategoryOption
import coil3.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerWorkspaceScreen(
    initialInviteCode: String = "",
    onBack: () -> Unit,
    viewModel: PartnerWorkspaceViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activeContext by viewModel.activeContext.collectAsState()
    val availableContexts by viewModel.availableContexts.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val programInfo by viewModel.programInfo.collectAsState()
    val participants by viewModel.participants.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val selectedSummary by viewModel.selectedSummary.collectAsState()
    val isLoadingSummary by viewModel.isLoadingSummary.collectAsState()
    val selectedPeriodDays by viewModel.selectedPeriodDays.collectAsState()
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsState()
    val generatedPdf by viewModel.generatedPdf.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var showOnboardingSheet by rememberSaveable { mutableStateOf(false) }
    var showConsentSheet by rememberSaveable { mutableStateOf(false) }
    var pendingInviteCode by rememberSaveable { mutableStateOf(initialInviteCode.trim().uppercase()) }

    LaunchedEffect(Unit) {
        viewModel.refreshWorkspace()
        if (pendingInviteCode.isNotBlank()) {
            showConsentSheet = true
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.consumeStatusMessage()
        }
    }

    val tokens = remember(activeContext.primaryColor) {
        B2BThemeEngine.resolveAccessiblePalette(activeContext.primaryColor)
    }
    val brandPrimary = remember(tokens.primaryHex) {
        Color(android.graphics.Color.parseColor(tokens.primaryHex))
    }
    val brandOnPrimary = remember(tokens.onPrimaryHex) {
        Color(android.graphics.Color.parseColor(tokens.onPrimaryHex))
    }
    val brandSoftSurface = remember(tokens.softSurfaceHex) {
        Color(android.graphics.Color.parseColor(tokens.softSurfaceHex))
    }
    val brandSoftBorder = remember(tokens.softBorderHex) {
        Color(android.graphics.Color.parseColor(tokens.softBorderHex))
    }
    val isDark = isSystemInDarkTheme()
    val brandAccentText = remember(tokens, isDark) {
        Color(
            android.graphics.Color.parseColor(
                if (isDark) tokens.accentOnDarkHex else tokens.accentOnLightHex
            )
        )
    }

    if (showOnboardingSheet) {
        PartnerProgramOnboardingSheet(
            apiClient = viewModel.partnerApiClient(),
            onDismiss = {
                showOnboardingSheet = false
                viewModel.refreshWorkspace()
            }
        )
    }

    if (showConsentSheet) {
        PartnerConsentSheet(
            apiClient = viewModel.partnerApiClient(),
            initialInviteCode = pendingInviteCode,
            onDismiss = {
                showConsentSheet = false
                pendingInviteCode = ""
                viewModel.refreshWorkspace()
            }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                    .background(brandPrimary)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = brandOnPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val logoUrl = activeContext.logoUrl
                        if (!logoUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = logoUrl,
                                contentDescription = activeContext.programName ?: "Logo do parceiro",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            val initial = (activeContext.companyName?.trim()?.firstOrNull() ?: 'B').uppercaseChar()
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(brandOnPrimary.copy(alpha = 0.20f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initial.toString(),
                                    color = brandOnPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = activeContext.programName ?: "Espaço Parceiro",
                                color = brandOnPrimary,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${activeContext.companyName ?: "Parceiro"} · ${B2BThemeEngine.PARTNER_ATTRIBUTION}",
                                color = brandOnPrimary.copy(alpha = 0.88f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(onClick = { viewModel.refreshWorkspace() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar",
                            tint = brandOnPrimary
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Seletor de contextos autorizados + retorno rapido ao modo pessoal
            if (availableContexts.size > 1 || activeContext.isPartnerContext) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Contexto ativo",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(availableContexts, key = { it.cacheKey() }) { ctx ->
                                val isSelected = ctx.cacheKey() == activeContext.cacheKey()
                                val label = when (ctx.parsedMode) {
                                    AppContextMode.PERSONAL -> "Meu espaço pessoal"
                                    AppContextMode.PARTNER_OWNER -> "Gestão: ${ctx.programName ?: "Minha empresa"}"
                                    AppContextMode.PARTNER_PARTICIPANT -> ctx.programName ?: "Programa"
                                }
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (ctx.parsedMode == AppContextMode.PERSONAL) {
                                            viewModel.selectContext(ctx)
                                            onBack()
                                        } else {
                                            viewModel.selectContext(ctx)
                                        }
                                    },
                                    label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = brandPrimary,
                                        selectedLabelColor = brandOnPrimary
                                    )
                                )
                            }
                        }
                    }
                }
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = brandAccentText)
                    }
                }
            } else if (!activeContext.isPartnerContext) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = brandSoftSurface),
                        border = BorderStroke(1.dp, brandSoftBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Você ainda não está em um programa parceiro ativo",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = brandAccentText
                            )
                            Text(
                                text = "Informe um código de convite recebido da clínica ou configure o programa com a marca da sua empresa.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { showConsentSheet = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = brandPrimary,
                                        contentColor = brandOnPrimary
                                    )
                                ) {
                                    Text("Participar por código")
                                }
                                OutlinedButton(
                                    onClick = { showOnboardingSheet = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Minha empresa")
                                }
                            }
                        }
                    }
                }
            } else if (activeContext.parsedMode == AppContextMode.PARTNER_OWNER) {
                // =========================================================================
                // VISAO DO RESPONSAVEL DO PROGRAMA (Rotina compartilhada / Registros autorizados)
                // =========================================================================
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, brandSoftBorder)
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = B2BThemeEngine.PARTNER_AREA_TITLE,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = brandAccentText
                                    )
                                    Text(
                                        text = B2BThemeEngine.PARTNER_RECORDS_TITLE,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                programInfo?.let { info ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = brandPrimary.copy(alpha = 0.14f)
                                    ) {
                                        Text(
                                            text = "${info.activeParticipants} / ${info.seatLimit} vagas",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = brandAccentText,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                            programInfo?.inviteCode?.let { code ->
                                Text(
                                    text = "Código ativo para convite: $code",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            OutlinedButton(
                                onClick = { showOnboardingSheet = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Gerenciar marca, convite e franquia")
                            }
                        }
                    }
                }

                if (selectedSummary != null) {
                    item {
                        OwnerParticipantSummaryCard(
                            summary = selectedSummary!!,
                            selectedDays = selectedPeriodDays,
                            isLoading = isLoadingSummary,
                            brandAccent = brandAccentText,
                            brandPrimary = brandPrimary,
                            brandOnPrimary = brandOnPrimary,
                            onChangeDays = { newDays ->
                                viewModel.openParticipantSummary(selectedSummary!!.patientUserId, newDays)
                            },
                            onClose = { viewModel.clearSelectedSummary() }
                        )
                    }
                }

                item {
                    Text(
                        text = "Participantes vinculados (${participants.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (participants.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Text(
                                text = "Nenhum participante ativo vinculado até o momento. Compartilhe o código de convite após a aprovação para receber autorizações de rotina.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(participants, key = { it.patientUserId }) { item ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.openParticipantSummary(item.patientUserId) },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(brandPrimary),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = (item.displayName.firstOrNull() ?: 'P').uppercaseChar().toString(),
                                        color = brandOnPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val categoryLabels = item.sharedCategories.mapNotNull {
                                        SharedCategoryOption.fromWire(it)?.titlePtBr
                                    }
                                    Text(
                                        text = if (categoryLabels.isEmpty()) {
                                            "Nenhuma categoria compartilhada"
                                        } else {
                                            "Autorizados: ${categoryLabels.joinToString(", ")}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    if (hasMore) {
                        item {
                            OutlinedButton(
                                onClick = { viewModel.loadMoreParticipants() },
                                enabled = !isLoadingMore,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (isLoadingMore) "Carregando..." else "Carregar mais participantes")
                            }
                        }
                    }
                }
            } else {
                // =========================================================================
                // VISAO DO PARTICIPANTE + GERACAO / REVISAO DO PDF PERSONALIZADO
                // =========================================================================
                item {
                    ParticipantShareAndPdfSection(
                        sharedCategories = activeContext.sharedCategories,
                        brandPrimary = brandPrimary,
                        brandOnPrimary = brandOnPrimary,
                        brandAccent = brandAccentText,
                        softSurface = brandSoftSurface,
                        softBorder = brandSoftBorder,
                        isGeneratingPdf = isGeneratingPdf,
                        generatedPdf = generatedPdf,
                        onOpenConsentSheet = { showConsentSheet = true },
                        onGeneratePdf = { days, categories ->
                            viewModel.generatePreConsultationPdf(days, categories)
                        },
                        onPreviewPdf = { file -> openPdfForReview(context, file) },
                        onSharePdf = { file -> sharePdfExternally(context, file, activeContext.programName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun OwnerParticipantSummaryCard(
    summary: ParticipantRoutineSummaryDto,
    selectedDays: Int,
    isLoading: Boolean,
    brandAccent: Color,
    brandPrimary: Color,
    brandOnPrimary: Color,
    onChangeDays: (Int) -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, brandAccent.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = B2BThemeEngine.PARTNER_AREA_TITLE,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = brandAccent
                    )
                    Text(
                        text = summary.participantName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Período: últimos ${summary.periodDays} dias",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onClose) { Text("Fechar") }
            }

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
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            summary.stepsSection?.let { steps ->
                SummarySectionBlock(
                    title = "Passos (${steps.source})",
                    body = "Média de ${steps.averageSteps} passos/dia em ${steps.daysRecorded} dia(s) com registro."
                )
            } ?: SummarySectionLocked("Passos não compartilhados por este participante.")

            summary.hydrationSection?.let { hyd ->
                SummarySectionBlock(
                    title = "Hidratação (${hyd.source})",
                    body = "Média diária de ${hyd.averageMl} ml em ${hyd.daysRecorded} dia(s)."
                )
            } ?: SummarySectionLocked("Hidratação não compartilhada por este participante.")

            summary.medicationsSection?.let { meds ->
                val listText = meds.items.take(5).joinToString("\n") { m ->
                    "• ${m.name} (${m.schedule.ifBlank { "sem horário fixo" }})"
                }
                SummarySectionBlock(
                    title = "Adesão a lembretes (${meds.source})",
                    body = "${meds.userConfirmationsInPeriod} confirmações no período. ${meds.note}" +
                        if (listText.isNotBlank()) "\n$listText" else ""
                )
            } ?: SummarySectionLocked("Lembretes de medicamentos não compartilhados.")

            summary.authorizedRecordsSection?.let { recs ->
                val lines = recs.records.take(6).joinToString("\n") { r ->
                    val parts = buildList {
                        if (r.systolicPressure != null || r.diastolicPressure != null) {
                            add("PA ${r.systolicPressure ?: "--"}/${r.diastolicPressure ?: "--"} mmHg")
                        }
                        r.glucoseLevel?.let { add("Glicose $it mg/dL") }
                        r.heartRate?.let { add("FC $it bpm") }
                        r.oxygenSaturation?.let { add("SpO2 ${"%.0f".format(it)}%") }
                    }.joinToString(" • ")
                    "• ${r.measuredAt.take(10)} (${r.origin}): $parts"
                }.ifBlank { "Sem medições autorizadas registradas no período." }
                SummarySectionBlock(
                    title = "${B2BThemeEngine.PARTNER_RECORDS_TITLE} (${recs.source})",
                    body = "$lines\n${recs.zeroFileEgressNotice}"
                )
            } ?: SummarySectionLocked("Registros de sinais não compartilhados por este participante.")
        }
    }
}

@Composable
private fun SummarySectionBlock(title: String, body: String) {
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
private fun SummarySectionLocked(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ParticipantShareAndPdfSection(
    sharedCategories: List<String>,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softSurface: Color,
    softBorder: BorderStroke?,
    isGeneratingPdf: Boolean,
    generatedPdf: File?,
    onOpenConsentSheet: () -> Unit,
    onGeneratePdf: (Int, Set<String>) -> Unit,
    onPreviewPdf: (File) -> Unit,
    onSharePdf: (File) -> Unit
) {
    var selectedPdfDays by remember { mutableStateOf(30) }
    var selectedPdfCategories by remember(sharedCategories) {
        mutableStateOf(
            sharedCategories.toSet().ifEmpty {
                setOf(
                    SharedCategoryOption.STEPS.wireKey,
                    SharedCategoryOption.HYDRATION.wireKey
                )
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Cartao de compartilhamento ativo no app
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = softSurface),
            border = softBorder ?: BorderStroke(1.dp, brandAccent.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = brandAccent)
                    Text(
                        text = "Compartilhamento ativo no app",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = brandAccent
                    )
                }
                val labels = sharedCategories.mapNotNull { SharedCategoryOption.fromWire(it)?.titlePtBr }
                Text(
                    text = if (labels.isEmpty()) {
                        "Você ainda não autorizou categorias in-app."
                    } else {
                        "Categorias visíveis ao responsável do programa: ${labels.joinToString(", ")}."
                    },
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = onOpenConsentSheet,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Gerenciar permissões ou encerrar vínculo", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Cartao de exportacao de PDF de pre-consulta personalizado
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = brandAccent)
                    Text(
                        text = B2BThemeEngine.PDF_PRECONSULTATION_TITLE,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = B2BThemeEngine.PDF_NON_PEP_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text(
                    text = "Período do resumo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7, 14, 30).forEach { days ->
                        FilterChip(
                            selected = selectedPdfDays == days,
                            onClick = { selectedPdfDays = days },
                            label = { Text("Últimos $days dias") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = brandPrimary,
                                selectedLabelColor = brandOnPrimary
                            )
                        )
                    }
                }

                Text(
                    text = "O que incluir no PDF (arquivos pessoais do Organizador nunca são incluídos)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                SharedCategoryOption.entries.forEach { opt ->
                    val checked = selectedPdfCategories.contains(opt.wireKey)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedPdfCategories = if (checked) {
                                    selectedPdfCategories - opt.wireKey
                                } else {
                                    selectedPdfCategories + opt.wireKey
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { nowChecked ->
                                selectedPdfCategories = if (nowChecked) {
                                    selectedPdfCategories + opt.wireKey
                                } else {
                                    selectedPdfCategories - opt.wireKey
                                }
                            },
                            colors = CheckboxDefaults.colors(checkedColor = brandPrimary)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(opt.titlePtBr, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                Button(
                    onClick = { onGeneratePdf(selectedPdfDays, selectedPdfCategories) },
                    enabled = !isGeneratingPdf && selectedPdfCategories.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
                    Text(
                        text = if (isGeneratingPdf) "Gerando resumo em PDF..." else "Gerar PDF para revisão",
                        fontWeight = FontWeight.Bold
                    )
                }

                if (generatedPdf != null) {
                    HorizontalDivider()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = brandAccent)
                        Text(
                            text = "PDF gerado: ${generatedPdf.name}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onPreviewPdf(generatedPdf) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Visualizar PDF")
                        }
                        Button(
                            onClick = { onSharePdf(generatedPdf) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = brandPrimary,
                                contentColor = brandOnPrimary
                            )
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(B2BThemeEngine.PDF_SHARE_ACTION_LABEL, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

private fun openPdfForReview(context: android.content.Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, B2BThemeEngine.PDF_PRECONSULTATION_TITLE))
    }.onFailure {
        Toast.makeText(
            context,
            "Instale ou selecione um leitor de PDF para revisar o documento.",
            Toast.LENGTH_LONG
        ).show()
    }
}

private fun sharePdfExternally(
    context: android.content.Context,
    file: File,
    programName: String?
) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val label = programName?.takeIf { it.isNotBlank() } ?: "o programa"
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_SUBJECT,
                "${B2BThemeEngine.PDF_PRECONSULTATION_TITLE} — $label"
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, B2BThemeEngine.PDF_SHARE_ACTION_LABEL))
    }.onFailure {
        Toast.makeText(
            context,
            "Não foi possível abrir o seletor de compartilhamento agora.",
            Toast.LENGTH_LONG
        ).show()
    }
}
