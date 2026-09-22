package br.com.bragasaude.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class ChartPeriod(val label: String, val days: Int) {
    SEVEN_DAYS("7D", 7), FIFTEEN_DAYS("15D", 15), THIRTY_DAYS("30D", 30)
}

@Composable
fun SimpleTrendChart(
    data: List<Double>, modifier: Modifier = Modifier, label: String? = null,
    color: Color = MaterialTheme.colorScheme.primary, targetValue: Double? = null,
    unit: String = "", showPeriodSelector: Boolean = false,
    onPeriodSelected: (ChartPeriod) -> Unit = {}, timestamps: List<String> = emptyList(),
    referenceRange: ClosedFloatingPointRange<Double>? = null
) {
    var period by remember { mutableStateOf(ChartPeriod.SEVEN_DAYS) }
    val hasDates = timestamps.size == data.size && timestamps.isNotEmpty() && timestamps.all { runCatching { Instant.parse(it) }.isSuccess }
    val samples = remember(data, timestamps, period, hasDates, showPeriodSelector) {
        val cutoff = java.time.LocalDate.now().minusDays(period.days.toLong() - 1).atStartOfDay(ZoneId.systemDefault()).toInstant()
        data.mapIndexedNotNull { index, value ->
            if (!value.isFinite()) null
            else if (showPeriodSelector && hasDates && Instant.parse(timestamps[index]).isBefore(cutoff)) null
            else index to value
        }.let { if (showPeriodSelector && !hasDates) it.takeLast(period.days) else it }
    }
    var selected by remember(samples) { mutableIntStateOf((samples.size - 1).coerceAtLeast(0)) }
    val surface = MaterialTheme.colorScheme.surface
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            label?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            if (showPeriodSelector) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChartPeriod.entries.forEach { option ->
                        FilterChip(selected = period == option, onClick = { period = option; onPeriodSelected(option) },
                            label = { Text(if (hasDates) option.label else "${option.days} registros") },
                            modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(50))
                    }
                }
            }
            if (samples.isEmpty()) {
                Text("Sem registros neste período.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val current = samples[selected.coerceIn(samples.indices)]
                val formatted = if (current.second % 1.0 == 0.0) current.second.toInt().toString() else "%.2f".format(current.second)
                val date = timestamps.getOrNull(current.first)?.let {
                    runCatching { DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(it)) }.getOrNull()
                }
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("$formatted $unit", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(date ?: "Registro ${current.first + 1}", style = MaterialTheme.typography.labelLarge)
                    }
                }
                Canvas(Modifier.fillMaxWidth().height(160.dp)
                    .semantics { contentDescription = "${label.orEmpty()}: $formatted $unit. ${date.orEmpty()}" }
                    .pointerInput(samples) {
                        detectTapGestures { point ->
                            val inset = 12.dp.toPx()
                            selected = (((point.x - inset) / (size.width - inset * 2).coerceAtLeast(1f)) * (samples.size - 1)).roundToInt().coerceIn(samples.indices)
                        }
                    }
                    .pointerInput(samples) {
                        detectHorizontalDragGestures { change, _ ->
                            val inset = 12.dp.toPx()
                            selected = (((change.position.x - inset) / (size.width - inset * 2).coerceAtLeast(1f)) * (samples.size - 1)).roundToInt().coerceIn(samples.indices)
                            change.consume()
                        }
                    }) {
                    val inset = 12.dp.toPx()
                    val bottom = size.height - inset
                    val guides = listOfNotNull(targetValue, referenceRange?.start, referenceRange?.endInclusive)
                    val min = (samples.map { it.second } + guides).minOrNull() ?: 0.0
                    val max = (samples.map { it.second } + guides).maxOrNull() ?: 1.0
                    val range = (max - min).coerceAtLeast(1.0)
                    fun y(value: Double) = bottom - ((value - min) / range * (size.height - 2 * inset)).toFloat()
                    referenceRange?.let {
                        drawRect(color.copy(alpha = 0.09f), Offset(inset, y(it.endInclusive)), Size(size.width - 2 * inset, y(it.start) - y(it.endInclusive)))
                    }
                    targetValue?.let { drawLine(color.copy(alpha = 0.4f), Offset(inset, y(it)), Offset(size.width - inset, y(it)), 1.dp.toPx()) }
                    val points = samples.mapIndexed { index, sample ->
                        Offset(if (samples.size == 1) size.width / 2 else inset + index * (size.width - 2 * inset) / (samples.size - 1), y(sample.second))
                    }
                    val path = Path().apply {
                        moveTo(points.first().x, points.first().y)
                        points.zipWithNext().forEach { (a, b) ->
                            // Horizontal handles keep the curve inside each pair of measured extrema.
                            val middle = (a.x + b.x) / 2
                            cubicTo(middle, a.y, middle, b.y, b.x, b.y)
                        }
                    }
                    val fill = Path().apply { addPath(path); lineTo(points.last().x, bottom); lineTo(points.first().x, bottom); close() }
                    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), Color.Transparent)))
                    drawPath(path, color, style = Stroke(2.5.dp.toPx()))
                    points.forEach { drawCircle(color, 3.dp.toPx(), it) }
                    val point = points[selected.coerceIn(points.indices)]
                    drawLine(color.copy(alpha = 0.5f), Offset(point.x, inset), Offset(point.x, bottom), 1.dp.toPx())
                    drawCircle(surface, 7.dp.toPx(), point)
                    drawCircle(color, 4.dp.toPx(), point)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { selected-- }, enabled = selected > 0) { Text("Anterior") }
                    TextButton(onClick = { selected++ }, enabled = selected < samples.lastIndex) { Text("Próximo") }
                }
                referenceRange?.let { Text("Faixa geral: ${it.start.toInt()}–${it.endInclusive.toInt()} $unit", style = MaterialTheme.typography.labelLarge) }
                Text("${samples.size} registros • toque para consultar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
