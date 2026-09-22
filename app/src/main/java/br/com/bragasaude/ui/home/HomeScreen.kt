package br.com.bragasaude.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.R
import br.com.bragasaude.ui.components.RedCrossIcon
import br.com.bragasaude.ui.family.FamilyNotesCard
import br.com.bragasaude.ui.components.HeartEcgIcon
import br.com.bragasaude.ui.theme.Success
import br.com.bragasaude.ui.theme.Warning
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.components.BragaMetricCard
import br.com.bragasaude.ui.components.BragaEmergencyBannerCard
import br.com.bragasaude.ui.components.RiskNotificationDialog
import br.com.bragasaude.ui.theme.BragaBackground
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary

private val GrayStatus = Color.LightGray

/**
 * Retorna saudação baseada no horário atual.
 */
fun getGreetingPrefix(calendar: java.util.Calendar = java.util.Calendar.getInstance()): String {
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Bom dia,"
        in 12..17 -> "Boa tarde,"
        else -> "Boa noite,"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToNotifications: () -> Unit,
    onNavigateToScreen: (br.com.bragasaude.ui.util.Screen) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val userName by viewModel.userName.collectAsState()
    val healthScore by viewModel.healthScore.collectAsState()
    val vitals by viewModel.dashboardVitals.collectAsState()
    val milestone by viewModel.latestMilestone.collectAsState()
    val clinicalAlerts by viewModel.clinicalAlerts.collectAsState()
    val scoreBreakdown by viewModel.scoreBreakdown.collectAsState()
    val userStepGoal by viewModel.userStepGoal.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val medications by viewModel.medications.collectAsState()
    val heartReadings by viewModel.heartReadings.collectAsState()
    val oxygenReadings by viewModel.oxygenReadings.collectAsState()
    
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    
    var showScoreDetails by remember { mutableStateOf(false) }
    var showEmergencyDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val useGooglePhoto = remember { br.com.bragasaude.util.AppPreferences.isUseGooglePhotoEnabled(context) }
    val avatarPhotoUrl = if (useGooglePhoto) com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.photoUrl?.toString() else null

    Scaffold(
        topBar = {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "alpha"
            )

            EmeraldHeaderBanner(
                greetingPrefix = getGreetingPrefix(),
                title = userName.ifBlank { "Meu perfil" },
                avatarInitials = userName.ifBlank { "BS" },
                photoUrl = avatarPhotoUrl,
                onAvatarClick = { onNavigateToScreen(br.com.bragasaude.ui.util.Screen.Profile) },
                trailingContent = {
                    IconButton(onClick = onNavigateToNotifications) {
                        BadgedBox(
                            badge = {
                                if (clinicalAlerts.isNotEmpty()) {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.offset(x = (-4).dp, y = 4.dp)
                                    )
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "Notificações",
                                tint = Color.White,
                                modifier = if (clinicalAlerts.isNotEmpty()) Modifier.graphicsLayer(alpha = alpha) else Modifier
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshData() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }

                // 1. Atalhos Rápidos de Navegação — topo visível
                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val isHybridCaregiver = profile?.userRole == "CAREGIVER" && profile?.caregiverMode == "HYBRID"
                        val items = buildList {
                            add(Triple("Hidratação", Icons.Default.WaterDrop, br.com.bragasaude.ui.util.Screen.Hydration()))
                            add(Triple("Pressão", Icons.Default.Favorite, br.com.bragasaude.ui.util.Screen.Vitals("PRESSURE")))
                            if (profile?.hasDiabetes == true || vitals.glucoseLevel != null) {
                                add(Triple("Glicose", Icons.Default.Bloodtype, br.com.bragasaude.ui.util.Screen.Vitals("GLUCOSE")))
                            }
                            if (isHybridCaregiver) {
                                add(Triple("Dados", Icons.Default.BarChart, br.com.bragasaude.ui.util.Screen.Report))
                            }
                            add(Triple("Mural", Icons.Default.Groups, br.com.bragasaude.ui.util.Screen.SocialFeed))
                            add(Triple("Exames", Icons.Default.Assignment, br.com.bragasaude.ui.util.Screen.Exams))
                            if (medications.isNotEmpty()) {
                                add(Triple("Remédios", Icons.Default.Medication, br.com.bragasaude.ui.util.Screen.Reminders))
                            }
                        }
                        
                        items(items.size) { index ->
                            val (label, icon, route) = items[index]
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clickable { onNavigateToScreen(route) }
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = BragaCardSurface,
                                    border = BorderStroke(1.dp, BragaCardBorder),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(icon, contentDescription = label, tint = BragaEmerald)
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(label, style = MaterialTheme.typography.labelSmall, color = BragaTextPrimary)
                            }
                        }
                    }
                }

                // 2. Métricas em Destaque: Passos e Hidratação Lado a Lado (Wireframe 110935.png)
                item {
                    val stps = vitals.steps ?: 0
                    val hyd = vitals.hydrationMl ?: 0
                    val cups = (hyd / 250).coerceAtLeast(0)
                    val targetMl = profile?.hydrationTargetMl?.takeIf { it > 0 }
                        ?: ((profile?.weight ?: 0.0) * 35).toInt().takeIf { it > 0 }
                        ?: 2000
                    val targetCups = kotlin.math.ceil(targetMl / 250.0).toInt()

                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BragaMetricCard(
                            value = if (stps > 0) "%,d".format(stps) else "0",
                            label = "passos",
                            unit = "meta %,d".format(userStepGoal),
                            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onNavigateToScreen(br.com.bragasaude.ui.util.Screen.Steps) }
                        )
                        BragaMetricCard(
                            value = "$cups/$targetCups",
                            label = "copos",
                            unit = "$hyd de $targetMl ml",
                            icon = Icons.Default.WaterDrop,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onNavigateToScreen(br.com.bragasaude.ui.util.Screen.Hydration()) }
                        )
                    }
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BragaMetricCard(
                            label = "Batimentos",
                            value = heartReadings.firstOrNull()?.value?.toInt()?.toString() ?: "—",
                            unit = if (heartReadings.isEmpty()) "Registrar medição" else "bpm • " + java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(heartReadings.first().measuredAt)),
                            iconPainter = painterResource(R.drawable.ic_shield_ecg),
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onNavigateToScreen(br.com.bragasaude.ui.util.Screen.HealthReadings("HEART_RATE")) }
                        )
                        BragaMetricCard(
                            label = "Oxigenação",
                            value = oxygenReadings.firstOrNull()?.value?.let { String.format(java.util.Locale.getDefault(), "%.1f", it) } ?: "—",
                            unit = if (oxygenReadings.isEmpty()) "Registrar medição" else "% • " + java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.getDefault()).format(java.util.Date(oxygenReadings.first().measuredAt)),
                            icon = Icons.Default.Bloodtype,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onNavigateToScreen(br.com.bragasaude.ui.util.Screen.HealthReadings("OXYGEN_SATURATION")) }
                        )
                    }
                }

                // Só mostra medicação que realmente pertence ao usuário.
                if (medications.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToScreen(br.com.bragasaude.ui.util.Screen.Reminders) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                        border = BorderStroke(1.dp, BragaCardBorder),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                text = "Seus remédios",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = BragaTextSecondary
                            )
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = BragaMint,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Medication,
                                            contentDescription = null,
                                            tint = BragaEmerald,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = if (medications.size == 1) medications.first().name else "${medications.size} remédios cadastrados",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BragaTextPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Ver horários e registros", style = MaterialTheme.typography.bodyMedium, color = BragaEmerald)
                        }
                    }
                }
                }

                // 4. Sinais Vitais (Pressão Arterial e Glicemia - Wireframe 110827.png)
                item {
                    val sys = vitals.systolicPressure
                    val dia = vitals.diastolicPressure
                    val gluc = vitals.glucoseLevel

                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        BragaMetricCard(
                            value = if (sys != null && dia != null) "$sys/$dia" else "--",
                            label = "Pressão",
                            unit = "mmHg",
                            icon = Icons.Default.Favorite,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                            onClick = { onNavigateToScreen(br.com.bragasaude.ui.util.Screen.Vitals("PRESSURE")) }
                        )
                        if (profile?.hasDiabetes == true || gluc != null) {
                            BragaMetricCard(
                                value = gluc?.toString() ?: "--",
                                label = "Glicemia",
                                unit = "mg/dL",
                                icon = Icons.Default.Bloodtype,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { onNavigateToScreen(br.com.bragasaude.ui.util.Screen.Vitals("GLUCOSE")) }
                            )
                        } else {
                            BragaMetricCard(
                                value = "$healthScore pts",
                                label = "Score Saúde",
                                unit = "Classificação geral",
                                icon = Icons.Default.EmojiEvents,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { showScoreDetails = true }
                            )
                        }
                    }
                }

                // 5. Recados da Família — Ponte Familiar & Modo Cuidador (card acolhedor e interativo)
                item {
                    FamilyNotesCard(
                        onSeeAll = { onNavigateToScreen(br.com.bragasaude.ui.util.Screen.FamilyChat) },
                        onOpenChat = { onNavigateToScreen(br.com.bragasaude.ui.util.Screen.FamilyChat) },
                        onManageFamily = { onNavigateToScreen(br.com.bragasaude.ui.util.Screen.FamilyConnect) }
                    )
                }

                // 7. Milestone Card
                milestone?.let {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = BragaMint),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, BragaMint.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.EmojiEvents,
                                    contentDescription = "Conquista",
                                    tint = BragaEmerald,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(
                                        "Conquista",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = BragaEmerald
                                    )
                                    Text(
                                        it.description ?: "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = BragaTextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                // Emergência SOS — movida para a parte inferior (após lembretes e sinais do dia)
                item {
                    BragaEmergencyBannerCard(
                        onClick = { showEmergencyDialog = true }
                    )
                }

                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }

    if (showEmergencyDialog) {
        RiskNotificationDialog(
            type = "EMERGENCIA",
            message = "Central de Emergência e Apoio",
            onDismiss = { showEmergencyDialog = false }
        )
    }

    if (showScoreDetails) {
        ScoreDetailsDialog(
            breakdown = scoreBreakdown,
            onDismiss = { showScoreDetails = false }
        )
    }
}

