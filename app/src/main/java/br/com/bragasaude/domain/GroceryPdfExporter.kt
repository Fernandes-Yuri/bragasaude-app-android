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

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val CONTENT_BOTTOM = 780f // preserva a zona do rodapé
    private const val FOOTER_Y = 820f

    fun generateAndShareGroceryPdf(
        context: Context,
        items: List<GroceryListItemEntity>,
        userName: String? = null
    ): Uri? {
        if (items.isEmpty()) return null

        // D-PDF2: toda a geração é defensiva — qualquer falha retorna null em vez de
        // derrubar a thread de UI (o caller já trata o null graciosamente).
        return try {
            val doc = PdfDocument()
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#BDBDBD")
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
            }

            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val dateStr = dateFormat.format(Date())

            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            var canvas = page.canvas

            // D-PDF2: quebra de página dinâmica. Finaliza a página atual (com rodapé)
            // e abre uma nova com cabeçalho de continuação.
            fun startNewPage() {
                drawPageFooter(canvas, textPaint, pageNum)
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                drawContinuationHeader(canvas, paint, textPaint, pageNum)
            }

            // 1 + 2. Cabeçalho verde e resumo de custo (página 1)
            var y = drawFirstPageSummary(canvas, paint, textPaint, userName, dateStr, items)

            // 3. Itens agrupados por corredor, paginando conforme o cursor y avança
            val grouped = items.groupBy { it.category }

            grouped.forEach { (corridor, corridorItems) ->
                // O título de corredor precisa de espaço para si + ao menos 1 item
                if (y > CONTENT_BOTTOM - 54f) startNewPage()

                y = drawCorridorTitle(canvas, paint, textPaint, corridor, y)

                corridorItems.forEach { item ->
                    if (y > CONTENT_BOTTOM) startNewPage()
                    y = drawItemRow(canvas, textPaint, borderPaint, item, y)
                }

                y += 12f
            }

            // 4. Campo de anotações (só na última página, se couber)
            if (y <= 710f) {
                textPaint.color = Color.parseColor("#757575")
                textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textPaint.textSize = 10f
                canvas.drawText("OUTROS ITENS DA CASA (ANOTAÇÃO MANUAL):", 40f, y + 10f, textPaint)

                for (i in 1..3) {
                    val lineY = y + 15f + (i * 18f)
                    if (lineY < FOOTER_Y - 6f) {
                        canvas.drawLine(40f, lineY, 555f, lineY, borderPaint)
                    }
                }
            }

            // 5. Rodapé da última página
            drawPageFooter(canvas, textPaint, pageNum)

            doc.finishPage(page)

            // D-PDF1/D-PDF2: grava no subdiretário de cache já autorizado pelo
            // file_paths.xml (shared_pdfs/), criando a pasta se necessário.
            val outputDir = File(context.cacheDir, "shared_pdfs").apply {
                if (!exists()) mkdirs()
            }
            val pdfFile = File(outputDir, "lista_compras_bragasaude_${System.currentTimeMillis()}.pdf")
            FileOutputStream(pdfFile).use { out ->
                doc.writeTo(out)
            }
            doc.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                pdfFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun drawFirstPageSummary(
        canvas: Canvas,
        paint: Paint,
        textPaint: Paint,
        userName: String?,
        dateStr: String,
        items: List<GroceryListItemEntity>
    ): Float {
        // 1. Cabeçalho verde
        paint.color = Color.parseColor("#1B5E20")
        canvas.drawRect(0f, 0f, 595f, 90f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 18f
        canvas.drawText("BRAGA SAÚDE • LISTA SEMANAL DE COMPRAS", 30f, 40f, textPaint)

        textPaint.textSize = 11f
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Planejamento Nutricional com Margem de Segurança (+20%) • Zero Desperdício", 30f, 65f, textPaint)

        // 2. Resumo e custo estimado
        val y = 115f
        val totalCost = items.sumOf { it.estimatedPriceBrl }
        val dailyAvg = totalCost / 7.0

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
        val costText = String.format(
            Locale.getDefault(),
            "Custo Total Estimado: R$ %.2f   •   Média Diária: ~R$ %.2f / dia (base feira/mercado)",
            totalCost,
            dailyAvg
        )
        canvas.drawText(costText, 45f, y + 25f, textPaint)

        return y + 75f
    }

    private fun drawContinuationHeader(
        canvas: Canvas,
        paint: Paint,
        textPaint: Paint,
        pageNum: Int
    ): Float {
        paint.color = Color.parseColor("#1B5E20")
        canvas.drawRect(0f, 0f, 595f, 36f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 11f
        canvas.drawText("BRAGA SAÚDE • LISTA SEMANAL DE COMPRAS (continuação)", 30f, 23f, textPaint)
        canvas.drawText("pág. $pageNum", 535f, 23f, textPaint)

        return 60f
    }

    private fun drawPageFooter(canvas: Canvas, textPaint: Paint, pageNum: Int) {
        textPaint.color = Color.parseColor("#9E9E9E")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 9f
        canvas.drawText(
            "Braga Saúde • Cuidado Contínuo e Longevidade Ativa • Documento de Autocuidado Familiar",
            90f,
            FOOTER_Y,
            textPaint
        )
        canvas.drawText("pág. $pageNum", 545f, FOOTER_Y, textPaint)
    }

    private fun drawCorridorTitle(
        canvas: Canvas,
        paint: Paint,
        textPaint: Paint,
        corridor: String,
        y: Float
    ): Float {
        paint.color = Color.parseColor("#2E7D32")
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(RectF(30f, y - 12f, 565f, y + 14f), 4f, 4f, paint)

        textPaint.color = Color.WHITE
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textPaint.textSize = 12f
        canvas.drawText(corridor.uppercase(Locale.getDefault()), 40f, y + 5f, textPaint)

        return y + 30f
    }

    private fun drawItemRow(
        canvas: Canvas,
        textPaint: Paint,
        borderPaint: Paint,
        item: GroceryListItemEntity,
        y: Float
    ): Float {
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

        return y + 24f
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
