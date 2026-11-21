package br.com.bragasaude.ui.b2b

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.domain.b2b.AppAccountMode
import br.com.bragasaude.domain.b2b.B2BThemeEngine
import br.com.bragasaude.domain.b2b.PartnerApprovalStatus
import br.com.bragasaude.domain.b2b.PartnerParticipantItemDto
import br.com.bragasaude.domain.b2b.PartnerProgramDto
import br.com.bragasaude.domain.b2b.ProgramAppointmentDto
import br.com.bragasaude.domain.b2b.ProgramExperienceConfigDto
import br.com.bragasaude.ui.auth.AuthViewModel
import br.com.bragasaude.ui.components.BragaAlertDialog
import br.com.bragasaude.ui.components.BragaBottomSheet
import kotlinx.coroutines.launch

enum class BusinessSessionTab(
    val label: String,
    val shortLabel: String,
    val icon: ImageVector
) {
    WALLET("Carteira", "Carteira", Icons.Default.Person),
    OPERATIONS("Operação", "Operação", Icons.Default.CalendarMonth),
    // Rótulo curto próprio: o contador vive no badge do ícone, sem encurtar o texto.
    CARE("Atendimento", "Atender", Icons.Default.SupportAgent),
    STUDIO("Estúdio", "Estúdio", Icons.Default.AutoAwesome),
    COMPANY("Empresa", "Empresa", Icons.Default.VerifiedUser)
}

/** Texto curto do badge da barra; 100+ vira "99+" para não estourar o ícone. */
internal fun navBadgeLabel(count: Int): String? = when {
    count <= 0 -> null
    count > 99 -> "99+"
    else -> count.toString()
}

/**
 * Fonte primária do badge de agendamentos. O contrato do gateway agrega
 * `kpis.pendingAppointmentsTotal` (PENDING + RESCHEDULE_REQUESTED) para o
 * programa inteiro; ele prevalece sobre a soma atual (página de agendamentos x
 * contadores por participante). O parser entrega 0 tanto para "sem pendências"
 * quanto para payload antigo sem o campo, então apenas total positivo é tratado
 * como fonte primária; 0/negativo mantém o fallback da UI.
 */
internal fun pendingAppointmentsBadgeTotal(
    kpiTotal: Int?,
    fallbackTotal: Int
): Int = kpiTotal?.takeIf { it > 0 } ?: fallbackTotal.coerceAtLeast(0)

/**
 * Fonte primária do badge de mensagens não lidas. O total global do gateway
 * (`kpis.unreadMessagesTotal`) cobre a caixa inteira, inclusive conversas fora
 * da página carregada; com o mesmo critério de "0 = ausente ou vazio", o
 * fallback soma os contadores dos participantes, que é a soma já exibida hoje.
 */
internal fun unreadMessagesBadgeTotal(
    kpiTotal: Int?,
    participants: List<PartnerParticipantItemDto>
): Int = kpiTotal?.takeIf { it > 0 }
    ?: participants.sumOf { it.unreadDirectMessagesCount }

/** Resultado da cópia do convite; falha usa liveRegion assertivo na UI. */
private data class InviteCopyFeedback(val success: Boolean, val message: String)

// O Scaffold já desconta a NavigationBar e o crédito de tecnologia via innerPadding; a
// reserva extra no fim da lista evita que o último card fique sob a barra/gesto do sistema
// e mantém 20dp de respiro nas laterais e no topo.
private val b2bListContentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 96.dp)

