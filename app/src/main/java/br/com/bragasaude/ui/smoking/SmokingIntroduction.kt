package br.com.bragasaude.ui.smoking

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun SmokingIntroduction(authenticated: Boolean, hasPlan: Boolean, busy: Boolean, onPrepare: () -> Unit, onResume: () -> Unit) {
    SupportCard {
        SmokingInfoHeading("Seu caminho, no seu ritmo", "Privacidade e limites do apoio", SmokingSupportContent.privacy + "\n\n" + SmokingSupportContent.limits)
        Text("Organize sua jornada para parar de fumar.")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmokingFeature("Meu plano", Icons.Default.Assignment, Modifier.weight(1f))
            SmokingFeature("Meu diário", Icons.Default.Edit, Modifier.weight(1f))
            SmokingFeature("Uma pausa", Icons.Default.Timer, Modifier.weight(1f))
        }
        Text(SmokingSupportContent.localNotice, style = MaterialTheme.typography.bodyMedium)
        Text("Gratuito e opcional. Não substitui atendimento de saúde.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!authenticated) Text("Entre em uma conta própria para guardar seu acompanhamento.")
        else if (hasPlan) {
            Text("Seu histórico foi mantido. Você pode retomar seu plano.")
            Button(enabled = !busy, onClick = onResume, modifier = Modifier.fillMaxWidth()) { Text("Ativar meu apoio") }
        } else Button(enabled = !busy, onClick = onPrepare, modifier = Modifier.fillMaxWidth()) { Text("Preparar meu plano") }
    }
}

@Composable
private fun SmokingFeature(label: String, icon: ImageVector, modifier: Modifier) {
    Column(modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
