package br.com.bragasaude.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmeraldDark
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class ChartPeriod(val label: String, val days: Int) {
    SEVEN_DAYS("7D", 7), FIFTEEN_DAYS("15D", 15), THIRTY_DAYS("30D", 30)
}

/**
 * Faixa de normalidade clínica (SBC/OMS) usada quando o gráfico plota duas séries
 * (Sistólica + Diastólica). A banda sombreada cobre 60 a 120 mmHg, englobando a
 * zona ideal de ambas as curvas: Sistólica 90–120 e Diastólica 60–80.
 */
private const val DUAL_NORMAL_BAND_TOP = 120.0
private const val DUAL_NORMAL_BAND_BOTTOM = 60.0

@Composable
fun SimpleTrendChart(
    data: List<Double>,
    modifier: Modifier = Modifier,
    label: String? = null,
    color: Color = MaterialTheme.colorScheme.primary,
    targetValue: Double? = null,
    unit: String = "",
    showPeriodSelector: Boolean = false,
    onPeriodSelected: (ChartPeriod) -> Unit = {},
    timestamps: List<String> = emptyList(),
    referenceRange: ClosedFloatingPointRange<Double>? = null,
    secondaryData: List<Double> = emptyList(),
    secondaryColor: Color = BragaEmeraldDark,
    secondaryLabel: String? = null,
    chartHeight: Dp = 220.dp
) {
    val dual = secondaryData.isNotEmpty()
    val primaryLegend = label.orEmpty().ifEmpty { "Série principal" }
    val secondaryLegend = secondaryLabel ?: "Diastólica"

    var period by remember { mutableStateOf(ChartPeriod.SEVEN_DAYS) }
    val hasDates = timestamps.size == data.size && timestamps.isNotEmpty() && timestamps.all { runCatching { Instant.parse(it) }.isSuccess }
    val samples = remember(data, secondaryData, timestamps, period, hasDates, showPeriodSelector, dual) {
        val cutoff = java.time.LocalDate.now().minusDays(period.days.toLong() - 1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        data.mapIndexedNotNull { index, value ->
            when {
                !value.isFinite() -> null
                showPeriodSelector && hasDates && Instant.parse(timestamps[index]).isBefore(cutoff) -> null
                dual && secondaryData.getOrNull(index)?.isFinite() != true -> null
                else -> index to value
            }
        }.let { if (showPeriodSelector && !hasDates) it.takeLast(period.days) else it }
    }
    var selected by remember(samples) { mutableIntStateOf((samples.size - 1).coerceAtLeast(0)) }

    val surface = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current

    // ---- Seleção atual (formatada fora do canvas para tooltip + semântica) ----
    val selectedIndex = selected.coerceIn(0, (samples.size - 1).coerceAtLeast(0))
    val current = samples.getOrNull(selectedIndex)
    val currentSecondary = current?.let { secondaryData.getOrNull(it.first) }
    val formatterDate = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(ZoneId.systemDefault()) }
    val formatterTime = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }
    val dateText = current?.first?.let { idx ->
        timestamps.getOrNull(idx)?.let {
            runCatching { formatterDate.format(Instant.parse(it)) }.getOrNull()
        }
    }
    val timeText = current?.first?.let { idx ->
        timestamps.getOrNull(idx)?.let {
            runCatching { formatterTime.format(Instant.parse(it)) }.getOrNull()
        }
    }
    val primaryText = current?.let { formatChartValue(it.second) }.orEmpty()
    val secondaryText = currentSecondary?.let { formatChartValue(it) }.orEmpty()
    val valueText = if (dual && currentSecondary != null) "$primaryText/$secondaryText $unit" else "$primaryText $unit"

    // ---- Mapeamento valor -> pixel (compartilhado por canvas e tooltip) ----
    val insetPx = with(density) { 12.dp.toPx() }
    val canvasHeightPx = with(density) { chartHeight.toPx() }
    val bottomPx = canvasHeightPx - insetPx
    val guides = listOfNotNull(targetValue, referenceRange?.start, referenceRange?.endInclusive) +
        if (dual) listOf(DUAL_NORMAL_BAND_TOP, DUAL_NORMAL_BAND_BOTTOM) else emptyList()
    val allValues = samples.map { it.second } +
        samples.mapNotNull { secondaryData.getOrNull(it.first) } +
        guides
    val minValue = allValues.minOrNull() ?: 0.0
    val maxValue = allValues.maxOrNull() ?: 1.0
    val rangeValue = (maxValue - minValue).coerceAtLeast(1.0)
    val usableHeight = (canvasHeightPx - 2 * insetPx).coerceAtLeast(1f)

    fun yFor(value: Double): Float = bottomPx - ((value - minValue) / rangeValue * usableHeight).toFloat()

    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            label?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (showPeriodSelector) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChartPeriod.entries.forEach { option ->
                        FilterChip(
                            selected = period == option,
                            onClick = { period = option; onPeriodSelected(option) },
                            label = { Text(if (hasDates) option.label else "${option.days} registros") },
                            modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(50)
                        )
                    }
                }
            }
            if (samples.isEmpty()) {
                Text("Sem registros neste período.", style = MaterialTheme.typography.bodyMedium)
            } else {
                if (dual) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ChartLegendItem(primaryLegend, color)
                        ChartLegendItem(secondaryLegend, secondaryColor)
                    }
                }

                val tooltipWidth = 180.dp
                val tooltipHeight = 68.dp
                val tooltipWidthPx = with(density) { tooltipWidth.toPx() }
                val tooltipHeightPx = with(density) { tooltipHeight.toPx() }
                val spacingPx = with(density) { 10.dp.toPx() }

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val widthPx = constraints.maxWidth.toFloat()
                    val count = samples.size
                    val selX = if (count == 1) widthPx / 2f else insetPx + selectedIndex * (widthPx - 2 * insetPx) / (count - 1)
                    val selY = current?.let { yFor(it.second) } ?: bottomPx

                    val tooltipX = (selX - tooltipWidthPx / 2f).coerceIn(0f, (widthPx - tooltipWidthPx).coerceAtLeast(0f))
                    val above = selY - tooltipHeightPx - spacingPx
                    val tooltipY = if (above >= 0f) above else (selY + spacingPx).coerceAtMost((canvasHeightPx - tooltipHeightPx).coerceAtLeast(0f))

                    Canvas(
                        Modifier
                            .fillMaxWidth()
                            .height(chartHeight)
                            .semantics {
                                contentDescription = buildString {
                                    append(label.orEmpty())
                                    if (isNotEmpty()) append(": ")
                                    append(valueText)
                                    dateText?.let { append(". $it") }
                                    timeText?.let { append(" às $it") }
                                }
                            }
                            .pointerInput(samples) {
                                detectTapGestures { point ->
                                    selected = (((point.x - insetPx) / (size.width - insetPx * 2).coerceAtLeast(1f)) * (samples.size - 1)).roundToInt().coerceIn(samples.indices)
                                }
                            }
                            .pointerInput(samples) {
                                detectHorizontalDragGestures { change, _ ->
                                    selected = (((change.position.x - insetPx) / (size.width - insetPx * 2).coerceAtLeast(1f)) * (samples.size - 1)).roundToInt().coerceIn(samples.indices)
                                    change.consume()
                                }
                            }
                    ) {
                        val inset = 12.dp.toPx()
                        val bottom = size.height - inset

                        // 1. Faixa de normalidade clínica (SBC/OMS) ao fundo.
                        if (dual) {
                            val bandTop = yFor(DUAL_NORMAL_BAND_TOP)
                            val bandBottom = yFor(DUAL_NORMAL_BAND_BOTTOM)
                            drawRect(
                                BragaMint.copy(alpha = 0.6f),
                                Offset(inset, bandTop),
                                Size(size.width - 2 * inset, (bandBottom - bandTop).coerceAtLeast(0f))
                            )
                        } else referenceRange?.let {
                            drawRect(
                                color.copy(alpha = 0.09f),
                                Offset(inset, yFor(it.endInclusive)),
                                Size(size.width - 2 * inset, yFor(it.start) - yFor(it.endInclusive))
                            )
                        }

                        targetValue?.let {
                            drawLine(color.copy(alpha = 0.4f), Offset(inset, yFor(it)), Offset(size.width - inset, yFor(it)), 1.dp.toPx())
                        }

                        fun xAt(index: Int) = if (count == 1) size.width / 2 else inset + index * (size.width - 2 * inset) / (count - 1)

                        val points = samples.mapIndexed { index, sample -> Offset(xAt(index), yFor(sample.second)) }

                        // 2. Curva suave (Spline cúbica de Bézier por interpolação de pontos médios).
                        val path = smoothPath(points)

                        if (!dual) {
                            // Preenchimento em gradiente vertical abaixo da curva principal.
                            val fill = Path().apply {
                                addPath(path)
                                lineTo(points.last().x, bottom)
                                lineTo(points.first().x, bottom)
                                close()
                            }
                            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
                        } else {
                            // 3. Série dupla: área sombreada entre as duas curvas (pressão de pulso).
                            val secondaryPoints = samples.mapIndexed { index, sample ->
                                Offset(xAt(index), yFor(secondaryData.getValue(sample.first)))
                            }
                            val secondaryPath = smoothPath(secondaryPoints)
                            val bandPath = Path().apply {
                                addPath(path)
                                appendReversedSmooth(secondaryPoints)
                                close()
                            }
                            drawPath(
                                bandPath,
                                Brush.verticalGradient(
                                    colors = listOf(color.copy(alpha = 0.22f), secondaryColor.copy(alpha = 0.06f)),
                                    startY = yFor(maxValue),
                                    endY = yFor(minValue)
                                )
                            )
                            drawPath(secondaryPath, secondaryColor, style = Stroke(2.5.dp.toPx()))
                            secondaryPoints.forEach { drawCircle(secondaryColor, 3.dp.toPx(), it) }
                        }

                        drawPath(path, color, style = Stroke(2.5.dp.toPx()))
                        points.forEach { drawCircle(color, 3.dp.toPx(), it) }

                        // 4. Marcador flutuante da seleção atual (scrubbing).
                        val marker = points[selectedIndex]
                        drawLine(color.copy(alpha = 0.5f), Offset(marker.x, inset), Offset(marker.x, bottom), 1.dp.toPx())
                        drawCircle(surface, 7.dp.toPx(), marker)
                        drawCircle(color, 4.dp.toPx(), marker)
                        if (dual) {
                            val secondaryMarker = secondaryData.getOrNull(samples[selectedIndex].first)?.let { Offset(marker.x, yFor(it)) }
                            secondaryMarker?.let {
                                drawCircle(secondaryColor, 4.dp.toPx(), it)
                                drawCircle(surface, 2.dp.toPx(), it)
                            }
                        }
                    }

                    // 5. Balão suspenso com data, horário e valores.
                    Surface(
                        modifier = Modifier
                            .offset { IntOffset(tooltipX.roundToInt(), tooltipY.roundToInt()) }
                            .width(tooltipWidth),
                        shape = RoundedCornerShape(14.dp),
                        color = BragaCardSurface,
                        border = BorderStroke(1.dp, BragaCardBorder),
                        shadowElevation = 6.dp
                    ) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text(
                                text = listOfNotNull(dateText, timeText?.takeIf { dateText != null }).joinToString(" ") { it }.ifEmpty { "Registro ${selectedIndex + 1}" },
                                style = MaterialTheme.typography.labelLarge,
                                color = BragaTextSecondary
                            )
                            Text(
                                text = valueText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BragaTextPrimary
                            )
                        }
                    }
                }

                if (dual) {
                    Text(
                        "Faixa normal SBC/OMS: Sistólica 90 a 120 e Diastólica 60 a 80 mmHg",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else referenceRange?.let {
                    Text("Faixa geral: ${it.start.toInt()}–${it.endInclusive.toInt()} $unit", style = MaterialTheme.typography.labelLarge)
                }
                Text(
                    "${samples.size} registros • arraste para consultar",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatChartValue(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else "%.2f".format(value)

/** Spline cúbica: tangentes horizontais nos pontos médios mantêm a curva suave e sem quinas. */
private fun smoothPath(points: List<Offset>): Path = Path().apply {
    if (points.isEmpty()) return@apply
    moveTo(points.first().x, points.first().y)
    points.zipWithNext().forEach { (a, b) ->
        val middle = (a.x + b.x) / 2
        cubicTo(middle, a.y, middle, b.y, b.x, b.y)
    }
}

/** Anexa a mesma curva no sentido inverso, fechando uma banda entre duas séries. */
private fun Path.appendReversedSmooth(points: List<Offset>) {
    if (points.isEmpty()) return
    lineTo(points.last().x, points.last().y)
    points.zipWithNext().reversed().forEach { (a, b) ->
        val middle = (a.x + b.x) / 2
        cubicTo(middle, b.y, middle, a.y, a.x, a.y)
    }
    close()
}

@Composable
private fun ChartLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = BragaTextSecondary)
    }
}
