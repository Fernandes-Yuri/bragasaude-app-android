package br.com.bragasaude.domain

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import br.com.bragasaude.data.local.ExamEntity
import br.com.bragasaude.data.local.ExamItemEntity
import br.com.bragasaude.data.remote.model.RemoteProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Organiza registros informados pelo usuário e arquivos originais, sem interpretar resultados. */
class MedicalDossierCompiler(private val context: Context) {
    data class CompilationResult(
        val pdfFile: File,
        val totalPagesCount: Int,
        val dashboardPagesCount: Int,
        val attachedPagesCount: Int,
        val fileSizeBytes: Long
    )

    suspend fun compileDossier(
        profile: RemoteProfile,
        exams: List<ExamEntity>,
        examItems: List<ExamItemEntity>,
        resolveFile: suspend (String) -> File? = { path -> File(path).takeIf { it.isFile } }
    ): CompilationResult = withContext(Dispatchers.IO) {
        val document = PdfDocument()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.rgb(30, 41, 59) }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        var pageNumber = 1
        var page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        var y = 120f
        PdfBranding.header(context, page.canvas, "Meus exames • Organização de registros")

        fun finishPage() {
            PdfBranding.footer(page.canvas, pageNumber)
            document.finishPage(page)
        }
        fun nextPage() {
            finishPage()
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
            PdfBranding.header(context, page.canvas, "Meus exames • Continuação")
            y = 120f
        }
        fun paragraph(text: String, bold: Boolean = false) {
            paint.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            PdfTextLayout.wrap(text, 491f, paint::measureText).forEach { line ->
                if (y > 766f) nextPage()
                page.canvas.drawText(line, 52f, y, paint)
                y += 15f
            }
            y += 9f
        }

        try {
            paragraph("Titular: ${profile.fullName ?: "Não informado"}", true)
            paragraph("Exportado em: ${dateFormat.format(Date())}")
            paragraph("Este documento reúne os exames e valores registrados por você. O Braga Saúde organiza os arquivos para facilitar a exportação e o compartilhamento com seu médico. Não realiza diagnóstico, interpretação de exames ou prescrição e não substitui uma avaliação profissional.")
            paragraph("${exams.size} exames • ${examItems.size} valores registrados", true)
            if (exams.isEmpty()) paragraph("Você ainda não possui exames registrados.")
            exams.sortedByDescending { it.examDate }.forEach { exam ->
                if (y > 680f) nextPage()
                paragraph(exam.title, true)
                paragraph("Data informada: ${dateFormat.format(exam.examDate)} • ${exam.category.orEmpty()}")
                val items = examItems.filter { it.examId == exam.remoteId }
                if (items.isEmpty()) paragraph("Nenhum valor transcrito. Consulte o arquivo original, quando disponível.")
                items.forEach { item ->
                    val value = item.valueText?.takeIf { it.isNotBlank() } ?: item.valueNumeric?.toString() ?: "Não informado"
                    paragraph("${item.itemName}: $value ${item.unit.orEmpty()}")
                }
            }
            val summaryPages = pageNumber
            finishPage()
            var attachedPages = 0
            exams.forEach { exam ->
                val path = (exam.localFilePath ?: exam.fileUrl)?.takeIf { it.isNotBlank() } ?: return@forEach
                val file = checkNotNull(resolveFile(path)) { "Um arquivo original não está disponível. Tente exportar novamente." }
                if (file.extension.equals("pdf", true)) {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            for (index in 0 until renderer.pageCount) {
                                renderer.openPage(index).use { original ->
                                    val scale = minOf(1030f / original.width, 1320f / original.height)
                                    val bitmap = Bitmap.createBitmap((original.width * scale).toInt().coerceAtLeast(1), (original.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                                    try {
                                        bitmap.eraseColor(Color.WHITE)
                                        original.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                        pageNumber++
                                        val attached = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                                        PdfBranding.header(context, attached.canvas, "Arquivo original • ${exam.title}")
                                        drawOriginal(attached.canvas, bitmap)
                                        PdfBranding.footer(attached.canvas, pageNumber)
                                        document.finishPage(attached)
                                        attachedPages++
                                    } finally { bitmap.recycle() }
                                }
                            }
                        }
                    }
                } else {
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                        ?: error("Não foi possível ler um arquivo original. Tente exportar novamente.")
                    try {
                        pageNumber++
                        val attached = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                        PdfBranding.header(context, attached.canvas, "Arquivo original • ${exam.title}")
                        drawOriginal(attached.canvas, bitmap)
                        PdfBranding.footer(attached.canvas, pageNumber)
                        document.finishPage(attached)
                        attachedPages++
                    } finally { bitmap.recycle() }
                }
            }
            val directory = File(context.cacheDir, "dossiers").apply { mkdirs() }
            val output = File(directory, "Nuvem_de_Exames_BragaSaude_${System.currentTimeMillis()}.pdf")
            output.outputStream().use { document.writeTo(it) }
            CompilationResult(output, pageNumber, summaryPages, attachedPages, output.length())
        } finally { document.close() }
    }

    private fun drawOriginal(canvas: Canvas, bitmap: Bitmap) {
        val scale = minOf(515f / bitmap.width, 660f / bitmap.height)
        val width = bitmap.width * scale
        val height = bitmap.height * scale
        val left = 40f + (515f - width) / 2
        val top = 115f + (660f - height) / 2
        canvas.drawBitmap(bitmap, null, RectF(left, top, left + width, top + height), Paint(Paint.FILTER_BITMAP_FLAG))
    }
}
