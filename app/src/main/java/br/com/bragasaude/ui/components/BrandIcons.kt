package br.com.bragasaude.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ui.theme.TealPrimary

/**
 * Símbolo oficial da marca Braga Saúde: squircle esmeralda com traçado de pulso (ECG).
 * Desenhado em Canvas puro — sem depender de drawable XML.
 */
@Composable
fun ShieldEcgIcon(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 48.dp,
    shieldColor: Color = TealPrimary,
    ecgColor: Color = Color.White,
    contentDescription: String? = null
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val w = size.width
        val h = size.height
        val r = w * 0.22f   // raio do squircle

        // Squircle (rounded rect)
        drawRoundRect(
            color = shieldColor,
            cornerRadius = CornerRadius(r, r)
        )

        // Traçado ECG branco
        val ecgPath = Path().apply {
            moveTo(w * 0.10f, h * 0.50f)
            lineTo(w * 0.28f, h * 0.50f)
            lineTo(w * 0.36f, h * 0.26f)
            lineTo(w * 0.46f, h * 0.74f)
            lineTo(w * 0.54f, h * 0.34f)
            lineTo(w * 0.60f, h * 0.58f)
            lineTo(w * 0.68f, h * 0.50f)
            lineTo(w * 0.90f, h * 0.50f)
        }
        drawPath(
            path = ecgPath,
            color = ecgColor,
            style = Stroke(
                width = (sizeDp.toPx() * 0.07f).coerceAtLeast(3f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}



/**
 * Ícone de coração branco (outline) - 24dp por padrão
 */
@Composable
fun WhiteHeartIcon(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 24.dp,
    tint: Color = Color.White,
    contentDescription: String? = null
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val width = size.width
        val height = size.height
        
        val heartPath = Path().apply {
            moveTo(width * 0.5f, height * 0.8f)
            cubicTo(
                width * 0.2f, height * 0.5f,
                width * 0.1f, height * 0.1f,
                width * 0.5f, height * 0.3f
            )
            cubicTo(
                width * 0.9f, height * 0.1f,
                width * 0.8f, height * 0.5f,
                width * 0.5f, height * 0.8f
            )
            close()
        }
        
        drawPath(
            path = heartPath,
            color = tint,
            style = Stroke(width = 2.dp.toPx())
        )
    }
}

/**
 * Ícone de cruz vermelha (símbolo de emergência/impacto) - 24dp por padrão
 */
@Composable
fun RedCrossIcon(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 24.dp,
    tint: Color = Color(0xFFDC2626),
    contentDescription: String? = null
) {
    Canvas(modifier = modifier.size(sizeDp)) {
        val width = size.width
        val height = size.height
        val barThickness = width * 0.3f
        
        // Barra vertical
        drawRect(
            color = tint,
            topLeft = Offset((width - barThickness) / 2f, height * 0.1f),
            size = Size(barThickness, height * 0.8f)
        )
        
        // Barra horizontal
        drawRect(
            color = tint,
            topLeft = Offset(width * 0.1f, (height - barThickness) / 2f),
            size = Size(width * 0.8f, barThickness)
        )
    }
}

/**
 * Coração com linha ECG mantido para retrocompatibilidade
 */
@Composable
fun HeartEcgIcon(
    modifier: Modifier = Modifier,
    sizeDp: Dp = 48.dp,
    heartColor: Color = TealPrimary,
    ecgColor: Color = Color.White,
    contentDescription: String? = null
) {
    ShieldEcgIcon(
        modifier = modifier,
        sizeDp = sizeDp,
        shieldColor = heartColor,
        ecgColor = ecgColor,
        contentDescription = contentDescription
    )
}
