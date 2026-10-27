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
    private fun blankOnePagePdf(file: File): File {
        PDDocument().use { document ->
            document.addPage(PDPage()); document.save(file)
        }
        return file
    }
    private fun onePageMarkedPdf(file: File, marker: String): File {
        PDDocument().use { document ->
            val page = PDPage(); document.addPage(page)
            PDPageContentStream(document, page).use {
                it.beginText(); it.setFont(PDType1Font.HELVETICA, 12f); it.newLineAtOffset(50f, 700f)
                it.showText(marker); it.endText()
            }; document.save(file)
        }
        return file
    }
    private fun twoPagePdf(file: File, marker: String): File {
        PDDocument().use { document ->
            repeat(2) { n ->
                val page = PDPage(); document.addPage(page)
                PDPageContentStream(document, page).use {
                    it.beginText(); it.setFont(PDType1Font.HELVETICA, 12f); it.newLineAtOffset(50f, 700f)
                    it.showText("$marker PAGINA ${n + 1}"); it.endText()
                }
            }; document.save(file)
        }
        return file
    }
    private fun pageText(document: PDDocument, page: Int): String =
        PDFTextStripper().apply { startPage = page; endPage = page }.getText(document)
    private fun indexPageCount(document: PDDocument): Int {
        val text = pageText(document, 2)
        val matched = Regex("Índice 1/(\\d+)").find(text) ?: error("Cabecalho do indice ausente na pagina 2")
        return matched.groupValues[1].toInt()
    }

    @Test fun preservesOriginalPagesWithCoverAdaptiveIndexSeparatorsAndClosing() {
        val context = RuntimeEnvironment.getApplication()
        val work = File(context.cacheDir, "pdf-test").apply { mkdirs() }
        try {
            val builder = OrganizerPdfBuilder(context)
            val items = (1..20).map { n ->
                OrganizerDocument("$n", "Exame $n " + "titulo extenso ".repeat(5), "01/10/2026", pages = 2, confirmed = true)
            }
            val sources = items.map { item -> twoPagePdf(File(work, "${item.id}.pdf"), "ORIGINAL ${item.id}") }
            val result = File(work, "result.pdf")
            builder.build(items, sources, result, work, newest = true)
            PDDocument.load(result).use { document ->
                val text = PDFTextStripper().getText(document)
                val ordered = OrganizerMetadata.sections(items, newest = true)
                    .flatMap { it.groups }.flatMap { it.documents }
                val indexPages = indexPageCount(document)
                // estrutura: capa(1) + indice(I) + separadora(1, setor > 1 pagina) + 40 originais + encerramento(1)
                assertEquals(1 + indexPages + 1 + 40 + 1, document.numberOfPages)
                ordered.forEachIndexed { n, item ->
                    // capa(1) + indice(I) + separadora(1) => primeiro original comeca na pagina I+3
                    val destination = 3 + indexPages + n * 2
                    assertTrue("Destino do documento ${item.id} deve constar no indice",
                        text.contains(Regex("página $destination(?!\\d)")))
                    repeat(2) { originalPage ->
                        val physical = destination + originalPage
                        val content = pageText(document, physical)
                        assertTrue("Documento ${item.id} fora da posicao $n",
                            content.contains("ORIGINAL ${item.id} PAGINA ${originalPage + 1}"))
                    }
                }
            }
        } finally { work.deleteRecursively() }
    }
    @Test(expected = IllegalArgumentException::class) fun refusesUnreviewedDocuments() {
        val context = RuntimeEnvironment.getApplication()
        OrganizerPdfBuilder(context).build(listOf(OrganizerDocument("a", "Exame", pages = 1)),
            listOf(File(context.cacheDir, "input.pdf")), File(context.cacheDir, "output.pdf"), context.cacheDir, newest = true)
    }
    @Test fun indexHeadersNeverOrphanAtPageBottom() {
        val context = RuntimeEnvironment.getApplication()
        val work = File(context.cacheDir, "pdf-orphan").apply { mkdirs() }
        try {
            val builder = OrganizerPdfBuilder(context)
            val sectorList = listOf("Laboratorial", "Imagem", "Cardiológico")
            val items = (1..18).map { n ->
                OrganizerDocument("$n", "Exame " + "titulo bastante extenso ".repeat(6), "10/11/2026", sectorList[n % 3], pages = 1, confirmed = true)
            }
            val sources = items.map { item -> blankOnePagePdf(File(work, "${item.id}.pdf")) }
            val result = File(work, "orphan.pdf")
            builder.build(items, sources, result, work, newest = true)
            PDDocument.load(result).use { document ->
                assertTrue(document.numberOfPages > 2)
                (2..6).forEach { page ->
                    if (page > document.numberOfPages) return@forEach
                    val text = pageText(document, page)
                    if (!text.contains("Índice")) return@forEach
                    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
                    val last = lines.lastOrNull().orEmpty()
                    assertFalse("Pagina $page terminaria com cabecalho orfao: $last",
                        last.startsWith("SETOR:") || last.startsWith("Data:"))
                }
            }
        } finally { work.deleteRecursively() }
    }
    @Test fun fitsOnOneIndexPageWithFewDocumentsAtComfortableScale() {
        val context = RuntimeEnvironment.getApplication()
        val work = File(context.cacheDir, "pdf-few").apply { mkdirs() }
        try {
            val builder = OrganizerPdfBuilder(context)
            val items = (1..6).map { n ->
                OrganizerDocument("$n", "Exame $n", "01/10/2026", pages = 1, confirmed = true)
            }
            val sources = items.map { item -> blankOnePagePdf(File(work, "${item.id}.pdf")) }
            val result = File(work, "few.pdf")
            builder.build(items, sources, result, work, newest = true)
            PDDocument.load(result).use { document ->
                assertEquals(1, indexPageCount(document))
                // poucos exames: nenhum aviso de tipografia comprimida
                assertFalse(pageText(document, 2).contains("Tipografia ajustada"))
            }
        } finally { work.deleteRecursively() }
    }
    @Test fun compressesManyDocumentsOntoOneIndexPageBeforePaginating() {
        val context = RuntimeEnvironment.getApplication()
        val work = File(context.cacheDir, "pdf-many").apply { mkdirs() }
        try {
            val builder = OrganizerPdfBuilder(context)
            val items = (1..19).map { n ->
                OrganizerDocument("$n", "Exame da rotina " + "titulo extenso ".repeat(8), "01/10/2026", pages = 1, confirmed = true)
            }
            val sources = items.map { item -> blankOnePagePdf(File(work, "${item.id}.pdf")) }
            val result = File(work, "many.pdf")
            builder.build(items, sources, result, work, newest = true)
            PDDocument.load(result).use { document ->
                assertEquals("19 exames devem caber em uma unica pagina de indice apos compressao",
                    1, indexPageCount(document))
            }
        } finally { work.deleteRecursively() }
    }
    @Test fun createsSectorSeparatorOnlyWhenSectorHasMoreThanOnePage() {
        val context = RuntimeEnvironment.getApplication()
        val work = File(context.cacheDir, "pdf-sep").apply { mkdirs() }
        try {
            val builder = OrganizerPdfBuilder(context)
            val items = listOf(
                OrganizerDocument("lab1", "Glicemia", "10/10/2026", "Laboratorial", pages = 1, confirmed = true),
                OrganizerDocument("lab2", "Hemograma", "11/10/2026", "Laboratorial", pages = 1, confirmed = true),
                OrganizerDocument("img1", "Raio-x", "12/10/2026", "Imagem", pages = 1, confirmed = true)
            )
            val sources = items.map { item -> blankOnePagePdf(File(work, "${item.id}.pdf")) }
            val result = File(work, "sep.pdf")
            builder.build(items, sources, result, work, newest = true)
            PDDocument.load(result).use { document ->
                val text = PDFTextStripper().getText(document)
                assertEquals("So o setor com mais de uma pagina ganha separadora",
                    1, Regex("SEPARADORA · SETOR").findAll(text).count())
                // estrutura: capa(1) + indice(1) + separadora(1) + 3 originais + encerramento(1)
                assertEquals(7, document.numberOfPages)
            }
        } finally { work.deleteRecursively() }
    }
    @Test fun coverDeclaresSoftwareCompanyAndClosingKeepsOriginalsWarning() {
        val context = RuntimeEnvironment.getApplication()
        val work = File(context.cacheDir, "pdf-brand").apply { mkdirs() }
        try {
            val builder = OrganizerPdfBuilder(context)
            val items = (1..3).map { n ->
                OrganizerDocument("$n", "Exame $n", "01/10/2026", pages = 1, confirmed = true)
            }
            val sources = items.map { item -> blankOnePagePdf(File(work, "${item.id}.pdf")) }
            val result = File(work, "brand.pdf")
            builder.build(items, sources, result, work, newest = true)
            PDDocument.load(result).use { document ->
                val cover = pageText(document, 1)
                assertTrue(cover.contains("BRAGA SAUDE"))
                assertTrue(cover.contains("empresa de software"))
                assertTrue(cover.contains("Não somos laboratório"))
                val closing = pageText(document, document.numberOfPages)
                assertTrue(closing.contains("Documento encerrado."))
                assertTrue(closing.contains("assinaturas digitais"))
            }
        } finally { work.deleteRecursively() }
    }
    @Test fun separatorsArePlacedPhysicallyBeforeTheirOwnSectorOriginals() {
        val context = RuntimeEnvironment.getApplication()
        val work = File(context.cacheDir, "pdf-interleave").apply { mkdirs() }
        try {
            val builder = OrganizerPdfBuilder(context)
            val items = listOf(
                OrganizerDocument("l1", "Glicemia", "10/10/2026", "Laboratorial", pages = 1, confirmed = true),
                OrganizerDocument("l2", "Hemograma", "10/10/2026", "Laboratorial", pages = 1, confirmed = true),
                OrganizerDocument("i1", "Raio-x", "10/10/2026", "Imagem", pages = 1, confirmed = true),
                OrganizerDocument("i2", "Tomografia", "10/10/2026", "Imagem", pages = 1, confirmed = true)
            )
            val sources = items.map { item -> onePageMarkedPdf(File(work, "${item.id}.pdf"), "ORIGINAL ${item.id}") }
            val result = File(work, "interleave.pdf")
            builder.build(items, sources, result, work, newest = true)
            PDDocument.load(result).use { document ->
                val indexPages = indexPageCount(document)
                val total = 1 + indexPages + 2 + 4 + 1 // capa + indice + 2 separadoras + 4 originais + encerramento
                assertEquals(total, document.numberOfPages)
                val sepLab = 2 + indexPages
                assertTrue(pageText(document, sepLab).contains("Laboratorial"))
                assertTrue("original l1 deve seguir a separadora Lab", pageText(document, sepLab + 1).contains("ORIGINAL l1"))
                assertTrue(pageText(document, sepLab + 2).contains("ORIGINAL l2"))
                val sepImg = sepLab + 3
                assertTrue(pageText(document, sepImg).contains("Imagem"))
                assertTrue("original i1 deve seguir a separadora Imagem", pageText(document, sepImg + 1).contains("ORIGINAL i1"))
                assertTrue(pageText(document, sepImg + 2).contains("ORIGINAL i2"))
            }
        } finally { work.deleteRecursively() }
    }
    @Test fun balancesContentAcrossPagesWhenIndexCannotFitOne() {
        val context = RuntimeEnvironment.getApplication()
        val work = File(context.cacheDir, "pdf-balanced").apply { mkdirs() }
        try {
            val builder = OrganizerPdfBuilder(context)
            val items = (1..35).map { n ->
                OrganizerDocument("$n", "Exame da rotina " + "titulo extenso ".repeat(3), "01/10/2026", pages = 1, confirmed = true)
            }
            val sources = items.map { item -> blankOnePagePdf(File(work, "${item.id}.pdf")) }
            val result = File(work, "balanced.pdf")
            builder.build(items, sources, result, work, newest = true)
            PDDocument.load(result).use { document ->
                val indexPages = indexPageCount(document)
                assertTrue("35 exames nao cabem numa pagina so", indexPages >= 2)
                // balanceado: as duas primeiras paginas carregam uma fatia significativa de entradas
                val first = Regex("página ").findAll(pageText(document, 2)).count()
                val second = Regex("página ").findAll(pageText(document, 3)).count()
                assertTrue("pagina 1 de indice quase vazia ($first entradas)", first >= 10)
                assertTrue("pagina 2 de indice cauda ($second entradas, devia ser balanceado)", second >= 10)
            }
        } finally { work.deleteRecursively() }
    }
}
