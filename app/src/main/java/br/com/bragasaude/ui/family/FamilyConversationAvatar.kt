package br.com.bragasaude.ui.family

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ui.components.ConnectedUserAvatar
import br.com.bragasaude.ui.components.SharedProfileAvatar
import br.com.bragasaude.ui.theme.BragaEmerald

@Composable
fun FamilyConversationAvatar(conversation: FamilyConversationUi?, size: Dp) {
    val members = if (conversation?.isCaredByMe == true) listOf(conversation.patientId to conversation.title)
        else conversation?.activeBindings.orEmpty().map { it.caregiverUserId to it.caregiverName }
            .filter { it.first.isNotBlank() }.distinctBy { it.first }
    when {
        members.isEmpty() -> SharedProfileAvatar(conversation?.title, null, null, size)
        members.size == 1 -> ConnectedUserAvatar(members[0].first, members[0].second, size)
        else -> Box(Modifier.size(size)) {
            Box(Modifier.align(Alignment.TopStart)) {
                ConnectedUserAvatar(members[0].first, members[0].second, size * 0.65f)
            }
            Box(Modifier.align(Alignment.BottomEnd)) {
                ConnectedUserAvatar(members[1].first, members[1].second, size * 0.65f)
            }
            if (members.size > 2) FamilyParticipantBadge(
                count = members.size - 2,
                size = size * 0.38f,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }
}

@Composable
internal fun FamilyParticipantBadge(count: Int, size: Dp, modifier: Modifier = Modifier) {
    val label = "+$count"
    val style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val measured = rememberTextMeasurer().measure(label, style, softWrap = false, maxLines = 1)
    val diameter = with(LocalDensity.current) {
        size.coerceAtLeast(18.dp).coerceAtLeast(measured.size.width.toDp() + 8.dp)
            .coerceAtLeast(measured.size.height.toDp() + 4.dp)
    }
    Surface(
        modifier = modifier.size(diameter).testTag("familyParticipantBadge")
            .semantics { contentDescription = "Mais $count participantes" },
        shape = CircleShape,
        color = BragaEmerald,
        contentColor = Color.White,
        border = BorderStroke(1.5.dp, Color.White)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = style, maxLines = 1, softWrap = false)
        }
    }
}
