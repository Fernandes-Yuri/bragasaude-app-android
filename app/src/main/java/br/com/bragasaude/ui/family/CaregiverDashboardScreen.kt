package br.com.bragasaude.ui.family

import br.com.bragasaude.ui.components.BragaFormSheet
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Forum

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.local.ConsultationEntity
import br.com.bragasaude.data.local.FamilyBindingEntity
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import java.util.*

/** Painel de cuidado familiar, com foco em identificação do paciente e leitura rápida dos dados. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverDashboardScreen(
    onBack: (() -> Unit)? = null,
    onConnectFamily: () -> Unit = {},
    onOpenChat: () -> Unit = {},
    viewModel: CaregiverDashboardViewModel = hiltViewModel()
) {
    val watchedPatients by viewModel.watchedPatients.collectAsState()
    val dashboard by viewModel.dashboard.collectAsState()
    val upcomingConsultations by viewModel.upcomingConsultations.collectAsState()
    val messages by remember(dashboard.patientUserId) {
        viewModel.recentMessages(dashboard.patientUserId)
    }.collectAsState(initial = emptyList())

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            val text = when (event) {
                is FamilyUiEvent.Notice -> event.text
                is FamilyUiEvent.Error -> event.text
            }
            snackbarHostState.showSnackbar(text)
        }
    }

    LaunchedEffect(watchedPatients) {
        if (watchedPatients.isNotEmpty() && dashboard.patientUserId.isEmpty()) {
            viewModel.selectPatient(watchedPatients.first())
        }
    }

    // Polling de fallback (2 min) para atualizar métricas diárias/perfil que não possuem stream realtime
    LaunchedEffect(dashboard.patientUserId) {
        if (dashboard.patientUserId.isNotBlank()) {
            while (true) {
                kotlinx.coroutines.delay(120_000L)
                viewModel.refreshPatientData()
            }
        }
    }

    var showReminderDialog by remember { mutableStateOf(false) }
    var showAppointmentDialog by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }
    var showFamilyPostDialog by remember { mutableStateOf(false) }
    var showDashboardMenu by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Acompanhamento",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenChat) {
                        Icon(Icons.Default.Forum, contentDescription = "Conversar no chat")
                    }
                    Box {
                        IconButton(onClick = { showDashboardMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Mais opções")
                        }
                        DropdownMenu(
                            expanded = showDashboardMenu,
                            onDismissRequest = { showDashboardMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Conectar familiar") },
                                leadingIcon = { Icon(Icons.Default.PersonAdd, contentDescription = null) },
                                onClick = {
                                    showDashboardMenu = false
                                    onConnectFamily()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Atualizar dados") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showDashboardMenu = false
                                    viewModel.refreshPatientData()
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        // Indicador de carregamento sutil no topo da tela
        if (dashboard.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (watchedPatients.isEmpty()) {
            Column(Modifier.padding(innerPadding)) {
                Button(onClick = onConnectFamily, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Conectar familiar por código")
                }
                EmptyCaregiverState(modifier = Modifier.weight(1f))
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)
            ) {
                // Seletor quando acompanha mais de um familiar
                if (watchedPatients.size > 1) {
                    item {
                        PatientSelectorRow(
                            patients = watchedPatients,
                            selectedIndex = watchedPatients.indexOfFirst { it.patientUserId == dashboard.patientUserId },
                            onSelect = { viewModel.selectPatient(watchedPatients[it]) }
                        )
                    }
                }

                item { PatientHeaderCard(state = dashboard) }

                item {
                    TodayVitalsCard(
                        state = dashboard,
                        onRefresh = {
                            watchedPatients.firstOrNull { it.patientUserId == dashboard.patientUserId }
                                ?.let { viewModel.selectPatient(it) }
                        }
                    )
                }

                item {
                    // AUD-AN11: getBPHistory() devolve um flow FRIO novo a cada
                    // chamada; dentro de item{} o collectAsState reiniciava a
                    // coleta (e a query do Room) a CADA recomposição do painel.
                    // remember pela chave do paciente reutiliza a mesma instância.
                    val bpRecords by remember(dashboard.patientUserId) {
                        viewModel.getBPHistory(dashboard.patientUserId)
                    }.collectAsState(initial = emptyList())
                    val glucoseRecords by remember(dashboard.patientUserId) {
                        viewModel.getGlucoseHistory(dashboard.patientUserId)
                    }.collectAsState(initial = emptyList())
                    val hydrationRecords by remember(dashboard.patientUserId) {
                        viewModel.getHydrationHistory(dashboard.patientUserId)
                    }.collectAsState(initial = emptyList())
                    val dailyMetrics by remember(dashboard.patientUserId) {
                        viewModel.getDailyMetricsHistory(dashboard.patientUserId)
                    }.collectAsState(initial = emptyList())

                    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()) }

                    val now = remember { java.util.Date() }
                    val cutoff7d = remember {
                        java.util.Calendar.getInstance().apply {
                            time = now
                            add(java.util.Calendar.DAY_OF_YEAR, -7)
                        }.time
                    }
                    val cutoff30d = remember {
                        java.util.Calendar.getInstance().apply {
                            time = now
                            add(java.util.Calendar.DAY_OF_YEAR, -30)
                        }.time
                    }

                    val bpRecords7d = remember(bpRecords, cutoff7d) {
                        bpRecords.filter { it.measuredAt != null && it.measuredAt >= cutoff7d }
                    }
                    val bpRecords30d = remember(bpRecords, cutoff30d) {
                        bpRecords.filter { it.measuredAt != null && it.measuredAt >= cutoff30d }
                    }
                    val glucoseRecords7d = remember(glucoseRecords, cutoff7d) {
                        glucoseRecords.filter { it.measuredAt != null && it.measuredAt >= cutoff7d }
                    }
                    val glucoseRecords30d = remember(glucoseRecords, cutoff30d) {
                        glucoseRecords.filter { it.measuredAt != null && it.measuredAt >= cutoff30d }
                    }

                    val hydrationByDay = remember(hydrationRecords) {
                        hydrationRecords.groupBy { record ->
                            record.measuredAt?.let { sdf.format(it) } ?: ""
                        }.filterKeys { it.isNotBlank() }
                    }

                    val recentDates7d = dailyMetrics.take(7).map { it.date }.toSet()
                    val recentDates30d = dailyMetrics.take(30).map { it.date }.toSet()

                    val hydrationDays7d = if (recentDates7d.isNotEmpty()) {
                        recentDates7d.count { date ->
                            val dayTotal = hydrationByDay[date]?.sumOf { it.hydrationMl ?: 0 } ?: 0
                            dayTotal >= dashboard.hydrationTargetMl
                        }
                    } else {
                        hydrationByDay.values.take(7).count { list ->
                            list.sumOf { it.hydrationMl ?: 0 } >= dashboard.hydrationTargetMl
                        }
                    }

                    val hydrationDays30d = if (recentDates30d.isNotEmpty()) {
                        recentDates30d.count { date ->
                            val dayTotal = hydrationByDay[date]?.sumOf { it.hydrationMl ?: 0 } ?: 0
                            dayTotal >= dashboard.hydrationTargetMl
                        }
                    } else {
                        hydrationByDay.values.take(30).count { list ->
                            list.sumOf { it.hydrationMl ?: 0 } >= dashboard.hydrationTargetMl
                        }
                    }

                    val stepsDays7d = dailyMetrics.take(7).count { it.steps >= dashboard.stepGoal }
                    val stepsDays30d = dailyMetrics.take(30).count { it.steps >= dashboard.stepGoal }

                    HistoryMetricsSection(
                        patientHasDiabetes = dashboard.hasDiabetes,
                        patientHydrationTargetMl = dashboard.hydrationTargetMl,
                        patientStepGoal = dashboard.stepGoal,
                        bpRecords7d = bpRecords7d,
                        bpRecords30d = bpRecords30d,
                        glucoseRecords7d = glucoseRecords7d,
                        glucoseRecords30d = glucoseRecords30d,
                        hydrationDaysMet7d = hydrationDays7d,
                        hydrationDaysTotal7d = dailyMetrics.take(7).size.coerceAtLeast(1),
                        stepsDaysMet7d = stepsDays7d,
                        stepsDaysTotal7d = dailyMetrics.take(7).size.coerceAtLeast(1),
                        hydrationDaysMet30d = hydrationDays30d,
                        hydrationDaysTotal30d = dailyMetrics.take(30).size.coerceAtLeast(1),
                        stepsDaysMet30d = stepsDays30d,
                        stepsDaysTotal30d = dailyMetrics.take(30).size.coerceAtLeast(1)
                    )
                }

                item {
                    QuickActionsChips(
                        onMedicationReminder = { showReminderDialog = true },
                        onAppointment = { showAppointmentDialog = true },
                        onCareNote = { showNoteDialog = true },
                        onFamilyPost = { showFamilyPostDialog = true }
                    )
                }

                item {
                    UpcomingConsultationsCard(
                        consultations = upcomingConsultations,
                        onAddConsultation = { showAppointmentDialog = true },
                        onCancelConsultation = { consultationId ->
                            viewModel.cancelConsultation(consultationId)
                        }
                    )
                }



                item {
                    FamilyChatAccessCard(
                        latestMessage = messages.maxByOrNull { it.sentAt },
                        onOpenChat = onOpenChat
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    // ==================== DIÁLOGOS ====================

    if (showReminderDialog) {
        SingleFieldDialog(
            title = "Lembrete de Medicacao",
            description = "Chega no celular do seu familiar com o seu nome.",
            label = "O que lembrar?",
            confirmLabel = "Enviar lembrete",
            onConfirm = { text ->
                viewModel.sendMedicationReminder(text)
                showReminderDialog = false
            },
            onDismiss = { showReminderDialog = false }
        )
    }

    if (showAppointmentDialog) {
        AppointmentDialog(
            onConfirm = { specialty, scheduledDateMillis ->
                viewModel.scheduleConsultation(specialty, scheduledDateMillis)
                showAppointmentDialog = false
            },
            onDismiss = { showAppointmentDialog = false }
        )
    }

    if (showNoteDialog) {
        CareNoteDialog(
            onSend = { text, iconType ->
                viewModel.sendCareMessage(text, iconType)
                showNoteDialog = false
            },
            onDismiss = { showNoteDialog = false }
        )
    }

    if (showFamilyPostDialog) {
        FamilyPridePostDialog(
            patientName = dashboard.patientName,
            caregiverName = dashboard.caregiverName,
            onPublish = { title, description ->
                viewModel.publishFamilyPride(title, description)
                showFamilyPostDialog = false
            },
            onDismiss = { showFamilyPostDialog = false }
        )
    }
}

// ==================== SELETOR DE PACIENTE ====================

@Composable
private fun PatientSelectorRow(
    patients: List<FamilyBindingEntity>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
    repeat(patients.size) { index ->
            val isSelected = index == selectedIndex
        val patientName = patients[index].patientName?.trim()?.takeIf { it.isNotEmpty() }
            ?: "Pessoa ${index + 1}"
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(index) },
                label = {
                    Text(
                        patientName,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    br.com.bragasaude.ui.components.ConnectedUserAvatar(
                        patients[index].patientUserId, patientName, 24.dp)
                }
            )
        }
    }
}

// ==================== CABECALHO DO PACIENTE ====================

@Composable
internal fun PatientHeaderCard(
    state: CaregiverDashboardState,
    avatarContent: @Composable () -> Unit = {
        br.com.bragasaude.ui.components.ConnectedUserAvatar(state.patientUserId, state.patientName, 52.dp)
    }
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.Top) {
            avatarContent()
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(state.patientName.ifBlank { "Seu familiar" },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface)
                val relation = state.caregiverRelation.replace("\"", "").replace("'", "")
                    .replace("(a)", "").replace("(ã)", "").trim().lowercase()
                Text("Você acompanha como $relation", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Surface(shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("Ativo", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
        }
    }
}

@Composable
private fun TodayVitalsCard(
    state: CaregiverDashboardState,
    onRefresh: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    "Sinais vitais",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.weight(1f))
                if (state.lastMeasurementLabel != null) {
                    Text(
                        state.lastMeasurementLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Mini-cards de Pressao e Glicose
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                VitalMiniCard(
                    label = "Pressao arterial",
                    value = if (state.systolic != null) "${state.systolic}/${state.diastolic ?: "--"}" else "--",
                    unit = "mmHg",
                    icon = Icons.Default.Favorite,
                    accentColor = MaterialTheme.colorScheme.error,
                    modifier = if (state.hasDiabetes) Modifier.weight(1f) else Modifier.fillMaxWidth()
                )
                if (state.hasDiabetes) {
                    VitalMiniCard(
                        label = "Glicose",
                        value = state.glucose?.toString() ?: "--",
                        unit = "mg/dL",
                        icon = Icons.Default.Medication,
                        accentColor = Color(0xFFE65100),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Barra de progresso: Hidratacao
            VitalProgressTile(
                icon = Icons.Default.WaterDrop,
                label = "Hidratacao",
                value = "${state.hydrationMl} ml",
                metaLabel = "Meta: ${state.hydrationTargetMl} ml",
                progress = if (state.hydrationTargetMl > 0) {
                    (state.hydrationMl.toFloat() / state.hydrationTargetMl).coerceIn(0f, 1f)
                } else 0f,
                accent = Color(0xFF0277BD)
            )

            Spacer(Modifier.height(12.dp))

            // Barra de progresso: Passos
            VitalProgressTile(
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                label = "Passos",
                value = "${state.steps}",
                metaLabel = "Meta: ${state.stepGoal} passos",
                progress = if (state.stepGoal > 0) {
                    (state.steps.toFloat() / state.stepGoal).coerceIn(0f, 1f)
                } else 0f,
                accent = Color(0xFF2E7D32)
            )

            Spacer(Modifier.height(12.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Spacer(Modifier.height(8.dp))

            Text(
                "Leituras autorreportadas ou medidas no dia pelo proprio familiar.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * Mini-card sofisticado para indicadores numericos (Pressao, Glicose).
 */
