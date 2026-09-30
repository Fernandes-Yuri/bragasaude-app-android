package br.com.bragasaude.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.local.voice.VoiceCatalog
import br.com.bragasaude.data.local.voice.VoiceProfileManager
import br.com.bragasaude.ui.theme.*
import br.com.bragasaude.ui.voice.VoiceSelectionBottomSheet

@Composable
fun VoiceSettingsCard(manager: VoiceProfileManager) {
    val state by manager.state.collectAsState()
    var showSelector by rememberSaveable { mutableStateOf(false) }
    val activeName = VoiceCatalog.find(state.activeId)?.displayName ?: "Voz do dispositivo"

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Assistente de voz", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = BragaTextPrimary, modifier = Modifier.padding(start = 4.dp))
        Card(
            onClick = { showSelector = true },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
            border = BorderStroke(1.dp, BragaMintBorder.copy(alpha = 0.7f))
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = BragaMint) {
                    Icon(Icons.Default.RecordVoiceOver, contentDescription = null,
                        tint = BragaEmeraldDark, modifier = Modifier.padding(12.dp).size(24.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Voz do assistente", style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = BragaTextPrimary)
                    Text(if (state.busy) "Preparando sua voz..." else activeName,
                        style = MaterialTheme.typography.bodyMedium, color = BragaTextSecondary)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Trocar voz",
                    tint = BragaTextSecondary)
            }
        }
    }
    if (showSelector) {
        VoiceSelectionBottomSheet(manager = manager,
            onDismissRequest = { showSelector = false },
            onVoiceConfigured = { showSelector = false })
    }
}
