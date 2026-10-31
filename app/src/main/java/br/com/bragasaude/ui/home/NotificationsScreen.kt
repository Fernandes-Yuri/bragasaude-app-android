package br.com.bragasaude.ui.home

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ui.MainViewModel
import br.com.bragasaude.ui.components.BragaFormSheet
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.theme.BragaBackground
import br.com.bragasaude.ui.util.BatteryOptimizationHelper
import br.com.bragasaude.ui.util.Screen

fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onNavigateToScreen: (Screen) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val mainViewModel: MainViewModel = hiltViewModel(context.findActivity()!!)
    val alerts by viewModel.clinicalAlerts.collectAsState()
    val readAlerts by viewModel.readAlerts.collectAsState()
    val identifiedAt by viewModel.alertIdentifiedAt.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var batteryAllowed by remember(context) { mutableStateOf(BatteryOptimizationHelper.estaLiberado(context)) }
    var batteryGuidance by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(mainViewModel) {
        mainViewModel.permissionUpdatedSignal.collect {
            viewModel.syncGrantedPermissions(context)
        }
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryAllowed = BatteryOptimizationHelper.estaLiberado(context)
                if (batteryAllowed) batteryGuidance = null
                viewModel.syncGrantedPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val openBatterySettings: () -> Unit = {
        val result = BatteryOptimizationHelper.pedirIgnorarOtimizacao(context)
        batteryGuidance = when (result) {
            BatteryOptimizationHelper.ResultadoLiberacao.CONFIG_APP_ABERTA ->
                "Nas configurações do Braga Saúde, abra Bateria e escolha Sem restrições, Irrestrito ou Não otimizado. O nome varia conforme o aparelho."
            BatteryOptimizationHelper.ResultadoLiberacao.INDISPONIVEL ->
                "Não foi possível abrir essa tela. Abra Configurações > Aplicativos > Braga Saúde > Bateria para permitir o uso sem restrições."
            else -> null
        }
        if (result == BatteryOptimizationHelper.ResultadoLiberacao.JA_LIBERADO) batteryAllowed = true
        batteryGuidance?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    NotificationsContent(
        alerts = alerts, readAlerts = readAlerts, identifiedAt = identifiedAt,
        batteryAllowed = batteryAllowed, batteryGuidance = batteryGuidance,
        onBack = onBack, onBatterySettings = openBatterySettings,
        onMarkAllRead = viewModel::markAlertsAsRead,
        onOpen = viewModel::markAlertAsRead,
        onDismiss = viewModel::dismissAlert,
        onNotificationPermission = mainViewModel::triggerNotificationPrompt,
        onMonitoringPermission = mainViewModel::requestPermissions,
        onNavigateToScreen = onNavigateToScreen
    )
}

@Composable
internal fun NotificationsContent(
    alerts: List<String>, readAlerts: Set<String>, identifiedAt: Map<String, Long>,
    batteryAllowed: Boolean, batteryGuidance: String?, onBack: () -> Unit,
    onBatterySettings: () -> Unit, onMarkAllRead: () -> Unit,
    onOpen: (String) -> Unit, onDismiss: (String) -> Unit,
    onNotificationPermission: () -> Unit = {},
    onMonitoringPermission: () -> Unit = {},
    onNavigateToScreen: (Screen) -> Unit = {}
) {
    var selectedAlert by rememberSaveable { mutableStateOf<String?>(null) }
    val openAlert: (String) -> Unit = { alert ->
        onOpen(alert)
        selectedAlert = alert
    }
    var batteryExpanded by rememberSaveable { mutableStateOf(false) }
    var batteryDismissed by rememberSaveable { mutableStateOf(false) }
    val unread = alerts.filter { it !in readAlerts }
    val previous = alerts.filter { it in readAlerts }
    Scaffold(containerColor = BragaBackground,
        topBar = { EmeraldHeaderBanner(title = "Notificações", subtitle = "Seus avisos de cuidado", onBack = onBack) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!batteryAllowed && !batteryDismissed) item(key = "batteryGuidance") {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.BatteryStd, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Avisos em segundo plano", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { batteryExpanded = !batteryExpanded }) {
                                Icon(if (batteryExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (batteryExpanded) "Recolher orientação de bateria" else "Ver orientação de bateria")
                            }
                            IconButton(onClick = { batteryDismissed = true }) {
                                Icon(Icons.Default.Close, contentDescription = "Dispensar orientação de bateria")
                            }
                        }
                        if (batteryExpanded) {
                            Text(batteryGuidance ?: "O Android pode limitar os avisos com o app fechado. Revise a bateria se os lembretes atrasarem.",
                                style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onBatterySettings) { Text("Configurar bateria") }
                        }
                    }
                }
            }
            if (alerts.isEmpty()) item(key = "empty") {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.NotificationsNone, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                    Text("Tudo tranquilo por aqui", style = MaterialTheme.typography.titleLarge)
                    Text("Nenhum aviso no momento.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (unread.isNotEmpty()) {
                item(key = "unreadHeading") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Novas (${unread.size})", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onMarkAllRead) { Text("Marcar todas como lidas") }
                    }
                }
                items(unread, key = { it }) { alert ->
                    NotificationCard(alertDisplayText(alert).substringBefore("\n\n"),
                        alertCategory(alert), true, identifiedAt[alert], { openAlert(alert) }, { onDismiss(alert) })
                }
            }
            if (previous.isNotEmpty()) {
                item(key = "readHeading") {
                    Text(if (unread.isEmpty()) "Histórico de avisos" else "Lidas (${previous.size})",
                        style = MaterialTheme.typography.titleMedium)
                }
                items(previous, key = { it }) { alert ->
                    NotificationCard(alertDisplayText(alert).substringBefore("\n\n"),
                        alertCategory(alert), false, identifiedAt[alert], { openAlert(alert) }, { onDismiss(alert) })
                }
            }
        }
    }
    selectedAlert?.let { alert ->
        NotificationDetailsSheet(
            alert = alert,
            onClose = { selectedAlert = null },
            onDismiss = { onDismiss(alert); selectedAlert = null },
            onNotificationPermission = onNotificationPermission,
            onMonitoringPermission = onMonitoringPermission,
            onNavigateToScreen = onNavigateToScreen
        )
    }
}

