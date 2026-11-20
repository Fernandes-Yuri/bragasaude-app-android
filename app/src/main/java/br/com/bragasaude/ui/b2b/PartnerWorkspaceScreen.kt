package br.com.bragasaude.ui.b2b

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.domain.b2b.AppContextMode

/** Área pessoal de participação. Não contém componentes nem ações de gestão. */
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
    val enrollmentRemindersFeed by viewModel.enrollmentRemindersFeed.collectAsState()
    val enrollmentAppointmentsFeed by viewModel.enrollmentAppointmentsFeed.collectAsState()
    val acknowledgingReminderId by viewModel.acknowledgingReminderId.collectAsState()
    val isUpdatingMute by viewModel.isUpdatingMute.collectAsState()
    val isReportingProgram by viewModel.isReportingProgram.collectAsState()
    val isSubmittingAppointment by viewModel.isSubmittingAppointment.collectAsState()
    val updatingAppointmentId by viewModel.updatingAppointmentId.collectAsState()
    val isGeneratingPdf by viewModel.isGeneratingPdf.collectAsState()
    val generatedPdf by viewModel.generatedPdf.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val savedExperience = activeContext.resolvedExperienceConfig()
    val brandPrimary = MaterialTheme.colorScheme.primary
    val brandOnPrimary = MaterialTheme.colorScheme.onPrimary
    val brandAccentText = MaterialTheme.colorScheme.primary
    val participantContexts = availableContexts.filter { it.parsedMode == AppContextMode.PARTNER_PARTICIPANT }
    var pendingInviteCode by rememberSaveable { mutableStateOf(initialInviteCode.trim().uppercase()) }
    var showConsentSheet by rememberSaveable { mutableStateOf(initialInviteCode.isNotBlank()) }
    var showReportProgramSheet by rememberSaveable { mutableStateOf(false) }
    var showRequestAppointmentSheet by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshWorkspace() }
    LaunchedEffect(statusMessage) {
        statusMessage?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
        viewModel.consumeStatusMessage()
    }
    if (showConsentSheet) {
        PartnerConsentSheet(
            apiClient = viewModel.partnerApiClient(),
            initialInviteCode = pendingInviteCode,
            initialProgramId = activeContext.programId,
            onDismiss = {
                showConsentSheet = false
                pendingInviteCode = ""
                viewModel.refreshWorkspace()
            }
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


    Scaffold(topBar = {
        TopAppBar(title = { Text("Meus programas") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
        }, actions = {
            IconButton(onClick = viewModel::refreshWorkspace) { Icon(Icons.Default.Refresh, "Atualizar") }
        })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (isLoading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            if (participantContexts.size > 1) item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(participantContexts, key = { it.cacheKey() }) { entry ->
                        FilterChip(selected = entry.cacheKey() == activeContext.cacheKey(),
                            onClick = { viewModel.selectContext(entry) },
                            label = { Text(entry.programName ?: "Programa") })
                    }
                }
            }
            if (!isLoading && activeContext.parsedMode == AppContextMode.PARTNER_PARTICIPANT) {
                item { Text(activeContext.programName ?: "Programa", style = MaterialTheme.typography.titleMedium) }
                if (savedExperience.features.partnerRemindersEnabled) item {
                    ProgramRemindersParticipantCard(
                        programName = activeContext.programName ?: "Programa",
                        companyName = activeContext.companyName.orEmpty(),
                        feed = enrollmentRemindersFeed, acknowledgingReminderId = acknowledgingReminderId,
                        isUpdatingMute = isUpdatingMute, brandPrimary = brandPrimary,
                        brandOnPrimary = brandOnPrimary, brandAccent = brandAccentText,
                        onAcknowledgeReminder = { id -> activeContext.programId?.let { viewModel.acknowledgeEnrollmentReminder(it, id) } },
                        onToggleMute = { muted -> activeContext.programId?.let { viewModel.toggleEnrollmentMuted(it, muted) } }
                    )
                }
                if (savedExperience.features.appointmentsEnabled) item {
                    ProgramAppointmentsParticipantCard(
                        programName = activeContext.programName ?: "Programa",
                        policy = savedExperience.appointmentsPolicy, feed = enrollmentAppointmentsFeed,
                        updatingAppointmentId = updatingAppointmentId, brandPrimary = brandPrimary,
                        brandOnPrimary = brandOnPrimary, brandAccent = brandAccentText,
                        onOpenRequestSheet = { showRequestAppointmentSheet = true },
                        onCancelAppointment = { id -> activeContext.programId?.let { viewModel.cancelEnrollmentAppointment(it, id) } }
                    )
                }
                if (savedExperience.features.preConsultationPdfEnabled) item {
                    PartnerPersonalPdfCard(
                        experienceConfig = savedExperience,
                        sharedCategories = activeContext.sharedCategories,
                        isGenerating = isGeneratingPdf,
                        generatedPdf = generatedPdf,
                        brandPrimary = brandPrimary,
                        brandOnPrimary = brandOnPrimary,
                        brandAccent = brandAccentText,
                        onGenerate = { days, categories ->
                            viewModel.generatePreConsultationPdf(days, categories)
                        }
                    )
                }
                item { OutlinedButton(onClick = { showConsentSheet = true }, modifier = Modifier.fillMaxWidth()) { Text("Meu consentimento") } }
                item { TextButton(onClick = { showReportProgramSheet = true }) { Text("Denunciar programa") } }
            }
            item {
                OutlinedButton(onClick = { pendingInviteCode = ""; showConsentSheet = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Participar por código")
                }
            }
        }
    }
}