@Composable
private fun VitalMiniCard(
    label: String,
    value: String,
    unit: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 28.sp
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    unit,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

/**
 * Tile com barra linear de progresso (Hidratacao, Passos).
 */
@Composable
private fun VitalProgressTile(
    icon: ImageVector,
    label: String,
    value: String,
    metaLabel: String,
    progress: Float,
    accent: Color
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accent.copy(alpha = 0.1f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = accent,
                trackColor = accent.copy(alpha = 0.12f),
                strokeCap = StrokeCap.Round
            )
            Spacer(Modifier.height(6.dp))
            Text(
                metaLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// ==================== ATALHOS RAPIDOS (CHIPS HORIZONTAIS) ====================

@Composable
private fun QuickActionsChips(
    onMedicationReminder: () -> Unit,
    onAppointment: () -> Unit,
    onCareNote: () -> Unit,
    onFamilyPost: () -> Unit
) {
    Column {
        Text(
            "Ações rápidas",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(
                    icon = Icons.Default.Medication,
                    label = "Medicação",
                    accent = Color(0xFFE65100),
                    modifier = Modifier.weight(1f),
                    onClick = onMedicationReminder
                )
                ActionChip(
                    icon = Icons.Default.CalendarMonth,
                    label = "Consulta",
                    accent = Color(0xFF6A1B9A),
                    modifier = Modifier.weight(1f),
                    onClick = onAppointment
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionChip(
                    icon = Icons.AutoMirrored.Filled.Message,
                    label = "Mensagem",
                    accent = Color(0xFFAD1457),
                    modifier = Modifier.weight(1f),
                    onClick = onCareNote
                )
                ActionChip(
                    icon = Icons.Default.Group,
                    label = "Família",
                    accent = Color(0xFF2E7D32),
                    modifier = Modifier.weight(1f),
                    onClick = onFamilyPost
                )
            }
        }
    }
}

@Composable
private fun ActionChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.2f)),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}




