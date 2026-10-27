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
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import java.io.File

/**
 * Modelo de um item do indice. Medidas em pontos PDF na escala 1.0; a renderizacao
 * multiplica tudo pela escala adaptativa (veja packIndex/chooseScale).
 */
private sealed interface IndexItem {
    fun height(scale: Float, widthOf: (String, Float, Int) -> Float): Float
}
private data class SectorHead(val type: String, val count: Int, val color: Triple<Int, Int, Int>) : IndexItem {
    override fun height(scale: Float, widthOf: (String, Float, Int) -> Float): Float = 30f * scale
}
private data class DateHead(val label: String) : IndexItem {
    override fun height(scale: Float, widthOf: (String, Float, Int) -> Float): Float = 15f * scale
}
private data class DocHead(val doc: OrganizerDocument) : IndexItem {
    /** Entrada compacta: sempre 1 linha-base + quebras por largura; topicos inline quando aperta. */
    fun entryText(pageNumber: Int, topicsInline: Boolean): String {
        val topics = doc.topics.take(3).joinToString(", ")
        val suffix = if (topicsInline && topics.isNotBlank()) " · tópicos: $topics" else ""
        return "${doc.title} — ${doc.date.ifBlank { "sem data" }} · ${doc.type} · página $pageNumber$suffix"
    }
    fun wrappedLines(pageNumber: Int, scale: Float, topicsInline: Boolean, widthOf: (String, Float, Int) -> Float): List<String> {
        val fontSize = 10f * scale
        return PdfTextLayout.wrap(entryText(pageNumber, topicsInline), 475f - 58f * (1f - scale)) { text ->
            widthOf(text, fontSize, 0)
        }
    }
    fun extraTopicsLine(scale: Float, topicsInline: Boolean): Boolean = !topicsInline && doc.topics.isNotEmpty() && scale >= 0.995f
    override fun height(scale: Float, widthOf: (String, Float, Int) -> Float): Float {
        // altura medida com placeholder de 3 digitos: a contagem de quebras nao muda com '9'..'999'
        val lines = wrappedLines(999, scale, scale < 0.995f, widthOf).size
        val topicsIfComfortable = if (extraTopicsLine(scale, scale < 0.995f)) 12f else 0f
        return (lines * 13f + 10f + topicsIfComfortable) * scale + 5f * scale
    }
}

/**
 * Constrói o consolidado de exames com identidade Braga Saúde:
 * capa institucional com declaração (empresa de software, não laboratório),
 * sumário adaptativo (1 página sempre que possível), separadora por setor com
 * mais de uma página, originais em sequência contínua e página de encerramento.
 *
 * Regras de densidade aprovadas no teste físico:
 *  - sem divisória por documento; separadora só existe em setor com mais de 1 página;
 *  - índice aperta até o mínimo legível e só cria página extra quando inviável;
 *  - poucos exames mantêm tipografia confortável (nada de letra miúda com página sobrando).
 */
class OrganizerPdfBuilder(context: Context) {
    init { PDFBoxResourceLoader.init(context) }

    /**
     * Embute A FOTO do documento com compressao fotografica (JPEG DCT). Sem isso, cada foto
     * entra como RGB bruto deflacionado e chega perto de 8 MB/pagina (batendo 150 MB com 20).
     * Alvo: ate ~0,4 MB/pagina nitida para leitura; o OCR continua rodando no bitmap original.
     */
    fun imagePdf(bitmap: Bitmap, output: File) {
        val scaled = downscale(bitmap, MAX_IMAGE_EDGE)
        try {
            saveJpegPdf(scaled, 0.82f, output)
            if (output.length() > 1_500_000L) saveJpegPdf(scaled, 0.65f, output) // segunda passada de seguranca
        } finally { if (scaled !== bitmap) scaled.recycle() }
    }

