package br.com.bragasaude.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaEmergencyOrange
import br.com.bragasaude.ui.theme.BragaMint

enum class BragaBadgeType {
    CONFIRMED, // Verde suave
    REVIEW,    // Laranja suave ("conferir")
    PENDING,   // Pêssego/Alerta suave
    NEUTRAL    // Cinza suave
}

/**
 * Pílula de status minimalista conforme os wireframes (110918.png, 110853.png).
 */
@Composable
fun BragaStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    type: BragaBadgeType = BragaBadgeType.CONFIRMED
) {
    val (backgroundColor, textColor) = when (type) {
        BragaBadgeType.CONFIRMED -> Color(0xFFD4EEDF) to BragaEmerald
        BragaBadgeType.REVIEW -> Color(0xFFFFE8D1) to Color(0xFFC05621)
        BragaBadgeType.PENDING -> Color(0xFFFDE8E4) to BragaEmergencyOrange
        BragaBadgeType.NEUTRAL -> Color(0xFFF1F5F3) to Color(0xFF4A5568)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}
