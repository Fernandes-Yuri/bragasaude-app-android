package br.com.bragasaude.ui.b2b

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import br.com.bragasaude.domain.b2b.PartnerApprovalStatus
import br.com.bragasaude.domain.b2b.ProgramAppointmentDto
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
    val walletQuery by viewModel.walletQuery.collectAsState()
    val walletCategoryFilter by viewModel.walletCategoryFilter.collectAsState()
    val walletAttentionOnly by viewModel.walletAttentionOnly.collectAsState()
    val isFilteringParticipants by viewModel.isFilteringParticipants.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val selectedParticipantItem by viewModel.selectedParticipantItem.collectAsState()
    val selectedSummary by viewModel.selectedSummary.collectAsState()
    val isLoadingSummary by viewModel.isLoadingSummary.collectAsState()
    val isRemovingParticipant by viewModel.isRemovingParticipant.collectAsState()
    val selectedPeriodDays by viewModel.selectedPeriodDays.collectAsState()
    val ownerReminders by viewModel.ownerReminders.collectAsState()
    val isSendingReminder by viewModel.isSendingReminder.collectAsState()
    val enrollmentRemindersFeed by viewModel.enrollmentRemindersFeed.collectAsState()
    val acknowledgingReminderId by viewModel.acknowledgingReminderId.collectAsState()
    val isUpdatingMute by viewModel.isUpdatingMute.collectAsState()
    val isReportingProgram by viewModel.isReportingProgram.collectAsState()
    val ownerAppointments by viewModel.ownerAppointments.collectAsState()
    val enrollmentAppointmentsFeed by viewModel.enrollmentAppointmentsFeed.collectAsState()
    val isSubmittingAppointment by viewModel.isSubmittingAppointment.collectAsState()
    val updatingAppointmentId by viewModel.updatingAppointmentId.collectAsState()
    val activeDirectChatPage by viewModel.activeDirectChatPage.collectAsState()
    val directChatIsOwnerView by viewModel.directChatIsOwnerView.collectAsState()
    val directChatCounterpartTitle by viewModel.directChatCounterpartTitle.collectAsState()
    val isLoadingDirectChat by viewModel.isLoadingDirectChat.collectAsState()
    val isSendingDirectMessage by viewModel.isSendingDirectMessage.collectAsState()
    val isUpdatingHandoff by viewModel.isUpdatingHandoff.collectAsState()
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsState()
    val generatedPdf by viewModel.generatedPdf.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()

    var showOnboardingSheet by rememberSaveable { mutableStateOf(false) }
    var showConsentSheet by rememberSaveable { mutableStateOf(false) }
    var showReceptionQrSheet by rememberSaveable { mutableStateOf(false) }
    var showAiSimulatorSheet by rememberSaveable { mutableStateOf(false) }
    var showReportProgramSheet by rememberSaveable { mutableStateOf(false) }
    var showRequestAppointmentSheet by rememberSaveable { mutableStateOf(false) }
    var selectedOwnerTab by rememberSaveable { mutableStateOf(PartnerOwnerTab.WALLET) }
    var pendingInviteCode by rememberSaveable { mutableStateOf(initialInviteCode.trim().uppercase()) }
    var participantToRemove by remember { mutableStateOf<Pair<String, String>?>(null) }
    var reminderComposerTarget by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var selectedAppointmentToManage by remember { mutableStateOf<ProgramAppointmentDto?>(null) }

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

    if (showReportProgramSheet) {
        val targetProgramId = activeContext.programId
        if (targetProgramId != null) {
            ReportProgramBottomSheet(
                programName = activeContext.programName ?: "este programa",
                isSubmitting = isReportingProgram,
                brandPrimary = brandPrimary,
                brandOnPrimary = brandOnPrimary,
                onSubmit = { reason, details, muteAlso ->
                    viewModel.reportProgramAbuse(
                        programId = targetProgramId,
                        reason = reason,
                        details = details,
                        muteAlso = muteAlso,
                        onSuccess = { showReportProgramSheet = false }
                    )
                },
                onDismiss = { if (!isReportingProgram) showReportProgramSheet = false }
            )
        }
    }

    if (showRequestAppointmentSheet) {
        val targetProgramId = activeContext.programId
        if (targetProgramId != null) {
            RequestAppointmentBottomSheet(
                programName = activeContext.programName ?: "este programa",
                policy = enrollmentAppointmentsFeed?.appointmentsPolicy ?: savedExperience.appointmentsPolicy,
                isSubmitting = isSubmittingAppointment,
                brandPrimary = brandPrimary,
                brandOnPrimary = brandOnPrimary,
                onSubmit = { modality, requestedSlotText, patientNote ->
                    viewModel.requestEnrollmentAppointment(
                        programId = targetProgramId,
                        modality = modality,
                        requestedSlotText = requestedSlotText,
                        patientNote = patientNote,
                        onSuccess = { showRequestAppointmentSheet = false }
                    )
                },
                onDismiss = { if (!isSubmittingAppointment) showRequestAppointmentSheet = false }
            )
        }
    }

    selectedAppointmentToManage?.let { appt ->
        OwnerAppointmentDecisionBottomSheet(
            appointment = appt,
            isUpdating = updatingAppointmentId == appt.appointmentId,
            brandPrimary = brandPrimary,
            brandOnPrimary = brandOnPrimary,
            onConfirmDecision = { targetStatus, scheduledAtIso, partnerNote, allowOverlap ->
                viewModel.updateOwnerAppointment(
                    appointmentId = appt.appointmentId,
                    status = targetStatus,
                    scheduledAtIso = scheduledAtIso,
                    partnerNote = partnerNote,
                    allowOverlap = allowOverlap,
                    onSuccess = { selectedAppointmentToManage = null }
                )
            },
            onDismiss = { if (updatingAppointmentId == null) selectedAppointmentToManage = null }
        )
    }

    activeDirectChatPage?.let { chatPage ->
        PartnerDirectChatBottomSheet(
            page = chatPage,
            isOwnerView = directChatIsOwnerView,
            counterpartTitle = directChatCounterpartTitle,
            isLoadingMore = isLoadingDirectChat,
            isSending = isSendingDirectMessage,
            isUpdatingHandoff = isUpdatingHandoff,
            brandPrimary = brandPrimary,
            brandOnPrimary = brandOnPrimary,
            brandAccent = brandAccentText,
            softSurface = brandSoftSurface,
            onLoadOlder = { viewModel.loadMoreDirectMessages() },
            onSendMessage = { msgBody -> viewModel.sendDirectMessage(msgBody) },
            onHandoffModeChange = { nextMode -> viewModel.handoffDirectChat(nextMode) },
            onRequestHuman = { viewModel.requestHumanHandoff() },
            onDismiss = { viewModel.closeDirectChat() }
        )
    }

    selectedSummary?.let { summary ->
        ParticipantDetailBottomSheet(
            summary = summary,
            participantItem = selectedParticipantItem
                ?: participants.firstOrNull { it.patientUserId == summary.patientUserId },
            experienceConfig = savedExperience,
            selectedDays = selectedPeriodDays,
            isLoading = isLoadingSummary,
            isRemoving = isRemovingParticipant,
            brandPrimary = brandPrimary,
            brandOnPrimary = brandOnPrimary,
            brandAccent = brandAccentText,
            softSurface = brandSoftSurface,
            onChangeDays = { newDays ->
                viewModel.openParticipantSummary(summary.patientUserId, newDays)
            },
            onActionFeedback = { feedback ->
                Toast.makeText(context, feedback, Toast.LENGTH_LONG).show()
            },
            onOpenSendReminder = { pid, pname ->
                viewModel.clearSelectedSummary()
                reminderComposerTarget = pid to pname
            },
            onOpenManageAppointments = { pid, pname ->
                val targetAppt = ownerAppointments.firstOrNull {
                    it.patientUserId == pid &&
                        it.status !in setOf("CANCELLED", "COMPLETED")
                } ?: ownerAppointments.firstOrNull { it.patientUserId == pid }
                if (targetAppt != null && targetAppt.status !in setOf("CANCELLED", "COMPLETED")) {
                    viewModel.clearSelectedSummary()
                    selectedAppointmentToManage = targetAppt
                } else {
                    Toast.makeText(
                        context,
                        "$pname não tem solicitações abertas no momento.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onOpenDirectChat = { pid, pname ->
                viewModel.clearSelectedSummary()
                viewModel.openOwnerDirectChat(pid, pname)
            },
            onRequestRemove = {
                participantToRemove = summary.patientUserId to summary.participantName
            },
            onDismiss = { viewModel.clearSelectedSummary() }
        )
    }

    reminderComposerTarget?.let { (targetId, targetName) ->
        PartnerSendReminderBottomSheet(
            targetPatientId = targetId,
            targetPatientName = targetName,
            enabledProgramMetrics = savedExperience.enabledMetrics,
            isSending = isSendingReminder,
            brandPrimary = brandPrimary,
            brandOnPrimary = brandOnPrimary,
            brandAccent = brandAccentText,
            onSend = { category, rType, remTitle, remBody ->
                viewModel.sendPartnerReminder(
                    targetPatientId = targetId,
                    targetCategory = category,
                    reminderType = rType,
                    title = remTitle,
                    body = remBody,
                    onSuccess = { reminderComposerTarget = null }
                )
            },
            onDismiss = { if (!isSendingReminder) reminderComposerTarget = null }
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
                                if (prog.aiPaused) {
                                    item {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer
                                            )
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(14.dp),
                                                verticalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "Recepcionista IA pausada pela moderação Braga",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                                Text(
                                                    text = prog.reviewNote?.takeIf { it.isNotBlank() }
                                                        ?: "As respostas automáticas da Recepcionista IA estão suspensas para conferência de conteúdo. O atendimento humano direto e o painel do programa seguem funcionando.",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            }
                                        }
                                    }
                                }
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

                            if (programInfo?.parsedStatus == PartnerApprovalStatus.APPROVED &&
                                savedExperience.features.partnerRemindersEnabled
                            ) {
                                item {
                                    OwnerSentRemindersCard(
                                        reminders = ownerReminders,
                                        brandPrimary = brandPrimary,
                                        brandOnPrimary = brandOnPrimary,
                                        brandAccent = brandAccentText,
                                        softBorder = BorderStroke(1.dp, brandSoftBorder),
                                        onOpenBatchComposer = {
                                            reminderComposerTarget = null to "Lote da carteira"
                                        }
                                    )
                                }
                            }

                            if (programInfo?.parsedStatus == PartnerApprovalStatus.APPROVED &&
                                savedExperience.features.appointmentsEnabled
                            ) {
                                item {
                                    OwnerAppointmentsWalletCard(
                                        appointments = ownerAppointments,
                                        policy = savedExperience.appointmentsPolicy,
                                        updatingAppointmentId = updatingAppointmentId,
                                        brandPrimary = brandPrimary,
                                        brandAccent = brandAccentText,
                                        softBorder = BorderStroke(1.dp, brandSoftBorder),
                                        onManageAppointment = { appt ->
                                            selectedAppointmentToManage = appt
                                        }
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

                            item {
                                PartnerWalletFiltersSection(
                                    query = walletQuery,
                                    selectedCategory = walletCategoryFilter,
                                    attentionOnly = walletAttentionOnly,
                                    enabledProgramMetrics = savedExperience.enabledMetrics,
                                    isFiltering = isFilteringParticipants,
                                    brandPrimary = brandPrimary,
                                    brandOnPrimary = brandOnPrimary,
                                    brandAccent = brandAccentText,
                                    onQueryChange = viewModel::updateWalletQuery,
                                    onSelectCategory = viewModel::selectWalletCategoryFilter,
                                    onToggleAttentionOnly = viewModel::toggleWalletAttentionOnly,
                                    onClearFilters = viewModel::clearWalletFilters
                                )
                            }

                            val hasActiveWalletFilter = walletQuery.isNotBlank() ||
                                walletCategoryFilter != null ||
                                walletAttentionOnly

                            if (participants.isEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = if (hasActiveWalletFilter) {
                                                    "Nenhum participante encontrado para os filtros aplicados."
                                                } else {
                                                    "Nenhum participante ativo vinculado até o momento. Compartilhe o código de convite após a aprovação para receber autorizações de rotina."
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            if (hasActiveWalletFilter) {
                                                TextButton(onClick = viewModel::clearWalletFilters) {
                                                    Text("Limpar filtros", color = brandAccentText)
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(participants, key = { it.patientUserId }) { item ->
                                    WalletParticipantCard(
                                        item = item,
                                        brandPrimary = brandPrimary,
                                        brandOnPrimary = brandOnPrimary,
                                        brandAccent = brandAccentText,
                                        softSurface = brandSoftSurface,
                                        onClick = { viewModel.openParticipantSummary(item.patientUserId) }
                                    )
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
                                    remindersFeed = enrollmentRemindersFeed,
                                    acknowledgingReminderId = acknowledgingReminderId,
                                    isUpdatingMute = isUpdatingMute,
                                    onAcknowledgeReminder = { remId ->
                                        activeContext.programId?.let { pid ->
                                            viewModel.acknowledgeEnrollmentReminder(pid, remId)
                                        }
                                    },
                                    onToggleMute = { muted ->
                                        activeContext.programId?.let { pid ->
                                            viewModel.toggleEnrollmentMuted(pid, muted)
                                        }
                                    },
                                    onOpenConsentSheet = { showConsentSheet = true },
                                    onOpenReportSheet = { showReportProgramSheet = true },
                                    appointmentsFeed = enrollmentAppointmentsFeed,
                                    updatingAppointmentId = updatingAppointmentId,
                                    onOpenRequestAppointmentSheet = { showRequestAppointmentSheet = true },
                                    onCancelAppointment = { apptId ->
                                        activeContext.programId?.let { pid ->
                                            viewModel.cancelEnrollmentAppointment(pid, apptId)
                                        }
                                    },
                                    onOpenDirectChatSheet = {
                                        activeContext.programId?.let { pid ->
                                            viewModel.openEnrollmentDirectChat(
                                                programId = pid,
                                                programName = activeContext.programName ?: "Programa Parceiro"
                                            )
                                        }
                                    },
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
