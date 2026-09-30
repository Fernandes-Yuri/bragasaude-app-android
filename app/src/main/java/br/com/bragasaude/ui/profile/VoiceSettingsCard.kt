package br.com.bragasaude.ui.profile

import android.media.MediaPlayer
import br.com.bragasaude.ui.components.BragaAlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.local.voice.VoiceCatalog
import br.com.bragasaude.data.local.voice.VoiceOption
import br.com.bragasaude.data.local.voice.VoiceProfileManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun VoiceSettingsCard(manager: VoiceProfileManager) {
    val state by manager.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmation by remember { mutableStateOf<VoiceOption?>(null) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var previewing by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { previewJob?.cancel() } }

    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Voz do assistente", style = MaterialTheme.typography.titleMedium)
            Text("Ouça uma prévia e escolha a voz que prefere.")
            VoiceCatalog.options.forEach { option ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(option.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(if (state.activeId == option.id) "Selecionada" else if (option.isNeural)
                            "Download: 67 MB • Instalada: cerca de 82 MB" else "Sem modelo adicional do aplicativo",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(enabled = !state.busy, onClick = {
                        previewJob?.cancel()
                        previewError = null
                        previewJob = scope.launch {
                            previewing = option.id
                            var player: MediaPlayer? = null
                            try {
                                manager.stop()
                                if (option.previewResId != null) {
                                    player = MediaPlayer.create(context, option.previewResId)
                                    checkNotNull(player).start()
                                    delay(2500)
                                } else if (!manager.previewSystem()) {
                                    previewError = "A voz do dispositivo ainda não está disponível. Verifique o mecanismo de fala nas configurações do Android."
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                previewError = "Não foi possível reproduzir a prévia."
                            } finally {
                                player?.release()
                                previewing = null
                            }
                        }
                    }) { Text(if (previewing == option.id) "Ouvindo" else "Ouvir") }
                    TextButton(enabled = !state.busy && state.activeId != option.id, onClick = {
                        previewJob?.cancel()
                        confirmation = option
                    }) { Text("Usar") }
                }
            }
            Text("Uma voz neural é mantida após a troca. Durante o preparo, o uso de espaço aumenta temporariamente.",
                style = MaterialTheme.typography.bodySmall)
            Text("Edresson: TTS-Portuguese-Corpus, CC BY 4.0. Faber e Cadu: conjuntos de voz CC0. Prévias do projeto Piper.",
                style = MaterialTheme.typography.bodySmall)
            previewError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = manager::dismissError) { Text("Entendi") }
            }
        }
    }
    confirmation?.let { option ->
        BragaAlertDialog(onDismissRequest = { confirmation = null },
            title = { Text("Usar ${option.displayName}?") },
            text = { Text(if (option.isNeural)
                "Será feito um download de aproximadamente 67 MB. Reserve cerca de 180 MB livres para preparar a voz. A voz anterior será removida depois que a nova estiver pronta."
                else "O modelo neural será removido. A disponibilidade e o funcionamento offline dependem das vozes instaladas no Android.") },
            confirmButton = { TextButton(onClick = { confirmation = null; manager.select(option.id) }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Voltar") } })
    }
    if (state.busy && state.preparingId != null) {
        var tip by remember { mutableIntStateOf(0) }
        val tips = listOf("Você pode trocar a voz nas configurações.", "Fale em um ambiente tranquilo para facilitar a compreensão.", "A voz neural funciona no aparelho depois do download.")
        LaunchedEffect(Unit) { while (true) { delay(4000); tip = (tip + 1) % tips.size } }
        BragaAlertDialog(onDismissRequest = {},
            title = { Text("Preparando seu assistente...") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(state.phase.orEmpty())
                val progress = state.progress
                if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else {
                    LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("${(progress * 100).toInt()}%")
                }
                Text(tips[tip])
            } },
            confirmButton = {},
            dismissButton = { if (state.phase != "Carregando voz") TextButton(onClick = manager::cancelDownload) { Text("Cancelar") } })
    }
}
