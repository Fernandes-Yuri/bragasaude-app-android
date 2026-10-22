package br.com.bragasaude.ui.smoking

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import br.com.bragasaude.domain.smoking.CravingIntensity
import kotlinx.coroutines.delay

@Composable
internal fun SmokingPause(
    reason: String?,
    busy: Boolean,
    onClose: () -> Unit,
    onRecord: (CravingIntensity) -> Unit,
    onActivitySelected: () -> Unit = {},
    monotonicMillis: () -> Long = SystemClock::elapsedRealtime
) {
    var deadline by remember { mutableStateOf<Long?>(null) }
    var remaining by remember { mutableIntStateOf(120) }
    var selected by remember { mutableStateOf<SmokingPauseActivity?>(null) }
    var remainingAtSelection by remember { mutableIntStateOf(120) }
    var showRecord by remember { mutableStateOf(false) }
    var intensity by remember { mutableStateOf<CravingIntensity?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val now by rememberUpdatedState(monotonicMillis)
    val uri = LocalUriHandler.current
    val startPause = {
        remaining = 120
        remainingAtSelection = 120
        deadline = now() + 120_000
    }
    LaunchedEffect(deadline, lifecycle) {
        val end = deadline ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                remaining = ((end - now()).coerceAtLeast(0) + 999).div(1000).toInt()
                if (remaining == 0) break
                delay(250)
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SupportCard {
            Text("Uma pausa para você", style = MaterialTheme.typography.titleLarge)
            if (deadline == null) {
                Text(SmokingSupportContent.pause)
                if (selected == null) Text("Toque em uma sugestão abaixo para ver como experimentar. O contador é opcional.", style = MaterialTheme.typography.bodyMedium)
                else Button(onClick = startPause, modifier = Modifier.fillMaxWidth()) { Text("Começar pausa de 2 minutos") }
            } else {
                Text("${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.testTag("pauseCountdown"))
                LinearProgressIndicator(progress = { (120 - remaining) / 120f }, modifier = Modifier.fillMaxWidth())
                Text(if (remaining == 0) "O tempo terminou. A vontade pode continuar; você pode tentar outra ideia ou buscar apoio." else "Siga a ideia abaixo no seu ritmo. Você não precisa acompanhar cada etapa.")
                if (remaining == 0) OutlinedButton(onClick = startPause, modifier = Modifier.fillMaxWidth()) { Text("Começar outra pausa") }
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Encerrar pausa") }
        }
        selected?.let { activity -> SupportCard {
            val step = ((remainingAtSelection - remaining) / 30).coerceIn(0, activity.steps.lastIndex)
            Text(activity.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("currentPauseActivity"))
            Text(activity.guidance, style = MaterialTheme.typography.bodyMedium)
            HorizontalDivider()
            Text(if (deadline == null) "Para experimentar agora" else if (remaining == 0) "Você decide o próximo passo" else "Ideia ${step + 1} de ${activity.steps.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(activity.steps[step], style = MaterialTheme.typography.bodyLarge, modifier = Modifier.testTag("pauseGuidance"))
            Text("Os dois minutos organizam a pausa, sem garantir que a vontade passe.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        reason?.let { SupportCard { Text("Meu motivo", style = MaterialTheme.typography.titleMedium); Text(it) } }
        Text(if (selected == null) "Qual ideia combina com você agora?" else "Escolha outra ideia, se quiser", style = MaterialTheme.typography.titleMedium)
        SmokingPauseActivity.entries.forEach { activity ->
            SmokingActivityCard(activity, selected == activity) {
                selected = activity
                remainingAtSelection = remaining
                // Keep the same deadline; a choice is neither a diary entry nor a success record.
                onActivitySelected()
            }
        }
        SupportCard {
            Text("Como está a vontade?", style = MaterialTheme.typography.titleMedium)
            Text("Registrar é opcional. A atividade e o fim do contador não registram um dia sem fumar.")
            if (!showRecord) OutlinedButton(onClick = { showRecord = true }, modifier = Modifier.fillMaxWidth()) { Text("Quero registrar como estou") }
            else {
                CravingIntensity.entries.forEach { value ->
                    val label = when (value) { CravingIntensity.LOW -> "Baixa"; CravingIntensity.MEDIUM -> "Média"; CravingIntensity.HIGH -> "Alta" }
                    ChoiceRow(label, intensity == value) { intensity = value }
                }
                Button(onClick = { intensity?.let(onRecord) }, enabled = intensity != null && !busy, modifier = Modifier.fillMaxWidth()) { Text("Registrar vontade e encerrar") }
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Pular e encerrar") }
        }
        Text("Sugestões adaptadas de OMS — Quick tips; conteúdo local v2, conferido em 22/10/2026. As etapas são ideias para organizar a atenção, não um tratamento.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { uri.openUri(SmokingSupportContent.pauseSourceUrl) }) { Text("Consultar fonte da OMS (internet)") }
    }
}

@Composable
private fun SmokingActivityCard(activity: SmokingPauseActivity, selected: Boolean, onSelect: () -> Unit) {
    val icon = when (activity) {
        SmokingPauseActivity.NOTICE -> Icons.Default.Visibility
        SmokingPauseActivity.WATER -> Icons.Default.WaterDrop
        SmokingPauseActivity.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
        SmokingPauseActivity.HANDS -> Icons.Default.Create
    }
    Card(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onSelect).testTag("pauseActivity-${activity.name}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface, contentColor = MaterialTheme.colorScheme.onSurface),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(Modifier.padding(16.dp).heightIn(min = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(activity.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(activity.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (selected) Text("Selecionada", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}
