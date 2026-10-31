package br.com.bragasaude.ui.family

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
