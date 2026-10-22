package br.com.bragasaude.ui.smoking

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ui.components.BragaFormSheet

/** Matches the existing (i) pattern: details live in a scrollable shared sheet. */
@Composable
internal fun SmokingInfoButton(title: String, body: String, sourceUrl: String? = null, sourceLabel: String = "Consultar fonte (internet)") {
    var open by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    IconButton(onClick = { open = true }) {
        Icon(Icons.Default.Info, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
    }
    if (open) BragaFormSheet(
        onDismissRequest = { open = false },
        title = { Text(title) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(body)
            if (sourceUrl != null) TextButton(onClick = { uri.openUri(sourceUrl) }) { Text(sourceLabel) }
        } },
        confirmButton = { Button(onClick = { open = false }, modifier = Modifier.fillMaxWidth()) { Text("Entendi") } }
    )
}

@Composable
internal fun SmokingInfoHeading(title: String, infoTitle: String, body: String, sourceUrl: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
        SmokingInfoButton(infoTitle, body, sourceUrl)
    }
}
