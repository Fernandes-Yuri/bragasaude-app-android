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

fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, viewModel: HomeViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val mainViewModel: MainViewModel = hiltViewModel(context.findActivity()!!)
    val alerts by viewModel.clinicalAlerts.collectAsState()
    val readAlerts by viewModel.readAlerts.collectAsState()
    val identifiedAt by viewModel.alertIdentifiedAt.collectAsState()
    var selectedAlert by rememberSaveable { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var batteryAllowed by remember(context) { mutableStateOf(BatteryOptimizationHelper.estaLiberado(context)) }
    var batteryGuidance by rememberSaveable { mutableStateOf<String?>(null) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                batteryAllowed = BatteryOptimizationHelper.estaLiberado(context)
                if (batteryAllowed) batteryGuidance = null
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
        onOpen = { alert -> viewModel.markAlertAsRead(alert); selectedAlert = alert },
        onDismiss = viewModel::dismissAlert
    )
    selectedAlert?.let { alert ->
        val isNotificationPermission = alert.startsWith("PERMISSION_NOTIFICATIONS:")
        val isMonitoringPermission = alert.startsWith("PERMISSION_REQUIRED:")
        BragaFormSheet(
            onDismissRequest = { selectedAlert = null },
            title = { Text(alertCategory(alert)) },
            text = { Text(alert.removePrefix("PERMISSION_NOTIFICATIONS:").removePrefix("PERMISSION_REQUIRED:").trim()) },
            confirmButton = {
                Button(onClick = {
                    if (isNotificationPermission) mainViewModel.triggerNotificationPrompt()
                    if (isMonitoringPermission) mainViewModel.requestPermissions()
                    selectedAlert = null
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text(if (isNotificationPermission || isMonitoringPermission) "Configurar permissões" else "Voltar aos avisos",
                        style = MaterialTheme.typography.bodyMedium)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAlert(alert); selectedAlert = null }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("Dispensar aviso", style = MaterialTheme.typography.bodyMedium)
                }
            }
        )
    }
}

@Composable
internal fun NotificationsContent(
    alerts: List<String>, readAlerts: Set<String>, identifiedAt: Map<String, Long>,
    batteryAllowed: Boolean, batteryGuidance: String?, onBack: () -> Unit,
    onBatterySettings: () -> Unit, onMarkAllRead: () -> Unit,
    onOpen: (String) -> Unit, onDismiss: (String) -> Unit
) {
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
                    NotificationCard(alert.removePrefix("PERMISSION_NOTIFICATIONS:").removePrefix("PERMISSION_REQUIRED:").trim(),
                        alertCategory(alert), true, identifiedAt[alert], { onOpen(alert) }, { onDismiss(alert) })
                }
            }
            if (previous.isNotEmpty()) {
                item(key = "readHeading") {
                    Text(if (unread.isEmpty()) "Histórico de avisos" else "Lidas (${previous.size})",
                        style = MaterialTheme.typography.titleMedium)
                }
                items(previous, key = { it }) { alert ->
                    NotificationCard(alert.removePrefix("PERMISSION_NOTIFICATIONS:").removePrefix("PERMISSION_REQUIRED:").trim(),
                        alertCategory(alert), false, identifiedAt[alert], { onOpen(alert) }, { onDismiss(alert) })
                }
            }
        }
    }
}

private fun alertCategory(alert: String): String = when {
    alert.startsWith("PERMISSION_") -> "Configurações"
    alert.contains("água", ignoreCase = true) || alert.contains("hidrat", ignoreCase = true) -> "Hidratação"
    alert.contains("passos", ignoreCase = true) || alert.contains("atividade", ignoreCase = true) -> "Atividade"
    alert.contains("remédio", ignoreCase = true) || alert.contains("medica", ignoreCase = true) -> "Medicamentos"
    else -> "Saúde e cuidado"
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
