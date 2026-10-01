package br.com.bragasaude.domain

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import br.com.bragasaude.R

/** Identidade compartilhada dos documentos pessoais exportados. */
object PdfBranding {
    fun watermark(context: Context, canvas: Canvas, width: Int = 595, height: Int = 842) {
        val logo = ContextCompat.getDrawable(context, R.drawable.ic_logo_braga)?.mutate() ?: return
        val size = minOf(width, height) / 2
        logo.alpha = 22 // Cerca de 9% de opacidade: visível sem competir com os registros.
        logo.setBounds((width - size) / 2, (height - size) / 2, (width + size) / 2, (height + size) / 2)
        logo.draw(canvas)
    }

    fun header(context: Context, canvas: Canvas, title: String) {
        watermark(context, canvas)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 105, 92) }
        canvas.drawRoundRect(40f, 30f, 555f, 95f, 12f, 12f, paint)
        ContextCompat.getDrawable(context, R.drawable.ic_logo_braga)?.mutate()?.apply {
            setBounds(50, 43, 88, 81)
            draw(canvas)
        }
        paint.color = Color.WHITE
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 15f
        canvas.drawText("Braga Saúde", 100f, 54f, paint)
        paint.textSize = 10f
        paint.typeface = Typeface.DEFAULT
        PdfTextLayout.wrap(title, 440f, paint::measureText).take(2).forEachIndexed { index, line ->
            canvas.drawText(line, 100f, 73f + index * 13f, paint)
        }
    }

    fun footer(canvas: Canvas, page: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 8f; color = Color.rgb(71, 85, 105) }
        canvas.drawLine(40f, 797f, 555f, 797f, paint)
        canvas.drawText("Registros pessoais • Sem finalidade diagnóstica", 40f, 814f, paint)
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Página $page", 555f, 814f, paint)
    }
}
