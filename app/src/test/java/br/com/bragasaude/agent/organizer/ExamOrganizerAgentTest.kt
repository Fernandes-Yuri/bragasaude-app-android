package br.com.bragasaude.agent.organizer

import android.graphics.Bitmap
import br.com.bragasaude.agent.extraction.CascadeTextExtractor
import br.com.bragasaude.agent.extraction.ExtractedBlock
import br.com.bragasaude.agent.extraction.ExtractedDocument
import br.com.bragasaude.agent.extraction.ExtractedPage
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExamOrganizerAgentTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val cascadeExtractor = mockk<CascadeTextExtractor>()
    private lateinit var agent: ExamOrganizerAgent

    @Before
    fun setUp() {
        agent = ExamOrganizerAgent(cascadeExtractor)
    }

    @Test
    fun `analyzeExam analisa PDF digital e retorna resultado estruturado com progresso`() = runTest {
        val testFile = tempFolder.newFile("exame_hemograma.pdf")

        val examText = """
            Laboratório Sabin Diagnósticos
            Paciente: Maria Silva
            Data de Coleta: 10/05/2026
            Hemograma Completo
            Glicemia de jejum: 92 mg/dL
        """.trimIndent()

        val page = ExtractedPage(
            pageIndex = 0,
            text = examText,
            blocks = listOf(ExtractedBlock("Hemograma Completo", confidence = 0.99f)),
            isDigital = true
        )
        val extractedDoc = ExtractedDocument(pages = listOf(page), fullText = examText)

        coEvery { cascadeExtractor.extractFromPdf(testFile) } returns extractedDoc

        val progressUpdates = mutableListOf<String>()
        val result = agent.analyzeExam(testFile, "laudo_sabin.pdf") { progressUpdates.add(it) }

        assertEquals("Hemograma Completo", result.title)
        assertEquals("10/05/2026", result.date)
        assertEquals("Laboratorial", result.type)
        assertTrue(result.topics.contains("Hemograma"))
        assertTrue(result.topics.contains("Glicemia"))
        assertEquals(1, result.pages)
        assertTrue(result.isDigital)
        assertTrue(progressUpdates.isNotEmpty())
    }

    @Test
    fun `analyzeExam aplica fallbackName quando titulo extraido for generico`() = runTest {
        val testFile = tempFolder.newFile("exame_desconhecido.pdf")

        val page = ExtractedPage(
            pageIndex = 0,
            text = "12345 67890 arquivo de teste geral 2026",
            blocks = emptyList(),
            isDigital = true
        )
        val extractedDoc = ExtractedDocument(pages = listOf(page), fullText = page.text)

        coEvery { cascadeExtractor.extractFromPdf(testFile) } returns extractedDoc

        val result = agent.analyzeExam(testFile, "declaracao_medica.pdf")

        assertEquals("declaracao_medica", result.title)
        assertEquals("Outros", result.type)
    }

    @Test
    fun `analyzeBitmap analisa foto de exame cardiologico via OCR e classifica corretamente`() = runTest {
        val mockBitmap = mockk<Bitmap>(relaxed = true)

        val ocrText = """
            Hospital e Maternidade São Luiz
            Data do exame: 12/04/2026
            Eletrocardiograma de repouso (ECG)
            Ritmo sinusal regular, FC: 72 bpm
        """.trimIndent()

        val page = ExtractedPage(
            pageIndex = 0,
            text = ocrText,
            blocks = listOf(ExtractedBlock("Eletrocardiograma", confidence = 0.88f)),
            isDigital = false
        )

        coEvery { cascadeExtractor.extractFromBitmap(mockBitmap, 0) } returns page

        val progressUpdates = mutableListOf<String>()
        val result = agent.analyzeBitmap(mockBitmap, "foto_ecg.jpg") { progressUpdates.add(it) }

        assertEquals("Eletrocardiograma", result.title)
        assertEquals("12/04/2026", result.date)
        assertEquals("Cardiológico", result.type)
        assertTrue(result.topics.contains("Eletrocardiograma"))
        assertEquals(1, result.pages)
        assertFalse(result.isDigital)
        assertTrue(progressUpdates.isNotEmpty())
    }

    @Test
    fun `analyzeExam preserva ultrassonografia pelvica com eco endometrial`() = runTest {
        val testFile = tempFolder.newFile("ultrassom_pelvico.pdf")
        val text = "ULTRASSONOGRAFIA PELVICA TRANSVAGINAL\n" +
            "Endometrio: eco endometrial centrado. Nodulo hipoecoico no ovario."
        val page = ExtractedPage(pageIndex = 0, text = text, blocks = emptyList(), isDigital = true)
        coEvery { cascadeExtractor.extractFromPdf(testFile) } returns ExtractedDocument(listOf(page), text)

        val result = agent.analyzeExam(testFile, "pelvica.pdf")

        assertEquals("Ultrassonografia", result.title)
        assertEquals("Imagem", result.type)
        assertFalse(result.topics.contains("Ecocardiograma"))
    }

    @Test
    fun `analyzeBitmap classifica painel de tireoide pelo cabecalho e pelos topicos`() = runTest {
        val mockBitmap = mockk<Bitmap>(relaxed = true)
        val text = "DIAGNOLAB - PAINEL TIREOIDE\nTSH e T4 livre dosados nesta amostra."
        val page = ExtractedPage(pageIndex = 0, text = text, blocks = emptyList(), isDigital = false)
        coEvery { cascadeExtractor.extractFromBitmap(mockBitmap, 0) } returns page

        val result = agent.analyzeBitmap(mockBitmap, "tireoide.jpg")

        assertEquals("Painel de Tireoide (TSH/T4)", result.title)
        assertEquals("Laboratorial", result.type)
        assertTrue(result.topics.contains("Tireoide"))
        assertTrue("o PDF deve reutilizar a mesma página OCR da análise", result.ocrPage === page)
    }
}
