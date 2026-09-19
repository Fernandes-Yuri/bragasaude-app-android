package br.com.bragasaude.ui.home

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.MainViewModel

fun Context.findActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val mainViewModel: MainViewModel = hiltViewModel(context.findActivity()!!)
    val alerts by viewModel.clinicalAlerts.collectAsState()

    // Regra: Ao visualizar a tela, as notificações são marcadas como lidas ao sair ou após tempo
    DisposableEffect(Unit) {
        onDispose {
            viewModel.markAlertsAsRead()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notificações", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        if (alerts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhuma notificação no momento.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(alerts) { alert ->
                    when {
                        alert.startsWith("PERMISSION_NOTIFICATIONS:") -> {
                            val message = alert.removePrefix("PERMISSION_NOTIFICATIONS:").trim()
                            AlertCard(
                                message = message,
                                modifier = Modifier.clickable {
                                    // G4 (doc 10 §3.4): este aviso é de NOTIFICAÇÃO — sinal certo.
                                    mainViewModel.triggerNotificationPrompt()
                                    viewModel.dismissAlert(alert)
                                },
                                onDismiss = { viewModel.dismissAlert(alert) },
                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                        }
                        alert.startsWith("PERMISSION_REQUIRED:") -> {
                            val message = alert.removePrefix("PERMISSION_REQUIRED:").trim()
                            AlertCard(
                                message = message,
                                modifier = Modifier.clickable {
                                    mainViewModel.requestPermissions()
                                    viewModel.dismissAlert(alert)
                                },
                                onDismiss = { viewModel.dismissAlert(alert) },
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        }
                        else -> {
                            AlertCard(
                                message = alert,
                                modifier = Modifier.clickable {
                                    viewModel.dismissAlert(alert)
                                },
                                onDismiss = { viewModel.dismissAlert(alert) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertCard(
    message: String,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                    Icon(
                        androidx.compose.material.icons.Icons.Default.Close,
                        contentDescription = "Fechar",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
