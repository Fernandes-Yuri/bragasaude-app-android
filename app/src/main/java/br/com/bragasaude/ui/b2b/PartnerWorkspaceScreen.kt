package br.com.bragasaude.ui.b2b

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
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
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
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.domain.b2b.AppContextMode
import br.com.bragasaude.domain.b2b.B2BThemeEngine
import br.com.bragasaude.domain.b2b.ParticipantRoutineSummaryDto
import br.com.bragasaude.domain.b2b.PartnerApprovalStatus
import br.com.bragasaude.domain.b2b.ProgramExperienceConfigDto
import br.com.bragasaude.domain.b2b.SharedCategoryOption
import br.com.bragasaude.ui.components.BragaAlertDialog
import br.com.bragasaude.ui.components.BragaBottomSheet
import coil3.compose.AsyncImage

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
    val templates by viewModel.templates.collectAsState()
    val isSavingExperience by viewModel.isSavingExperience.collectAsState()
    val participants by viewModel.participants.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val selectedSummary by viewModel.selectedSummary.collectAsState()
    val isLoadingSummary by viewModel.isLoadingSummary.collectAsState()
    val isRemovingParticipant by viewModel.isRemovingParticipant.collectAsState()
    val selectedPeriodDays by viewModel.selectedPeriodDays.collectAsState()
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsState()
    val generatedPdf by viewModel.generatedPdf.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var showOnboardingSheet by rememberSaveable { mutableStateOf(false) }
    var showConsentSheet by rememberSaveable { mutableStateOf(false) }
    var showReceptionQrSheet by rememberSaveable { mutableStateOf(false) }
    var showAiSimulatorSheet by rememberSaveable { mutableStateOf(false) }
    var selectedOwnerTab by rememberSaveable { mutableStateOf(PartnerOwnerTab.WALLET) }
    var pendingInviteCode by rememberSaveable { mutableStateOf(initialInviteCode.trim().uppercase()) }
    var participantToRemove by remember { mutableStateOf<Pair<String, String>?>(null) }

    val savedExperience = remember(programInfo, activeContext) {
        programInfo?.experienceConfig ?: activeContext.resolvedExperienceConfig()
    }
    var draftExperience by remember(programInfo?.programId, savedExperience) {
        mutableStateOf(savedExperience)
    }

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

    if (showReceptionQrSheet) {
        val prog = programInfo
        val activeCode = prog?.inviteCode?.takeIf { it.isNotBlank() }
        if (prog != null && activeCode != null) {
            BragaBottomSheet(onDismissRequest = { showReceptionQrSheet = false }) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PartnerInviteQrCard(
                        programName = prog.programName,
                        companyName = prog.companyName,
                        inviteCode = activeCode,
                        primaryColorHex = prog.primaryColor,
                        accentColor = brandPrimary,
                        onAccentColor = brandOnPrimary
                    )
                    TextButton(
                        onClick = { showReceptionQrSheet = false },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Fechar")
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    if (showAiSimulatorSheet) {
        AiReceptionistSimulatorSheet(
            programName = programInfo?.programName ?: activeContext.programName ?: "Seu Programa",
            config = draftExperience,
            brandPrimary = brandPrimary,
            brandOnPrimary = brandOnPrimary,
            onDismiss = { showAiSimulatorSheet = false }
        )
    }

    participantToRemove?.let { (targetId, targetName) ->
        BragaAlertDialog(
            onDismissRequest = { if (!isRemovingParticipant) participantToRemove = null },
            title = { Text("Encerrar vínculo deste participante?") },
            text = {
                Text(
                    "O acesso de $targetName a este programa será encerrado e a vaga voltará a ficar disponível na sua franquia. A conta pessoal e os registros no app do paciente não serão apagados."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeParticipant(targetId)
                        participantToRemove = null
                    },
                    enabled = !isRemovingParticipant,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text("Encerrar vínculo")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { participantToRemove = null },
                    enabled = !isRemovingParticipant
                ) {
                    Text("Cancelar")
                }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (availableContexts.size > 1 || activeContext.isPartnerContext) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
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

            if (!isLoading && activeContext.parsedMode == AppContextMode.PARTNER_OWNER) {
                TabRow(
                    selectedTabIndex = PartnerOwnerTab.entries.indexOf(selectedOwnerTab).coerceAtLeast(0),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = brandAccentText
                ) {
                    PartnerOwnerTab.entries.forEach { tab ->
                        Tab(
                            selected = selectedOwnerTab == tab,
                            onClick = { selectedOwnerTab = tab },
                            text = {
                                Text(
                                    text = tab.title,
                                    fontWeight = if (selectedOwnerTab == tab) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        )
                    }
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = brandAccentText)
                    }
                }
                activeContext.parsedMode == AppContextMode.PARTNER_OWNER &&
                    selectedOwnerTab == PartnerOwnerTab.STUDIO -> {
                    PartnerStudioTabContent(
                        programInfo = programInfo,
                        draftConfig = draftExperience,
                        savedConfig = savedExperience,
                        templates = templates,
                        isSaving = isSavingExperience,
                        brandPrimary = brandPrimary,
                        brandOnPrimary = brandOnPrimary,
                        brandAccent = brandAccentText,
                        onDraftChange = { draftExperience = it },
                        onOpenOnboarding = { showOnboardingSheet = true },
                        onOpenSimulator = { showAiSimulatorSheet = true },
                        onSaveChanges = {
                            viewModel.saveExperienceConfig(
                                templateKey = draftExperience.templateKey,
                                applyTemplateDefaults = false,
                                experienceConfig = draftExperience
                            )
                        }
                    )
                }
                activeContext.parsedMode == AppContextMode.PARTNER_OWNER &&
                    selectedOwnerTab == PartnerOwnerTab.PREVIEW -> {
                    PartnerPreviewTabContent(
                        programName = programInfo?.programName ?: activeContext.programName ?: "Seu Programa",
                        companyName = programInfo?.companyName ?: activeContext.companyName ?: "Sua Empresa",
                        logoUrl = programInfo?.logoUrl ?: activeContext.logoUrl,
                        primaryColorHex = programInfo?.primaryColor ?: activeContext.primaryColor,
                        draftConfig = draftExperience,
                        brandPrimary = brandPrimary,
                        brandOnPrimary = brandOnPrimary,
                        brandAccent = brandAccentText,
                        softSurface = brandSoftSurface,
                        softBorder = BorderStroke(1.dp, brandSoftBorder),
                        onOpenSimulator = { showAiSimulatorSheet = true }
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (!activeContext.isPartnerContext) {
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
                            // ABA CARTEIRA — VISAO DO RESPONSAVEL DO PROGRAMA
                            // =========================================================================
                            programInfo?.let { prog ->
                                item {
                                    FirstAccessActivationChecklistCard(
                                        programInfo = prog,
                                        brandPrimary = brandPrimary,
                                        brandAccent = brandAccentText,
                                        softSurface = brandSoftSurface,
                                        softBorder = BorderStroke(1.dp, brandSoftBorder),
                                        onOpenOnboarding = { showOnboardingSheet = true },
                                        onOpenStudio = { selectedOwnerTab = PartnerOwnerTab.STUDIO },
                                        onOpenQr = { showReceptionQrSheet = true }
                                    )
                                }
                            }

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
                                        if (programInfo?.parsedStatus == PartnerApprovalStatus.APPROVED &&
                                            !programInfo?.inviteCode.isNullOrBlank()
                                        ) {
                                            OutlinedButton(
                                                onClick = { showReceptionQrSheet = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.QrCode2,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("QR Code e Placa PDF da Recepção")
                                            }
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
                                        isRemoving = isRemovingParticipant,
                                        brandAccent = brandAccentText,
                                        brandPrimary = brandPrimary,
                                        brandOnPrimary = brandOnPrimary,
                                        onChangeDays = { newDays ->
                                            viewModel.openParticipantSummary(selectedSummary!!.patientUserId, newDays)
                                        },
                                        onRequestRemove = {
                                            val current = selectedSummary!!
                                            participantToRemove = current.patientUserId to current.participantName
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
                            // VISAO DO PARTICIPANTE ADAPTATIVA (B2B-16)
                            // =========================================================================
                            val participantProgramCount = availableContexts.count {
                                it.parsedMode == AppContextMode.PARTNER_PARTICIPANT
                            }
                            item {
                                ParticipantExperienceContent(
                                    programName = activeContext.programName ?: "Programa Parceiro",
                                    companyName = activeContext.companyName ?: "Parceiro Braga Saúde",
                                    logoUrl = activeContext.logoUrl,
                                    experienceConfig = activeContext.resolvedExperienceConfig(),
                                    sharedCategories = activeContext.sharedCategories,
                                    coParticipatedCount = participantProgramCount,
                                    brandPrimary = brandPrimary,
                                    brandOnPrimary = brandOnPrimary,
                                    brandAccent = brandAccentText,
                                    softSurface = brandSoftSurface,
                                    softBorder = BorderStroke(1.dp, brandSoftBorder),
                                    isGeneratingPdf = isGeneratingPdf,
                                    generatedPdf = generatedPdf,
                                    isPreview = false,
                                    onOpenConsentSheet = { showConsentSheet = true },
                                    onGeneratePdf = { days, categories ->
                                        viewModel.generatePreConsultationPdf(days, categories)
                                    },
                                    onPreviewPdf = { file ->
                                        openPartnerPdfFile(context, file, B2BThemeEngine.PDF_PRECONSULTATION_TITLE)
                                    },
                                    onSharePdf = { file ->
                                        val label = activeContext.programName?.takeIf { it.isNotBlank() } ?: "o programa"
                                        sharePartnerPdfFile(
                                            context = context,
                                            file = file,
                                            subject = "${B2BThemeEngine.PDF_PRECONSULTATION_TITLE} — $label"
                                        )
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

@Composable
private fun OwnerParticipantSummaryCard(
    summary: ParticipantRoutineSummaryDto,
    selectedDays: Int,
    isLoading: Boolean,
    isRemoving: Boolean,
    brandAccent: Color,
    brandPrimary: Color,
    brandOnPrimary: Color,
    onChangeDays: (Int) -> Unit,
    onRequestRemove: () -> Unit,
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