/**
 * Item de checklist clean com checkbox circular e tachado suave.
 */


@Composable
private fun FamilyChatAccessCard(
    latestMessage: br.com.bragasaude.data.local.FamilyMessageEntity?,
    onOpenChat: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Conversa Familiar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        latestMessage?.let {
                            "Último recado: " + android.text.format.DateUtils.getRelativeTimeSpanString(
                                it.sentAt, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
                            )
                        } ?: "Toque para falar com seu familiar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            latestMessage?.takeIf { !it.isRead }?.let {
                Text(it.messageText, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Button(onClick = onOpenChat, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp)) {
                Text("Abrir Chat Familiar", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ==================== ESTADO VAZIO ====================

@Composable
private fun EmptyCaregiverState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Nenhum familiar vinculado",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
            Text(
                "Solicite o codigo de 8 caracteres do seu familiar e conecte-se na tela Ponte Familiar.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
    }
}

// ==================== AGENDA DO CUIDADO (CONSULTAS DURÁVEIS) ====================

@Composable
private fun UpcomingConsultationsCard(
    consultations: List<ConsultationEntity>,
    onAddConsultation: () -> Unit,
    onCancelConsultation: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF6A1B9A).copy(alpha = 0.1f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = Color(0xFF6A1B9A),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "Agenda do Cuidado",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Consultas agendadas e lembretes duráveis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onAddConsultation) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Agendar Consulta",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (consultations.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Nenhuma consulta agendada para os próximos dias.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                val sdfDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val sdfTime = SimpleDateFormat("HH:mm", Locale.getDefault())

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    consultations.forEach { consultation ->
                        val dateFormatted = sdfDate.format(consultation.scheduledDate)
                        val timeFormatted = sdfTime.format(consultation.scheduledDate)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        consultation.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "$dateFormatted às $timeFormatted",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF6A1B9A).copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            "Lembretes ativos (24h e 1h antes)",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF6A1B9A),
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onCancelConsultation(consultation.id) }
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Cancelar Consulta",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
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

