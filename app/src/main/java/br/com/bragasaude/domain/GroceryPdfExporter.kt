package br.com.bragasaude.domain

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import br.com.bragasaude.data.local.GroceryListItemEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GroceryPdfExporter {

    fun generateAndShareGroceryPdf(
        context: Context,
        items: List<GroceryListItemEntity>,
        userName: String? = null
    ): Uri? {
        if (items.isEmpty()) return null

        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 (595 x 842 pt)
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // 1. CABEÇALHO VERDE
        paint.color = Color.parseColor("#1B5E20") // Verde Saúde Braga
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 18f
        canvas.drawText("BRAGA SAÚDE • LISTA SEMANAL DE COMPRAS", 30f, 40f, textPaint)

        textPaint.textSize = 11f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Planejamento Nutricional com Margem de Segurança (+20%) • Zero Desperdício", 30f, 65f, textPaint)

        // 2. RESUMO E CUSTO ESTIMADO
        var y = 115f
        val totalCost = items.sumOf { it.estimatedPriceBrl }
        val dailyAvg = totalCost / 7.0
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateStr = dateFormat.format(Date())

        paint.color = Color.parseColor("#F1F8E9")
        val bgRect = RectF(30f, y - 15f, 565f, y + 45f)
        canvas.drawRoundRect(bgRect, 8f, 8f, paint)

        textPaint.color = Color.parseColor("#1B5E20")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 12f
        canvas.drawText("Beneficiário: ${userName ?: "Plano Familiar"}   |   Data: $dateStr", 45f, y + 5f, textPaint)

        textPaint.color = Color.parseColor("#33691E")
        textPaint.textSize = 11f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val costText = String.format(Locale.getDefault(), "Custo Total Estimado: R$ %.2f   •   Média Diária: ~R$ %.2f / dia (base feira/mercado)", totalCost, dailyAvg)
        canvas.drawText(costText, 45f, y + 25f, textPaint)

        y += 75f

        // 3. ITENS AGRUPADOS POR CORREDOR
        val grouped = items.groupBy { it.category }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BDBDBD")
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }

        grouped.forEach { (corridor, corridorItems) ->
            // Título do Corredor
            paint.color = Color.parseColor("#2E7D32")
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(RectF(30f, y - 12f, 565f, y + 14f), 4f, 4f, paint)

            textPaint.color = Color.WHITE
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 12f
            canvas.drawText(corridor.uppercase(Locale.getDefault()), 40f, y + 5f, textPaint)

            y += 30f

            corridorItems.forEach { item ->
                // Checkbox
                val checkRect = RectF(40f, y - 9f, 52f, y + 3f)
                canvas.drawRoundRect(checkRect, 2f, 2f, borderPaint)

                // Nome do Alimento
                textPaint.color = Color.parseColor("#212121")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textPaint.textSize = 11f
                canvas.drawText(item.foodName, 60f, y, textPaint)

                // Embalagem / Peso
                textPaint.color = Color.parseColor("#616161")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                textPaint.textSize = 10f
                canvas.drawText("Comprar: ${item.purchaseUnitText}", 260f, y, textPaint)

                // Preço Estimado
                textPaint.color = Color.parseColor("#1B5E20")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                val itemPrice = String.format(Locale.getDefault(), "R$ %.2f", item.estimatedPriceBrl)
                canvas.drawText(itemPrice, 500f, y, textPaint)

                // Linha pontilhada divisória
                canvas.drawLine(40f, y + 8f, 555f, y + 8f, borderPaint)

                y += 24f
            }

            y += 12f
        }

        // 4. CAMPO DE ANOTAÇÕES
        if (y < 730f) {
            textPaint.color = Color.parseColor("#757575")
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 10f
            canvas.drawText("OUTROS ITENS DA CASA (ANOTAÇÃO MANUAL):", 40f, y + 10f, textPaint)

            for (i in 1..3) {
                val lineY = y + 15f + (i * 18f)
                if (lineY < 790f) {
                    canvas.drawLine(40f, lineY, 555f, lineY, borderPaint)
                }
            }
        }

        // 5. RODAPÉ
        textPaint.color = Color.parseColor("#9E9E9E")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 9f
        canvas.drawText("Braga Saúde • Cuidado Contínuo e Longevidade Ativa • Documento de Autocuidado Familiar", 90f, 820f, textPaint)

        doc.finishPage(page)

        // Grava no cache
        val pdfFile = File(context.cacheDir, "lista_compras_bragasaude.pdf")
        FileOutputStream(pdfFile).use { out ->
            doc.writeTo(out)
        }
        doc.close()

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )

        return uri
    }

    fun sharePdfUri(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Lista Semanal de Compras • Braga Saúde")
            putExtra(Intent.EXTRA_TEXT, "Segue a lista semanal de compras com margem de segurança do Braga Saúde.")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Compartilhar Lista de Compras via...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