    private fun downscale(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val scale = minOf(1f, maxEdge.toFloat() / maxOf(bitmap.width, bitmap.height))
        if (scale >= 1f) return bitmap
        return Bitmap.createScaledBitmap(bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1), (bitmap.height * scale).toInt().coerceAtLeast(1), true)
    }

    private fun saveJpegPdf(bitmap: Bitmap, quality: Float, output: File) {
        PDDocument().use { doc ->
            val page = PDPage(PDRectangle(bitmap.width.toFloat(), bitmap.height.toFloat()))
            doc.addPage(page)
            PDPageContentStream(doc, page).use { it.drawImage(JPEGFactory.createFromImage(doc, bitmap, quality), 0f, 0f) }
            doc.save(output)
        }
    }

    private companion object {
        const val PAGE_TOP_CONTENT = 735f
        const val PAGE_BOTTOM_SAFE = 70f
        val CONTENT_HEIGHT = PAGE_TOP_CONTENT - PAGE_BOTTOM_SAFE
        const val MIN_SCALE = 0.78f
        const val MAX_IMAGE_EDGE = 2200
    }

    fun build(documents: List<OrganizerDocument>, originals: List<File>, output: File, work: File, newest: Boolean,
              personalization: OrganizerPersonalization = OrganizerPersonalization()) {
        require(documents.isNotEmpty() && documents.size == originals.size && documents.all { it.confirmed })
        val font = personalization.regularFont()
        val boldFont = personalization.boldFont()
        val accent = personalization.accent()
        val (accentR, accentG, accentB) = accent.main
        val (lightR, lightG, lightB) = accent.light
        val (subR, subG, subB) = accent.subtitle

        fun safe(text: String, f: PDType1Font = font): String = buildString {
            text.forEach { c -> append(if (runCatching { f.encode(c.toString()) }.isSuccess) c else '?') }
        }
        // medidores de largura: kind 0 = regular, 1 = bold
        val widthOf: (String, Float, Int) -> Float = { text, size, kind ->
            (if (kind == 1) boldFont else font).getStringWidth(safe(text, if (kind == 1) boldFont else font)) / 1000f * size
        }
        fun lines(text: String, maxWidth: Float = 470f, f: PDType1Font = font, size: Float = 11f) =
            PdfTextLayout.wrap(safe(text, f), maxWidth) { f.getStringWidth(it) / 1000f * size }

        val sectors = OrganizerMetadata.sections(documents, newest)
        val originalsById = documents.withIndex().associate { (index, item) -> item.id to originals[index] }
        val flatDocuments = sectors.flatMap { it.groups }.flatMap { it.documents }
        require(flatDocuments.size == documents.size)

        // ---- modelo do índice ----
        val indexItems = mutableListOf<IndexItem>()
        sectors.forEach { sector ->
            val count = sector.groups.sumOf { it.documents.size }
            indexItems.add(SectorHead(sector.type, count, OrganizerMetadata.pdfColor(sector.type)))
            sector.groups.forEach { group ->
                if (sector.groups.size > 1 || group.date.isNotBlank()) indexItems.add(DateHead(group.date.ifBlank { "Sem data" }))
                group.documents.forEach { doc -> indexItems.add(DocHead(doc)) }
            }
        }

        // ---- empacotamento do índice: ganancioso (1 página) ou balanceado (2+ páginas) ----
        fun heightsAt(scale: Float): List<Float> = indexItems.map { it.height(scale, widthOf) }
        // Cabecalhos nunca ficam orfaos: precisam couber junto aa proxima entrada (keep-with-next).
        fun keepWithNextNeed(index: Int, heights: List<Float>): Float {
            val item = indexItems[index]
            if (item !is SectorHead && item !is DateHead) return heights[index]
            val nextDocHeight = (index + 1 until indexItems.size)
                .firstOrNull { indexItems[it] is DocHead }?.let { heights[it] } ?: 0f
            return heights[index] + nextDocHeight
        }
        fun packGreedy(scale: Float): List<List<IndexItem>> {
            val heights = heightsAt(scale)
            val pages = mutableListOf<MutableList<IndexItem>>(mutableListOf())
            var available = CONTENT_HEIGHT
            indexItems.forEachIndexed { index, item ->
                val need = keepWithNextNeed(index, heights)
                if (need > available && pages.last().isNotEmpty()) {
                    pages.add(mutableListOf()); available = CONTENT_HEIGHT
                }
                pages.last().add(item); available -= heights[index]
            }
            return pages
        }
        // Fix do teste físico: se vai quebrar em 2+ páginas mesmo, distribua a altura
        // por elas em vez de encher a primeira até a borda e deixar a última quase vazia.
        fun packBalanced(scale: Float, pageCount: Int): List<List<IndexItem>> {
            val heights = heightsAt(scale)
            val target = heights.sum() / pageCount
            val pages = mutableListOf<MutableList<IndexItem>>(mutableListOf())
            var used = 0f
            indexItems.forEachIndexed { index, item ->
                val need = keepWithNextNeed(index, heights)
                val mustBreakPhysically = used + need > CONTENT_HEIGHT
                val shouldBalanceBreak = used + need > target && pages.size < pageCount
                if (pages.last().isNotEmpty() && (mustBreakPhysically || shouldBalanceBreak)) {
                    pages.add(mutableListOf()); used = 0f
                }
                pages.last().add(item); used += heights[index]
            }
            return pages
        }

        // ---- escala adaptativa: 1 página sempre que viável, letra legível ----
        var scale = 1.0f
        if (packGreedy(1.0f).size > 1) {
            val estimated = indexItems.sumOf { it.height(1.0f, widthOf).toDouble() }.toFloat()
            scale = (CONTENT_HEIGHT / estimated).coerceIn(MIN_SCALE, 1.0f)
            var guard = 0
            while (packGreedy(scale).size > 1 && scale > MIN_SCALE + 0.005f && guard++ < 8) {
                scale = (scale - 0.03f).coerceAtLeast(MIN_SCALE)
            }
        }
        val topicsInline = scale < 0.995f
        val greedy = packGreedy(scale)
        val indexGroups = if (greedy.size > 1) packBalanced(scale, greedy.size) else greedy

        // ---- numeracao fisica: capa + indice + separadoras + originais + encerramento ----
        val sectorHasSeparator = sectors.associate { sector ->
            sector.type to (sector.groups.sumOf { g -> g.documents.sumOf { d -> d.pages } } > 1)
        }
        val separatorPageByType = HashMap<String, Int>()
        val firstPageById = HashMap<String, Int>()
        var nextPage = 2 + indexGroups.size
        sectors.forEach { sector ->
            if (sectorHasSeparator[sector.type] == true) {
                separatorPageByType[sector.type] = nextPage; nextPage += 1
            }
            sector.groups.forEach { group ->
                group.documents.forEach { doc -> firstPageById[doc.id] = nextPage; nextPage += doc.pages }
            }
        }
        val totalPages = nextPage // página de encerramento é a última

        PDDocument(MemoryUsageSetting.setupTempFileOnly().setTempDir(work)).use { combined ->

            fun drawBragaHeader(stream: PDPageContentStream, subtitle: String) {
                stream.setNonStrokingColor(accentR, accentG, accentB)
                stream.addRect(0f, 770f, 595f, 72f)
                stream.fill()
                stream.setNonStrokingColor(lightR, lightG, lightB)
                stream.addRect(0f, 766f, 595f, 4f)
                stream.fill()
                stream.beginText()
                stream.setFont(boldFont, 16f)
                stream.setNonStrokingColor(255, 255, 255)
                stream.newLineAtOffset(48f, 804f)
                stream.showText(safe(personalization.displayTitle(), boldFont))
                stream.endText()
                stream.beginText()
                stream.setFont(font, 9.5f)
                stream.setNonStrokingColor(subR, subG, subB)
                stream.newLineAtOffset(48f, 786f)
                stream.showText(safe(subtitle.uppercase()))
                stream.endText()
            }

            fun drawBragaFooter(stream: PDPageContentStream, pageNumber: Int) {
                stream.setStrokingColor(203, 213, 225)
                stream.setLineWidth(0.6f)
                stream.moveTo(48f, 38f)
                stream.lineTo(547f, 38f)
                stream.stroke()
                stream.beginText()
                stream.setFont(font, 7.5f)
                stream.setNonStrokingColor(100, 116, 139)
                stream.newLineAtOffset(48f, 26f)
                stream.showText("Braga Saude | Plataforma de Autocuidado • Organizacao Pessoal de Exames")
                stream.endText()
                stream.beginText()
                stream.setFont(font, 7.5f)
                stream.setNonStrokingColor(100, 116, 139)
                stream.newLineAtOffset(470f, 26f)
                stream.showText("Pagina $pageNumber de $totalPages")
                stream.endText()
            }

            // ==================== CAPA ====================
            run {
                val page = PDPage(PDRectangle.A4)
                combined.addPage(page)
                PDPageContentStream(combined, page).use { stream ->
                    stream.setNonStrokingColor(accentR, accentG, accentB)
                    stream.addRect(0f, 562f, 595f, 280f)
                    stream.fill()
                    stream.setNonStrokingColor(lightR, lightG, lightB)
                    stream.addRect(0f, 556f, 595f, 6f)
                    stream.fill()
                    // Titulo da capa: nome do usuario substitui a marca (ela fica so na assinatura)
                    val coverTitle = safe(personalization.displayTitle(), boldFont)
                    var titleSize = 30f
                    while (titleSize > 18f && widthOf(coverTitle, titleSize, 1) > 499f) titleSize -= 1f
                    stream.beginText()
                    stream.setFont(boldFont, titleSize)
                    stream.setNonStrokingColor(255, 255, 255)
                    stream.newLineAtOffset(48f, 780f)
                    stream.showText(coverTitle)
                    stream.endText()
                    stream.beginText()
                    stream.setFont(font, 13f)
                    stream.setNonStrokingColor(subR, subG, subB)
                    stream.newLineAtOffset(48f, 756f)
                    stream.showText(if (personalization.coverName.isNotBlank()) "Documento consolidado de exames" else "Plataforma de Autocuidado • Organização Pessoal de Exames")
                    stream.endText()
                    if (personalization.coverName.isBlank()) {
                        stream.beginText()
                        stream.setFont(boldFont, 15f)
                        stream.setNonStrokingColor(255, 255, 255)
                        stream.newLineAtOffset(48f, 700f)
                        stream.showText("Documento consolidado de exames para consulta")
                        stream.endText()
                    }

                    // Declaração institucional: somos software, nao laboratório
                    val boxTop = 500f
                    val statement = listOf(
                        if (personalization.coverName.isNotBlank())
                            "A assinatura deste documento — BRAGA SAÚDE, empresa de software —"
                        else
                            "A Braga Saúde é uma empresa de software. Não somos laboratório,",
                        if (personalization.coverName.isNotBlank())
                            "não é laboratório, clínica, hospital ou profissional de saúde."
                        else
                            "clínica, hospital ou profissional de saúde.",
                        "",
                        "Nosso método apenas organiza em um único documento os exames",
                        "que já existiam, fornecidos e conferidos pelo próprio usuário,",
                        "para facilitar a consulta do paciente e a leitura do médico.",
                        "",
                        "Nenhum resultado, diagnóstico ou conclusão clínica é produzido,",
                        "alterado ou validado por nós: o conteúdo clínico permanece de",
                        "responsabilidade exclusiva dos laboratórios e profissionais que",
                        "emitiram os documentos originais aqui reunidos."
                    )
                    val lineHeight = 15f
                    val boxHeight = statement.size * lineHeight + 44f
                    stream.setNonStrokingColor(248, 250, 252)
                    stream.addRect(48f, boxTop - boxHeight, 499f, boxHeight)
                    stream.fill()
                    stream.setStrokingColor(226, 232, 240)
                    stream.setLineWidth(0.8f)
                    stream.addRect(48f, boxTop - boxHeight, 499f, boxHeight)
                    stream.stroke()
                    stream.setNonStrokingColor(accentR, accentG, accentB)
                    stream.addRect(48f, boxTop - boxHeight, 6f, boxHeight)
                    stream.fill()
                    var y = boxTop - 30f
                    statement.forEachIndexed { i, text ->
                        stream.beginText()
                        if (i == 0) {
                            stream.setFont(boldFont, 11f)
                            stream.setNonStrokingColor(26, 26, 46)
                        } else {
                            stream.setFont(font, 10.5f)
                            stream.setNonStrokingColor(51, 65, 85)
                        }
                        stream.newLineAtOffset(68f, y)
                        stream.showText(safe(text, if (i == 0) boldFont else font))
                        stream.endText()
                        y -= lineHeight
                    }

                    // Sumário numérico do pacote
                    stream.beginText()
                    stream.setFont(font, 10.5f)
                    stream.setNonStrokingColor(71, 85, 105)
                    stream.newLineAtOffset(48f, boxTop - boxHeight - 40f)
                    stream.showText(safe("Este documento reúne ${documents.size} exame(s) em ${sectors.size} setor(es), somando $totalPages páginas."))
                    stream.endText()

                    drawBragaFooter(stream, 1)
                }
            }

            // ==================== SUMÁRIO (índice adaptativo) ====================
            indexGroups.forEachIndexed { i, items ->
                val page = PDPage(PDRectangle.A4)
                combined.addPage(page)
                PDPageContentStream(combined, page).use { stream ->
                    drawBragaHeader(stream, "Sumário de Exames para Consulta")
                    drawBragaFooter(stream, i + 2)

                    var y = PAGE_TOP_CONTENT
                    stream.beginText()
                    stream.setFont(boldFont, 12f * scale.coerceAtLeast(0.9f))
                    stream.setNonStrokingColor(26, 26, 46)
                    stream.newLineAtOffset(48f, y)
                    stream.showText(safe("Exames organizados para consulta - Índice ${i + 1}/${indexGroups.size}"))
                    stream.endText()
                    y -= 16f

                    stream.beginText()
                    stream.setFont(font, 8.5f * scale.coerceAtLeast(0.9f))
                    stream.setNonStrokingColor(100, 116, 139)
                    stream.newLineAtOffset(48f, y)
                    stream.showText("Documentos fornecidos e conferidos pelo usuário. Guarde os originais.")
                    stream.endText()
                    y -= 14f

                    // nota geral de compressao no topo: nunca mascara um cabecalho orfao no fim
                    if (scale < 0.995f) {
                        stream.beginText()
                        stream.setFont(font, 8f)
                        stream.setNonStrokingColor(100, 116, 139)
                        stream.newLineAtOffset(48f, y)
                        stream.showText(safe("Tipografia ajustada para caber tudo em ${indexGroups.size} página(s), preservando a legibilidade."))
                        stream.endText()
                        y -= 10f
                    }
                    y -= 10f

                    items.forEach { item ->
                        when (item) {
                            is SectorHead -> {
                                val (r, g, b) = item.color
                                val bandHeight = 20f * scale
                                stream.setNonStrokingColor(r, g, b)
                                stream.addRect(48f, y - bandHeight, 499f, bandHeight)
                                stream.fill()
                                stream.beginText()
                                stream.setFont(boldFont, 11f * scale)
                                stream.setNonStrokingColor(255, 255, 255)
                                stream.newLineAtOffset(58f, y - bandHeight + 6f * scale)
                                stream.showText(safe("SETOR: ${item.type.uppercase()} • ${item.count} exame(s)"))
                                stream.endText()
                                y -= 30f * scale
                            }
                            is DateHead -> {
                                stream.beginText()
                                stream.setFont(boldFont, 9f * scale)
                                stream.setNonStrokingColor(71, 85, 105)
                                stream.newLineAtOffset(48f, y - 9f * scale)
                                stream.showText(safe("Data: ${item.label}"))
                                stream.endText()
                                stream.setStrokingColor(226, 232, 240)
                                stream.setLineWidth(0.5f)
                                stream.moveTo(48f, y - 11f * scale)
                                stream.lineTo(547f, y - 11f * scale)
                                stream.stroke()
                                y -= 15f * scale
                            }
                            is DocHead -> {
                                val doc = item.doc
                                val (r, g, b) = OrganizerMetadata.pdfColor(doc.type)
                                val pageNumber = firstPageById.getValue(doc.id)
                                val entryLines = item.wrappedLines(pageNumber, scale, topicsInline, widthOf)
                                val topicsLine = item.extraTopicsLine(scale, topicsInline)
                                val cardHeight = entryLines.size * 13f * scale + 10f * scale + (if (topicsLine) 12f * scale else 0f)

                                stream.setNonStrokingColor(248, 250, 252)
                                stream.addRect(48f, y - cardHeight, 499f, cardHeight)
                                stream.fill()
                                stream.setNonStrokingColor(r, g, b)
                                stream.addRect(48f, y - cardHeight, 4f, cardHeight)
                                stream.fill()

                                var textY = y - 11f * scale
                                entryLines.forEach { line ->
                                    stream.beginText()
                                    stream.setFont(font, 10f * scale)
                                    stream.setNonStrokingColor(26, 26, 46)
                                    stream.newLineAtOffset(58f, textY)
                                    stream.showText(line)
                                    stream.endText()
                                    textY -= 13f * scale
                                }
                                if (topicsLine) {
                                    stream.beginText()
                                    stream.setFont(font, 8f)
                                    stream.setNonStrokingColor(0, 105, 92)
                                    stream.newLineAtOffset(58f, textY)
                                    stream.showText(safe("Tópicos: ${doc.topics.joinToString(", ")}"))
                                    stream.endText()
                                }
                                y -= cardHeight + 5f * scale
                            }
                        }
                    }
                }
            }

            // ======= SEPARADORA E ORIGINAIS INTERCALADOS POR SETOR (montagem física = numeração) =======
            sectors.forEach { sector ->
                val separatorPage = separatorPageByType[sector.type]
                if (separatorPage != null) {
                val page = PDPage(PDRectangle.A4)
                combined.addPage(page)
                PDPageContentStream(combined, page).use { stream ->
                    drawBragaHeader(stream, "Setor: ${sector.type}")
                    drawBragaFooter(stream, separatorPage)
                    val (r, g, b) = OrganizerMetadata.pdfColor(sector.type)
                    val sectorDocs = sector.groups.flatMap { it.documents }
                    val sectorPages = sectorDocs.sumOf { it.pages }

                    // Aba lateral colorida
                    stream.setNonStrokingColor(r, g, b)
                    stream.addRect(583f, 0f, 12f, 842f)
                    stream.fill()

                    stream.setNonStrokingColor(r, g, b)
                    stream.addRect(48f, 560f, 499f, 130f)
                    stream.fill()
                    stream.beginText()
                    stream.setFont(boldFont, 13f)
                    stream.setNonStrokingColor(232, 246, 243)
                    stream.newLineAtOffset(68f, 662f)
                    stream.showText(safe("SEPARADORA · SETOR"))
                    stream.endText()
                    stream.beginText()
                    stream.setFont(boldFont, 24f)
                    stream.setNonStrokingColor(255, 255, 255)
                    stream.newLineAtOffset(68f, 628f)
                    stream.showText(safe(sector.type))
                    stream.endText()
                    stream.beginText()
                    stream.setFont(font, 11f)
                    stream.setNonStrokingColor(232, 246, 243)
                    stream.newLineAtOffset(68f, 602f)
                    stream.showText(safe("${sectorDocs.size} exame(s) · $sectorPages página(s) neste setor"))
                    stream.endText()

                    stream.beginText()
                    stream.setFont(font, 9f)
                    stream.setNonStrokingColor(100, 116, 139)
                    stream.newLineAtOffset(48f, 530f)
                    stream.showText(safe("Os exames deste setor seguem em sequência nas próximas páginas. Números exatos no sumário."))
                    stream.endText()
                }
                }

                // Originais deste setor logo apos a separadora dele (nao ha divisória por documento)
                sector.groups.forEach { group ->
                    group.documents.forEach { item ->
                        PDDocument.load(originalsById.getValue(item.id), MemoryUsageSetting.setupTempFileOnly().setTempDir(work)).use { source ->
                            check(source.numberOfPages == item.pages) { "Quantidade de páginas alterada. Confira o documento novamente." }
                            com.tom_roush.pdfbox.multipdf.PDFMergerUtility().appendDocument(combined, source)
                        }
                    }
                }
            }

            // ==================== PÁGINA DE ENCERRAMENTO ====================
            run {
                val page = PDPage(PDRectangle.A4)
                combined.addPage(page)
                PDPageContentStream(combined, page).use { stream ->
                    drawBragaHeader(stream, "Encerramento")
                    drawBragaFooter(stream, totalPages)

                    stream.setNonStrokingColor(248, 250, 252)
                    stream.addRect(48f, 360f, 499f, 190f)
                    stream.fill()
                    stream.setStrokingColor(226, 232, 240)
                    stream.setLineWidth(0.8f)
                    stream.addRect(48f, 360f, 499f, 190f)
                    stream.stroke()
                    stream.setNonStrokingColor(accentR, accentG, accentB)
                    stream.addRect(48f, 360f, 6f, 190f)
                    stream.fill()

                    var y = 512f
                    stream.beginText()
                    stream.setFont(boldFont, 14f)
                    stream.setNonStrokingColor(26, 26, 46)
                    stream.newLineAtOffset(68f, y)
                    stream.showText("Documento encerrado.")
                    stream.endText()
                    y -= 24f

                    val closing = listOf(
                        "Guarde os originais destes exames. Esta organização não preserva",
                        "assinaturas digitais nem substitui os documentos emitidos pelos",
                        "laboratórios, clínicas e profissionais responsáveis.",
                        "",
                        "A Braga Saúde é uma empresa de software de autocuidado. Conteúdos",
                        "clínicos, resultados e condutas são de responsabilidade exclusiva",
                        "dos profissionais e instituições que emitiram os originais."
                    )
                    closing.forEach { text ->
                        if (text.isNotEmpty()) {
                            stream.beginText()
                            stream.setFont(font, 10.5f)
                            stream.setNonStrokingColor(51, 65, 85)
                            stream.newLineAtOffset(68f, y)
                            stream.showText(safe(text))
                            stream.endText()
                        }
                        y -= 15f
                    }

                    // Assinatura da plataforma: marca discreta sob a caixa de encerramento,
                    // na cor de acento escolhida pelo usuario (padrao Braga = esmeralda).
                    stream.setStrokingColor(accentR, accentG, accentB)
                    stream.setLineWidth(1.2f)
                    stream.moveTo(48f, 352f)
                    stream.lineTo(212f, 352f)
                    stream.stroke()
                    stream.beginText()
                    stream.setFont(boldFont, 9f)
                    stream.setNonStrokingColor(accentR, accentG, accentB)
                    stream.newLineAtOffset(48f, 338f)
                    stream.showText(safe("Organizado com BRAGA SAUDE — plataforma de autocuidado", boldFont))
                    stream.endText()
                }
            }

            combined.save(output)
        }
    }
}
