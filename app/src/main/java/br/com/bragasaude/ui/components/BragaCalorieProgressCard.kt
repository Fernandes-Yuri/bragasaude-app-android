package br.com.bragasaude.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary

/**
 * Card de Calorias Diárias com gráfico circular de progresso expressivo.
 * Acompanha dinamicamente o progresso do usuário em relação à sua meta calculada clinicamente.
 */
@Composable
fun BragaCalorieProgressCard(
    consumedKcal: Double,
    targetKcal: Double,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val progress = (consumedKcal / targetKcal.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)

    Card(
        modifier = modifier
            .defaultMinSize(minHeight = 152.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
        border = BorderStroke(1.dp, BragaCardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 1. Gráfico circular de progresso com porcentagem ou ícone institucional
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = BragaEmerald,
                    trackColor = BragaMint,
                    strokeWidth = 4.5.dp,
                    strokeCap = StrokeCap.Round
                )
                if (consumedKcal > 0) {
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = BragaEmerald,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = BragaEmerald.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // 2. Valor métrico: calorias consumidas hoje
            Text(
                text = "%,d".format(consumedKcal.toInt()),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BragaTextPrimary,
                letterSpacing = (-0.5).sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(2.dp))

            // 3. Rótulo e meta clínica calculada
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "calorias",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = BragaTextSecondary,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "meta %,d kcal".format(targetKcal.toInt()),
                    fontSize = 11.sp,
                    color = BragaTextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
