package br.com.bragasaude.ui.devices

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.bragasaude.data.local.WearableReading
import br.com.bragasaude.domain.HealthReadingInput
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun WearablesScreen(onBack: () -> Unit, viewModel: WearablesViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val availability by viewModel.availability.collectAsState()
    val granted by viewModel.granted.collectAsState()
    val sync by viewModel.syncState.collectAsState()
    val error by viewModel.error.collectAsState()
    val heart by viewModel.heartReadings.collectAsState()
    val oxygen by viewModel.oxygenReadings.collectAsState()
    var oxygenTab by rememberSaveable { mutableStateOf(false) }
    var showGuide by rememberSaveable { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }
    val manager = viewModel.manager
    val available = availability == HealthConnectClient.SDK_AVAILABLE
    val permissionLauncher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        viewModel.refresh()
        if (it.intersect(manager.permissions).isNotEmpty()) viewModel.sync()
    }
    fun authorize() {
        try { permissionLauncher.launch(manager.permissions) } catch (_: Exception) {
            Toast.makeText(context, "Abra as permissões no Health Connect e tente novamente.", Toast.LENGTH_LONG).show()
        }
    }
    fun open(intent: Intent) {
        try { context.startActivity(intent) } catch (_: Exception) {
            Toast.makeText(context, "Procure Health Connect nas configurações do Android ou na Play Store.", Toast.LENGTH_LONG).show()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh() }
        lifecycle.addObserver(observer)
        viewModel.refresh()
        onDispose { lifecycle.removeObserver(observer) }
    }
    Scaffold(containerColor = BragaBackground, topBar = { EmeraldHeaderBanner(title = "Relógios e saúde", subtitle = "Suas leituras, sem complicação", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = BragaCardSurface), border = BorderStroke(1.dp, BragaCardBorder)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Surface(shape = RoundedCornerShape(16.dp), color = BragaMint) {
                                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) { Icon(Icons.Default.Watch, null, tint = BragaEmerald, modifier = Modifier.size(30.dp)) }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(when { sync.running -> "Buscando suas leituras"; !available -> "Prepare a conexão"; granted.isEmpty() -> "Conecte os dados do relógio"; else -> "Acesso aos dados autorizado" },
                                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Text("Via Health Connect", style = MaterialTheme.typography.bodyMedium, color = BragaTextSecondary)
                            }
                        }
                        Text(when {
                            !available -> "Este celular precisa de um Health Connect compatível para importar dados."
                            granted.isEmpty() -> "Autorize os dados que deseja trazer para o Braga Saúde."
                            else -> "Abra o aplicativo do relógio para enviar as medições e depois sincronize aqui."
                        }, style = MaterialTheme.typography.bodyLarge, color = BragaTextSecondary)
                        Button(enabled = !sync.running, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(14.dp), onClick = {
                            when (availability) {
                                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> open(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")))
                                HealthConnectClient.SDK_UNAVAILABLE -> viewModel.refresh()
                                else -> if (granted.isEmpty()) authorize() else viewModel.sync()
                            }
                        }) {
                            if (sync.running) { CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                            Text(when {
                                sync.running -> "Sincronizando…"
                                availability == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Instalar ou atualizar"
                                !available -> "Verificar novamente"
                                granted.isEmpty() -> "Autorizar meus dados"
                                else -> "Sincronizar agora"
                            })
                        }
                        if (sync.running || granted.isNotEmpty()) Text(sync.message, style = MaterialTheme.typography.bodyMedium, color = BragaTextSecondary)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        TextButton(onClick = { showGuide = !showGuide }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Icon(Icons.Default.HelpOutline, null); Spacer(Modifier.width(8.dp)); Text(if (showGuide) "Ocultar orientações" else "Como conectar meu relógio?")
                        }
                        if (showGuide) {
                            Text("1. Pareie o relógio no aplicativo do fabricante.\n\n2. Nesse aplicativo, habilite o compartilhamento com Health Connect.\n\n3. Autorize o Braga Saúde e sincronize.", style = MaterialTheme.typography.bodyLarge)
                            Text("Funciona com os aparelhos cujos aplicativos compartilham esses dados. Só autorizar não significa que já existem leituras.", color = BragaTextSecondary)
                        }
                    }
                }
            }
            if (available) item {
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BragaCardSurface), border = BorderStroke(1.dp, BragaCardBorder)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Dados que você permite", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        listOf("Passos" to manager.stepsPermission, "Batimentos" to manager.heartPermission, "Oxigenação (SpO₂)" to manager.oxygenPermission).forEach { (name, permission) ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(if (permission in granted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null, tint = if (permission in granted) BragaEmerald else BragaTextSecondary)
                                Text(name, modifier = Modifier.weight(1f))
                                Text(if (permission in granted) "Permitido" else "Não permitido", style = MaterialTheme.typography.labelMedium, color = BragaTextSecondary)
                            }
                        }
                        TextButton(onClick = { authorize() }, enabled = !sync.running) { Text("Alterar permissões") }
                    }
                }
            }
            item {
                Text("Últimas leituras", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ReadingSummary("Frequência cardíaca", heart.firstOrNull())
                ReadingSummary("Oxigenação (SpO₂)", oxygen.firstOrNull())
            }
            if (heart.isNotEmpty() || oxygen.isNotEmpty()) {
                item { TextButton(onClick = { showHistory = !showHistory }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (showHistory) "Recolher histórico" else "Ver histórico de leituras") } }
                if (showHistory) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !oxygenTab, onClick = { oxygenTab = false }, label = { Text("Batimentos") })
                            FilterChip(selected = oxygenTab, onClick = { oxygenTab = true }, label = { Text("SpO₂") })
                        }
                        if ((if (oxygenTab) oxygen else heart).isEmpty()) Text("Ainda não há leituras deste tipo.")
                    }
                    items(if (oxygenTab) oxygen else heart, key = { it.recordKey }) { ReadingSummary(if (oxygenTab) "SpO₂" else "Batimentos", it) }
                }
            }
            item {
                TextButton(onClick = { showPrivacy = !showPrivacy }) { Text(if (showPrivacy) "Ocultar detalhes" else "Sobre seus dados") }
                if (showPrivacy) {
                    Text("As leituras ficam salvas neste celular, na sua conta. O relatório inclui os últimos 30 dias. Dados dependem do sensor e do compartilhamento do fabricante. Desconectar mantém o histórico já salvo.", color = BragaTextSecondary)
                    if (available) OutlinedButton(onClick = {
                        open(Intent(if (Build.VERSION.SDK_INT >= 34) "android.health.connect.action.HEALTH_HOME_SETTINGS" else "androidx.health.ACTION_HEALTH_CONNECT_SETTINGS"))
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Gerenciar conexão no Android") }
                }
            }
        }
    }
}

