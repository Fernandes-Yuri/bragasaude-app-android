package br.com.bragasaude.ui.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ui.components.BragaFormSheet

@Composable
fun ReportPostDialog(
    state: SocialFeedViewModel.ReportState,
    onDismiss: () -> Unit,
    onSend: (String, String) -> Unit
) {
    var reason by rememberSaveable { mutableStateOf<String?>(null) }
    var details by rememberSaveable { mutableStateOf("") }
    val reasons = listOf(
        "offensive" to "Ofensa ou desrespeito",
        "misinformation" to "Informação médica prejudicial ou falsa",
        "spam" to "Spam ou propaganda",
        "other" to "Outro motivo"
    )
    BragaFormSheet(
        onDismissRequest = { if (!state.sending) onDismiss() },
        dismissEnabled = !state.sending,
        icon = { Icon(Icons.Default.Flag, contentDescription = null) },
        title = { Text("Denunciar publicação") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Por que você está denunciando esta publicação?")
                Column(Modifier.selectableGroup()) {
                    reasons.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .selectable(selected = reason == value, enabled = !state.sending, role = Role.RadioButton, onClick = { reason = value })
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = reason == value, onClick = null, enabled = !state.sending)
                            Spacer(Modifier.width(12.dp))
                            Text(label, Modifier.weight(1f))
                        }
                    }
                }
                OutlinedTextField(
                    value = details, onValueChange = { if (it.length <= 2000) details = it },
                    label = { Text("Detalhes (opcional)") }, enabled = !state.sending,
                    supportingText = { Text("${details.length}/2000") },
                    minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth()
                )
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
        },
        confirmButton = {
            Button(onClick = { reason?.let { onSend(it, details) } }, enabled = reason != null && !state.sending,
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (state.sending) "Enviando denúncia..." else "Enviar denúncia")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.sending) { Text("Cancelar") } }
    )
}
