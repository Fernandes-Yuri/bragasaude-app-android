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

/** Itens do índice com peso em unidades de linha (cabeçalhos de setor/data e entradas de documento). */
private sealed interface IndexItem { val weight: Int }
private data class SectorHead(val type: String, val count: Int, val color: Triple<Int, Int, Int>, override val weight: Int = 3) : IndexItem
private data class DateHead(val label: String, override val weight: Int = 2) : IndexItem
private data class DocHead(val doc: OrganizerDocument, val entry: List<String>, override val weight: Int) : IndexItem

/**
 * Constrói o consolidado de exames para consulta médica com identidade visual Braga Saúde,
 * índice por setores coloridos e datas, divisórias por documento e cópia vetorial das páginas originais.
 */
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

    fun build(documents: List<OrganizerDocument>, originals: List<File>, output: File, work: File, newest: Boolean) {
        require(documents.isNotEmpty() && documents.size == originals.size && documents.all { it.confirmed })
        val font = PDType1Font.HELVETICA
        val boldFont = PDType1Font.HELVETICA_BOLD

        fun safe(text: String, f: PDType1Font = font): String = buildString {
            text.forEach { c -> append(if (runCatching { f.encode(c.toString()) }.isSuccess) c else '?') }
        }

        fun lines(text: String, maxWidth: Float = 470f, f: PDType1Font = font, size: Float = 11f) =
            PdfTextLayout.wrap(safe(text, f), maxWidth) { f.getStringWidth(it) / 1000f * size }

        val sectors = OrganizerMetadata.sections(documents, newest)
        val originalsById = documents.withIndex().associate { (index, item) -> item.id to originals[index] }
        val flatDocuments = sectors.flatMap { it.groups }.flatMap { it.documents }
        require(flatDocuments.size == documents.size)

        // Itens do índice: cabeçalho de setor, subtítulo de data e entrada de documento
        val indexItems = mutableListOf<IndexItem>()
        sectors.forEach { sector ->
            val count = sector.groups.sumOf { it.documents.size }
            indexItems.add(SectorHead(sector.type, count, OrganizerMetadata.pdfColor(sector.type)))
            sector.groups.forEach { group ->
                if (sector.groups.size > 1 || group.date.isNotBlank()) indexItems.add(DateHead(group.date.ifBlank { "Sem data" }))
                group.documents.forEach { doc ->
                    val entry = lines("${doc.title} | ${doc.date.ifBlank { "Data não informada" }} | ${doc.type} | página 999", 450f, font, 11f)
                    indexItems.add(DocHead(doc, entry, entry.size + 1))
                }
            }
        }
        val indexGroups = mutableListOf<MutableList<IndexItem>>(mutableListOf())
        var available = 24
        indexItems.forEach { item ->
            if (item.weight > available) { indexGroups.add(mutableListOf()); available = 24 }
            indexGroups.last().add(item); available -= item.weight
        }

        // Página física: índices, depois capa de cada setor, divisória de cada documento e suas páginas
        val sectorCoverPage = IntArray(sectors.size)
        val firstPageById = HashMap<String, Int>()
        var cursor = indexGroups.size
        sectors.forEachIndexed { sectorIndex, sector ->
            cursor += 1; sectorCoverPage[sectorIndex] = cursor
            sector.groups.forEach { group ->
                group.documents.forEach { doc ->
                    cursor += 1; firstPageById[doc.id] = cursor
                    cursor += doc.pages
                }
            }
        }
        val totalPages = cursor

        PDDocument(MemoryUsageSetting.setupTempFileOnly().setTempDir(work)).use { combined ->

            fun drawBragaHeader(stream: PDPageContentStream, subtitle: String) {
                // Barra superior verde esmeralda institucional
                stream.setNonStrokingColor(0, 137, 123)
                stream.addRect(0f, 770f, 595f, 72f)
                stream.fill()

                // Filete de realce esmeralda claro
                stream.setNonStrokingColor(0, 168, 132)
                stream.addRect(0f, 766f, 595f, 4f)
                stream.fill()

                // Logomarca e título
                stream.beginText()
                stream.setFont(boldFont, 16f)
                stream.setNonStrokingColor(255, 255, 255)
                stream.newLineAtOffset(48f, 804f)
                stream.showText("BRAGA SAUDE")
                stream.endText()

                stream.beginText()
                stream.setFont(font, 9.5f)
                stream.setNonStrokingColor(230, 244, 241)
                stream.newLineAtOffset(48f, 786f)
                stream.showText(safe(subtitle.uppercase()))
                stream.endText()
            }

            fun drawBragaFooter(stream: PDPageContentStream, pageNumber: Int) {
                // Divisória do rodapé
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

            // ==================== PÁGINAS DE ÍNDICE ====================
            indexGroups.forEachIndexed { i, items ->
                val page = PDPage(PDRectangle.A4)
                combined.addPage(page)
                PDPageContentStream(combined, page).use { stream ->
                    drawBragaHeader(stream, "Indice Consolidado de Exames para Consulta")
                    drawBragaFooter(stream, i + 1)

                    // Bloco informativo no topo do índice
                    var y = 735f
                    stream.beginText()
                    stream.setFont(boldFont, 12f)
                    stream.setNonStrokingColor(26, 26, 46)
                    stream.newLineAtOffset(48f, y)
                    stream.showText(safe("Exames organizados para consulta - Índice ${i + 1}/${indexGroups.size}"))
                    stream.endText()
                    y -= 16f

                    stream.beginText()
                    stream.setFont(font, 8.5f)
                    stream.setNonStrokingColor(100, 116, 139)
                    stream.newLineAtOffset(48f, y)
                    stream.showText("Documentos fornecidos e conferidos pelo usuário.")
                    stream.endText()
                    y -= 22f

                    // Itens do índice: setores, grupos de data e entradas
                    items.forEach { item ->
                        when (item) {
                            is SectorHead -> {
                                val (r, g, b) = item.color
                                stream.setNonStrokingColor(r, g, b)
                                stream.addRect(48f, y - 20f, 499f, 20f)
                                stream.fill()
                                stream.beginText()
                                stream.setFont(boldFont, 11f)
                                stream.setNonStrokingColor(255, 255, 255)
                                stream.newLineAtOffset(58f, y - 14f)
                                stream.showText(safe("SETOR: ${item.type.uppercase()} • ${item.count} exame(s)"))
                                stream.endText()
                                y -= 30f
                            }
                            is DateHead -> {
                                stream.beginText()
                                stream.setFont(boldFont, 9f)
                                stream.setNonStrokingColor(71, 85, 105)
                                stream.newLineAtOffset(48f, y - 9f)
                                stream.showText(safe("Data: ${item.label}"))
                                stream.endText()
                                stream.setStrokingColor(203, 213, 225)
                                stream.setLineWidth(0.6f)
                                stream.moveTo(48f, y - 13f)
                                stream.lineTo(547f, y - 13f)
                                stream.stroke()
                                y -= 19f
                            }
                            is DocHead -> {
                                val doc = item.doc
                                val (r, g, b) = OrganizerMetadata.pdfColor(doc.type)
                                val entryLines = lines("${doc.title} | ${doc.date.ifBlank { "Data não informada" }} | ${doc.type} | página ${firstPageById[doc.id]}", 450f, font, 10f)
                                val hasTopics = doc.topics.isNotEmpty()
                                val cardHeight = 14f + entryLines.size * 13f + (if (hasTopics) 12f else 0f)

                                // Fundo do card
                                stream.setNonStrokingColor(248, 250, 252)
                                stream.addRect(48f, y - cardHeight, 499f, cardHeight)
                                stream.fill()

                                stream.setStrokingColor(226, 232, 240)
                                stream.setLineWidth(0.5f)
                                stream.addRect(48f, y - cardHeight, 499f, cardHeight)
                                stream.stroke()

                                // Ticket colorido do setor à esquerda
                                stream.setNonStrokingColor(r, g, b)
                                stream.addRect(48f, y - cardHeight, 4f, cardHeight)
                                stream.fill()

                                var textY = y - 13f
                                entryLines.forEach { line ->
                                    stream.beginText()
                                    stream.setFont(font, 10f)
                                    stream.setNonStrokingColor(26, 26, 46)
                                    stream.newLineAtOffset(58f, textY)
                                    stream.showText(line)
                                    stream.endText()
                                    textY -= 13f
                                }

                                if (hasTopics) {
                                    stream.beginText()
                                    stream.setFont(font, 8f)
                                    stream.setNonStrokingColor(0, 105, 92)
                                    stream.newLineAtOffset(58f, textY)
                                    stream.showText(safe("Tópicos: ${doc.topics.joinToString(", ")}"))
                                    stream.endText()
                                }

                                y -= (cardHeight + 8f)
                            }
                        }
                    }

                    // Aviso no rodapé do índice
                    stream.beginText()
                    stream.setFont(font, 8f)
                    stream.setNonStrokingColor(100, 116, 139)
                    stream.newLineAtOffset(48f, 52f)
                    stream.showText("Guarde os originais. A organização não preserva assinaturas digitais.")
                    stream.endText()
                }
            }

            // ==================== CAPAS DE SETOR ====================
            sectors.forEachIndexed { sectorIndex, sector ->
                val page = PDPage(PDRectangle.A4)
                combined.addPage(page)
                PDPageContentStream(combined, page).use { stream ->
                    drawBragaHeader(stream, "Setor: ${sector.type}")
                    drawBragaFooter(stream, sectorCoverPage[sectorIndex])
                    val (r, g, b) = OrganizerMetadata.pdfColor(sector.type)
                    val sectorDocs = sector.groups.flatMap { it.documents }

                    // Faixa colorida do setor
                    stream.setNonStrokingColor(r, g, b)
                    stream.addRect(48f, 540f, 499f, 140f)
                    stream.fill()
                    stream.beginText()
                    stream.setFont(boldFont, 22f)
                    stream.setNonStrokingColor(255, 255, 255)
                    stream.newLineAtOffset(68f, 638f)
                    stream.showText(safe(sector.type))
                    stream.endText()
                    stream.beginText()
                    stream.setFont(font, 11f)
                    stream.setNonStrokingColor(232, 246, 243)
                    stream.newLineAtOffset(68f, 614f)
                    stream.showText(safe("${sectorDocs.size} exame(s) neste setor"))
                    stream.endText()

                    // Lista de títulos do setor
                    var ly = 505f
                    stream.beginText()
                    stream.setFont(boldFont, 10f)
                    stream.setNonStrokingColor(26, 26, 46)
                    stream.newLineAtOffset(48f, ly)
                    stream.showText(safe(if (newest) "Documentos deste setor, dos mais recentes para os mais antigos:" else "Documentos deste setor, dos mais antigos para os mais recentes:"))
                    stream.endText()
                    ly -= 18f
                    sectorDocs.take(14).forEach { doc ->
                        stream.beginText()
                        stream.setFont(font, 10f)
                        stream.setNonStrokingColor(71, 85, 105)
                        stream.newLineAtOffset(58f, ly)
                        stream.showText(safe("- ${doc.title} — ${doc.date.ifBlank { "Data não informada" }}"))
                        stream.endText()
                        ly -= 15f
                    }
                    if (sectorDocs.size > 14) {
                        stream.beginText()
                        stream.setFont(font, 10f)
                        stream.setNonStrokingColor(100, 116, 139)
                        stream.newLineAtOffset(58f, ly)
                        stream.showText(safe("+ ${sectorDocs.size - 14} demais documentos deste setor"))
                        stream.endText()
                    }
                    stream.beginText()
                    stream.setFont(font, 8.5f)
                    stream.setNonStrokingColor(100, 116, 139)
                    stream.newLineAtOffset(48f, 396f)
                    stream.showText(safe("Cada documento começa por uma divisória própria; os números de página estão no índice."))
                    stream.endText()
                }
            }

            // ==================== FOLHAS DIVISÓRIAS E ORIGINAIS ====================
            flatDocuments.forEachIndexed { i, item ->
                val page = PDPage(PDRectangle.A4)
                combined.addPage(page)
                PDPageContentStream(combined, page).use { stream ->
                    drawBragaHeader(stream, "Documento de Exame Anexado")
                    drawBragaFooter(stream, firstPageById.getValue(item.id))

                    // Aba lateral colorida do setor
                    val (tr, tg, tb) = OrganizerMetadata.pdfColor(item.type)
                    stream.setNonStrokingColor(tr, tg, tb)
                    stream.addRect(583f, 0f, 12f, 842f)
                    stream.fill()

                    val (r, g, b) = OrganizerMetadata.pdfColor(item.type)

                    // Card principal do exame
                    val cardHeight = 220f
                    val cardY = 510f

                    stream.setNonStrokingColor(240, 250, 248)
                    stream.addRect(48f, cardY, 499f, cardHeight)
                    stream.fill()

                    stream.setStrokingColor(178, 223, 219)
                    stream.setLineWidth(1f)
                    stream.addRect(48f, cardY, 499f, cardHeight)
                    stream.stroke()

                    // Barra da categoria à esquerda
                    stream.setNonStrokingColor(r, g, b)
                    stream.addRect(48f, cardY, 6f, cardHeight)
                    stream.fill()

                    // Tag
                    stream.beginText()
                    stream.setFont(boldFont, 9f)
                    stream.setNonStrokingColor(r, g, b)
                    stream.newLineAtOffset(68f, cardY + 192f)
                    stream.showText(safe("EXAME ${i + 1} DE ${documents.size} • CATEGORIA: ${item.type.uppercase()}"))
                    stream.endText()

                    // Título em destaque
                    val titleLines = lines(item.title, 440f, boldFont, 15f)
                    var curY = cardY + 168f
                    titleLines.take(2).forEach { line ->
                        stream.beginText()
                        stream.setFont(boldFont, 15f)
                        stream.setNonStrokingColor(26, 26, 46)
                        stream.newLineAtOffset(68f, curY)
                        stream.showText(line)
                        stream.endText()
                        curY -= 18f
                    }

                    // Metadados
                    curY = minOf(curY, cardY + 130f)
                    stream.beginText()
                    stream.setFont(font, 10f)
                    stream.setNonStrokingColor(71, 85, 105)
                    stream.newLineAtOffset(68f, curY)
                    stream.showText(safe("Data: ${item.date.ifBlank { "Não informada" }}"))
                    stream.endText()
                    curY -= 16f

                    stream.beginText()
                    stream.setFont(font, 10f)
                    stream.setNonStrokingColor(71, 85, 105)
                    stream.newLineAtOffset(68f, curY)
                    stream.showText(safe("Tipo: ${item.type}"))
                    stream.endText()
                    curY -= 16f

                    stream.beginText()
                    stream.setFont(font, 10f)
                    stream.setNonStrokingColor(71, 85, 105)
                    stream.newLineAtOffset(68f, curY)
                    stream.showText(safe("Original: ${item.pages} página(s), a seguir."))
                    stream.endText()
                    curY -= 20f

                    if (item.topics.isNotEmpty()) {
                        stream.beginText()
                        stream.setFont(boldFont, 9.5f)
                        stream.setNonStrokingColor(0, 105, 92)
                        stream.newLineAtOffset(68f, curY)
                        stream.showText("Tópicos e Analitos Identificados:")
                        stream.endText()
                        curY -= 14f

                        stream.beginText()
                        stream.setFont(font, 9f)
                        stream.setNonStrokingColor(26, 26, 46)
                        stream.newLineAtOffset(68f, curY)
                        stream.showText(safe(item.topics.joinToString(", ")).take(75))
                        stream.endText()
                        curY -= 18f
                    }

                    // Texto de aviso no rodapé do card
                    stream.beginText()
                    stream.setFont(font, 8.5f)
                    stream.setNonStrokingColor(100, 116, 139)
                    stream.newLineAtOffset(68f, cardY + 15f)
                    stream.showText("Consulte o documento original e a avaliação do profissional de saúde.")
                    stream.endText()

                    // Card de orientações para a consulta
                    stream.setNonStrokingColor(248, 250, 252)
                    stream.addRect(48f, 380f, 499f, 95f)
                    stream.fill()

                    stream.setStrokingColor(226, 232, 240)
                    stream.setLineWidth(0.6f)
                    stream.addRect(48f, 380f, 499f, 95f)
                    stream.stroke()

                    stream.beginText()
                    stream.setFont(boldFont, 8.5f)
                    stream.setNonStrokingColor(71, 85, 105)
                    stream.newLineAtOffset(64f, 452f)
                    stream.showText("ORIENTACOES PARA A CONSULTA MEDICA")
                    stream.endText()

                    stream.beginText()
                    stream.setFont(font, 8f)
                    stream.setNonStrokingColor(100, 116, 139)
                    stream.newLineAtOffset(64f, 436f)
                    stream.showText("1. Documento anexado em formato original preservando tabelas, graficos e assinaturas.")
                    stream.endText()

                    stream.beginText()
                    stream.setFont(font, 8f)
                    stream.setNonStrokingColor(100, 116, 139)
                    stream.newLineAtOffset(64f, 420f)
                    stream.showText("2. O Braga Saude atua na organizacao pessoal de autocuidado e nao substitui laudos medicos.")
                    stream.endText()

                    stream.beginText()
                    stream.setFont(font, 8f)
                    stream.setNonStrokingColor(100, 116, 139)
                    stream.newLineAtOffset(64f, 404f)
                    stream.showText("3. Apresente este consolidado ao seu medico para acompanhamento da sua saude.")
                    stream.endText()
                }

                // Anexa as páginas originais do documento
                PDDocument.load(originalsById.getValue(item.id), MemoryUsageSetting.setupTempFileOnly().setTempDir(work)).use { source ->
                    check(source.numberOfPages == item.pages) { "Quantidade de páginas alterada. Confira o documento novamente." }
                    com.tom_roush.pdfbox.multipdf.PDFMergerUtility().appendDocument(combined, source)
                }
            }

            combined.save(output)
        }
    }
}
