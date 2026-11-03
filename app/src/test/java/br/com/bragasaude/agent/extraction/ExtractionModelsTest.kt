package br.com.bragasaude.agent.extraction

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractionModelsTest {

    @Test
    fun `valida limites do NormalizedBoundingBox`() {
        val box = NormalizedBoundingBox(0.1f, 0.2f, 0.8f, 0.9f)
        assertEquals(0.7f, box.width, 0.001f)
        assertEquals(0.7f, box.height, 0.001f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejeita NormalizedBoundingBox com coordenadas negativas`() {
        NormalizedBoundingBox(-0.1f, 0.2f, 0.8f, 0.9f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejeita NormalizedBoundingBox com right menor que left`() {
        NormalizedBoundingBox(0.5f, 0.2f, 0.4f, 0.9f)
    }

    @Test
    fun `identifica blocos ambiguos com confianca abaixo do limiar padrao de 0_75`() {
        val highConfidenceBlock = ExtractedBlock("Dipirona 500mg", confidence = 0.92f)
        val lowConfidenceBlock = ExtractedBlock("d i p i ~ ~ 5 0", confidence = 0.55f)

        assertFalse(highConfidenceBlock.isAmbiguous())
        assertTrue(lowConfidenceBlock.isAmbiguous())
        assertTrue(lowConfidenceBlock.isAmbiguous(threshold = 0.60f))
        assertFalse(lowConfidenceBlock.isAmbiguous(threshold = 0.50f))
    }

    @Test
    fun `agrega blocos ambiguos por pagina e documento`() {
        val block1 = ExtractedBlock("Amoxicilina", confidence = 0.95f)
        val block2 = ExtractedBlock("posologia cursiva ilegivel", confidence = 0.60f)
        val block3 = ExtractedBlock("1 comprimido de 8/8h", confidence = 0.88f)

        val page0 = ExtractedPage(
            pageIndex = 0,
            text = "Amoxicilina\nposologia cursiva ilegivel",
            blocks = listOf(block1, block2),
            isDigital = false
        )
        val page1 = ExtractedPage(
            pageIndex = 1,
            text = "1 comprimido de 8/8h",
            blocks = listOf(block3),
            isDigital = true
        )

        assertTrue(page0.hasAmbiguities())
        assertEquals(1, page0.getAmbiguousBlocks().size)
        assertEquals(block2, page0.getAmbiguousBlocks().first())

        assertFalse(page1.hasAmbiguities())
        assertTrue(page1.getAmbiguousBlocks().isEmpty())

        val document = ExtractedDocument(
            pages = listOf(page0, page1),
            fullText = "${page0.text}\n${page1.text}"
        )

        val allAmbiguities = document.getAllAmbiguousBlocks()
        assertEquals(1, allAmbiguities.size)
        assertEquals(0, allAmbiguities.first().first) // página 0
        assertEquals(block2, allAmbiguities.first().second)
        assertFalse(document.isFullyDigital)
    }
}