// ==================== DIALOGOS ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppointmentDialog(
    onConfirm: (specialty: String, scheduledDateMillis: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var specialty by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }
    var parsedDateMillis by remember { mutableLongStateOf(0L) }

    BragaFormSheet(
        scrollContent = false,
        onDismissRequest = onDismiss,
        title = {
            Text("Agendar Consulta", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Preencha os dados da consulta. Os lembretes no celular (24h e 1h antes) serão configurados automaticamente pelo WorkManager.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                OutlinedTextField(
                    value = specialty,
                    onValueChange = { specialty = it },
                    label = { Text("Especialidade / Nome do Medico") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = dateText,
                    onValueChange = { dateText = it },
                    label = { Text("Data (ex: 31/10/2026)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it },
                    label = { Text("Horario (ex: 14:30)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                        try {
                            val dateTime = sdf.parse("$dateText $timeText")
                            if (dateTime != null && specialty.isNotBlank()) {
                                parsedDateMillis = dateTime.time

                                val intent = android.content.Intent(android.content.Intent.ACTION_INSERT).apply {
                                    data = android.provider.CalendarContract.Events.CONTENT_URI
                                    putExtra(android.provider.CalendarContract.Events.TITLE, "Consulta: $specialty")
                                    putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, parsedDateMillis)
                                    putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, parsedDateMillis + 3600_000L)
                                }
                                context.startActivity(intent)
                            }
                        } catch (_: Exception) {}
                    },
                    enabled = specialty.isNotBlank() && dateText.isNotBlank() && timeText.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Event, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Google Agenda", fontSize = 13.sp)
                }
                Button(
                    onClick = {
                        val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                        try {
                            val dateTime = sdf.parse("$dateText $timeText")
                            if (dateTime != null && specialty.isNotBlank()) {
                                onConfirm(specialty, dateTime.time)
                            }
                        } catch (_: Exception) {}
                    },
                    enabled = specialty.isNotBlank() && dateText.isNotBlank() && timeText.isNotBlank(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Agendar")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun SingleFieldDialog(
    title: String,
    description: String,
    label: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    BragaFormSheet(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(label) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun CareNoteDialog(
    onSend: (text: String, iconType: String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    var iconType by remember { mutableStateOf("LOVE") }

    val iconOptions = listOf(
        "LOVE" to Icons.Default.Favorite,
        "WATER" to Icons.Default.WaterDrop,
        "MED" to Icons.Default.Medication,
        "WALK" to Icons.AutoMirrored.Filled.DirectionsWalk,
        "CUSTOM" to Icons.Default.Star
    )

    BragaFormSheet(
        scrollContent = false,
        onDismissRequest = onDismiss,
        title = { Text("Mensagem personalizada", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Escolha um icone para a mensagem:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    iconOptions.forEach { (type, icon) ->
                        val selected = iconType == type
                        Surface(
                            onClick = { iconType = type },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(
                                2.dp,
                                if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                            ),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    icon,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Sua mensagem") },
                    placeholder = { Text("Pai, vi que bateu a meta de agua hoje!") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSend(text, iconType) },
                enabled = text.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Enviar mensagem") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun FamilyPridePostDialog(
    patientName: String,
    caregiverName: String,
    onPublish: (title: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    BragaFormSheet(
        scrollContent = false,
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Orgulho familiar", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Publicacao em Familia",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Celebre um momento especial do seu familiar no mural da comunidade. Valores de saude permanecem privados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titulo do momento") },
                    placeholder = { Text("Caminhada no parque juntos!") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Conte como foi") },
                    placeholder = { Text("Hoje fomos caminhar no parque!") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onPublish(title, description) },
                enabled = title.isNotBlank() || description.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Publicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
