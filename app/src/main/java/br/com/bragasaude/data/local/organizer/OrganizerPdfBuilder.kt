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

/**
 * Constrói o consolidado de exames para consulta médica com identidade visual Braga Saúde,
 * índice cronológico inteligente, divisão por categorias e cópia vetorial das páginas originais.
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

    fun build(documents: List<OrganizerDocument>, originals: List<File>, output: File, work: File) {
        require(documents.isNotEmpty() && documents.size == originals.size && documents.all { it.confirmed })
        val font = PDType1Font.HELVETICA
        val boldFont = PDType1Font.HELVETICA_BOLD

        fun safe(text: String, f: PDType1Font = font): String = buildString {
            text.forEach { c -> append(if (runCatching { f.encode(c.toString()) }.isSuccess) c else '?') }
        }

        fun lines(text: String, maxWidth: Float = 470f, f: PDType1Font = font, size: Float = 10.5f) =
            PdfTextLayout.wrap(safe(text, f), maxWidth) { f.getStringWidth(it) / 1000f * size }

        // Linhas de cada entrada para calcular a paginação do índice
        val entries = documents.map { lines("${it.title} | ${it.date.ifBlank { "Data não informada" }} | ${it.type} | página 999", 450f) }

        val indexGroups = mutableListOf<MutableList<Int>>(mutableListOf())
        var available = 22
        entries.forEachIndexed { i, entry ->
            val needed = maxOf(2, entry.size + (if (documents[i].topics.isNotEmpty()) 1 else 0))
            if (needed > available) { indexGroups.add(mutableListOf()); available = 22 }
            indexGroups.last().add(i); available -= needed
        }

        val firstPages = mutableListOf<Int>()
        var next = indexGroups.size + 1
        documents.forEach { firstPages.add(next); next += 1 + it.pages }
        val totalPages = indexGroups.size + documents.sumOf { 1 + it.pages }

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

            fun getCategoryColor(type: String): Triple<Int, Int, Int> = when (type.lowercase()) {
                "laboratorial" -> Triple(0, 137, 123)     // Esmeralda
                "imagem" -> Triple(2, 132, 199)           // Azul
                "cardiológico", "cardiologico" -> Triple(124, 58, 237) // Violeta
                else -> Triple(100, 116, 139)             // Cinza ardósia
            }

            // ==================== PÁGINAS DE ÍNDICE ====================
            indexGroups.forEachIndexed { i, group ->
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

                    // Cards dos exames listados
                    group.forEach { n ->
                        val doc = documents[n]
                        val (r, g, b) = getCategoryColor(doc.type)
                        val titleLines = lines(doc.title, 360f, boldFont, 10.5f)
                        val hasTopics = doc.topics.isNotEmpty()
                        val cardHeight = 36f + (titleLines.size - 1) * 12f + (if (hasTopics) 12f else 0f)

                        // Fundo do card
                        stream.setNonStrokingColor(248, 250, 252)
                        stream.addRect(48f, y - cardHeight, 499f, cardHeight)
                        stream.fill()

                        stream.setStrokingColor(226, 232, 240)
                        stream.setLineWidth(0.5f)
                        stream.addRect(48f, y - cardHeight, 499f, cardHeight)
                        stream.stroke()

                        // Barra indicadora de categoria à esquerda
                        stream.setNonStrokingColor(r, g, b)
                        stream.addRect(48f, y - cardHeight, 4f, cardHeight)
                        stream.fill()

                        // Título do documento
                        var textY = y - 13f
                        titleLines.forEachIndexed { _, line ->
                            stream.beginText()
                            stream.setFont(boldFont, 10f)
                            stream.setNonStrokingColor(26, 26, 46)
                            stream.newLineAtOffset(58f, textY)
                            stream.showText(line)
                            stream.endText()
                            textY -= 12f
                        }

                        // Detalhes e Destino de Página
                        val metaText = "${doc.title} | ${doc.date.ifBlank { "Data não informada" }} | ${doc.type} | página ${firstPages[n]}"
                        stream.beginText()
                        stream.setFont(font, 8.5f)
                        stream.setNonStrokingColor(71, 85, 105)
                        stream.newLineAtOffset(58f, textY)
                        stream.showText(safe(metaText).take(80))
                        stream.endText()

                        if (hasTopics) {
                            textY -= 11f
                            stream.beginText()
                            stream.setFont(font, 8f)
                            stream.setNonStrokingColor(0, 105, 92)
                            stream.newLineAtOffset(58f, textY)
                            stream.showText(safe("Tópicos: ${doc.topics.joinToString(", ")}").take(75))
                            stream.endText()
                        }

                        y -= (cardHeight + 8f)
                    }

                    // Disclaimer no rodapé do índice
                    stream.beginText()
                    stream.setFont(font, 8f)
                    stream.setNonStrokingColor(100, 116, 139)
                    stream.newLineAtOffset(48f, 52f)
                    stream.showText("Guarde os originais. A organização não preserva assinaturas digitais.")
                    stream.endText()
                }
            }

            // ==================== FOLHAS DIVISÓRIAS E ORIGINAIS ====================
            documents.forEachIndexed { i, item ->
                val page = PDPage(PDRectangle.A4)
                combined.addPage(page)
                PDPageContentStream(combined, page).use { stream ->
                    drawBragaHeader(stream, "Documento de Exame Anexado")
                    drawBragaFooter(stream, firstPages[i])

                    val (r, g, b) = getCategoryColor(item.type)

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
                PDDocument.load(originals[i], MemoryUsageSetting.setupTempFileOnly().setTempDir(work)).use { source ->
                    check(source.numberOfPages == item.pages) { "Quantidade de páginas alterada. Confira o documento novamente." }
                    com.tom_roush.pdfbox.multipdf.PDFMergerUtility().appendDocument(combined, source)
                }
            }

            combined.save(output)
        }
    }
}