/**
 * Shell exclusivo da Sessão Empresarial Pura (B2B-30), 100% isolado da Home pessoal e do ORB B2C.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerBusinessScaffold(
    authViewModel: AuthViewModel,
    partnerViewModel: PartnerWorkspaceViewModel = hiltViewModel()
) {
    val program by partnerViewModel.programInfo.collectAsState()
    val active by partnerViewModel.activeContext.collectAsState()
    PartnerBrandTheme(program?.primaryColor ?: active.primaryColor) {
        PartnerBusinessContent(authViewModel, partnerViewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PartnerBusinessContent(
    authViewModel: AuthViewModel,
    partnerViewModel: PartnerWorkspaceViewModel
) {
    val context = LocalContext.current

    val accountMode by authViewModel.accountMode.collectAsState()
    val activeContext by partnerViewModel.activeContext.collectAsState()
    val programInfo by partnerViewModel.programInfo.collectAsState()
    val templates by partnerViewModel.templates.collectAsState()
    val isLoading by partnerViewModel.isLoading.collectAsState()
    val isLoadingMore by partnerViewModel.isLoadingMore.collectAsState()
    val isSavingExperience by partnerViewModel.isSavingExperience.collectAsState()
    val isUpdatingInvitePolicy by partnerViewModel.isUpdatingInvitePolicy.collectAsState()
    val participants by partnerViewModel.participants.collectAsState()
    val walletKpis by partnerViewModel.walletKpis.collectAsState()
    val walletQuery by partnerViewModel.walletQuery.collectAsState()
    val walletCategoryFilter by partnerViewModel.walletCategoryFilter.collectAsState()
    val walletAttentionOnly by partnerViewModel.walletAttentionOnly.collectAsState()
    val isFilteringParticipants by partnerViewModel.isFilteringParticipants.collectAsState()
    val hasMore by partnerViewModel.hasMore.collectAsState()
    val selectedParticipantItem by partnerViewModel.selectedParticipantItem.collectAsState()
    val selectedSummary by partnerViewModel.selectedSummary.collectAsState()
    val detailError by partnerViewModel.detailError.collectAsState()
    val isLoadingSummary by partnerViewModel.isLoadingSummary.collectAsState()
    val isRemovingParticipant by partnerViewModel.isRemovingParticipant.collectAsState()
    val selectedPeriodDays by partnerViewModel.selectedPeriodDays.collectAsState()
    val ownerReminders by partnerViewModel.ownerReminders.collectAsState()
    val isSendingReminder by partnerViewModel.isSendingReminder.collectAsState()
    val ownerAppointments by partnerViewModel.ownerAppointments.collectAsState()
    // Agrega a primeira página de agendamentos e os contadores por participante;
    // pode subestimar quando o contrato não expõe um total global.
    val pendingAppointmentsTotal by partnerViewModel.pendingAppointmentsTotal.collectAsState()
    val updatingAppointmentId by partnerViewModel.updatingAppointmentId.collectAsState()
    val activeDirectChatPage by partnerViewModel.activeDirectChatPage.collectAsState()
    val directChatIsOwnerView by partnerViewModel.directChatIsOwnerView.collectAsState()
    val directChatCounterpartTitle by partnerViewModel.directChatCounterpartTitle.collectAsState()
    val isLoadingDirectChat by partnerViewModel.isLoadingDirectChat.collectAsState()
    val isSendingDirectMessage by partnerViewModel.isSendingDirectMessage.collectAsState()
    val isUpdatingHandoff by partnerViewModel.isUpdatingHandoff.collectAsState()
    val statusMessage by partnerViewModel.statusMessage.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(BusinessSessionTab.WALLET) }
    var studioShowPreview by rememberSaveable { mutableStateOf(false) }
    var showOnboardingSheet by rememberSaveable { mutableStateOf(false) }
    var showReceptionQrSheet by rememberSaveable { mutableStateOf(false) }
    var showAiSimulatorSheet by rememberSaveable { mutableStateOf(false) }
    var showActivatePersonalDialog by rememberSaveable { mutableStateOf(false) }
    var showPureBusinessDialog by rememberSaveable { mutableStateOf(false) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var isSwitchingMode by remember { mutableStateOf(false) }
    var participantToRemove by remember { mutableStateOf<Pair<String, String>?>(null) }
    var reminderComposerTarget by remember { mutableStateOf<Pair<String?, String>?>(null) }
    var selectedAppointmentToManage by remember { mutableStateOf<ProgramAppointmentDto?>(null) }

    // Status do workspace aparece acima da barra de navegação (Scaffold posiciona o host
    // acima do bottomBar); Toasts continuam restritos às ações pontuais.
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(detailError) {
        val message = detailError ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = "Tentar novamente",
            withDismissAction = true,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            partnerViewModel.retrySelectedSummary()
        } else {
            partnerViewModel.clearDetailError()
        }
    }

    val savedExperience = remember(programInfo, activeContext) {
        programInfo?.experienceConfig ?: activeContext.resolvedExperienceConfig()
    }
    var draftExperience by remember(programInfo?.programId, savedExperience) {
        mutableStateOf(savedExperience)
    }

    LaunchedEffect(Unit) {
        partnerViewModel.refreshWorkspace()
        val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("pending_b2b_company_onboarding", false)) {
            prefs.edit().putBoolean("pending_b2b_company_onboarding", false).apply()
            showOnboardingSheet = true
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            partnerViewModel.consumeStatusMessage()
        }
    }

    BackHandler(enabled = selectedTab != BusinessSessionTab.WALLET) {
        selectedTab = BusinessSessionTab.WALLET
    }

    val brand = partnerBrandColors()
    val brandPrimary = brand.primary
    val brandOnPrimary = brand.onPrimary
    val brandSoftSurface = brand.softSurface
    val brandSoftBorder = brand.softBorder
    val brandAccentText = brand.accent
    val softBorderStroke = remember(brandSoftBorder) { BorderStroke(1.dp, brandSoftBorder) }

    if (showOnboardingSheet) {
        PartnerProgramOnboardingSheet(
            apiClient = partnerViewModel.partnerApiClient(),
            onDismiss = {
                showOnboardingSheet = false
                partnerViewModel.refreshWorkspace()
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
            programName = programInfo?.programName ?: activeContext.programName ?: "Seu programa",
            config = draftExperience,
            brandPrimary = brandPrimary,
            brandOnPrimary = brandOnPrimary,
            onDismiss = { showAiSimulatorSheet = false }
        )
    }

    selectedAppointmentToManage?.let { appt ->
        OwnerAppointmentDecisionBottomSheet(
            appointment = appt,
            isUpdating = updatingAppointmentId == appt.appointmentId,
            brandPrimary = brandPrimary,
            brandOnPrimary = brandOnPrimary,
            onConfirmDecision = { targetStatus, scheduledAtIso, partnerNote, allowOverlap ->
                partnerViewModel.updateOwnerAppointment(
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
            onLoadOlder = { partnerViewModel.loadMoreDirectMessages() },
            onSendMessage = { msgBody -> partnerViewModel.sendDirectMessage(msgBody) },
            onHandoffModeChange = { nextMode -> partnerViewModel.handoffDirectChat(nextMode) },
            onRequestHuman = { partnerViewModel.requestHumanHandoff() },
            onDismiss = { partnerViewModel.closeDirectChat() }
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
                partnerViewModel.openParticipantSummary(summary.patientUserId, newDays)
            },
            onClinicalDetail = { category ->
                if (category == null) partnerViewModel.collapseClinicalDetails()
                else partnerViewModel.openParticipantSummary(summary.patientUserId, summary.periodDays, category)
            },
            onActionFeedback = { feedback ->
                Toast.makeText(context, feedback, Toast.LENGTH_LONG).show()
            },
            onOpenSendReminder = { pid, pname ->
                partnerViewModel.clearSelectedSummary()
                reminderComposerTarget = pid to pname
            },
            onOpenManageAppointments = { pid, pname ->
                val targetAppt = ownerAppointments.firstOrNull {
                    it.patientUserId == pid &&
                        it.status !in setOf("CANCELLED", "COMPLETED")
                } ?: ownerAppointments.firstOrNull { it.patientUserId == pid }
                if (targetAppt != null && targetAppt.status !in setOf("CANCELLED", "COMPLETED")) {
                    partnerViewModel.clearSelectedSummary()
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
                partnerViewModel.clearSelectedSummary()
                partnerViewModel.openOwnerDirectChat(pid, pname)
            },
            onRequestRemove = {
                participantToRemove = summary.patientUserId to summary.participantName
            },
            onDismiss = { partnerViewModel.clearSelectedSummary() }
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
                partnerViewModel.sendPartnerReminder(
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
                        partnerViewModel.removeParticipant(targetId)
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

    if (showActivatePersonalDialog) {
        BragaAlertDialog(
            onDismissRequest = { if (!isSwitchingMode) showActivatePersonalDialog = false },
            title = { Text("Ativar seu espaço pessoal de saúde?") },
            text = {
                Text(
                    "Sua conta passará a operar em modo duplo: o ambiente da empresa continua separado dos seus dados pessoais de saúde, e você poderá alternar entre os dois quando quiser."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSwitchingMode) return@Button
                        isSwitchingMode = true
                        authViewModel.launchSessionAction {
                            val result = authViewModel.switchAccountMode(
                                target = AppAccountMode.DUAL,
                                enterBusinessSession = false
                            )
                            isSwitchingMode = false
                            if (result.isSuccess) {
                                showActivatePersonalDialog = false
                                partnerViewModel.clearSensitiveBusinessMemory()
                                Toast.makeText(
                                    context,
                                    "Espaço pessoal ativado! Complete seus dados pessoais quando quiser.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    result.errorDetail ?: "Não foi possível ativar o espaço pessoal agora.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    },
                    enabled = !isSwitchingMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
                    Text(if (isSwitchingMode) "Ativando..." else "Ativar e ir ao espaço pessoal")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showActivatePersonalDialog = false },
                    enabled = !isSwitchingMode
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showPureBusinessDialog) {
        BragaAlertDialog(
            onDismissRequest = { if (!isSwitchingMode) showPureBusinessDialog = false },
            title = { Text("Usar apenas sessão empresarial?") },
            text = {
                Text(
                    "O aplicativo abrirá sempre diretamente nesta sessão empresarial, ocultando menus pessoais."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSwitchingMode) return@Button
                        isSwitchingMode = true
                        authViewModel.launchSessionAction {
                            val result = authViewModel.switchAccountMode(
                                target = AppAccountMode.BUSINESS_ONLY,
                                enterBusinessSession = true
                            )
                            isSwitchingMode = false
                            showPureBusinessDialog = false
                            Toast.makeText(
                                context,
                                if (result.isSuccess) {
                                    "Modo empresarial exclusivo ativado."
                                } else {
                                    result.errorDetail ?: "Não foi possível alterar para modo empresarial exclusivo."
                                },
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    enabled = !isSwitchingMode,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
                    Text(if (isSwitchingMode) "Aplicando..." else "Confirmar")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPureBusinessDialog = false },
                    enabled = !isSwitchingMode
                ) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showLogoutDialog) {
        BragaAlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Sair da sessão empresarial?") },
            text = {
                Text("O cache local da carteira de participantes neste aparelho será limpo ao encerrar a sessão.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        partnerViewModel.clearSensitiveBusinessMemory()
                        authViewModel.performSignOut()
                    }
                ) {
                    Text("Sair da conta")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Continuar aqui")
                }
            }
        )
    }

    if (showDeleteDialog) {
        BragaAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Excluir conta e empresa?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Esta ação apagará permanentemente os dados da sua conta neste dispositivo e na nuvem."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        partnerViewModel.clearSensitiveBusinessMemory()
                        authViewModel.launchSessionAction { authViewModel.deleteAccount() }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Excluir definitivamente", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Badges da barra: o total global de `walletKpis` é a fonte primária; a soma
    // da página/participantes continua como fallback para payloads antigos. O KPI
    // só é recalculado no refresh da carteira (ações locais de agendamento/leitura
    // atualizam participantes, não o KPI), então o badge pode ficar defasado até
    // o próximo refresh; o fallback evita esconder pendência em payload legado.
    val pendingAppointmentsBadgeCount = pendingAppointmentsBadgeTotal(
        kpiTotal = walletKpis?.pendingAppointmentsTotal,
        fallbackTotal = pendingAppointmentsTotal
    )
    val unreadMessagesBadgeCount = unreadMessagesBadgeTotal(
        kpiTotal = walletKpis?.unreadMessagesTotal,
        participants = participants
    )
    val isProgramApproved = programInfo?.parsedStatus == PartnerApprovalStatus.APPROVED
    val openStudio: () -> Unit = {
        studioShowPreview = false
        selectedTab = BusinessSessionTab.STUDIO
    }
    val walletListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // O card de Participantes leva à lista já filtrada por "Com atenção"; o filtro é
    // idempotente (não desliga quando o gestor toca no card duas vezes).
    val showWalletParticipants: () -> Unit = {
        if (!walletAttentionOnly) partnerViewModel.toggleWalletAttentionOnly()
        coroutineScope.launch { walletListState.animateScrollToItem(1) }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PartnerIdentityLockup(
                            companyName = programInfo?.companyName ?: activeContext.companyName ?: "Sua empresa",
                            programName = programInfo?.programName ?: activeContext.programName ?: "Seu programa",
                            logoUrl = programInfo?.logoUrl ?: activeContext.logoUrl,
                            modifier = Modifier.fillMaxWidth()
                        )
                        PartnerStatusChip(status = programInfo?.parsedStatus)
                    }

                    if (accountMode == AppAccountMode.DUAL.name) {
                        TextButton(
                            onClick = {
                                partnerViewModel.clearSensitiveBusinessMemory()
                                authViewModel.exitBusinessSession()
                            }
                        ) {
                            Text(
                                text = "Espaço pessoal",
                                color = brandAccentText,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(onClick = { partnerViewModel.refreshWorkspace() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Atualizar",
                            tint = brandAccentText
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Em telas estreitas (~320dp) os rótulos longos são truncados com o
                // tamanho padrão do M3; reduzir a fonte do rótulo mantém as 5 abas
                // legíveis sem encurtar o texto ("Operação"/"Estúdio") nem o badge.
                val narrowBar = LocalConfiguration.current.screenWidthDp < 360
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    BusinessSessionTab.entries.forEach { tab ->
                        val count = when (tab) {
                            BusinessSessionTab.CARE -> unreadMessagesBadgeCount
                            BusinessSessionTab.OPERATIONS -> pendingAppointmentsBadgeCount
                            else -> 0
                        }
                        val badgeText = navBadgeLabel(count)
                        val spokenLabel = when {
                            count == 0 -> tab.label
                            tab == BusinessSessionTab.CARE -> "${tab.label}, ${B2BTextFormatters.countLabel(count, "mensagem não lida", "mensagens não lidas")}"
                            else -> "${tab.label}, ${B2BTextFormatters.countLabel(count, "agendamento pendente", "agendamentos pendentes")}"
                        }
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = {
                                // O indicador fica sobre o ícone para o rótulo curto ("Atender",
                                // "Operação") continuar inteiro mesmo com fonte grande.
                                BadgedBox(
                                    badge = {
                                        if (badgeText != null) {
                                            Badge {
                                                Text(
                                                    text = badgeText,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.clearAndSetSemantics {}
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(imageVector = tab.icon, contentDescription = spokenLabel)
                                }
                            },
                            label = {
                                Text(
                                    text = tab.shortLabel,
                                    modifier = Modifier.clearAndSetSemantics {},
                                    fontSize = if (narrowBar) 8.sp else 10.sp,
                                    letterSpacing = 0.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = brandAccentText,
                                selectedTextColor = brandAccentText,
                                indicatorColor = brandSoftSurface
                            )
                        )
                    }
                }
                PartnerTechnologyCredit(Modifier.background(MaterialTheme.colorScheme.surface))
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // A carteira tem skeleton próprio e pull-to-refresh; o spinner de tela
            // cheia fica só para as demais abas.
            if (isLoading && selectedTab != BusinessSessionTab.WALLET) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = brandAccentText,
                        strokeWidth = 4.dp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Carregando indicadores do programa...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                when (selectedTab) {
                    BusinessSessionTab.WALLET -> {
                        val showWalletSkeleton = isLoading && walletKpis == null
                        PullToRefreshBox(
                            isRefreshing = isLoading && !showWalletSkeleton,
                            onRefresh = { partnerViewModel.refreshWorkspace() },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                state = walletListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = b2bListContentPadding,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    PartnerWalletKpis(
                                        kpis = walletKpis,
                                        isLoading = showWalletSkeleton,
                                        onOpenParticipants = if (isProgramApproved) showWalletParticipants else null,
                                        onOpenOperations = { selectedTab = BusinessSessionTab.OPERATIONS },
                                        onOpenStudio = openStudio
                                    )
                                }
                                if (!showWalletSkeleton) {
                                    if (!isProgramApproved) {
                                        item {
                                            PartnerPreApprovalBannerCard(
                                                programInfo = programInfo, brandPrimary = brandPrimary,
                                                brandOnPrimary = brandOnPrimary, brandAccent = brandAccentText,
                                                softSurface = brandSoftSurface, softBorder = softBorderStroke,
                                                onOpenOnboarding = { showOnboardingSheet = true }
                                            )
                                        }
                                    } else {
                                        item {
                                            PartnerWalletFiltersSection(
                                                query = walletQuery, selectedCategory = walletCategoryFilter,
                                                attentionOnly = walletAttentionOnly, enabledProgramMetrics = savedExperience.enabledMetrics,
                                                isFiltering = isFilteringParticipants, brandPrimary = brandPrimary,
                                                brandOnPrimary = brandOnPrimary, brandAccent = brandAccentText,
                                                onQueryChange = partnerViewModel::updateWalletQuery,
                                                onSelectCategory = partnerViewModel::selectWalletCategoryFilter,
                                                onToggleAttentionOnly = partnerViewModel::toggleWalletAttentionOnly,
                                                onClearFilters = partnerViewModel::clearWalletFilters
                                            )
                                        }
                                        if (participants.isEmpty()) item {
                                            WalletParticipantsEmptyState(
                                                hasFilters = walletQuery.isNotBlank() ||
                                                    walletCategoryFilter != null || walletAttentionOnly,
                                                inviteCode = programInfo?.inviteCode?.takeIf { it.isNotBlank() },
                                                brandPrimary = brandPrimary,
                                                brandOnPrimary = brandOnPrimary,
                                                brandAccent = brandAccentText,
                                                softSurface = brandSoftSurface,
                                                softBorder = softBorderStroke,
                                                onClearFilters = partnerViewModel::clearWalletFilters,
                                                onOpenStudio = openStudio
                                            )
                                        }
                                        items(participants, key = { it.patientUserId }) { item ->
                                            WalletParticipantCard(item = item, brandPrimary = brandPrimary,
                                                brandOnPrimary = brandOnPrimary, brandAccent = brandAccentText,
                                                softSurface = brandSoftSurface,
                                                onClick = { partnerViewModel.openParticipantSummary(item.patientUserId) })
                                        }
                                        if (hasMore) item {
                                            OutlinedButton(onClick = partnerViewModel::loadMoreParticipants,
                                                enabled = !isLoadingMore, modifier = Modifier.fillMaxWidth()) {
                                                Text(if (isLoadingMore) "Carregando..." else "Carregar mais")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    BusinessSessionTab.OPERATIONS -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = b2bListContentPadding,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            programInfo?.takeIf { it.aiPaused }?.let {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = brandSoftSurface),
                                        border = softBorderStroke
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.SupportAgent,
                                                    contentDescription = null,
                                                    tint = brandAccentText
                                                )
                                                Text(
                                                    text = "Recepcionista IA pausada",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = brandAccentText
                                                )
                                            }
                                            Text(
                                                text = "Mensagens sem resposta automática até retomar no Estúdio.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Button(
                                                onClick = {
                                                    studioShowPreview = false
                                                    selectedTab = BusinessSessionTab.STUDIO
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = brandPrimary,
                                                    contentColor = brandOnPrimary
                                                )
                                            ) {
                                                Text("Abrir Estúdio", fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                }
                            }
                            if (programInfo == null || programInfo?.parsedStatus != PartnerApprovalStatus.APPROVED) {
                                item {
                                    PartnerPreApprovalBannerCard(
                                        programInfo = programInfo,
                                        brandPrimary = brandPrimary,
                                        brandOnPrimary = brandOnPrimary,
                                        brandAccent = brandAccentText,
                                        softSurface = brandSoftSurface,
                                        softBorder = softBorderStroke,
                                        onOpenOnboarding = { showOnboardingSheet = true }
                                    )
                                }
                            } else {
                                item {
                                    if (savedExperience.features.appointmentsEnabled) {
                                        // O card da agenda já cobre o estado vazio (com
                                        // "Compartilhar disponibilidade" e "Abrir Estúdio");
                                        // mantê-lo evita duplicar dois vazios na mesma aba.
                                        OwnerAppointmentsWalletCard(
                                            appointments = ownerAppointments,
                                            policy = savedExperience.appointmentsPolicy,
                                            updatingAppointmentId = updatingAppointmentId,
                                            brandPrimary = brandPrimary,
                                            brandAccent = brandAccentText,
                                            softBorder = softBorderStroke,
                                            onOpenStudio = openStudio,
                                            onManageAppointment = { appt ->
                                                selectedAppointmentToManage = appt
                                            }
                                        )
                                    } else {
                                        ModuleDisabledPromptCard(
                                            title = "Agenda de retornos desligada",
                                            body = "Ative agendamentos no Estúdio para receber pedidos de horário.",
                                            brandAccent = brandAccentText,
                                            softBorder = softBorderStroke,
                                            onOpenStudio = openStudio
                                        )
                                    }
                                }

                                item {
                                    if (savedExperience.features.partnerRemindersEnabled) {
                                        if (ownerReminders.isEmpty()) {
                                            BusinessEmptyStateCard(
                                                icon = Icons.Default.NotificationsActive,
                                                title = "Nenhum lembrete enviado ainda",
                                                body = "Envie um aviso em lote para a carteira. Lembretes educativos, sem diagnóstico nem prescrição.",
                                                primaryActionLabel = "Enviar lembrete em lote",
                                                primaryActionIcon = Icons.Default.NotificationsActive,
                                                onPrimaryAction = {
                                                    reminderComposerTarget = null to "Lote da carteira"
                                                },
                                                secondaryActionLabel = "Abrir Estúdio",
                                                onSecondaryAction = openStudio,
                                                brandPrimary = brandPrimary,
                                                brandOnPrimary = brandOnPrimary,
                                                brandAccent = brandAccentText,
                                                softSurface = brandSoftSurface,
                                                softBorder = softBorderStroke
                                            )
                                        } else {
                                            OwnerSentRemindersCard(
                                                reminders = ownerReminders,
                                                brandPrimary = brandPrimary,
                                                brandOnPrimary = brandOnPrimary,
                                                brandAccent = brandAccentText,
                                                softBorder = softBorderStroke,
                                                onOpenBatchComposer = {
                                                    reminderComposerTarget = null to "Lote da carteira"
                                                }
                                            )
                                        }
                                    } else {
                                        ModuleDisabledPromptCard(
                                            title = "Lembretes do programa desligados",
                                            body = "Ative lembretes no Estúdio para enviar avisos em lote.",
                                            brandAccent = brandAccentText,
                                            softBorder = softBorderStroke,
                                            onOpenStudio = openStudio
                                        )
                                    }
                                }
                            }
                        }
                    }

                    BusinessSessionTab.CARE -> {
                        BusinessCareInboxTabContent(
                            programInfo = programInfo,
                            participants = participants,
                            experienceConfig = savedExperience,
                            hasMore = hasMore,
                            isLoadingMore = isLoadingMore,
                            brandPrimary = brandPrimary,
                            brandOnPrimary = brandOnPrimary,
                            brandAccent = brandAccentText,
                            softSurface = brandSoftSurface,
                            softBorder = softBorderStroke,
                            onOpenOnboarding = { showOnboardingSheet = true },
                            onOpenSimulator = { showAiSimulatorSheet = true },
                            onOpenStudio = openStudio,
                            onOpenDirectChat = { patientId, displayName ->
                                partnerViewModel.openOwnerDirectChat(patientId, displayName)
                            },
                            onLoadMore = { partnerViewModel.loadMoreParticipants() }
                        )
                    }

                    BusinessSessionTab.STUDIO -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { studioShowPreview = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (!studioShowPreview) brandPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (!studioShowPreview) brandOnPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Estúdio", fontWeight = FontWeight.SemiBold)
                                }
                                Button(
                                    onClick = { studioShowPreview = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (studioShowPreview) brandPrimary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (studioShowPreview) brandOnPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Prévia ao vivo", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                if (studioShowPreview) {
                                    PartnerPreviewTabContent(
                                        programName = programInfo?.programName ?: activeContext.programName ?: "Seu programa",
                                        companyName = programInfo?.companyName ?: activeContext.companyName ?: "Sua empresa",
                                        logoUrl = programInfo?.logoUrl ?: activeContext.logoUrl,
                                        primaryColorHex = programInfo?.primaryColor ?: activeContext.primaryColor,
                                        draftConfig = draftExperience,
                                        brandPrimary = brandPrimary,
                                        brandOnPrimary = brandOnPrimary,
                                        brandAccent = brandAccentText,
                                        softSurface = brandSoftSurface,
                                        softBorder = softBorderStroke,
                                        onOpenSimulator = { showAiSimulatorSheet = true }
                                    )
                                } else {
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
                                            partnerViewModel.saveExperienceConfig(
                                                templateKey = draftExperience.templateKey,
                                                applyTemplateDefaults = false,
                                                experienceConfig = draftExperience
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }

                    BusinessSessionTab.COMPANY -> {
                        BusinessCompanyTabContent(
                            accountMode = accountMode,
                            programInfo = programInfo,
                            isUpdatingInvitePolicy = isUpdatingInvitePolicy,
                            brandPrimary = brandPrimary,
                            brandOnPrimary = brandOnPrimary,
                            brandAccent = brandAccentText,
                            softSurface = brandSoftSurface,
                            softBorder = softBorderStroke,
                            onOpenOnboarding = { showOnboardingSheet = true },
                            onOpenStudio = {
                                studioShowPreview = false
                                selectedTab = BusinessSessionTab.STUDIO
                            },
                            onOpenQr = { showReceptionQrSheet = true },
                            onToggleInvitePaused = { partnerViewModel.togglePartnerInvitePaused() },
                            onRegenerateInvite = { partnerViewModel.regenerateInviteCode() },
                            onActivatePersonalSpace = { showActivatePersonalDialog = true },
                            onExitToPersonalSpace = {
                                partnerViewModel.clearSensitiveBusinessMemory()
                                authViewModel.exitBusinessSession()
                            },
                            onUsePureBusinessMode = { showPureBusinessDialog = true },
                            onLogout = { showLogoutDialog = true },
                            onDeleteAccount = { showDeleteDialog = true }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Notas de revisão podem carregar metadados internos de homologação (contas de
 * teste, aprovações de demonstração). Esses marcadores nunca vão para a UI do parceiro.
 */
