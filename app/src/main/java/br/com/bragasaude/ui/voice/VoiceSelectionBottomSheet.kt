package br.com.bragasaude.ui.voice

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.data.local.voice.VoiceCatalog
import br.com.bragasaude.data.local.voice.VoiceOption
import br.com.bragasaude.data.local.voice.VoiceProfileManager
import br.com.bragasaude.ui.components.BragaAlertDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Modal / BottomSheet de boas-vindas e seleção inicial de voz para o Assistente Orb.
 *
 * Apresentado no primeiro toque do usuário no Orb caso ele ainda não tenha selecionado
 * uma voz neural ou confirmado o uso do sintetizador padrão do sistema.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceSelectionBottomSheet(
    manager: VoiceProfileManager,
    onDismissRequest: () -> Unit,
    onVoiceConfigured: () -> Unit
) {
    val state by manager.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var previewJob by remember { mutableStateOf<Job?>(null) }
    var previewingId by remember { mutableStateOf<String?>(null) }
    var previewError by remember { mutableStateOf<String?>(null) }
    var selectedOptionToPrepare by remember { mutableStateOf<VoiceOption?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            previewJob?.cancel()
        }
    }

    // Se o usuário selecionou uma voz neural e ela terminou de carregar com sucesso
    LaunchedEffect(state.activeId, state.phase) {
        if (state.hasChosenVoice && !state.busy && selectedOptionToPrepare != null) {
            selectedOptionToPrepare = null
            onVoiceConfigured()
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!state.busy) {
                onDismissRequest()
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Escolha a Voz do Assistente",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Para conversar com respostas rápidas e naturais, escolha a voz que você prefere para o seu assistente de saúde:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Opções de vozes neurais
            VoiceCatalog.options.filter { it.isNeural }.forEach { option ->
                val isCurrentlyPreviewing = previewingId == option.id
                val sizeText = when (option.id) {
                    "edresson" -> "Leve • 30 MB"
                    "cadu" -> "Dinâmica • 65 MB"
                    else -> "Clássica • 67 MB"
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = option.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = sizeText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Botão de ouvir prévia
                            FilledTonalIconButton(
                                enabled = !state.busy,
                                onClick = {
                                    previewJob?.cancel()
                                    previewError = null
                                    previewJob = scope.launch {
                                        previewingId = option.id
                                        var player: MediaPlayer? = null
                                        try {
                                            manager.stop()
                                            if (option.previewResId != null) {
                                                player = MediaPlayer.create(context, option.previewResId)
                                                player?.start()
                                                delay(2700)
                                            }
                                        } catch (e: kotlinx.coroutines.CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            previewError = "Não foi possível reproduzir a prévia."
                                        } finally {
                                            player?.release()
                                            previewingId = null
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isCurrentlyPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    contentDescription = "Ouvir demonstração da voz ${option.displayName}"
                                )
                            }

                            // Botão de escolher
                            Button(
                                enabled = !state.busy,
                                onClick = {
                                    previewJob?.cancel()
                                    selectedOptionToPrepare = option
                                    manager.select(option.id)
                                }
                            ) {
                                Text("Escolher")
                            }
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Opção de usar voz padrão do sistema
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.busy,
                onClick = {
                    previewJob?.cancel()
                    manager.selectSystemExplicit()
                    onVoiceConfigured()
                }
            ) {
                Text("Usar voz padrão do aparelho (sem download)")
            }

            previewError?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = manager::dismissError) {
                    Text("OK")
                }
            }
        }
    }

    // Modal de Preparação com barra de progresso durante o download do modelo
    if (state.busy && state.preparingId != null) {
        var tipIndex by remember { mutableIntStateOf(0) }
        val healthTips = listOf(
            "Você pode trocar de voz a qualquer momento nas configurações.",
            "A voz neural é executada localmente no seu aparelho com máxima privacidade.",
            "Beber água regularmente melhora sua disposição e saúde física."
        )

        LaunchedEffect(Unit) {
            while (true) {
                delay(3500)
                tipIndex = (tipIndex + 1) % healthTips.size
            }
        }

        BragaAlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = "Preparando seu assistente...",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = state.phase ?: "Baixando modelo neural...",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    val progress = state.progress
                    if (progress == null) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${(progress * 100).toInt()}% concluído",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Text(
                        text = healthTips[tipIndex],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                if (state.phase != "Carregando voz") {
                    TextButton(onClick = manager::cancelDownload) {
                        Text("Cancelar")
                    }
                }
            }
        )
    }
}