@Composable
fun ScoreDetailsDialog(
    breakdown: br.com.bragasaude.domain.ScoreBreakdown,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Por que minha nota é ${breakdown.finalScore}?", fontWeight = FontWeight.Bold) },
        text = {
            // DECISOES.md (D4): disclaimer inseparável da interpretação de dados.
            val context = LocalContext.current
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (breakdown.positiveFactors.isNotEmpty()) {
                    Text("Pontos Positivos", style = MaterialTheme.typography.labelLarge, color = Success)
                    breakdown.positiveFactors.forEach { factor ->
                        Text("• $factor", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (breakdown.negativeFactors.isNotEmpty()) {
                    Text("Oportunidades de Melhoria", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
                    breakdown.negativeFactors.forEach { factor ->
                        Text("• $factor", style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (breakdown.positiveFactors.isEmpty() && breakdown.negativeFactors.isEmpty()) {
                    Text("Adicione mais dados para um detalhamento completo.")
                }

                Text(
                    context.getString(R.string.disclaimer_not_medical),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Entendido") }
        }
    )
}

@Composable
fun AlertCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RedCrossIcon(
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.width(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun IndicatorCard(
    label: String,
    value: String,
    unit: String,
    statusColor: Color,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = if (onClick != null) modifier.clickable { onClick() } else modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(0.5.dp, Color.LightGray.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}