@Composable
private fun NotificationDetailsSheet(
    alert: String,
    onClose: () -> Unit,
    onDismiss: () -> Unit,
    onNotificationPermission: () -> Unit,
    onMonitoringPermission: () -> Unit,
    onNavigateToScreen: (Screen) -> Unit
) {
    val isNotificationPermission = alert.startsWith("PERMISSION_NOTIFICATIONS:")
    val isMonitoringPermission = alert.startsWith("PERMISSION_REQUIRED:")
    val category = alertCategory(alert)
    // O primeiro parágrafo é o título; os demais preservam a orientação completa.
    val paragraphs = alertDisplayText(alert).split("\n\n", limit = 2)
    val title = when {
        isNotificationPermission || isMonitoringPermission -> paragraphs.first()
        paragraphs.size == 2 -> paragraphs.first()
        else -> null
    }
    val body = when {
        isNotificationPermission ->
            "Permita as notificações para receber lembretes e avisos de saúde mesmo com o aplicativo fechado. Toque em Configurar permissões para autorizar no Android."
        isMonitoringPermission ->
            "Permita o acesso à atividade física e à localização para acompanhar seus passos e caminhadas e atualizar seu Score. Toque em Configurar permissões para revisar os acessos no Android."
        else -> paragraphs.getOrElse(1) { paragraphs.first() }
    }
    val destination = when (category) {
        "Hidratação" -> Screen.Hydration(autoOpenDialog = true)
        "Atividade" -> Screen.Steps
        "Medicamentos" -> Screen.Reminders
        else -> null
    }
    val hasAction = isNotificationPermission || isMonitoringPermission || destination != null
    val actionLabel = when {
        isNotificationPermission || isMonitoringPermission -> "Configurar permissões"
        category == "Hidratação" -> "Registrar água"
        category == "Atividade" -> "Ver passos"
        category == "Medicamentos" -> "Ver medicamentos"
        else -> "Voltar aos avisos"
    }
    BragaFormSheet(
        onDismissRequest = onClose,
        title = { Text(category) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                title?.let {
                    Text(it, modifier = Modifier.testTag("notificationAlertTitle"),
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                }
                Text(body, modifier = Modifier.testTag("notificationAlertBody"),
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        confirmButton = {
            Column {
                Button(
                    onClick = {
                        when {
                            isNotificationPermission -> onNotificationPermission()
                            isMonitoringPermission -> onMonitoringPermission()
                            destination != null -> onNavigateToScreen(destination)
                        }
                        onClose()
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.bodyMedium)
                }
                if (hasAction) {
                    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                        Text("Voltar aos avisos", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("Dispensar aviso", style = MaterialTheme.typography.bodyMedium)
            }
        }
    )
}

private fun alertDisplayText(alert: String): String =
    alert.removePrefix("PERMISSION_NOTIFICATIONS:").removePrefix("PERMISSION_REQUIRED:").trim()

private fun alertCategory(alert: String): String {
    // A orientação pode mencionar outros hábitos; a categoria acompanha o título do aviso.
    val title = alert.substringBefore("\n\n")
    return when {
        alert.startsWith("PERMISSION_") -> "Configurações"
        listOf("água", "hidrat").any { title.contains(it, ignoreCase = true) } -> "Hidratação"
        listOf("passos", "atividade", "movimento", "caminhada", "sedentar").any { title.contains(it, ignoreCase = true) } -> "Atividade"
        listOf("remédio", "medica").any { title.contains(it, ignoreCase = true) } -> "Medicamentos"
        else -> "Saúde e cuidado"
    }
}

@Composable
private fun NotificationCard(message: String, category: String, unread: Boolean, identifiedAt: Long?, onOpen: () -> Unit, onDismiss: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                    Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                        Icon(when (category) {
                            "Configurações" -> Icons.Default.Settings
                            "Hidratação" -> Icons.Default.WaterDrop
                            "Atividade" -> Icons.Default.DirectionsWalk
                            "Medicamentos" -> Icons.Default.Medication
                            else -> Icons.Default.Favorite
                        }, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(category, style = MaterialTheme.typography.titleSmall)
                    Text(if (unread) "Não lido" else "Lido", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dispensar aviso")
                }
            }
            Text(message, style = MaterialTheme.typography.bodyMedium)
            identifiedAt?.let { timestamp ->
                val time = java.text.SimpleDateFormat("dd/MM 'às' HH:mm", java.util.Locale.forLanguageTag("pt-BR"))
                    .format(java.util.Date(timestamp))
                Text("Identificado em $time", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Ler aviso", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        }
    }
}
