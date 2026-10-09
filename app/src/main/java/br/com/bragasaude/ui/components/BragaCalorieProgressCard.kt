package br.com.bragasaude.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary

/**
 * Card de Calorias Diárias com gráfico circular de progresso.
 * Substitui o antigo score de saúde na grade principal de métricas da Home.
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
            // 1. Gráfico circular de progresso com ícone nutricional
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(38.dp),
                    color = BragaEmerald,
                    trackColor = BragaMint,
                    strokeWidth = 3.5.dp,
                    strokeCap = StrokeCap.Round
                )
                Icon(
                    imageVector = Icons.Default.Restaurant,
                    contentDescription = null,
                    tint = BragaEmerald,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.height(6.dp))

            // 2. Calorias consumidas hoje
            Text(
                text = consumedKcal.toInt().toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = BragaTextPrimary,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center
            )

            // 3. Rótulo da métrica
            Text(
                text = "Calorias",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = BragaTextSecondary,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(2.dp))

            // 4. Meta diária de referência
            Text(
                text = "Meta ${targetKcal.toInt()} kcal",
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = BragaTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}
