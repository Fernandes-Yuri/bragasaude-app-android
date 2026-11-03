package br.com.bragasaude.agent.extraction

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pipeline de Extração de Texto em Cascata (Digital -> OCR Local ML Kit).
 *
 * Prioriza leitura digital nativa do PDF (custo zero de processamento e 100% de fidelidade).
 * Degrada automaticamente para renderização visual e OCR local via ML Kit caso o PDF seja escaneado
 * ou o documento seja uma imagem fotográfica.
 */
@Singleton
class CascadeTextExtractor @Inject constructor(
    private val ocrEngine: OcrEngine
) {
    companion object {
        private const val MIN_DIGITAL_TEXT_CHARS = 30
        private const val MIN_DIGITAL_WORDS = 5
        private const val MAX_RENDER_DIMENSION = 1800
    }

    /**
     * Verifica se o texto digital extraído diretamente do stream do PDF é substancial e confiável.
     */
    fun isReliableDigitalText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < MIN_DIGITAL_TEXT_CHARS) return false
        val words = trimmed.split(Regex("""\s+""")).filter { it.length >= 2 }
        return words.size >= MIN_DIGITAL_WORDS
    }

    /**
     * Extrai o conteúdo textual de um arquivo PDF aplicando a estratégia em cascata página a página.
     */
    suspend fun extractFromPdf(pdfFile: File): ExtractedDocument = withContext(Dispatchers.IO) {
        require(pdfFile.exists() && pdfFile.length() > 0) { "Arquivo PDF inválido ou inexistente: ${pdfFile.path}" }

        val pages = mutableListOf<ExtractedPage>()

        PDDocument.load(pdfFile).use { document ->
            val totalPages = document.numberOfPages

            for (pageIndex in 0 until totalPages) {
                // Camada 1: Extração Digital Direta via PDFBox nativo
                val digitalText = runCatching {
                    val stripper = PDFTextStripper().apply {
                        startPage = pageIndex + 1
                        endPage = pageIndex + 1
                    }
                    stripper.getText(document).trim()
                }.getOrDefault("")

                if (isReliableDigitalText(digitalText)) {
                    // Texto digital disponível e de alta fidelidade
                    val lines = digitalText.lines().filter { it.isNotBlank() }
                    val blocks = lines.mapIndexed { idx, line ->
                        ExtractedBlock(
                            text = line,
                            confidence = 1.0f,
                            box = null,
                            lineIndex = idx
                        )
                    }

                    pages.add(
                        ExtractedPage(
                            pageIndex = pageIndex,
                            text = digitalText,
                            blocks = blocks,
                            isDigital = true,
                            averageConfidence = 1.0f
                        )
                    )
                } else {
                    // Camada 2: PDF escaneado/imagem -> Renderiza página e executa OCR local (ML Kit)
                    val bitmap = renderPdfPage(pdfFile, pageIndex)
                    try {
                        val ocrBlocks = ocrEngine.recognize(bitmap)
                        val pageText = ocrBlocks.joinToString("\n") { it.text }

                        pages.add(
                            ExtractedPage(
                                pageIndex = pageIndex,
                                text = pageText,
                                blocks = ocrBlocks,
                                isDigital = false
                            )
                        )
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
        }

        val fullText = pages.joinToString("\n\n") { "--- Página ${it.pageIndex + 1} ---\n${it.text}" }
        ExtractedDocument(
            pages = pages,
            fullText = fullText
        )
    }

    /**
     * Extrai texto diretamente de um [Bitmap] (ex: foto de receita ou laudo) via OCR local.
     */
    suspend fun extractFromBitmap(bitmap: Bitmap, pageIndex: Int = 0): ExtractedPage = withContext(Dispatchers.IO) {
        val ocrBlocks = ocrEngine.recognize(bitmap)
        val text = ocrBlocks.joinToString("\n") { it.text }
        ExtractedPage(
            pageIndex = pageIndex,
            text = text,
            blocks = ocrBlocks,
            isDigital = false
        )
    }

    /**
     * Renderiza uma página de arquivo PDF em um [Bitmap] com escala otimizada para OCR.
     */
    fun renderPdfPage(pdfFile: File, pageIndex: Int, maxDimension: Int = MAX_RENDER_DIMENSION): Bitmap {
        return ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                renderer.openPage(pageIndex).use { page ->
                    val scale = minOf(2.0f, maxDimension.toFloat() / maxOf(page.width, page.height))
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        }
    }
}
