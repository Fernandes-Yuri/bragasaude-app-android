package br.com.bragasaude.ui.family

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import br.com.bragasaude.ui.components.ConnectedUserAvatar
import br.com.bragasaude.ui.components.SharedProfileAvatar

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
            if (members.size > 2) Text("+${members.size - 2}", Modifier.align(Alignment.TopEnd))
        }
    }
}
