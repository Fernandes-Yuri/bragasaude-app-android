package br.com.bragasaude.domain

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import br.com.bragasaude.data.local.FamilyMessageEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import android.widget.Toast

/**
 * D47 — Exportação da conversa familiar do ciclo vigente (janela de 24h).
 *
 * Gera um PDF A4 de alto contraste (padrão geriátrico) com remetente, data/hora
 * e texto de cada mensagem, seguido do aviso de retenção. O compartilhamento usa
 * a share sheet nativa (WhatsApp, e-mail, Drive etc.).
 *
 * Importante: a exportação cobre apenas o que ainda está dentro da janela de 24h —
 * mensagens expiradas ou apagadas não aparecem (as telas já as filtram).
 */
object FamilyConversationPdfExporter {

    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 40f
    private const val BOTTOM_LIMIT = 780f

    private val RETENTION_NOTICE =
        "As mensagens da família ficam disponíveis por 24 horas no aplicativo e depois " +
        "são apagadas automaticamente. Este documento é um registro pessoal exportado " +
        "pelo usuário dentro dessa janela."

    fun generateConversationPdf(
        context: Context,
        messages: List<FamilyMessageEntity>,
        familyLabel: String? = null
    ): Uri? {
        val now = System.currentTimeMillis()
        val active = messages.filter { it.deletedAt == null && it.expiresAt > now }
        if (active.isEmpty()) return null
        cleanupTemporaryExports(context)

        // Ordem cronológica (a tela exibe da mais recente para a mais antiga)
        val ordered = active.sortedBy { it.sentAt }
        val timeFormat = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault())
        val dateStr = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

        val doc = PdfDocument()
        var pageNumber = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
        var canvas = page.canvas

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

        fun drawHeader() {
            paint.color = Color.parseColor("#1B5E20") // Verde Saúde Braga
            canvas.drawRect(0f, 0f, PAGE_W.toFloat(), 90f, paint)

            textPaint.color = Color.WHITE
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 18f
            canvas.drawText("BRAGA SAÚDE • CONVERSA DA FAMÍLIA", MARGIN, 40f, textPaint)

            textPaint.textSize = 11f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(
                "Exportada em $dateStr • Mensagens disponíveis neste aparelho",
                MARGIN, 65f, textPaint
            )
        }

        fun drawFooter() {
            textPaint.color = Color.parseColor("#9E9E9E")
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.textSize = 9f
            canvas.drawText(
                "Braga Saúde • Documento pessoal de autocuidado familiar • Página $pageNumber",
                MARGIN, 820f, textPaint
            )
        }

        fun finishPageAndStartNext() {
            drawFooter()
            doc.finishPage(page)
            pageNumber++
            page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNumber).create())
            canvas = page.canvas
            drawHeader()
        }

        drawHeader()

        var y = 120f

        // Aviso de retenção em destaque (transparência D47)
        paint.color = Color.parseColor("#F1F8E9")
        canvas.drawRoundRect(RectF(MARGIN - 10f, y - 15f, PAGE_W - MARGIN + 10f, y + 42f), 8f, 8f, paint)
        textPaint.color = Color.parseColor("#33691E")
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = 10f
        val noticeLines = wrapText(RETENTION_NOTICE, textPaint, PAGE_W - 2 * MARGIN)
        var noticeY = y + 2f
        for (line in noticeLines.take(3)) {
            canvas.drawText(line, MARGIN, noticeY, textPaint)
            noticeY += 13f
        }
        y += 70f

        // Mensagens em ordem cronológica
        for (msg in ordered) {
            val senderLine = "${msg.senderName}  •  ${timeFormat.format(Date(msg.sentAt))}"
            textPaint.textSize = 11f
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val bodyLines = wrapText(msg.messageText, textPaint, PAGE_W - 2 * MARGIN - 8f)

            val blockHeight = 50f
            if (y + blockHeight > BOTTOM_LIMIT) {
                finishPageAndStartNext()
                y = 115f
            }

            // Remetente + data/hora
            textPaint.color = Color.parseColor("#1B5E20")
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textPaint.textSize = 11f
            for (line in wrapText(senderLine, textPaint, PAGE_W - 2 * MARGIN)) {
                if (y + 16f > BOTTOM_LIMIT) {
                    finishPageAndStartNext()
                    y = 115f
                    textPaint.color = Color.parseColor("#1B5E20")
                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    textPaint.textSize = 11f
                }
                canvas.drawText(line, MARGIN, y, textPaint)
                y += 16f
            }

            // Texto da mensagem
            textPaint.color = Color.parseColor("#212121")
            textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textPaint.textSize = 11f
            for (line in bodyLines) {
                if (y + 15f > BOTTOM_LIMIT) {
                    finishPageAndStartNext()
                    y = 115f
                    textPaint.color = Color.parseColor("#212121")
                    textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    textPaint.textSize = 11f
                }
                canvas.drawText(line, MARGIN + 8f, y, textPaint)
                y += 15f
            }

            // Divisor sutil
            paint.color = Color.parseColor("#E0E0E0")
            canvas.drawLine(MARGIN, y + 2f, PAGE_W - MARGIN, y + 2f, paint)
            y += 14f
        }

        drawFooter()
        doc.finishPage(page)

        val dir = File(context.cacheDir, "family_exports").apply { mkdirs() }
        val pdfFile = File(dir, "${active.minOf { it.expiresAt }}_${java.util.UUID.randomUUID()}.pdf")
        try {
            FileOutputStream(pdfFile).use { out -> doc.writeTo(out) }
        } catch (error: Exception) {
            pdfFile.delete()
            throw error
        } finally {
            doc.close()
        }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            pdfFile
        )
    }

    fun sharePdfUri(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Conversa da Família • Braga Saúde")
            putExtra(
                Intent.EXTRA_TEXT,
                "Conversa da família exportada do Braga Saúde. " +
                    "No aplicativo, as mensagens ficam disponíveis por 24 horas."
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Compartilhar conversa da família via...")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun exportAndShare(context: Context, messages: List<FamilyMessageEntity>, scope: CoroutineScope) {
        scope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { generateConversationPdf(context, messages) }
                if (uri == null) Toast.makeText(context, "Não há mensagens válidas para exportar.", Toast.LENGTH_LONG).show()
                else sharePdfUri(context, uri)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(context, "Não foi possível exportar a conversa. Tente novamente.", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun cleanupTemporaryExports(context: Context, now: Long = System.currentTimeMillis()) {
        File(context.cacheDir, "conversa_familia_bragasaude.pdf").delete()
        File(context.cacheDir, "family_exports").listFiles()?.forEach { file ->
            val expires = file.name.substringBefore('_').toLongOrNull()
            if (expires == null || expires <= now) file.delete()
        }
    }

    private fun wrapText(text: String, paint: Paint, width: Float): List<String> =
        text.split('\n').flatMap { paragraph ->
            if (paragraph.isEmpty()) listOf("") else buildList {
                var remaining = paragraph
                while (remaining.isNotEmpty()) {
                    var count = paint.breakText(remaining, true, width, null).coerceAtLeast(1)
                    if (count < remaining.length) {
                        val space = remaining.lastIndexOf(' ', count - 1)
                        if (space > 0) count = space
                        if (count > 1 && remaining[count - 1].isHighSurrogate()) count--
                    }
                    add(remaining.take(count))
                    remaining = remaining.drop(count).trimStart(' ')
                }
            }
        }
}
