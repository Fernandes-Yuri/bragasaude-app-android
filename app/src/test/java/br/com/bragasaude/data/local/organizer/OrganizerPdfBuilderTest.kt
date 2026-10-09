package br.com.bragasaude.data.local.organizer

import android.app.Application
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class OrganizerPdfBuilderTest {
    @Test fun preservesAllOriginalPagesAndIndexDestinationsAcrossMultipleIndexPages() {
        val context = RuntimeEnvironment.getApplication()
        val work = File(context.cacheDir, "pdf-test").apply { mkdirs() }
        try {
            val builder = OrganizerPdfBuilder(context)
            val items = (1..20).map { n ->
                OrganizerDocument("$n", "Exame $n " + "titulo extenso ".repeat(5), "01/10/2026", pages = 2, confirmed = true)
            }
            val sources = items.map { item -> File(work, "${item.id}.pdf").apply {
                PDDocument().use { document ->
                    repeat(2) { n ->
                        val page = PDPage(); document.addPage(page)
                        PDPageContentStream(document, page).use {
                            it.beginText(); it.setFont(PDType1Font.HELVETICA, 12f); it.newLineAtOffset(50f, 700f)
                            it.showText("ORIGINAL ${item.id} PAGINA ${n + 1}"); it.endText()
                        }
                    }; document.save(this)
                }
            } }
            val result = File(work, "result.pdf")
            builder.build(items, sources, result, work)
            PDDocument.load(result).use { document ->
                val text = PDFTextStripper().getText(document)
                val indexPages = document.numberOfPages - 60
                assertTrue("O índice precisa paginar títulos extensos", indexPages > 1)
                items.forEachIndexed { n, item ->
                    assertTrue(text.contains("página ${indexPages + 1 + n * 3}"))
                    repeat(2) { originalPage ->
                        val page = indexPages + n * 3 + originalPage + 2
                        val content = PDFTextStripper().apply { startPage = page; endPage = page }.getText(document)
                        assertTrue(content.contains("ORIGINAL ${item.id} PAGINA ${originalPage + 1}"))
                    }
                }
            }
        } finally { work.deleteRecursively() }
    }
    @Test(expected = IllegalArgumentException::class) fun refusesUnreviewedDocuments() {
        val context = RuntimeEnvironment.getApplication()
        OrganizerPdfBuilder(context).build(listOf(OrganizerDocument("a", "Exame", pages = 1)),
            listOf(File(context.cacheDir, "input.pdf")), File(context.cacheDir, "output.pdf"), context.cacheDir)
    }
}
