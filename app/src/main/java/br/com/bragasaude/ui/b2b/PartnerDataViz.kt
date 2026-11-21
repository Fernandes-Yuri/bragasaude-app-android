package br.com.bragasaude.ui.b2b

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.util.Locale

/**
 * Anel de proporção com rótulo central.
 *
 * [centerLabel] permite mostrar o número absoluto (ex.: "2") ou um percentual já
 * formatado (ex.: "100%"). Sem rótulo explícito, o centro mostra o percentual, que é
 * a leitura mais direta da proporção — assim o detalhe de lembretes "1 de 1" vira
 * "100%" sem depender de quem chama. [contentDescription] deve trazer o contexto
 * falado (ex.: "2 de 2 ativos, 100%"); o padrão fala parte/total e percentual.
 */
@Composable
internal fun PartnerRing(
    part: Int,
    total: Int,
    centerLabel: String? = null,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    if (total <= 0 || part !in 0..total) return
    val ink = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val percent = ringPercentLabel(part, total)
    val label = centerLabel ?: percent
    val spoken = contentDescription ?: "$part de $total, $percent"
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold
    )
    Canvas(
        modifier.size(40.dp).semantics { this.contentDescription = spoken }
    ) {
        val stroke = 6.dp.toPx()
        val style = Stroke(stroke)
        val diameter = size.minDimension - stroke
        val inset = Offset(stroke / 2, stroke / 2)
        drawCircle(track, diameter / 2, style = style)
        drawArc(ink, -90f, 360f * part / total, false, inset, Size(diameter, diameter), style = style)
        if (label.isNotBlank()) {
            val layout = measurer.measure(AnnotatedString(label), style = labelStyle)
            drawText(
                layout,
                topLeft = Offset(
                    (size.width - layout.size.width) / 2f,
                    (size.height - layout.size.height) / 2f
                )
            )
        }
    }
}

/** Percentual inteiro usado no centro do anel e na descrição falada. */
internal fun ringPercentLabel(part: Int, total: Int): String {
    if (total <= 0) return ""
    return "${Math.round(part * 100f / total)}%"
}

/**
 * Trilha visível do progresso com contraste mínimo de 3:1 sobre o fundo informado
 * (WCAG 1.4.11, componentes não textuais). Começa translúcida (25%) e sobe a
 * opacidade só até atingir o mínimo; se o tom base não chegar lá, usa um neutro
 * preto/branco a 50% sobre o fundo.
 */
internal fun trackColorFor(seed: Color, surface: Color, minimum: Float = 3f): Color {
    for (step in 0..15) {
        val alpha = 0.25f + step * 0.05f
        val candidate = seed.copy(alpha = alpha).compositeOver(surface)
        if (contrastRatio(candidate, surface) >= minimum) return candidate
    }
    val neutral = if (surface.luminance() < 0.5f) Color.White else Color.Black
    return neutral.copy(alpha = 0.5f).compositeOver(surface)
}

/** Barra de progresso com trilha sempre visível e sem ponto final. */
@Composable
internal fun PartnerProgress(fraction: Float, showTrack: Boolean = true) {
    val ink = MaterialTheme.colorScheme.primary
    // surfaceContainerLow é o fundo real dos Cards e é o caso mais restritivo de
    // contraste; garantir 3:1 nele cobre também a superfície pura das folhas.
    val track = trackColorFor(
        MaterialTheme.colorScheme.onSurfaceVariant,
        MaterialTheme.colorScheme.surfaceContainerLow
    )
    val clamped = fraction.coerceIn(0f, 1f)
    Canvas(Modifier.fillMaxWidth().height(8.dp).clearAndSetSemantics {}) {
        val radius = CornerRadius(size.height / 2f)
        if (showTrack) drawRoundRect(track, cornerRadius = radius)
        if (clamped > 0f) {
            drawRoundRect(ink, size = Size(size.width * clamped, size.height), cornerRadius = radius)
        }
    }
}

/** Formata a leitura textual da sparkline; null quando não há série para resumir. */
internal fun sparklineSummaryLabel(values: List<Int>): String? {
    if (values.isEmpty()) return null
    return "máx. ${values.max()} · último dia ${values.last()}"
}

/**
 * Traço da atividade diária com resumo textual (máximo e último dia) sempre visível
 * abaixo do canvas. O leitor de tela ouve a série completa pelo canvas e o resumo
 * pelo texto, sem depender de "Ver detalhes diários".
 */
@Composable
internal fun PartnerSparkline(values: List<Int>) {
    if (values.isEmpty()) {
        Text("Sem atividade no período")
        return
    }
    val ink = MaterialTheme.colorScheme.primary
    val spoken = "Participantes ativos por dia, do mais antigo ao mais recente: ${values.joinToString()}."
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (values.size >= 2) {
            Canvas(
                Modifier.fillMaxWidth().height(40.dp).semantics { this.contentDescription = spoken }
            ) {
                val radius = 2.dp.toPx()
                val peak = values.max().coerceAtLeast(1)
                val points = values.mapIndexed { index, count ->
                    Offset(
                        radius + index * (size.width - 2 * radius) / values.lastIndex,
                        size.height - radius - (size.height - 2 * radius) * count / peak
                    )
                }
                points.zipWithNext().forEach { (from, to) -> drawLine(ink, from, to, radius) }
                points.forEach { drawCircle(ink, radius, it) }
            }
        }
        val summary = sparklineSummaryLabel(values)
        if (summary != null) {
            Text(
                text = summary,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Descrição falada da série diária, independente do botão de detalhes. */
internal fun dailyBarsDescription(values: List<Long?>, average: Double): String {
    if (values.isEmpty()) return "Sem registros no período."
    val counts = values.joinToString(", ") { it?.toString() ?: "sem registro" }
    val mean = String.format(Locale.forLanguageTag("pt-BR"), "%.1f", average)
    return "Série diária de ${values.size} dias, do mais antigo ao mais recente: $counts. " +
        "Média de $mean por dia, indicada pela linha tracejada."
}

/** Cada posição corresponde a um dia. Sem registro fica em branco e zero recebe uma marca na base. */
@Composable
internal fun PartnerDailyBars(values: List<Long?>, average: Double) {
    val ink = MaterialTheme.colorScheme.primary
    val meanInk = MaterialTheme.colorScheme.onSurface
    val spoken = dailyBarsDescription(values, average)
    Canvas(Modifier.fillMaxWidth().height(84.dp).semantics { this.contentDescription = spoken }) {
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
