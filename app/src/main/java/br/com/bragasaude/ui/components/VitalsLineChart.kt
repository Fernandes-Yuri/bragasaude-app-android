package br.com.bragasaude.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import br.com.bragasaude.ui.theme.TealSurface

data class VitalPoint(
    val value: Float,
    val secondaryValue: Float? = null, // Ex: Diastólica para pressão
    val label: String? = null
)

/**
 * Componente de Gráfico Moderno para Sinais Vitais (Pressão, Glicose, Peso).
 * Segue a linguagem visual U-Fit: paleta Teal, linhas finas de 2dp, gradientes suaves
 * e destaque prioritário para a última medição registrada.
 */
@Composable
fun VitalsLineChart(
    title: String,
    unit: String,
    points: List<VitalPoint>,
    modifier: Modifier = Modifier,
    referenceMin: Float? = null,
    referenceMax: Float? = null,
    primaryColor: Color = TealPrimary,
    secondaryColor: Color = TealSecondary,
    secondaryLabel: String? = null
) {
    if (points.isEmpty()) {
        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Box(modifier = Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "Sem dados registrados para este sinal vital.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val latestPoint = points.last()
    val allValues = remember(points) {
        points.flatMap { listOfNotNull(it.value, it.secondaryValue) }
    }
    val minVal = (allValues.minOrNull() ?: 0f).coerceAtMost(referenceMin ?: Float.MAX_VALUE)
    val maxVal = (allValues.maxOrNull() ?: 100f).coerceAtLeast(referenceMax ?: Float.MIN_VALUE)
    val range = (maxVal - minVal).coerceAtLeast(10f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header com Destaque do Último Valor
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (referenceMin != null && referenceMax != null) {
                        Text(
                            text = "Faixa esperada: ${referenceMin.toInt()} - ${referenceMax.toInt()} $unit",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    color = TealSurface,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    val latestText = if (latestPoint.secondaryValue != null) {
                        "${latestPoint.value.toInt()}/${latestPoint.secondaryValue.toInt()} $unit"
                    } else {
                        "${latestPoint.value.toInt()} $unit"
                    }
                    Text(
                        text = latestText,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = primaryColor
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Canvas de Desenho da Linha
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
            ) {
                val width = size.width
                val height = size.height
                val padY = 18.dp.toPx()
                val padX = 14.dp.toPx()
                val drawW = width - (padX * 2)
                val drawH = height - (padY * 2)

                val spacing = if (points.size > 1) drawW / (points.size - 1) else drawW

                // 1. Faixas de Referência (Linhas tracejadas)
                referenceMax?.let { refMax ->
                    val y = height - padY - ((refMax - minVal) / range * drawH)
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.6f),
                        start = Offset(padX, y),
                        end = Offset(width - padX, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                }
                referenceMin?.let { refMin ->
                    val y = height - padY - ((refMin - minVal) / range * drawH)
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.6f),
                        start = Offset(padX, y),
                        end = Offset(width - padX, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                    )
                }

                // 2. Linha Primária (ex: Sistólica ou Glicose)
                val primaryOffsets = points.mapIndexed { index, pt ->
                    val x = padX + (index * spacing)
                    val y = height - padY - ((pt.value - minVal) / range * drawH)
                    Offset(x, y)
                }

                if (primaryOffsets.isNotEmpty()) {
                    val primaryPath = Path().apply {
                        primaryOffsets.forEachIndexed { i, pt ->
                            if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                        }
                    }

                    // Gradiente de Fundo
                    val fillPath = Path().apply {
                        addPath(primaryPath)
                        lineTo(primaryOffsets.last().x, height - padY)
                        lineTo(primaryOffsets.first().x, height - padY)
                        close()
                    }

                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                primaryColor.copy(alpha = 0.18f),
                                TealLight.copy(alpha = 0.01f)
                            ),
                            startY = padY,
                            endY = height - padY
                        )
                    )

                    // Traço Fino Primário
                    drawPath(
                        path = primaryPath,
                        color = primaryColor,
                        style = Stroke(width = 2.2.dp.toPx())
                    )

                    // Pontos Primários
                    primaryOffsets.forEachIndexed { i, pt ->
                        val isLast = i == primaryOffsets.size - 1
                        if (isLast) {
                            drawCircle(color = primaryColor.copy(alpha = 0.25f), radius = 6.dp.toPx(), center = pt)
                            drawCircle(color = Color.White, radius = 4.dp.toPx(), center = pt)
                            drawCircle(color = primaryColor, radius = 2.8.dp.toPx(), center = pt)
                        } else {
                            drawCircle(color = primaryColor.copy(alpha = 0.7f), radius = 2.dp.toPx(), center = pt)
                        }
                    }
                }

                // 3. Linha Secundária (se houver, ex: Diastólica)
                if (points.any { it.secondaryValue != null }) {
                    val secOffsets = points.mapIndexedNotNull { index, pt ->
                        pt.secondaryValue?.let { sec ->
                            val x = padX + (index * spacing)
                            val y = height - padY - ((sec - minVal) / range * drawH)
                            Offset(x, y)
                        }
                    }

                    if (secOffsets.isNotEmpty()) {
                        val secPath = Path().apply {
                            secOffsets.forEachIndexed { i, pt ->
                                if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y)
                            }
                        }

                        drawPath(
                            path = secPath,
                            color = secondaryColor,
                            style = Stroke(width = 2.dp.toPx())
                        )

                        secOffsets.forEachIndexed { i, pt ->
                            val isLast = i == secOffsets.size - 1
                            if (isLast) {
                                drawCircle(color = secondaryColor.copy(alpha = 0.25f), radius = 5.dp.toPx(), center = pt)
                                drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = pt)
                                drawCircle(color = secondaryColor, radius = 2.5.dp.toPx(), center = pt)
                            } else {
                                drawCircle(color = secondaryColor.copy(alpha = 0.7f), radius = 2.dp.toPx(), center = pt)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Legenda
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (secondaryLabel != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(primaryColor))
                            Spacer(Modifier.width(4.dp))
                            Text("Sistólica", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(secondaryColor))
                            Spacer(Modifier.width(4.dp))
                            Text("Diastólica", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Text(
                    text = "${points.size} medições",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
