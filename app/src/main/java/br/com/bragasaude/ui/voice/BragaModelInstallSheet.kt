package br.com.bragasaude.ui.voice

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.local.slm.BragaModelManager
import br.com.bragasaude.ui.components.BragaFormSheet

@Composable
fun BragaModelInstallSheet(manager: BragaModelManager, onDismiss: () -> Unit, onReady: () -> Unit) {
    val state by manager.state.collectAsState()
    BragaFormSheet(
        onDismissRequest = onDismiss,
        title = { Text("Braga no seu aparelho") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Para conversar por texto ou voz, baixe o Braga local (396 MB). Depois você escolhe a voz do Piper. Os modelos ficam no aparelho.")
                Text("Você pode deixar para depois e continuar usando os outros recursos do aplicativo.")
                state.message?.let { Text(it) }
                if (state.busy) LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            if (state.ready && !state.busy) Button(onClick = onReady) { Text("Continuar") }
            else if (!state.busy) Button(onClick = manager::install) { Text("Baixar Braga") }
            else TextButton(onClick = onDismiss) { Text("Continuar em segundo plano") }
        },
        dismissButton = {
            if (state.busy) TextButton(onClick = manager::cancel) { Text("Cancelar download") }
            else TextButton(onClick = onDismiss) { Text("Agora não") }
        }
    )
}