private fun sanitizeReviewNote(note: String?): String? {
    val clean = note?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val internalMarkers = listOf(
        "conta de teste",
        "aprovação operacional de demonstração",
        "b2b-31"
    )
    return clean.takeUnless { text ->
        internalMarkers.any { marker -> text.contains(marker, ignoreCase = true) }
    }
}

/**
 * Retorno neutro apenas quando o cadastro está em análise e não há observação
 * pública utilizável; nos demais status o rótulo e os textos do card já bastam.
 */
private fun neutralReviewNote(status: PartnerApprovalStatus): String? =
    if (status == PartnerApprovalStatus.PENDING_REVIEW) {
        "Cadastro em análise pela equipe Braga."
    } else {
        null
    }

private fun String?.isKybVerified(): Boolean = this?.trim()?.uppercase() == "GREEN"

/**
 * Linha única de KYB da aba Empresa. Substitui o texto cru
 * "Documentos enviados: N · KYB: <status>" e fala a mesma língua do onboarding:
 * a verificação documental é uma etapa separada da aprovação do programa, então
 * o selo "Empresa aprovada" não contradiz mais o checklist.
 */
internal fun companyKybSummaryLine(
    proofDocumentsCount: Int,
    autoKybStatus: String?
): String = if (autoKybStatus.isKybVerified()) {
    "Verificação KYB concluída"
} else {
    "Documentos: $proofDocumentsCount de 2 enviados · verificação KYB em andamento"
}

