package br.com.bragasaude.ui.voice

import android.media.MediaPlayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.local.voice.VoiceCatalog
import br.com.bragasaude.data.local.voice.VoiceOption
import br.com.bragasaude.data.local.voice.VoiceProfileManager
import br.com.bragasaude.ui.components.BragaFormSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Mesmo seletor para as configurações e a primeira conversa pelo ORB. */
@Composable
fun VoiceSelectionBottomSheet(
    manager: VoiceProfileManager,
    onDismissRequest: () -> Unit,
    onVoiceConfigured: () -> Unit
) {
    val state by manager.state.collectAsState()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var selectedId by rememberSaveable { mutableStateOf(state.activeId) }
    var requestedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showCredits by rememberSaveable { mutableStateOf(false) }
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var previewingId by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    val selected = VoiceCatalog.find(selectedId) ?: VoiceCatalog.options.first()
    val preparing = state.busy && state.preparingId != null
    val willDownload = selected.isNeural && selected.id != state.activeId

    DisposableEffect(Unit) { onDispose { previewJob?.cancel() } }
    LaunchedEffect(state.busy, state.activeId, state.hasChosenVoice, state.error, requestedId) {
        val requested = requestedId
        if (requested != null && !state.busy) {
            requestedId = null
            if (state.hasChosenVoice && state.activeId == requested && state.error == null) onVoiceConfigured()
        }
    }

    fun preview(option: VoiceOption) {
        val previous = previewJob
        if (previewingId == option.id) {
            previous?.cancel()
            return
        }
        previewJob = scope.launch {
            previous?.cancelAndJoin()
            previewError = null
            previewingId = option.id
            var player: MediaPlayer? = null
            try {
                manager.stop()
                if (option.previewResId != null) {
                    val created = checkNotNull(MediaPlayer.create(context, option.previewResId))
                    player = created
                    created.start()
                    delay(2500)
                } else if (!manager.previewSystem()) {
                    previewError = "A prévia do aparelho está indisponível. Tente novamente em instantes."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                previewError = "Não foi possível ouvir a prévia. Tente novamente."
            } finally {
                player?.release()
                previewingId = null
            }
        }
    }

    BragaFormSheet(
        onDismissRequest = onDismissRequest,
        dismissEnabled = !state.busy,
        title = { Text(if (preparing) "Preparando sua voz" else "Qual voz você prefere?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (preparing) {
                    Column(Modifier.fillMaxWidth().heightIn(min = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(VoiceCatalog.find(state.preparingId.orEmpty())?.displayName.orEmpty(),
                            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(state.phase.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        val progress = state.progress
                        if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        else {
                            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                            Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                        }
                        Text("Só um instante. Você poderá conversar assim que a voz estiver pronta.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text("Ouça as prévias e escolha. Você pode mudar depois.", style = MaterialTheme.typography.bodyMedium)
                    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceCatalog.options.forEach { option ->
                            val isSelected = option.id == selectedId
                            Surface(shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant)) {
                                Row(Modifier.fillMaxWidth()
                                    .selectable(selected = isSelected, enabled = !state.busy, role = Role.RadioButton,
                                        onClick = { selectedId = option.id; manager.dismissError() })
                                    .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    RadioButton(selected = isSelected, onClick = null, enabled = !state.busy)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(option.displayName, style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                        Text(if (!option.isNeural) "Sem download" else if (state.activeId == option.id)
                                            "Em uso" else "Voz neural",
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                    IconButton(enabled = !state.busy, onClick = { preview(option) }) {
                                        Icon(if (previewingId == option.id) Icons.Default.Stop else Icons.Default.PlayArrow,
                                            contentDescription = if (previewingId == option.id) "Parar prévia de ${option.displayName}"
                                                else "Ouvir prévia de ${option.displayName}", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                    Text("Ao trocar de voz, o modelo anterior é removido do aparelho para economizar espaço.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    previewError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    TextButton(onClick = { showCredits = !showCredits }, contentPadding = PaddingValues(0.dp)) {
                        Text(if (showCredits) "Ocultar créditos" else "Sobre as vozes", style = MaterialTheme.typography.labelMedium)
                    }
                    if (showCredits) {
                        Text("Prévias do Piper. Faber e Cadu: dados CC0. Edresson: TTS-Portuguese-Corpus, CC BY 4.0.",
                            style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { uriHandler.openUri("https://huggingface.co/rhasspy/piper-voices/tree/main/pt/pt_BR") }) {
                            Text("Modelos e autores")
                        }
                        TextButton(onClick = { uriHandler.openUri("https://creativecommons.org/licenses/by/4.0/") }) {
                            Text("Licença de Edresson")
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!preparing) {
                Button(modifier = Modifier.fillMaxWidth(), enabled = !state.busy, onClick = {
                    previewJob?.cancel()
                    if (state.hasChosenVoice && selected.id == state.activeId) onVoiceConfigured()
                    else {
                        requestedId = selected.id
                        manager.select(selected.id)
                    }
                }) {
                    Text(if (willDownload) "Baixar e usar ${selected.displayName}"
                        else if (!state.hasChosenVoice) "Começar com esta voz" else "Usar esta voz")
                }
            }
        },
        dismissButton = {
            if (preparing) {
                TextButton(modifier = Modifier.fillMaxWidth(), enabled = state.phase != "Carregando voz",
                    onClick = manager::cancelDownload) { Text("Cancelar preparo") }
            } else {
                TextButton(modifier = Modifier.fillMaxWidth(), onClick = onDismissRequest) { Text("Agora não") }
            }
        }
    )
}
