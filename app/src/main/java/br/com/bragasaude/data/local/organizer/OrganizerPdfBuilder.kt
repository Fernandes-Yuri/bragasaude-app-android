package br.com.bragasaude.data.local.organizer

import android.content.Context
import android.graphics.Bitmap
import br.com.bragasaude.domain.PdfTextLayout
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import java.io.File

/** Copies PDF pages as pages, without rasterizing or transcribing the original content. */
class OrganizerPdfBuilder(context: Context) {
    init { PDFBoxResourceLoader.init(context) }
    fun imagePdf(bitmap: Bitmap, output: File) {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
            doc.addPage(page)
            PDPageContentStream(doc, page).use { it.drawImage(LosslessFactory.createFromImage(doc, bitmap), 0f, 0f) }
            doc.save(output)
        }
    }
    fun build(documents: List<OrganizerDocument>, originals: List<File>, output: File, work: File) {
        require(documents.isNotEmpty() && documents.size == originals.size && documents.all { it.confirmed })
        val font = PDType1Font.HELVETICA
        fun safe(text: String): String = buildString {
            text.forEach { c -> append(if (runCatching { font.encode(c.toString()) }.isSuccess) c else '?') }
        }
        fun lines(text: String) = PdfTextLayout.wrap(safe(text), 490f) { font.getStringWidth(it) / 1000f * 12f }
        val entries = documents.map { lines("${it.title} | ${it.date.ifBlank { "Data não informada" }} | ${it.type} | página 999") }
        // Title/date/type can wrap; reserve their actual height before calculating destinations.
        val indexGroups = mutableListOf<MutableList<Int>>(mutableListOf())
        var available = 26
        entries.forEachIndexed { i, entry ->
            val needed = entry.size + 1
            if (needed > available) { indexGroups.add(mutableListOf()); available = 26 }
            indexGroups.last().add(i); available -= needed
        }
        val firstPages = mutableListOf<Int>(); var next = indexGroups.size + 1
        documents.forEach { firstPages.add(next); next += 1 + it.pages }
        PDDocument(MemoryUsageSetting.setupTempFileOnly().setTempDir(work)).use { combined ->
            fun textPage(heading: String, paragraphs: List<String>) {
                val page = PDPage(PDRectangle.A4); combined.addPage(page)
                PDPageContentStream(combined, page).use { stream ->
                    stream.beginText(); stream.setFont(font, 12f); stream.newLineAtOffset(48f, 790f)
                    val all = listOf(heading, "") + paragraphs
                    all.forEach { paragraph ->
                        lines(paragraph).ifEmpty { listOf("") }.forEach { line ->
                            stream.showText(line); stream.newLineAtOffset(0f, -18f)
                        }
                        stream.newLineAtOffset(0f, -6f)
                    }
                    stream.endText()
                }
            }
            indexGroups.forEachIndexed { i, group ->
                textPage("Exames organizados para consulta - Índice ${i + 1}/${indexGroups.size}",
                    listOf("Documentos fornecidos e conferidos pelo usuário.") + group.map { n ->
                        "${documents[n].title} | ${documents[n].date.ifBlank { "Data não informada" }} | ${documents[n].type} | página ${firstPages[n]}"
                    } + listOf("Guarde os originais. A organização não preserva assinaturas digitais.")
                )
            }
            documents.forEachIndexed { i, item ->
                textPage("${item.title}", listOf("Data: ${item.date.ifBlank { "Não informada" }}", "Tipo: ${item.type}",
                    "Original: ${item.pages} página(s), a seguir.", "Consulte o documento original e a avaliação do profissional de saúde."))
                PDDocument.load(originals[i], MemoryUsageSetting.setupTempFileOnly().setTempDir(work)).use { source ->
                    check(source.numberOfPages == item.pages) { "Quantidade de páginas alterada. Confira o documento novamente." }
                    com.tom_roush.pdfbox.multipdf.PDFMergerUtility().appendDocument(combined, source)
                }
            }
            combined.save(output)
        }
    }
}
