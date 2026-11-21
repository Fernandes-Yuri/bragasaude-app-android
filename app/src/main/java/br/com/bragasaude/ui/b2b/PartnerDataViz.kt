package br.com.bragasaude.ui.b2b

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** O rótulo numérico fica junto da proporção, inclusive quando o total é desconhecido. */
@Composable
internal fun PartnerRing(part: Int, total: Int) {
    if (total <= 0 || part !in 0..total) return
    val ink = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Canvas(Modifier.size(40.dp)) {
        val stroke = 6.dp.toPx()
        val style = Stroke(stroke)
        val diameter = size.minDimension - stroke
        val inset = Offset(stroke / 2, stroke / 2)
        drawCircle(track, diameter / 2, style = style)
        drawArc(ink, -90f, 360f * part / total, false, inset, Size(diameter, diameter), style = style)
    }
}

@Composable
internal fun PartnerProgress(fraction: Float) {
    LinearProgressIndicator(progress = { fraction.coerceIn(0f, 1f) },
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {})
}

@Composable
internal fun PartnerSparkline(values: List<Int>) {
    if (values.size < 2) return
    val ink = MaterialTheme.colorScheme.primary
    val spoken = "Participantes ativos por dia, do mais antigo ao mais recente: ${values.joinToString()}."
    Canvas(Modifier.fillMaxWidth().height(40.dp).semantics { contentDescription = spoken }) {
        val radius = 2.dp.toPx()
        val peak = values.max().coerceAtLeast(1)
        val points = values.mapIndexed { index, count ->
            Offset(radius + index * (size.width - 2 * radius) / values.lastIndex,
                size.height - radius - (size.height - 2 * radius) * count / peak)
        }
        points.zipWithNext().forEach { (from, to) -> drawLine(ink, from, to, radius) }
        points.forEach { drawCircle(ink, radius, it) }
    }
}

/** Cada posição corresponde a um dia. Sem registro fica em branco e zero recebe uma marca na base. */
@Composable
internal fun PartnerDailyBars(values: List<Long?>, average: Double) {
    val ink = MaterialTheme.colorScheme.primary
    val meanInk = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(84.dp)) {
        if (values.isEmpty()) return@Canvas
        val peak = (values.filterNotNull().maxOrNull() ?: 0L).coerceAtLeast(1L)
        val slot = size.width / values.size
        val margin = 2.dp.toPx()
        val plotHeight = size.height - 2 * margin
        values.forEachIndexed { index, count ->
            if (count != null) {
                val height = (plotHeight * (count.toDouble() / peak).toFloat()).coerceAtLeast(1.dp.toPx())
                drawRect(ink, Offset((index + 0.15f) * slot, size.height - margin - height),
                    Size(slot * 0.7f, height))
            }
        }
        val y = size.height - margin - plotHeight * (average / peak).toFloat()
        drawLine(meanInk, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())))
    }
}
