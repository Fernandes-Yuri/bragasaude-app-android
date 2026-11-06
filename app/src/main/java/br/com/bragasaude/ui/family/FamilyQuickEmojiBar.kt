package br.com.bragasaude.ui.family

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class FamilyQuickEmoji(val text: String, val description: String)

// SPEC-16: conteúdo enviado pela família, com exceção restrita no gate AUD-AN02.
private val quickEmojis = listOf(
    FamilyQuickEmoji("❤️", "Amor e carinho"),
    FamilyQuickEmoji("🙏", "Gratidão e bênção"),
    FamilyQuickEmoji("💊", "Remédio"),
    FamilyQuickEmoji("💧", "Água"),
    FamilyQuickEmoji("👍", "Tudo certo"),
    FamilyQuickEmoji("😊", "Estou bem"),
    FamilyQuickEmoji("🚶", "Caminhada"),
    FamilyQuickEmoji("👵", "Avó"),
    FamilyQuickEmoji("👴", "Avô")
)

@Composable
internal fun FamilyQuickEmojiBar(enabled: Boolean, onSendEmoji: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(quickEmojis, key = { it.text }) { emoji ->
            IconButton(
                onClick = { onSendEmoji(emoji.text) },
                enabled = enabled,
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = "Enviar: ${emoji.description}"
                }
            ) {
                Text(text = emoji.text, fontSize = 26.sp)
            }
        }
    }
}

/**
 * Gaveta retrátil dos atalhos do chat familiar (#13).
 *
 * Uma única linha compacta (~48dp) permanece visível; ao ser expandida ela
 * revela os chips de resposta rápida e a faixa de emojis. Recolhida por
 * padrão, devolve a área da conversa para a lista de mensagens.
 *
 * A lista de emojis continua exclusivamente neste arquivo (gate AUD-AN02);
 * [quickReplies] recebe os chips de texto de quem usa a gaveta.
 */
@Composable
internal fun FamilyQuickActionsDrawer(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    enabled: Boolean,
    onSendEmoji: (String) -> Unit,
    quickReplies: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(
                        onClickLabel = if (expanded) {
                            "Recolher atalhos rápidos"
                        } else {
                            "Expandir atalhos rápidos"
                        },
                        role = Role.Button,
                        onClick = onToggleExpanded
                    )
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Atalhos rápidos",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.ExpandLess
                    } else {
                        Icons.Default.ExpandMore
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                quickReplies()
                FamilyQuickEmojiBar(enabled = enabled, onSendEmoji = onSendEmoji)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}
