package br.com.bragasaude.ui.devices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.domain.HealthReadingInput
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.components.BragaActionCard
import br.com.bragasaude.ui.theme.*
import br.com.bragasaude.ui.util.*

@Composable
fun HealthReadingsScreen(metric: String, initialValue: String?, onBack: () -> Unit, onConnectWatch: () -> Unit, viewModel: HealthReadingsViewModel = hiltViewModel()) {
    val heart by viewModel.heart.collectAsState()
    val oxygen by viewModel.oxygen.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val error by viewModel.error.collectAsState()
    val isHeart = metric == HealthReadingInput.HEART
    val title = if (isHeart) "Frequência cardíaca" else "Oxigenação"
    val unit = if (isHeart) "bpm" else "%"
    val readings = if (isHeart) heart else oxygen
    var value by rememberSaveable(metric, initialValue) { mutableStateOf(initialValue.orEmpty()) }
    var fromVoice by rememberSaveable(metric, initialValue) { mutableStateOf(initialValue != null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    var showLeave by rememberSaveable { mutableStateOf(false) }
    var confirm by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val leave = { if (value.isNotBlank()) showLeave = true else onBack() }
    androidx.activity.compose.BackHandler { leave() }
    if (showLeave) AlertDialog(onDismissRequest = { showLeave = false }, title = { Text("Descartar este valor?") },
        text = { Text("A leitura digitada ainda não foi salva.") },
        confirmButton = { TextButton(onClick = onBack, enabled = !saving) { Text("Descartar e voltar") } },
        dismissButton = { TextButton(onClick = { showLeave = false }) { Text("Continuar") } })
    if (confirm) AlertDialog(onDismissRequest = { if (!saving) confirm = false },
        title = { Text("Confirmar leitura") },
        text = { Column {
            Text(title + ": " + value + " " + unit + "\nRegistro com a data e hora de agora.")
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { TextButton(enabled = !saving, onClick = {
            viewModel.save(metric, value, fromVoice) { value = ""; fromVoice = false; confirm = false; notice = "Leitura salva no seu histórico." }
        }) { Text(if (saving) "Salvando…" else "Salvar leitura") } },
        dismissButton = { TextButton(enabled = !saving, onClick = { confirm = false }) { Text("Corrigir") } })
    Scaffold(containerColor = BragaBackground, topBar = { EmeraldHeaderBanner(title = title, subtitle = "Suas medições, no mesmo lugar", onBack = leave) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
            item {
                ReadingSummary("Última leitura", readings.firstOrNull())
            }
            item {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = BragaCardSurface), border = BorderStroke(1.dp, BragaCardBorder)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Registrar uma medição", style = MaterialTheme.typography.titleLarge)
                        Text("Meça no seu aparelho e informe o resultado.", color = BragaTextSecondary)
                        OutlinedTextField(value = value, onValueChange = { value = it; fromVoice = false; notice = null }, label = { Text("Valor medido") },
                            suffix = { Text(unit) }, modifier = Modifier.fillMaxWidth(), singleLine = true, enabled = !saving,
                            shape = RoundedCornerShape(12.dp), keyboardOptions = KeyboardOptions(keyboardType = if (isHeart) KeyboardType.Number else KeyboardType.Decimal),
                            supportingText = { Text(if (isHeart) "Exemplo: 72 bpm" else "Exemplo: 98%") })
                        Button(onClick = { confirm = true }, enabled = !saving && HealthReadingInput.value(metric, value) != null,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Conferir e salvar") }
                        notice?.let { Text(it, color = BragaEmerald) }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
            item { BragaActionCard("Usar meu relógio", "Importe as medições compartilhadas pelo seu dispositivo.", Icons.Default.Watch, onConnectWatch) }
            item {
                Text("Histórico recente", style = MaterialTheme.typography.titleLarge)
                Text("Data, hora e origem de cada leitura. Até 200 registros recentes.", color = BragaTextSecondary)
                if (readings.isEmpty()) Text("Suas leituras aparecerão aqui após o primeiro registro.", modifier = Modifier.padding(vertical = 16.dp))
            }
            items(readings, key = { it.recordKey }) { ReadingSummary(if (isHeart) "Batimentos" else "SpO₂", it) }
            item { Text("Dados salvos neste celular e incluídos no relatório de consulta dos últimos 30 dias.", style = MaterialTheme.typography.bodySmall, color = BragaTextSecondary) }
        }
    }
}
