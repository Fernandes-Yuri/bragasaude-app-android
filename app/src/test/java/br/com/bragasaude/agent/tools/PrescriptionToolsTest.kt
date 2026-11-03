package br.com.bragasaude.agent.tools

import br.com.bragasaude.agent.extraction.ExtractedDocument
import br.com.bragasaude.agent.extraction.ExtractedPage
import br.com.bragasaude.data.local.MedicationEntity
import br.com.bragasaude.data.remote.model.BarcodeMedication
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrescriptionToolsTest {

    @Test
    fun `ExtractMedicationItemsTool extrai farmacos, doses, frequencia e horarios calculados`() = runTest {
        val prescriptionText = """
            RECEITUÁRIO MÉDICO
            Dr. Roberto Santos - CRM 98765-SP
            Paciente: João da Silva
            
            1. Amoxicilina 500mg
            Tomar 1 cápsula de 8 em 8 horas por 7 dias
            
            2. Dipirona 500mg
            Tomar 1 comprimido de 6 em 6 horas se dor ou febre
            
            3. Losartana Potássica 50mg
            1 comprimido 1x ao dia pela manhã (uso contínuo)
        """.trimIndent()

        val page = ExtractedPage(pageIndex = 0, text = prescriptionText, blocks = emptyList(), isDigital = true)
        val doc = ExtractedDocument(pages = listOf(page), fullText = prescriptionText)

        val tool = ExtractMedicationItemsTool { doc }
        val resultJson = tool.execute("{}")

        val root = Json.parseToJsonElement(resultJson).jsonObject
        assertEquals("success", root["status"]?.jsonPrimitive?.content)
        assertEquals(3, root["total_found"]?.jsonPrimitive?.int)

        val meds = root["medications"]?.jsonArray
        assertEquals(3, meds?.size)

        // Item 1: Amoxicilina 8/8h por 7 dias
        val med1 = meds?.get(0)?.jsonObject
        assertTrue(med1?.get("name")?.jsonPrimitive?.content?.contains("Amoxicilina") ?: false)
        assertEquals("500mg", med1?.get("dosage")?.jsonPrimitive?.content)
        assertEquals("8/8h", med1?.get("frequency")?.jsonPrimitive?.content)
        assertEquals(8, med1?.get("frequency_interval_hours")?.jsonPrimitive?.int)
        assertEquals(3, med1?.get("daily_doses_count")?.jsonPrimitive?.int)
        val times1 = med1?.get("suggested_times")?.jsonArray?.map { it.jsonPrimitive.content }
        assertEquals(listOf("08:00", "16:00", "00:00"), times1)
        assertEquals(7, med1?.get("treatment_duration_days")?.jsonPrimitive?.int)
        assertFalse(med1?.get("requires_human_fill")?.jsonPrimitive?.boolean ?: true)

        // Item 2: Dipirona 6/6h
        val med2 = meds?.get(1)?.jsonObject
        assertTrue(med2?.get("name")?.jsonPrimitive?.content?.contains("Dipirona") ?: false)
        assertEquals("6/6h", med2?.get("frequency")?.jsonPrimitive?.content)
        assertEquals(6, med2?.get("frequency_interval_hours")?.jsonPrimitive?.int)
        assertEquals(4, med2?.get("daily_doses_count")?.jsonPrimitive?.int)

        // Item 3: Losartana 1x ao dia
        val med3 = meds?.get(2)?.jsonObject
        assertTrue(med3?.get("name")?.jsonPrimitive?.content?.contains("Losartana") ?: false)
        assertEquals("1x ao dia", med3?.get("frequency")?.jsonPrimitive?.content)
        assertEquals(24, med3?.get("frequency_interval_hours")?.jsonPrimitive?.int)
        assertEquals(1, med3?.get("daily_doses_count")?.jsonPrimitive?.int)
    }

    @Test
    fun `ExtractMedicationItemsTool sinaliza requires_human_fill quando posologia for incompleta`() = runTest {
        val ambiguousText = """
            1. Medicamento Específico
            Posologia a critério médico sem horário
        """.trimIndent()

        val page = ExtractedPage(pageIndex = 0, text = ambiguousText, blocks = emptyList(), isDigital = true)
        val doc = ExtractedDocument(pages = listOf(page), fullText = ambiguousText)

        val tool = ExtractMedicationItemsTool { doc }
        val resultJson = tool.execute("{}")

        val root = Json.parseToJsonElement(resultJson).jsonObject
        val meds = root["medications"]?.jsonArray
        assertEquals(1, meds?.size)

        val med = meds?.get(0)?.jsonObject
        assertTrue(med?.get("requires_human_fill")?.jsonPrimitive?.boolean ?: false)
    }

    @Test
    fun `SearchAnvisaCatalogTool busca principio ativo e registro na base oficial`() = runTest {
        val tool = SearchAnvisaCatalogTool()

        // 1. Medicamento no catálogo ANVISA
        val jsonFound = tool.execute("""{"name": "Amoxicilina 500mg"}""")
        val rootFound = Json.parseToJsonElement(jsonFound).jsonObject
        assertTrue(rootFound["found"]?.jsonPrimitive?.boolean ?: false)
        assertEquals("Amoxicilina tri-hidratada", rootFound["active_principle"]?.jsonPrimitive?.content)
        assertEquals("1023508490011", rootFound["anvisa_registration_number"]?.jsonPrimitive?.content)
        assertEquals("7891234567890", rootFound["ean_barcode"]?.jsonPrimitive?.content)

        // 2. Medicamento desconhecido
        val jsonNotFound = tool.execute("""{"name": "Composto Fitoterapico X"}""")
        val rootNotFound = Json.parseToJsonElement(jsonNotFound).jsonObject
        assertFalse(rootNotFound["found"]?.jsonPrimitive?.boolean ?: true)
    }

    @Test
    fun `SearchAnvisaCatalogTool consulta barcode via delegado quando EAN for fornecido`() = runTest {
        val fakeBarcodeMed = BarcodeMedication(
            eanBarcode = "7891045032014",
            name = "Dipirona 500mg Novalgina",
            activePrinciple = "Dipirona Monoidratada",
            sourceName = "ANVISA",
            anvisaRegistrationNumber = "1023506890011"
        )

        val tool = SearchAnvisaCatalogTool { ean ->
            if (ean == "7891045032014") fakeBarcodeMed else null
        }

        val json = tool.execute("""{"name": "Novalgina", "ean": "7891045032014"}""")
        val root = Json.parseToJsonElement(json).jsonObject
        assertTrue(root["found"]?.jsonPrimitive?.boolean ?: false)
        assertEquals("Dipirona 500mg Novalgina", root["official_name"]?.jsonPrimitive?.content)
        assertEquals("1023506890011", root["anvisa_registration_number"]?.jsonPrimitive?.content)
    }

    @Test
    fun `GetPatientMedicationsTool retorna tratamentos ativos para conciliacao`() = runTest {
        val activeMeds = listOf(
            MedicationEntity(
                id = "med-1",
                userId = "pat-123",
                name = "Losartana Potássica",
                dosage = "50mg",
                activePrinciple = "Losartana"
            )
        )

        val tool = GetPatientMedicationsTool { patientId ->
            if (patientId == "pat-123") activeMeds else emptyList()
        }

        val json = tool.execute("""{"patient_id": "pat-123"}""")
        val root = Json.parseToJsonElement(json).jsonObject
        assertEquals("success", root["status"]?.jsonPrimitive?.content)
        assertEquals(1, root["total_active"]?.jsonPrimitive?.int)
    }
}
