package br.com.bragasaude.ui.components


import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import br.com.bragasaude.ai.BragaRoutingPolicy
import androidx.compose.ui.unit.dp

@Composable
fun RiskNotificationDialog(
    type: String,
    message: String,
    onDismiss: () -> Unit
) {
    BragaAlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Emergência e apoio",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Text(
                message,
                modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                EmergencySupportActions(
                    mentalHealthFirst = type == BragaRoutingPolicy.MENTAL_HEALTH_EMERGENCY,
                    requestInitialFocus = true
                )
            }
        }
    )
}