/**
 * Complemento da linha de KYB, exibido apenas com programa aprovado: deixa
 * explícito que a conferência documental não bloqueia convite, QR Code nem
 * placa PDF (mesma mensagem do onboarding e do checklist).
 */
internal fun companyKybSupportingLine(
    proofDocumentsCount: Int,
    autoKybStatus: String?,
    approvalStatus: PartnerApprovalStatus?
): String? {
    if (autoKybStatus.isKybVerified()) return null
    if (approvalStatus != PartnerApprovalStatus.APPROVED) return null
    return if (proofDocumentsCount <= 0) {
        "Empresa aprovada sem comprovantes anexados: a verificação KYB não bloqueia convite, QR Code nem placa PDF."
    } else {
        "A verificação KYB não bloqueia convite, QR Code nem placa PDF."
    }
}

/**
 * Chip compacto e único de status do programa. Usar o mesmo componente no header
 * (presente em Carteira/Operação) e na aba Empresa evita que o mesmo estado apareça
 * com pesos visuais diferentes. O selo de aprovado usa CheckCircle; os demais estados
 * ficam com ícone neutro e cores de apoio da marca.
 */
@Composable
internal fun PartnerStatusChip(
    status: PartnerApprovalStatus?,
    modifier: Modifier = Modifier
) {
    val brand = partnerBrandColors()
    val label = status?.labelPtBr ?: "Configuração inicial"
    val approved = status == PartnerApprovalStatus.APPROVED
    val contentColor = readableBrandAccent(if (approved) brand.primary else MaterialTheme.colorScheme.onSurfaceVariant, brand.softSurface)
    Surface(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Status do programa: $label"
        },
        shape = RoundedCornerShape(8.dp),
        color = brand.softSurface,
        border = BorderStroke(
            1.dp,
            if (approved) brand.primary.copy(alpha = 0.45f) else brand.softBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (approved) Icons.Default.CheckCircle else Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Estado vazio compartilhado das abas do B2B. Ícone decorativo + título + texto curto
 * e CTAs de até uma ação principal e uma secundária, sempre em largura total para
 * fonte grande não cortar o rótulo.
 */
@Composable
private fun BusinessEmptyStateCard(
    icon: ImageVector,
    title: String,
    body: String,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softSurface: Color,
    softBorder: BorderStroke,
    primaryActionLabel: String? = null,
    primaryActionIcon: ImageVector? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = softBorder
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(softSurface),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = brandAccent,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = brandAccent,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (onPrimaryAction != null && primaryActionLabel != null) {
                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
                    if (primaryActionIcon != null) {
                        Icon(
                            imageVector = primaryActionIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(primaryActionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
            if (onSecondaryAction != null && secondaryActionLabel != null) {
                TextButton(onClick = onSecondaryAction, modifier = Modifier.fillMaxWidth()) {
                    Text(secondaryActionLabel, color = brandAccent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Carteira sem participantes: distingue "filtro sem resultado" de "carteira vazia".
 * A carteira vazia oferece o código de convite já disponível no programa; sem código,
 * leva ao Estúdio em vez de prometer um convite inexistente.
 */
@Composable
private fun WalletParticipantsEmptyState(
    hasFilters: Boolean,
    inviteCode: String?,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softSurface: Color,
    softBorder: BorderStroke,
    onClearFilters: () -> Unit,
    onOpenStudio: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    if (hasFilters) {
        BusinessEmptyStateCard(
            icon = Icons.Default.Person,
            title = "Nenhum participante com esses filtros",
            body = "Ajuste a busca ou limpe os filtros para ver toda a carteira.",
            brandPrimary = brandPrimary,
            brandOnPrimary = brandOnPrimary,
            brandAccent = brandAccent,
            softSurface = softSurface,
            softBorder = softBorder,
            primaryActionLabel = "Limpar filtros",
            onPrimaryAction = onClearFilters
        )
        return
    }
    BusinessEmptyStateCard(
        icon = Icons.Default.Person,
        title = "Sua carteira ainda está vazia",
        body = if (inviteCode != null) {
            "Compartilhe o convite do programa para o primeiro participante entrar."
        } else {
            "Abra o Estúdio para configurar o programa e liberar o convite aos participantes."
        },
        brandPrimary = brandPrimary,
        brandOnPrimary = brandOnPrimary,
        brandAccent = brandAccent,
        softSurface = softSurface,
        softBorder = softBorder,
        primaryActionLabel = if (inviteCode != null) "Copiar convite" else "Abrir Estúdio",
        primaryActionIcon = if (inviteCode != null) Icons.Default.ContentCopy else Icons.Default.AutoAwesome,
        onPrimaryAction = {
            if (inviteCode != null) {
                clipboard.setText(AnnotatedString(inviteCode))
                Toast.makeText(
                    context,
                    "Convite copiado. Cole no WhatsApp ou no e-mail.",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                onOpenStudio()
            }
        },
        secondaryActionLabel = if (inviteCode != null) "Abrir Estúdio" else null,
        onSecondaryAction = if (inviteCode != null) onOpenStudio else null
    )
}

@Composable
private fun PartnerPreApprovalBannerCard(
    programInfo: PartnerProgramDto?,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softSurface: Color,
    softBorder: BorderStroke,
    onOpenOnboarding: () -> Unit
) {
    val status = programInfo?.parsedStatus
    val title = when (status) {
        null -> "Finalize os dados da sua empresa e envio KYB"
        PartnerApprovalStatus.DRAFT -> "Rascunho em preparação"
        PartnerApprovalStatus.PENDING_REVIEW -> "Cadastro em análise de conformidade (KYB)"
        PartnerApprovalStatus.CHANGES_REQUESTED -> "Ajustes solicitados pela revisão Braga"
        PartnerApprovalStatus.SUSPENDED -> "Programa temporariamente suspenso"
        PartnerApprovalStatus.REJECTED -> "Cadastro recusado na análise"
        PartnerApprovalStatus.APPROVED -> "Empresa aprovada"
    }
    val description = sanitizeReviewNote(programInfo?.reviewNote) ?: when (status) {
        null -> "Informe razão social, nome do programa, dados do responsável e documentos comprobatórios para habilitar o código de convite aos pacientes."
        PartnerApprovalStatus.DRAFT -> "Conclua o preenchimento cadastral, envie os comprovantes e submeta para validação."
        PartnerApprovalStatus.PENDING_REVIEW -> "Enquanto a análise é concluída, você já pode preparar o Estúdio, os textos da Recepcionista IA e a prévia do paciente."
        PartnerApprovalStatus.CHANGES_REQUESTED -> "Revise os pontos indicados pela equipe Braga e reenvie para aprovação."
        PartnerApprovalStatus.SUSPENDED -> "Convites e novos fluxos estão suspensos. Consulte o cadastro e os termos da empresa."
        PartnerApprovalStatus.REJECTED -> "Verifique os dados informados ou ajuste a documentação antes de nova submissão."
        PartnerApprovalStatus.APPROVED -> "Programa pronto para receber participantes."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = softSurface),
        border = softBorder
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = brandAccent
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onOpenOnboarding,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = brandPrimary,
                    contentColor = brandOnPrimary
                )
            ) {
                Text(
                    text = if (programInfo == null) "Preencher cadastro e KYB" else "Abrir dados da empresa e KYB",
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ModuleDisabledPromptCard(
    title: String,
    body: String,
    brandAccent: Color,
    softBorder: BorderStroke,
    onOpenStudio: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = softBorder
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = brandAccent
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(onClick = onOpenStudio) {
                Text("Configurar no Estúdio", color = brandAccent)
            }
        }
    }
}

@Composable
private fun BusinessCareInboxTabContent(
    programInfo: PartnerProgramDto?,
    participants: List<PartnerParticipantItemDto>,
    experienceConfig: ProgramExperienceConfigDto,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softSurface: Color,
    softBorder: BorderStroke,
    onOpenOnboarding: () -> Unit,
    onOpenSimulator: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenDirectChat: (patientId: String, displayName: String) -> Unit,
    onLoadMore: () -> Unit
) {
    val orderedParticipants = remember(participants) {
        participants.sortedWith(
            compareByDescending<PartnerParticipantItemDto> { it.unreadDirectMessagesCount }
                .thenByDescending { it.pendingAppointmentsCount }
                .thenBy { it.displayName }
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = b2bListContentPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = softSurface),
                border = softBorder
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Recepcionista IA e Chat Direto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = brandAccent
                    )
                    Text(
                        text = "Horário configurado: ${experienceConfig.directChatPolicy.serviceHoursLabel} · ${experienceConfig.directChatPolicy.responseSlaLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Atendimento automático: ${experienceConfig.aiReceptionistPolicy.personaName} (${B2BTextFormatters.activationModeLabel(experienceConfig.aiReceptionistPolicy.activationMode)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = brandAccent
                    )
                    OutlinedButton(
                        onClick = onOpenSimulator,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Simular Recepcionista IA")
                    }
                }
            }
        }

        if (programInfo == null || programInfo.parsedStatus != PartnerApprovalStatus.APPROVED) {
            item {
                PartnerPreApprovalBannerCard(
                    programInfo = programInfo,
                    brandPrimary = brandPrimary,
                    brandOnPrimary = brandOnPrimary,
                    brandAccent = brandAccent,
                    softSurface = softSurface,
                    softBorder = softBorder,
                    onOpenOnboarding = onOpenOnboarding
                )
            }
        } else if (orderedParticipants.isEmpty()) {
            item {
                BusinessEmptyStateCard(
                    icon = Icons.Default.SupportAgent,
                    title = "Fila de atendimento vazia",
                    body = "Quando um participante iniciar uma conversa ou pedir um horário, a conversa aparece aqui.",
                    brandPrimary = brandPrimary,
                    brandOnPrimary = brandOnPrimary,
                    brandAccent = brandAccent,
                    softSurface = softSurface,
                    softBorder = softBorder,
                    primaryActionLabel = "Abrir Estúdio",
                    primaryActionIcon = Icons.Default.AutoAwesome,
                    onPrimaryAction = onOpenStudio
                )
            }
        } else {
            item {
                Text(
                    text = "Conversas com participantes (${orderedParticipants.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            items(orderedParticipants, key = { it.patientUserId }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = softBorder
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = item.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            val subtitle = when {
                                item.unreadDirectMessagesCount > 0 ->
                                    B2BTextFormatters.countLabel(
                                        item.unreadDirectMessagesCount,
                                        "mensagem aguardando leitura",
                                        "mensagens aguardando leitura"
                                    )
                                item.pendingAppointmentsCount > 0 ->
                                    B2BTextFormatters.countLabel(
                                        item.pendingAppointmentsCount,
                                        "solicitação de horário em aberto",
                                        "solicitações de horário em aberto"
                                    )
                                else ->
                                    B2BTextFormatters.countLabel(
                                        item.effectiveCategories.size,
                                        "categoria autorizada",
                                        "categorias autorizadas"
                                    )
                            }
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (item.unreadDirectMessagesCount > 0) brandAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (item.unreadDirectMessagesCount > 0) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                        Button(
                            onClick = { onOpenDirectChat(item.patientUserId, item.displayName) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = brandPrimary,
                                contentColor = brandOnPrimary
                            )
                        ) {
                            Text("Abrir chat", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (hasMore) {
                item {
                    OutlinedButton(
                        onClick = onLoadMore,
                        enabled = !isLoadingMore,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isLoadingMore) "Carregando..." else "Carregar mais conversas")
                    }
                }
            }
        }
    }
}

@Composable
private fun BusinessCompanyTabContent(
    accountMode: String,
    programInfo: PartnerProgramDto?,
    isUpdatingInvitePolicy: Boolean,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softSurface: Color,
    softBorder: BorderStroke,
    onOpenOnboarding: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenQr: () -> Unit,
    onToggleInvitePaused: () -> Unit,
    onRegenerateInvite: () -> Unit,
    onActivatePersonalSpace: () -> Unit,
    onExitToPersonalSpace: () -> Unit,
    onUsePureBusinessMode: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    // Feedback da cópia com ícone+texto e liveRegion: Toast sozinho não é
    // confiável para leitores de tela e some antes da leitura terminar.
    var copyFeedback by remember { mutableStateOf<InviteCopyFeedback?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = b2bListContentPadding,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        programInfo?.let { prog ->
            item {
                FirstAccessActivationChecklistCard(
                    programInfo = prog,
                    brandPrimary = brandPrimary,
                    brandAccent = brandAccent,
                    softSurface = softSurface,
                    softBorder = softBorder,
                    onOpenOnboarding = onOpenOnboarding,
                    onOpenStudio = onOpenStudio,
                    onOpenQr = onOpenQr
                )
            }
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = softSurface),
                border = softBorder
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = programInfo?.companyName ?: "Empresa e Verificação KYB",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = brandAccent
                    )
                    if (programInfo != null) {
                        Text(
                            text = "Programa: ${programInfo.programName}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        // Mesmo componente de status do header, para o estado não mudar
                        // de forma entre as abas Empresa/Carteira.
                        PartnerStatusChip(status = programInfo.parsedStatus)
                        programInfo.cnpj?.takeIf { it.isNotBlank() }?.let { cnpj ->
                            Text(
                                text = "CNPJ: $cnpj",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val kybVerified = programInfo.autoKybStatus.isKybVerified()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (kybVerified) Icons.Default.CheckCircle else Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (kybVerified) brandPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = companyKybSummaryLine(
                                    proofDocumentsCount = programInfo.proofDocuments.size,
                                    autoKybStatus = programInfo.autoKybStatus
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (kybVerified) brandAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        companyKybSupportingLine(
                            proofDocumentsCount = programInfo.proofDocuments.size,
                            autoKybStatus = programInfo.autoKybStatus,
                            approvalStatus = programInfo.parsedStatus
                        )?.let { supporting ->
                            Text(
                                text = supporting,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        val reviewNote = sanitizeReviewNote(programInfo.reviewNote)
                            ?: neutralReviewNote(programInfo.parsedStatus)
                        reviewNote?.let { note ->
                            Text(
                                text = "Observação: $note",
                                style = MaterialTheme.typography.bodySmall,
                                color = brandAccent
                            )
                        }
                    } else {
                        Text(
                            text = "Nenhum programa cadastrado ainda. Preencha os dados da empresa e anexe os comprovantes para validação.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onOpenOnboarding,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = brandPrimary,
                            contentColor = brandOnPrimary
                        )
                    ) {
                        Text(
                            text = if (programInfo == null) "Cadastrar empresa e KYB" else "Gerenciar cadastro, logo e KYB",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        programInfo?.let { prog ->
            item {
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
                        Text(
                            text = "Convites e franquia",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = brandAccent
                        )
                        Text(
                            text = "Vagas em uso: ${prog.activeParticipants} / ${prog.seatLimit}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (prog.seatLimit > 0) PartnerProgress(prog.activeParticipants.toFloat() / prog.seatLimit)
                        prog.inviteCode?.takeIf { it.isNotBlank() }?.let { code ->
                            // Um código novo invalida o feedback da cópia anterior.
                            LaunchedEffect(code) { copyFeedback = null }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "Código do convite",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = code,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = brandAccent,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (prog.invitePaused) "Convites pausados" else "Convites ativos",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        val success = runCatching {
                                            clipboard.setText(AnnotatedString(code))
                                        }.isSuccess
                                        val feedback = if (success) {
                                            InviteCopyFeedback(
                                                success = true,
                                                message = "Código copiado. Cole no WhatsApp ou no e-mail."
                                            )
                                        } else {
                                            InviteCopyFeedback(
                                                success = false,
                                                message = "Não foi possível copiar o código. Selecione o texto e copie manualmente."
                                            )
                                        }
                                        copyFeedback = feedback
                                        Toast.makeText(context, feedback.message, Toast.LENGTH_LONG).show()
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Copiar código")
                                }
                            }
                            copyFeedback?.let { feedback ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (feedback.success) {
                                            Icons.Default.CheckCircle
                                        } else {
                                            Icons.Default.Info
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = if (feedback.success) {
                                            brandPrimary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                    Text(
                                        text = feedback.message,
                                        modifier = Modifier.semantics {
                                            liveRegion = if (feedback.success) {
                                                LiveRegionMode.Polite
                                            } else {
                                                LiveRegionMode.Assertive
                                            }
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (feedback.success) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                    )
                                }
                            }
                            if (prog.parsedStatus == PartnerApprovalStatus.APPROVED) {
                                OutlinedButton(
                                    onClick = onOpenQr,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCode2,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("QR Code e placa PDF")
                                }
                            }
                        }
                        if (prog.parsedStatus == PartnerApprovalStatus.APPROVED) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = onToggleInvitePaused,
                                    enabled = !isUpdatingInvitePolicy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(if (prog.invitePaused) "Retomar convites" else "Pausar convites")
                                }
                                OutlinedButton(
                                    onClick = onRegenerateInvite,
                                    enabled = !isUpdatingInvitePolicy,
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Novo código")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
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
                    Text(
                        text = "Sessão e espaço pessoal de saúde",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = brandAccent
                    )
                    if (accountMode == AppAccountMode.DUAL.name) {
                        Text(
                            text = "Você possui os dois ambientes ativos e separados neste login: esta Sessão Clínica e o seu espaço pessoal de saúde.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Button(
                            onClick = onExitToPersonalSpace,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = brandPrimary,
                                contentColor = brandOnPrimary
                            )
                        ) {
                            Text("Alternar para meu espaço pessoal", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = onUsePureBusinessMode,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Usar apenas sessão empresarial")
                        }
                    } else {
                        Text(
                            text = "Esta conta está dedicada apenas à gestão da empresa, sem questionários pessoais nem monitoramento individual.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onActivatePersonalSpace,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Ativar meu espaço pessoal de saúde", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = softBorder
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    TextButton(
                        onClick = onLogout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                            Text("Sair da conta", style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    TextButton(
                        onClick = onDeleteAccount,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = "Excluir perfil e conta",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
