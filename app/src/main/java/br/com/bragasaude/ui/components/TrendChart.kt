package br.com.bragasaude.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.data.local.DailyMetricsEntity
import br.com.bragasaude.ui.theme.TealLight
import br.com.bragasaude.ui.theme.TealPrimary
import br.com.bragasaude.ui.theme.TealSecondary

/**
 * Gráfico Moderno e Leve de Tendência de Atividade (Passos)
 * Design U-Fit: Gradiente suave em Teal, linhas finas (2dp) e destaque claro do último valor.
 */
@Composable
fun TrendChart(
    data: List<DailyMetricsEntity>,
    modifier: Modifier = Modifier,
    targetSteps: Int = 8000
) {
    if (data.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Sem dados suficientes de passos registrados.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val reversedData = remember(data) { data.reversed() }
    val latestSteps = reversedData.lastOrNull()?.steps ?: 0
    val maxSteps = (reversedData.maxOfOrNull { it.steps } ?: 1000).coerceAtLeast(targetSteps).toFloat()
    
    var animationPlayed by remember { mutableStateOf(false) }
    val animationProgress by animateFloatAsState(
        targetValue = if (animationPlayed) 1f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "trendChartAnimation"
    )

    LaunchedEffect(Unit) {
        animationPlayed = true
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Título e Último Valor Destacado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Tendência de Passos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Última medição: $latestSteps passos",
                        style = MaterialTheme.typography.bodySmall,
                        color = TealPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Surface(
                    color = if (latestSteps >= targetSteps) TealLight else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (latestSteps >= targetSteps) "Meta Atingida!" else "Meta: $targetSteps",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (latestSteps >= targetSteps) TealPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            
            // Canvas Gráfico Suave
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
            ) {
                val width = size.width
                val height = size.height
                val padY = 20.dp.toPx()
                val padX = 12.dp.toPx()
                val drawW = width - (padX * 2)
                val drawH = height - (padY * 2)

                val spacing = if (reversedData.size > 1) drawW / (reversedData.size - 1) else drawW
                
                // 1. Linha Tracejada de Meta
                val targetY = height - padY - ((targetSteps / maxSteps) * drawH)
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.7f),
                    start = Offset(padX, targetY),
                    end = Offset(width - padX, targetY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                if (reversedData.isNotEmpty()) {
                    val points = reversedData.mapIndexed { index, item ->
                        val x = padX + (index * spacing)
                        val y = height - padY - ((item.steps / maxSteps) * drawH * animationProgress)
                        Offset(x, y)
                    }

                    // 2. Caminho de Linha
                    val linePath = Path().apply {
                        points.forEachIndexed { i, pt ->
                            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                    }

                    // 3. Área Preenchida com Gradiente Suave
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(points.last().x, height - padY)
                        lineTo(points.first().x, height - padY)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                TealPrimary.copy(alpha = 0.22f),
                                TealLight.copy(alpha = 0.02f)
                            ),
                            startY = padY,
                            endY = height - padY
                        )
                    )

                    // 4. Desenha a linha fina (2dp)
                    drawPath(
                        path = linePath,
                        color = TealPrimary,
                        style = Stroke(width = 2.2.dp.toPx())
                    )

                    // 5. Pontos (sutis para os intermediários, destacados no último)
                    points.forEachIndexed { index, pt ->
                        val isLast = index == points.size - 1
                        if (isLast) {
                            // Halo exterior
                            drawCircle(
                                color = TealPrimary.copy(alpha = 0.25f),
                                radius = 7.dp.toPx(),
                                center = pt
                            )
                            // Núcleo branco
                            drawCircle(
                                color = Color.White,
                                radius = 4.5.dp.toPx(),
                                center = pt
                            )
                            // Ponto central Teal
                            drawCircle(
                                color = TealPrimary,
                                radius = 3.dp.toPx(),
                                center = pt
                            )
                        } else {
                            drawCircle(
                                color = TealSecondary.copy(alpha = 0.7f),
                                radius = 2.5.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }
        }
    }
}
