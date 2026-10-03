package br.com.bragasaude.ui.profile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.local.slm.BragaModelManager

@Composable
fun BragaModelSettingsCard(manager: BragaModelManager) {
    val state by manager.state.collectAsState()
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Braga no aparelho", style = MaterialTheme.typography.titleMedium)
            Text("Download opcional de 396 MB para o chat de texto e o assistente de voz. A voz Piper é instalada separadamente. Sem o Braga, esses dois recursos ficam indisponíveis.")
            Text(state.message ?: if (state.ready) "Braga V2.2 instalado no aparelho" else "Braga ainda não instalado")
            if (state.busy) {
                LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = manager::cancel) { Text("Cancelar download") }
            } else if (!state.ready) {
                Button(onClick = manager::install) { Text("Baixar Braga local") }
            }
        }
    }
}
