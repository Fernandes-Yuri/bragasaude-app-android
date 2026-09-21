package br.com.bragasaude.ui.steps

import android.Manifest
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.local.DailyMetricsEntity
import br.com.bragasaude.data.util.ActivityState
import br.com.bragasaude.ui.components.ChartPeriod
import br.com.bragasaude.ui.components.MetricInfoBottomSheet
import br.com.bragasaude.ui.components.MetricInfoCatalog
import br.com.bragasaude.ui.components.SimpleTrendChart
import br.com.bragasaude.ui.components.WhiteHeartIcon
import br.com.bragasaude.ui.theme.Info
import br.com.bragasaude.ui.theme.Success
import br.com.bragasaude.ui.theme.TealPrimary
import br.com.bragasaude.ui.theme.Warning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepsScreen(
    onBack: () -> Unit,
    onConnectWatch: () -> Unit = {},
    viewModel: StepsViewModel = hiltViewModel()
) {
    val currentSteps by viewModel.currentSteps.collectAsState()
    val distance by viewModel.currentDistance.collectAsState()
    val currentActiveMinutes by viewModel.currentActiveMinutes.collectAsState()
    val currentTotalCalories by viewModel.currentTotalCalories.collectAsState()
    val dailyMetrics by viewModel.dailyMetrics.collectAsState()
    val targetSteps by viewModel.targetSteps.collectAsState()
    val weeklyAvg by viewModel.weeklyAverage.collectAsState()
    val monthlyTrend by viewModel.monthlyTrend.collectAsState()
    val activityState by viewModel.activityState.collectAsState()
    val currentReliability by viewModel.currentReliability.collectAsState()

    // Pontos de Cardio (Diretriz OMS - 150 pts semanais)
    val dailyHeartPoints by viewModel.currentDailyHeartPoints.collectAsState()
    val weeklyHeartPoints by viewModel.currentWeeklyHeartPoints.collectAsState()
    val weeklyCardioHistory by viewModel.weeklyCardioHistory.collectAsState()
    val weeklyStepsHistory by viewModel.weeklyStepsHistory.collectAsState()
    val targetWeeklyHeartPoints = viewModel.targetWeeklyHeartPoints

    var activeModalMetric by remember { mutableStateOf<ActivityMetricType?>(null) }
    var showWeeklyStepsModal by remember { mutableStateOf(false) }
    var showWeeklyCardioModal by remember { mutableStateOf(false) }
    var showStepGoalDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.entries.all { it.value }
        if (granted) viewModel.startTracking()
        else Toast.makeText(context, "Permissões necessárias.", Toast.LENGTH_LONG).show()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Atividade Física", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                OutlinedButton(onClick = onConnectWatch, modifier = Modifier.fillMaxWidth()) {
                    Text("Conectar relógio / ver leituras")
                }
            }
            item {
                val liveMins = maxOf(dailyMetrics?.activeMinutes ?: 0, currentActiveMinutes)
                val liveCalories = if (currentTotalCalories > 0f) currentTotalCalories else (dailyMetrics?.caloriesBurned ?: 0f)
                
                DailySummaryCard(
                    steps = maxOf(currentSteps, dailyMetrics?.steps ?: 0),
                    distance = distance,
                    calories = liveCalories,
                    activeMinutes = liveMins,
                    target = targetSteps,
                    activityState = activityState,
                    onMetricClick = { metric -> activeModalMetric = metric },
                    onEditTarget = { showStepGoalDialog = true }
                )
            }

            if (activityState != ActivityState.RESTING && currentReliability < 0.4f) {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Sinal de GPS/movimento em calibração. Caminhe em área aberta para validar os pontos na Liga.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Card de Pontos de Cardio com Cronologia Semanal (Diretriz OMS - 150 pts/semana)
            item {
                HeartPointsCard(
                    dailyPoints = dailyHeartPoints,
                    weeklyPoints = weeklyHeartPoints,
                    targetWeekly = targetWeeklyHeartPoints,
                    weeklyHistory = weeklyCardioHistory,
                    onClick = { showWeeklyCardioModal = true }
                )
            }

            item {
                WeeklyProgressSection(
                    avgSteps = weeklyAvg.first,
                    avgMinutes = weeklyAvg.second,
                    onClick = { showWeeklyStepsModal = true }
                )
            }

            item {
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                                Manifest.permission.ACTIVITY_RECOGNITION
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                ) {
                    WhiteHeartIcon(
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Sincronizar Atividade (GPS)")
                }
            }
        }
    }

    // Modal / Janela com Gráfico sob demanda
    activeModalMetric?.let { metric ->
        MetricDetailModal(
            metric = metric,
            monthlyTrend = monthlyTrend,
            targetSteps = targetSteps,
            currentSteps = currentSteps,
            currentCalories = if (currentTotalCalories > 0f) currentTotalCalories else (dailyMetrics?.caloriesBurned ?: 0f),
            currentDistance = distance,
            currentMinutes = maxOf(dailyMetrics?.activeMinutes ?: 0, currentActiveMinutes),
            onDismiss = { activeModalMetric = null }
        )
    }

    if (showWeeklyStepsModal) {
        WeeklyStepsModal(
            weeklyHistory = weeklyStepsHistory,
            avgSteps = weeklyAvg.first,
            avgMinutes = weeklyAvg.second,
            targetSteps = targetSteps,
            onDismiss = { showWeeklyStepsModal = false }
        )
    }

    if (showWeeklyCardioModal) {
        WeeklyCardioModal(
            weeklyHistory = weeklyCardioHistory,
            weeklyPoints = weeklyHeartPoints,
            targetWeekly = targetWeeklyHeartPoints,
            onDismiss = { showWeeklyCardioModal = false }
        )
    }

    if (showStepGoalDialog) {
        br.com.bragasaude.ui.profile.StepGoalDialog(
            currentGoal = targetSteps,
            onDismiss = { showStepGoalDialog = false },
            onSave = { newGoal ->
                viewModel.updateTargetSteps(newGoal)
                showStepGoalDialog = false
            }
        )
    }
}

