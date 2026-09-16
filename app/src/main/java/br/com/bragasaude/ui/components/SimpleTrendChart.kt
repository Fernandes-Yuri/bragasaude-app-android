package br.com.bragasaude.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
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
import br.com.bragasaude.ui.theme.TealLight
import br.com.bragasaude.ui.theme.TealPrimary
import br.com.bragasaude.ui.theme.TealSecondary

enum class ChartPeriod(val label: String, val days: Int) {
    SEVEN_DAYS("7D", 7),
    FIFTEEN_DAYS("15D", 15),
    THIRTY_DAYS("30D", 30)
}

@Composable
fun SimpleTrendChart(
    data: List<Double>,
    modifier: Modifier = Modifier,
    label: String? = null,
    color: Color = TealPrimary,
    targetValue: Double? = null,
    unit: String = "",
    showPeriodSelector: Boolean = false,
    onPeriodSelected: (ChartPeriod) -> Unit = {}
) {
    var selectedPeriod by remember { mutableStateOf(ChartPeriod.SEVEN_DAYS) }

    if (data.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Box(modifier = Modifier.padding(20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Sem dados registrados para este período.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val displayData = remember(data, selectedPeriod) {
        data.takeLast(selectedPeriod.days)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header: Título, Último Valor e Seletor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    if (label != null) {
                        Text(
                            label,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                    displayData.lastOrNull()?.let {
                        val formattedVal = if (unit == "km" || (it % 1.0 != 0.0 && it < 100)) {
                            "%.2f".format(it)
                        } else {
                            "${it.toInt()}"
                        }
                        Text(
                            "$formattedVal $unit",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = color
                        )
                    }
                }

                if (showPeriodSelector) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            ChartPeriod.values().forEach { period ->
                                val isSelected = selectedPeriod == period
                                Surface(
                                    color = if (isSelected) color else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.clickable {
                                        selectedPeriod = period
                                        onPeriodSelected(period)
                                    }
                                ) {
                                    Text(
                                        period.label,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Canvas Gráfico
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                val width = size.width
                val height = size.height
                
                val verticalPadding = 20.dp.toPx()
                val horizontalPadding = 12.dp.toPx()
                val availableHeight = height - (verticalPadding * 2)
                val availableWidth = width - (horizontalPadding * 2)

                val maxVal = displayData.maxOrNull() ?: 1.0
                val minVal = displayData.minOrNull() ?: 0.0
                
                val effectiveMax = maxOf(maxVal, targetValue ?: maxVal)
                val effectiveMin = minOf(minVal, targetValue ?: minVal)
                
                val range = (effectiveMax - effectiveMin).coerceAtLeast(1.0)
                val spacing = if (displayData.size > 1) availableWidth / (displayData.size - 1) else availableWidth

                // Linha Guia de Referência (Tracejada suave)
                targetValue?.let { target ->
                    val targetY = height - verticalPadding - ((target - effectiveMin) / range * availableHeight).toFloat()
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.8f),
                        start = Offset(horizontalPadding, targetY),
                        end = Offset(width - horizontalPadding, targetY),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                if (displayData.isNotEmpty()) {
                    val points = displayData.mapIndexed { index, value ->
                        val x = horizontalPadding + (index * spacing)
                        val y = height - verticalPadding - ((value - effectiveMin) / range * availableHeight).toFloat()
                        Offset(x, y)
                    }

                    // Caminho de Linha
                    val linePath = Path().apply {
                        points.forEachIndexed { i, pt ->
                            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                    }

                    // Preenchimento de Gradiente Suave
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(points.last().x, height - verticalPadding)
                        lineTo(points.first().x, height - verticalPadding)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                color.copy(alpha = 0.20f),
                                color.copy(alpha = 0.01f)
                            ),
                            startY = verticalPadding,
                            endY = height - verticalPadding
                        )
                    )

                    // Linha Fina (2dp)
                    drawPath(
                        path = linePath,
                        color = color,
                        style = Stroke(width = 2.2.dp.toPx())
                    )

                    // Pontos
                    points.forEachIndexed { index, pt ->
                        val isLast = index == points.size - 1
                        if (isLast) {
                            // Destaque para o último ponto
                            drawCircle(
                                color = color.copy(alpha = 0.25f),
                                radius = 6.5.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 4.5.dp.toPx(),
                                center = pt
                            )
                            drawCircle(
                                color = color,
                                radius = 3.dp.toPx(),
                                center = pt
                            )
                        } else {
                            drawCircle(
                                color = color.copy(alpha = 0.6f),
                                radius = 2.dp.toPx(),
                                center = pt
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            
            // Rodapé com Metadados
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (targetValue != null) {
                    Text(
                        "Referência: ${targetValue.toInt()} $unit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Text(
                    "${displayData.size} registros recentes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
