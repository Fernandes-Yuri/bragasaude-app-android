package br.com.bragasaude.agent.organizer

import android.content.Context
import br.com.bragasaude.agent.extraction.CascadeTextExtractor
import br.com.bragasaude.agent.extraction.ExtractedDocument
import br.com.bragasaude.agent.extraction.ExtractedPage
import br.com.bragasaude.data.local.MedicationDao
import br.com.bragasaude.data.local.MedicationEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrescriptionOrganizerAgentTest {

    private val context = mockk<Context>(relaxed = true)
    private val cascadeExtractor = mockk<CascadeTextExtractor>(relaxed = true)
    private val medicationDao = mockk<MedicationDao>(relaxed = true)
    private lateinit var agent: PrescriptionOrganizerAgent

    @Before
    fun setUp() {
        agent = PrescriptionOrganizerAgent(
            context = context,
            cascadeExtractor = cascadeExtractor,
            medicationDao = medicationDao,
            apiClient = null
        )
    }

    @Test
    fun `analyzeExtractedDocument enriquece com ANVISA e detecta duplicidade terapeutica no Room`() = runTest {
        val patientId = "patient-abc"

        // Paciente já toma Losartana 50mg no banco local
        val existingMed = MedicationEntity(
            id = "med-uuid-1",
            userId = patientId,
            name = "Losartana Potássica",
            dosage = "50mg",
            activePrinciple = "Losartana potássica"
        )
        coEvery { medicationDao.getAllSync(patientId) } returns listOf(existingMed)

        val prescriptionText = """
            RECEITA MÉDICA
            1. Losartana 50mg
            Tomar 1 comprimido ao dia
            
            2. Amoxicilina 500mg
            Tomar 1 cápsula de 8/8h por 7 dias
        """.trimIndent()

        val page = ExtractedPage(pageIndex = 0, text = prescriptionText, blocks = emptyList(), isDigital = true)
        val extractedDoc = ExtractedDocument(pages = listOf(page), fullText = prescriptionText)

        val response = agent.analyzeExtractedDocument(patientId, extractedDoc)

        assertEquals("success", response.status)
        assertEquals(2, response.medications.size)

        // Item 1: Losartana deve ter alerta de duplicidade terapêutica com o banco local
        val losartana = response.medications.first { it.name.contains("Losartana") }
        assertTrue(losartana.requiresHumanFill)
        assertNotNull(losartana.divergenceReason)
        assertTrue(losartana.divergenceReason?.contains("já possui tratamento ativo") ?: false)
        assertEquals("Losartana potássica", losartana.activePrinciple)
        assertEquals("1023508910012", losartana.anvisaRegistrationNumber)

        // Item 2: Amoxicilina deve estar limpa e enriquecida pela ANVISA
        val amoxicilina = response.medications.first { it.name.contains("Amoxicilina") }
        assertEquals("8/8h", amoxicilina.frequency)
        assertEquals(listOf("08:00", "16:00", "00:00"), amoxicilina.suggestedTimes)
        assertEquals("Amoxicilina tri-hidratada", amoxicilina.activePrinciple)
        assertEquals("1023508490011", amoxicilina.anvisaRegistrationNumber)
    }

    @Test
    fun `analyzeExtractedDocument mescla vias duplicadas da farmacia`() = runTest {
        val patientId = "patient-xyz"
        coEvery { medicationDao.getAllSync(patientId) } returns emptyList()

        // 1ª via (página 0) e 2ª via (página 1) contendo o mesmo medicamento
        val page0 = ExtractedPage(
            pageIndex = 0,
            text = "1. Dipirona 500mg\nTomar de 6 em 6 horas",
            blocks = emptyList(),
            isDigital = true
        )
        val page1 = ExtractedPage(
            pageIndex = 1,
            text = "1. Dipirona 500mg\nTomar de 6 em 6 horas",
            blocks = emptyList(),
            isDigital = true
        )
        val extractedDoc = ExtractedDocument(
            pages = listOf(page0, page1),
            fullText = "${page0.text}\n---\n${page1.text}"
        )

        val response = agent.analyzeExtractedDocument(patientId, extractedDoc)

        assertEquals("success", response.status)
        assertEquals(1, response.medications.size)
        assertEquals(1, response.duplicatesMergedCount)
    }
}
