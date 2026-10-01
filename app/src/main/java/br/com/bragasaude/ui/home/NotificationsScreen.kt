package br.com.bragasaude.ui.home

import android.content.Context
import android.content.ContextWrapper
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
    val unreadCount = alerts.count { it !in readAlerts }

    Scaffold(
        containerColor = BragaBackground,
        topBar = { EmeraldHeaderBanner(title = "Notificações", subtitle = "Seus avisos de cuidado", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!BatteryOptimizationHelper.estaLiberado(context)) {
                item {
                    Card(
                        onClick = { BatteryOptimizationHelper.pedirIgnorarOtimizacao(context) },
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.NotificationsActive, contentDescription = null)
                            Text("Receba os avisos com o app fechado", style = MaterialTheme.typography.titleSmall)
                            Text("Toque para permitir que o Braga Saúde continue funcionando em segundo plano. Nas configurações de bateria, escolha \"Não otimizado\".",
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (alerts.isEmpty()) {
                item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.NotificationsNone, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                        Text("Tudo tranquilo por aqui", style = MaterialTheme.typography.titleLarge)
                        Text("Nenhum aviso pendente.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (unreadCount > 0) "$unreadCount avisos para ler" else "Seus avisos estão em dia",
                            style = MaterialTheme.typography.titleMedium)
                        Text("Toque para ler. Os avisos ficam aqui até você dispensá-los.", style = MaterialTheme.typography.bodyMedium)
                        if (unreadCount > 0) {
                            TextButton(onClick = viewModel::markAlertsAsRead, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("Marcar todos como lidos", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                items(alerts, key = { it }) { alert ->
                    NotificationCard(
                        message = alert.removePrefix("PERMISSION_NOTIFICATIONS:").removePrefix("PERMISSION_REQUIRED:").trim(),
                        category = alertCategory(alert),
                        unread = alert !in readAlerts,
                        identifiedAt = identifiedAt[alert],
                        onOpen = {
                            viewModel.markAlertAsRead(alert)
                            selectedAlert = alert
                        },
                        onDismiss = { viewModel.dismissAlert(alert) }
                    )
                }
            }
        }
    }
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
