package br.com.bragasaude.ui.voice

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private data class AssistantTip(
    val icon: ImageVector,
    val title: String,
    val description: String,
    val example: String
)

private val assistantTips = listOf(
    AssistantTip(Icons.AutoMirrored.Filled.Chat, "Menos toques, mais conversa",
        "Conte o que precisa em suas próprias palavras. O assistente pode ajudar a preparar seus registros de saúde.",
        "O que você pode fazer?"),
    AssistantTip(Icons.Default.WaterDrop, "Sua água, sem perder a conta",
        "Diga quanto bebeu. O assistente prepara o registro para você conferir e confirmar na tela.",
        "Tomei um copo de água"),
    AssistantTip(Icons.Default.FavoriteBorder, "A pressão que você mediu",
        "Depois de medir, fale os dois valores. O assistente ajuda a preencher o registro no aplicativo.",
        "Minha pressão está 12 por 8"),
    AssistantTip(Icons.Default.Mic, "Sua glicemia também pode ser por voz",
        "Fale o resultado da sua medição. Confira o valor na tela antes de confirmar o registro.",
        "Minha glicemia está 105"),
    AssistantTip(Icons.Default.VerifiedUser, "A conversa no seu ritmo",
        "Você pode pedir para repetir, dizer “pausar” ou “encerrar”. Os registros preparados pelo assistente precisam da sua confirmação.",
        "Pode repetir?")
)

/** Conteúdo de apresentação compartilhado por todas as opções de voz, sem espera artificial. */
@Composable
internal fun AssistantPreparationExperience(ready: Boolean) {
    var index by rememberSaveable { mutableIntStateOf(0) }
    var autoAdvance by rememberSaveable { mutableStateOf(true) }
    val context = LocalContext.current
    val accessibility = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    var touchExploration by remember { mutableStateOf(accessibility?.isTouchExplorationEnabled == true) }
    DisposableEffect(accessibility) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        accessibility?.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibility?.removeTouchExplorationStateChangeListener(listener) }
    }
    LaunchedEffect(autoAdvance, index, touchExploration) {
        if (autoAdvance && !touchExploration) {
            delay(10_000)
            index = (index + 1) % assistantTips.size
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(if (ready) "Tudo pronto. Conheça algumas ideias para a sua primeira conversa."
            else "Estamos preparando o ambiente para você. A primeira configuração pode levar alguns minutos, dependendo da conexão e do aparelho.",
            style = MaterialTheme.typography.bodyMedium)
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("CONHEÇA SEU ASSISTENTE", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary)
                AnimatedContent(targetState = index, label = "Dicas do assistente") { tipIndex ->
                    val tip = assistantTips[tipIndex]
                    Column(Modifier.fillMaxWidth().heightIn(min = 260.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                            Icon(tip.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(14.dp).size(32.dp))
                        }
                        Text(tip.title, style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(tip.description, style = MaterialTheme.typography.bodyMedium)
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Experimente dizer", style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("“${tip.example}”", style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { index = (index + assistantTips.size - 1) % assistantTips.size }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Dica anterior")
                    }
                    Text("${index + 1} de ${assistantTips.size}", style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.semantics { contentDescription = "Dica ${index + 1} de ${assistantTips.size}" })
                    IconButton(onClick = { index = (index + 1) % assistantTips.size }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próxima dica")
                    }
                }
            }
        }
        if (!touchExploration) {
            TextButton(onClick = { autoAdvance = !autoAdvance }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text(if (autoAdvance) "Pausar dicas" else "Alternar dicas automaticamente")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (ready) Icon(Icons.Default.CheckCircle, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            else CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(if (ready) "Seu assistente está pronto" else "Configuração em andamento",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