@Composable
internal fun ReadingSummary(label: String, reading: WearableReading?) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(top = 10.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BragaCardSurface), border = BorderStroke(1.dp, BragaCardBorder)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.titleMedium, color = BragaTextSecondary)
            if (reading == null) {
                Text("Nenhuma leitura ainda", style = MaterialTheme.typography.titleMedium)
                Text("Registre uma medição no início ou sincronize seu dispositivo.", style = MaterialTheme.typography.bodyMedium, color = BragaTextSecondary)
            } else {
                Text(if (reading.metric == "HEART_RATE") reading.value.toInt().toString() + " bpm" else String.format(Locale.getDefault(), "%.1f %%", reading.value),
                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = BragaEmerald)
                Text(SimpleDateFormat("dd/MM/yyyy • HH:mm", Locale.getDefault()).format(Date(reading.measuredAt)), style = MaterialTheme.typography.bodyMedium)
                val source = remember(reading.sourcePackage) {
                    if (reading.sourcePackage.startsWith("braga.")) HealthReadingInput.origin(reading.sourcePackage)
                    else runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(reading.sourcePackage, 0)).toString() }.getOrDefault(reading.sourcePackage)
                }
                Text(source + (reading.deviceModel?.let { " • " + it } ?: ""), style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary)
            }
        }
    }
}

