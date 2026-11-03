package br.com.bragasaude.agent.extraction

import android.app.Application
import android.graphics.Bitmap
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CascadeTextExtractorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val ocrEngine = mockk<OcrEngine>(relaxed = true)
    private val extractor = CascadeTextExtractor(ocrEngine)

    @Test
    fun `isReliableDigitalText valida presenca de palavras reais e rejeita texto escasso`() {
        val goodText = "Receita médica do paciente Dr. Silva CRM 12345 Tomar Dipirona 500mg"
        val sparseText = "A 1"
        val emptyText = "   "

        assertTrue(extractor.isReliableDigitalText(goodText))
        assertFalse(extractor.isReliableDigitalText(sparseText))
        assertFalse(extractor.isReliableDigitalText(emptyText))
    }

    @Test
    fun `extrai PDF textual digital diretamente com custo zero de OCR na Camada 1`() = runTest {
        val pdfFile = tempFolder.newFile("receita_digital.pdf")

        // Cria PDF digital com texto embutido
        PDDocument().use { doc ->
            val page = PDPage()
            doc.addPage(page)
            PDPageContentStream(doc, page).use { cs ->
                cs.beginText()
                cs.setFont(PDType1Font.HELVETICA, 12f)
                cs.newLineAtOffset(50f, 700f)
                cs.showText("Receituario Digital Medico - Tomar Amoxicilina 500mg de 8 em 8 horas")
                cs.endText()
            }
            doc.save(pdfFile)
        }

        val result = extractor.extractFromPdf(pdfFile)

        assertEquals(1, result.totalPages)
        assertTrue(result.isFullyDigital)
        assertEquals(1.0f, result.averageConfidence, 0.001f)

        val page = result.pages[0]
        assertTrue(page.isDigital)
        assertTrue(page.text.contains("Receituario Digital Medico"))
        assertTrue(page.text.contains("Amoxicilina 500mg"))

        // Garante que o motor de OCR NÃO foi invocado desnecessariamente
        coVerify(exactly = 0) { ocrEngine.recognize(any()) }
    }

    @Test
    fun `extrai diretamente de bitmap delegando para o motor de OCR na Camada 2`() = runTest {
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)

        val mockBlocks = listOf(
            ExtractedBlock(
                text = "Hemograma Completo",
                confidence = 0.94f,
                box = NormalizedBoundingBox(0.1f, 0.1f, 0.9f, 0.3f),
                lineIndex = 0
            ),
            ExtractedBlock(
                text = "Hemoglobina: 14.5 g/dL",
                confidence = 0.91f,
                box = NormalizedBoundingBox(0.1f, 0.4f, 0.9f, 0.6f),
                lineIndex = 1
            )
        )

        coEvery { ocrEngine.recognize(any()) } returns mockBlocks

        val page = extractor.extractFromBitmap(bitmap, pageIndex = 0)

        assertFalse(page.isDigital)
        assertEquals(2, page.blocks.size)
        assertTrue(page.text.contains("Hemograma Completo"))
        assertTrue(page.text.contains("Hemoglobina: 14.5 g/dL"))
        assertTrue(page.averageConfidence > 0.90f)

        coVerify(exactly = 1) { ocrEngine.recognize(any()) }
    }
}