@Composable
fun DailySummaryCard(
    steps: Int,
    distance: Float,
    calories: Float,
    activeMinutes: Int,
    target: Int,
    activityState: ActivityState,
    onMetricClick: (ActivityMetricType) -> Unit,
    onEditTarget: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFF3F4F6)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .clickable(onClick = { onMetricClick(ActivityMetricType.STEPS) })
            ) {
                val progress by animateFloatAsState(if (target > 0) (steps.toFloat() / target).coerceIn(0f, 1f) else 0f, label = "Progrès des pas")
                val track = MaterialTheme.colorScheme.primaryContainer
                val accent = MaterialTheme.colorScheme.primary
                Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                    drawArc(track, -90f, 360f, false, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
                    if (progress > 0f) drawArc(Brush.sweepGradient(listOf(accent, br.com.bragasaude.ui.theme.BragaEmeraldLight, accent)), -90f, progress * 360f, false, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        when(activityState) {
                            ActivityState.VIGOROUS -> Icons.AutoMirrored.Filled.DirectionsRun
                            else -> Icons.AutoMirrored.Filled.DirectionsWalk
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Text("$steps", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Text("de $target", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            AnimatedVisibility(visible = target > 0 && steps >= target) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.EmojiEvents, contentDescription = null)
                        Spacer(Modifier.width(8.dp)); Text("Meta do dia alcançada!", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            TextButton(
                onClick = onEditTarget,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Alterar Meta ($target)", style = MaterialTheme.typography.labelMedium)
            }
            
            Spacer(Modifier.height(16.dp))
            
            // 3 Métricas Sutis e Clicáveis
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SubtleMetricItem(
                    icon = Icons.Default.LocalFireDepartment,
                    value = "%.0f".format(calories),
                    unit = "kcal",
                    tint = Warning,
                    onClick = { onMetricClick(ActivityMetricType.CALORIES) }
                )
                SubtleMetricItem(
                    icon = Icons.Default.LocationOn,
                    value = "%.1f".format(distance / 1000f),
                    unit = "km",
                    tint = Info,
                    onClick = { onMetricClick(ActivityMetricType.DISTANCE) }
                )
                SubtleMetricItem(
                    icon = Icons.Default.Timer,
                    value = "$activeMinutes",
                    unit = "min",
                    tint = Success,
                    onClick = { onMetricClick(ActivityMetricType.ACTIVE_MINUTES) }
                )
            }
            
            Spacer(Modifier.height(16.dp))
            ActivityStateBadge(activityState)
        }
    }
}

@Composable
fun SubtleMetricItem(
    icon: ImageVector,
    value: String,
    unit: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Janela Modal elegante com Gráfico de Evolução e botão X de fechamento.
 */
@Composable
fun MetricDetailModal(
    metric: ActivityMetricType,
    monthlyTrend: List<DailyMetricsEntity>,
    targetSteps: Int,
    currentSteps: Int,
    currentCalories: Float,
    currentDistance: Float,
    currentMinutes: Int,
    onDismiss: () -> Unit
) {
    val scrollState = androidx.compose.foundation.rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(20.dp)
            ) {
                // Top Bar com Título, Ícone e Botão Fechar com área de toque de 48dp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val icon = when (metric) {
                        ActivityMetricType.STEPS -> Icons.AutoMirrored.Filled.DirectionsWalk
                        ActivityMetricType.HEART_POINTS -> Icons.Default.Favorite
                        ActivityMetricType.CALORIES -> Icons.Default.LocalFireDepartment
                        ActivityMetricType.DISTANCE -> Icons.Default.LocationOn
                        ActivityMetricType.ACTIVE_MINUTES -> Icons.Default.Timer
                    }
                    val tint = when (metric) {
                        ActivityMetricType.STEPS -> MaterialTheme.colorScheme.primary
                        ActivityMetricType.HEART_POINTS -> TealPrimary
                        ActivityMetricType.CALORIES -> Warning
                        ActivityMetricType.DISTANCE -> Info
                        ActivityMetricType.ACTIVE_MINUTES -> Success
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = tint.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            metric.label,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .padding(4.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Destaque do Valor Atual ("Hoje")
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total de Hoje", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            val displayVal = when (metric) {
                                ActivityMetricType.STEPS -> "%,d".format(currentSteps)
                                ActivityMetricType.HEART_POINTS -> "$currentMinutes"
                                ActivityMetricType.CALORIES -> "%.0f".format(currentCalories)
                                ActivityMetricType.DISTANCE -> "%.2f".format(currentDistance / 1000f)
                                ActivityMetricType.ACTIVE_MINUTES -> "$currentMinutes"
                            }
                            Text("$displayVal ${metric.unit}", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        }

                        if (metric == ActivityMetricType.STEPS) {
                            Text(
                                "Meta: %,d".format(targetSteps),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (metric == ActivityMetricType.HEART_POINTS) {
                            Text(
                                "Meta OMS: 150 pts/sem",
                                style = MaterialTheme.typography.labelSmall,
                                color = TealPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Gráfico com períodos (7D, 15D, 30D)
                val (chartData, chartUnit, targetVal, chartColor) = when (metric) {
                    ActivityMetricType.STEPS -> Quadruple(
                        monthlyTrend.map { it.steps.toDouble() },
                        "passos",
                        targetSteps.toDouble(),
                        MaterialTheme.colorScheme.primary
                    )
                    ActivityMetricType.HEART_POINTS -> Quadruple(
                        monthlyTrend.map { it.activeMinutes.toDouble() },
                        "pts",
                        22.0, // Meta diária proporcional para 150/sem
                        TealPrimary
                    )
                    ActivityMetricType.CALORIES -> Quadruple(
                        monthlyTrend.map { it.caloriesBurned.toDouble() },
                        "kcal",
                        null,
                        Warning
                    )
                    ActivityMetricType.DISTANCE -> Quadruple(
                        monthlyTrend.map { (it.distanceMeters / 1000.0) },
                        "km",
                        null,
                        Info
                    )
                    ActivityMetricType.ACTIVE_MINUTES -> Quadruple(
                        monthlyTrend.map { it.activeMinutes.toDouble() },
                        "min",
                        null,
                        Success
                    )
                }

                SimpleTrendChart(
                    data = chartData,
                    label = "Histórico (${chartUnit})",
                    unit = chartUnit,
                    targetValue = targetVal,
                    color = chartColor,
                    showPeriodSelector = true
                )
            }
        }
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@Composable
fun ActivityStateBadge(state: ActivityState) {
    val (label, color) = when(state) {
        ActivityState.RESTING -> "Em Repouso" to Color.Gray
        ActivityState.LIGHT -> "Em Movimento" to Color(0xFF4CAF50)
        ActivityState.MODERATE -> "Caminhando" to Color(0xFF4CAF50)
        ActivityState.VIGOROUS -> "Correndo" to Color(0xFF4CAF50)
    }
    
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                color = color,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun WeeklyProgressSection(avgSteps: Int, avgMinutes: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Assessment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Média Semanal (Seg – Dom)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text("$avgSteps passos/dia • $avgMinutes min ativos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Ver evolução semanal",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * Modal com Gráfico de Evolução Semanal de Passos (iniciando segunda-feira e finalizando domingo)
 * com comparação em relação ao dia anterior e seleção interativa ao toque.
 */
@Composable
fun WeeklyStepsModal(
    weeklyHistory: List<StepDayProgress>,
    avgSteps: Int,
    avgMinutes: Int,
    targetSteps: Int,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    var selectedIndex by remember {
        mutableStateOf<Int?>(
            weeklyHistory.indexOfFirst { it.isToday }.takeIf { it >= 0 }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(20.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Filled.DirectionsWalk,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Evolução Semanal",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            val firstDay = weeklyHistory.firstOrNull()?.dateFormatted ?: ""
                            val lastDay = weeklyHistory.lastOrNull()?.dateFormatted ?: ""
                            Text(
                                "Segunda ($firstDay) a Domingo ($lastDay)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Resumo Semanal Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Média da Semana", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "%,d".format(avgSteps),
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text("passos / dia", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val totalSteps = weeklyHistory.filter { !it.isFuture }.sumOf { it.steps }
                            Text("Total da Semana", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "%,d".format(totalSteps),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text("Meta diária: %,d".format(targetSteps), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Gráfico de 7 Barras da Semana (Seg a Dom)
                Text(
                    "Toque em um dia para ver detalhes e variação:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                val maxSteps = maxOf(targetSteps, weeklyHistory.maxOfOrNull { it.steps } ?: targetSteps)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            weeklyHistory.forEachIndexed { index, day ->
                                val isSelected = selectedIndex == index
                                val barFraction = if (maxSteps > 0) (day.steps.toFloat() / maxSteps).coerceIn(0.04f, 1f) else 0.04f
                                val barColor = when {
                                    isSelected -> MaterialTheme.colorScheme.primary
                                    day.isToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
                                    day.isGoalMet -> Color(0xFF10B981)
                                    day.steps > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else -> Color.LightGray.copy(alpha = 0.3f)
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedIndex = index }
                                        .padding(horizontal = 2.dp)
                                ) {
                                    if (day.steps > 0) {
                                        Text(
                                            text = if (day.steps >= 1000) "%.1fk".format(day.steps / 1000f) else "${day.steps}",
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }

                                val maxBarHeight = 75.dp
                                val barHeight = (maxBarHeight * barFraction).coerceAtLeast(6.dp)

                                Box(
                                    modifier = Modifier
                                        .width(if (isSelected) 18.dp else 14.dp)
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(barColor)
                                        .then(
                                            if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            else Modifier
                                        )
                                )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = day.dayLabel,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected || day.isToday) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                    Text(
                                        text = day.dateFormatted,
                                        fontSize = 14.sp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Detalhes Minimalistas do Dia Selecionado
                selectedIndex?.let { idx ->
                    if (idx in weeklyHistory.indices) {
                        val day = weeklyHistory[idx]
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${day.fullDayName} (${day.dateFormatted})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "%,d passos".format(day.steps),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                val pctOfGoal = if (targetSteps > 0) ((day.steps.toFloat() / targetSteps) * 100).toInt() else 0
                                val targetFormatted = "%,d".format(targetSteps)
                                Text(
                                    text = if (day.isFuture) "Dia ainda não iniciado" else "$pctOfGoal% da meta diária de $targetFormatted passos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (day.isGoalMet) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // Variação em relação ao dia anterior
                                if (!day.isFuture) {
                                    day.diffFromPreviousDay?.let { diff ->
                                        val isPositive = diff >= 0
                                        val diffFormatted = "%,d".format(diff)
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (isPositive) "+$diffFormatted passos em relação ao dia anterior" else "$diffFormatted passos em relação ao dia anterior",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)
                                            )
                                        }
                                    } ?: run {
                                        Text(
                                            text = "Primeiro dia da semana (Segunda-feira)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
}



@Composable
fun HeartPointsCard(
    dailyPoints: Int,
    weeklyPoints: Int,
    targetWeekly: Int = 150,
    weeklyHistory: List<CardioDayProgress> = emptyList(),
    onClick: () -> Unit
) {
    var showInfoSheet by remember { mutableStateOf(false) }
    var selectedDayIndex by remember { mutableStateOf<Int?>(null) }

    if (showInfoSheet) {
        MetricInfoBottomSheet(
            metricInfo = MetricInfoCatalog.heartPoints,
            onDismiss = { showInfoSheet = false }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFF3F4F6)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabeçalho Oficial OMS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = TealPrimary.copy(alpha = 0.12f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Favorite,
                                contentDescription = null,
                                tint = TealPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Pontos de Cardio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Diretriz OMS (Organização Mundial da Saúde)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = { showInfoSheet = true }, modifier = Modifier.size(44.dp)) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = "Informações sobre Pontos de Cardio",
                        tint = TealPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Resumo: Hoje vs Meta Semanal
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text(
                        "$dailyPoints pts",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Ganhos hoje",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$weeklyPoints / $targetWeekly pts",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )
                    val percent = ((weeklyPoints.toFloat() / targetWeekly) * 100).toInt()
                    Text(
                        "Meta Semanal OMS ($percent%)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Barra de Progresso Semanal (0 a 150 pontos da OMS)
            val weeklyProgress = (weeklyPoints.toFloat() / targetWeekly).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { weeklyProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = TealPrimary,
                trackColor = TealPrimary.copy(alpha = 0.15f)
            )

            // Seção de Evolução Semanal compacta e elegante com a mesma estética do WeeklyProgressSection
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.2f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Surface(
                            shape = CircleShape,
                            color = TealPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.ShowChart,
                                    contentDescription = null,
                                    tint = TealPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Evolução Semanal (Seg – Dom)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "Toque para ver gráfico diário de 7 dias e metas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Ver evolução de cardio",
                        tint = TealPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

/**
 * Modal com Gráfico de Evolução Semanal de Pontos de Cardio (Seg a Dom)
 * seguindo a exata mesma estética e princípios do WeeklyStepsModal.
 */
@Composable
fun WeeklyCardioModal(
    weeklyHistory: List<CardioDayProgress>,
    weeklyPoints: Int,
    targetWeekly: Int = 150,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    var selectedIndex by remember {
        mutableStateOf<Int?>(
            weeklyHistory.indexOfFirst { it.isToday }.takeIf { it >= 0 }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(20.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = TealPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = TealPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Evolução de Cardio",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            val firstDay = weeklyHistory.firstOrNull()?.dateFormatted ?: ""
                            val lastDay = weeklyHistory.lastOrNull()?.dateFormatted ?: ""
                            Text(
                                "Segunda ($firstDay) a Domingo ($lastDay)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar", modifier = Modifier.size(20.dp))
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Resumo Semanal Card
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total da Semana", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "$weeklyPoints",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TealPrimary
                            )
                            Text("pontos acumulados", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val percent = ((weeklyPoints.toFloat() / targetWeekly) * 100).toInt()
                            Text(
                                "$percent% da meta OMS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (weeklyPoints >= targetWeekly) Color(0xFF10B981) else TealPrimary
                            )
                            Text("Meta: 150 pts (~22 pts/dia)", style = MaterialTheme.typography.labelSmall, color = TealPrimary)
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Gráfico de 7 Barras da Semana (Seg a Dom)
                Text(
                    "Toque em um dia para ver detalhes e variação:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                val maxDayPoints = maxOf(30, weeklyHistory.maxOfOrNull { it.points } ?: 30)

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            weeklyHistory.forEachIndexed { index, day ->
                                val isSelected = selectedIndex == index
                                val barFraction = (day.points.toFloat() / maxDayPoints).coerceIn(0.04f, 1f)
                                val barColor = when {
                                    isSelected -> TealPrimary
                                    day.isToday -> TealPrimary.copy(alpha = 0.85f)
                                    day.isGoalMet -> Color(0xFF10B981)
                                    day.points > 0 -> TealPrimary.copy(alpha = 0.5f)
                                    else -> Color.LightGray.copy(alpha = 0.3f)
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { selectedIndex = index }
                                        .padding(horizontal = 2.dp)
                                ) {
                                    if (day.points > 0) {
                                        Text(
                                            text = "${day.points}",
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) TealPrimary else Color.Gray
                                        )
                                        Spacer(Modifier.height(2.dp))
                                    }

                                    val maxBarHeight = 75.dp
                                    val barHeight = (maxBarHeight * barFraction).coerceAtLeast(6.dp)

                                    Box(
                                        modifier = Modifier
                                            .width(if (isSelected) 18.dp else 14.dp)
                                            .height(barHeight)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(barColor)
                                            .then(
                                                if (isSelected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                                else Modifier
                                            )
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = day.dayLabel,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected || day.isToday) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected || day.isToday) TealPrimary else Color.Gray
                                    )
                                    Text(
                                        text = day.dateFormatted,
                                        fontSize = 14.sp,
                                        color = if (isSelected) TealPrimary else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Detalhes Minimalistas do Dia Selecionado
                selectedIndex?.let { idx ->
                    if (idx in weeklyHistory.indices) {
                        val day = weeklyHistory[idx]
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, TealPrimary.copy(alpha = 0.25f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${day.fullDayName} (${day.dateFormatted})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${day.points} pts",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TealPrimary
                                    )
                                }

                                val dailyTarget = 22
                                val pctOfDaily = ((day.points.toFloat() / dailyTarget) * 100).toInt()
                                Text(
                                    text = if (day.isFuture) "Dia ainda não iniciado" else "$pctOfDaily% da meta diária da OMS (22 pts/dia)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (day.isGoalMet) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (!day.isFuture && idx > 0) {
                                    val previousDay = weeklyHistory[idx - 1]
                                    if (!previousDay.isFuture) {
                                        val diff = day.points - previousDay.points
                                        val isPositive = diff >= 0
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                if (isPositive) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                                contentDescription = null,
                                                tint = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (isPositive) "+$diff pts em relação ao dia anterior" else "$diff pts em relação ao dia anterior",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isPositive) Color(0xFF10B981) else Color(0xFFEF4444)
                                            )
                                        }
                                    }
                                } else if (!day.isFuture && idx == 0) {
                                    Text(
                                        text = "Primeiro dia da semana (Segunda-feira)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
