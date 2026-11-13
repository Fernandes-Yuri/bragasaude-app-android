package br.com.bragasaude.agent.tools

import br.com.bragasaude.agent.extraction.ExtractedBlock
import br.com.bragasaude.agent.extraction.ExtractedDocument
import br.com.bragasaude.agent.extraction.ExtractedPage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamToolsTest {

    @Test
    fun `InspectExamPagesTool retorna erro amigavel quando documento nao esta disponivel`() = runTest {
        val tool = InspectExamPagesTool { null }
        val resultJson = tool.execute("{}")

        val root = Json.parseToJsonElement(resultJson).jsonObject
        assertEquals("error", root["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `InspectExamPagesTool inspeciona paginas e metadados com sucesso`() = runTest {
        val page0 = ExtractedPage(
            pageIndex = 0,
            text = "Laboratório Fleury - Hemograma Completo - Coleta: 15/04/2026",
            blocks = listOf(ExtractedBlock("Hemograma Completo", confidence = 0.98f)),
            isDigital = true
        )
        val page1 = ExtractedPage(
            pageIndex = 1,
            text = "Leucócitos: 6.500 /mm3 - Plaquetas: 250.000 /mm3",
            blocks = listOf(ExtractedBlock("Leucócitos: 6.500", confidence = 0.65f)),
            isDigital = false
        )
        val doc = ExtractedDocument(pages = listOf(page0, page1), fullText = "${page0.text}\n${page1.text}")

        val tool = InspectExamPagesTool { doc }
        val resultJson = tool.execute("""{"max_preview_length": 30}""")

        val root = Json.parseToJsonElement(resultJson).jsonObject
        assertEquals("success", root["status"]?.jsonPrimitive?.content)
        assertEquals(2, root["total_pages"]?.jsonPrimitive?.int)
        assertFalse(root["is_fully_digital"]?.jsonPrimitive?.boolean ?: true)

        val pages = root["pages"]?.jsonArray
        assertEquals(2, pages?.size)
        assertEquals(30, pages?.get(0)?.jsonObject?.get("text_preview")?.jsonPrimitive?.content?.length)
        assertTrue(pages?.get(1)?.jsonObject?.get("has_ambiguities")?.jsonPrimitive?.boolean ?: false)
    }

    @Test
    fun `ExtractExamHeaderTool extrai tipo, laboratorio e data ignorando data de nascimento`() = runTest {
        val examText = """
            LABORATÓRIO FLEURY MEDICINA DIAGNÓSTICA
            Paciente: Carlos Drumond
            Data de Nasc.: 25/07/1980
            Data da Coleta: 18/03/2026
            HEMOGRAMA COMPLETO
            Eritrócitos: 4.8 milhões/mm³
            Plaquetas: 210.000 /mm³
        """.trimIndent()

        val page = ExtractedPage(pageIndex = 0, text = examText, blocks = emptyList(), isDigital = true)
        val doc = ExtractedDocument(pages = listOf(page), fullText = examText)

        val tool = ExtractExamHeaderTool { doc }
        val resultJson = tool.execute("{}")

        val root = Json.parseToJsonElement(resultJson).jsonObject
        assertEquals("success", root["status"]?.jsonPrimitive?.content)
        assertEquals("Hemograma Completo", root["title"]?.jsonPrimitive?.content)
        assertEquals("18/03/2026", root["exam_date"]?.jsonPrimitive?.content)
        assertTrue(root["laboratory"]?.jsonPrimitive?.content?.contains("FLEURY") ?: false)

        val topics = root["topics"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        assertTrue(topics.contains("Hemograma"))
    }

    @Test
    fun `DetectExamSectorsTool classifica corretamente os 4 setores canonicos`() = runTest {
        val tool = DetectExamSectorsTool()

        // 1. Laboratorial
        val labJson = tool.execute("""{"title": "Hemograma Completo", "topics": ["Hemograma", "Glicemia"]}""")
        val labRoot = Json.parseToJsonElement(labJson).jsonObject
        assertEquals("Laboratorial", labRoot["sector"]?.jsonPrimitive?.content)

        // 2. Imagem
        val imgJson = tool.execute("""{"title": "Ultrassonografia de Abdome Total", "topics": ["Ultrassonografia"]}""")
        val imgRoot = Json.parseToJsonElement(imgJson).jsonObject
        assertEquals("Imagem", imgRoot["sector"]?.jsonPrimitive?.content)

        // 3. Cardiológico
        val cardioJson = tool.execute("""{"title": "Ecocardiograma Transtorácico", "topics": ["Ecocardiograma"]}""")
        val cardioRoot = Json.parseToJsonElement(cardioJson).jsonObject
        assertEquals("Cardiológico", cardioRoot["sector"]?.jsonPrimitive?.content)

        // 4. Outros (fallback)
        val otherJson = tool.execute("""{"title": "Declaração de Comparecimento", "topics": []}""")
        val otherRoot = Json.parseToJsonElement(otherJson).jsonObject
        assertEquals("Outros", otherRoot["sector"]?.jsonPrimitive?.content)
    }

    @Test
    fun `ExtractExamHeaderTool reconhece titulos canonicos e DetectExamSectorsTool usa seus topicos`() = runTest {
        val cases = listOf(
            Triple("TESTE ERGOMETRICO ESTEIRA PROTOCOLO BRUCE", "Teste Ergométrico", "Cardiológico"),
            Triple("MAPA - MONITORIZACAO AMBULATORIAL DA PRESSAO ARTERIAL 24H", "MAPA 24h", "Cardiológico"),
            Triple("DENSITOMETRIA OSSEA DE COLUNA E FEMUR", "Densitometria Óssea", "Imagem"),
            Triple("URINA TIPO I (EAS) COM SEDIMENTOSCOPIA\nLeucocitos: 2/campo", "Urina Tipo I (EAS)", "Laboratorial"),
            Triple("PAINEL TIREOIDE - TSH E T4 LIVRE", "Painel de Tireoide (TSH/T4)", "Laboratorial"),
            Triple("FUNCAO RENAL - CREATININA E UREIA", "Função Renal (Creatinina/Ureia)", "Laboratorial")
        )
        cases.forEach { (text, expectedTitle, expectedSector) ->
            val page = ExtractedPage(pageIndex = 0, text = text, blocks = emptyList(), isDigital = true)
            val document = ExtractedDocument(pages = listOf(page), fullText = text)
            val header = Json.parseToJsonElement(ExtractExamHeaderTool { document }.execute("{}")).jsonObject
            val title = header["title"]?.jsonPrimitive?.content
            assertEquals(text, expectedTitle, title)

            val topics = header["topics"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
            val arguments = buildJsonObject {
                put("title", title)
                putJsonArray("topics") { topics.forEach { add(JsonPrimitive(it)) } }
            }.toString()
            val sector = Json.parseToJsonElement(DetectExamSectorsTool().execute(arguments)).jsonObject
            assertEquals(text, expectedSector, sector["sector"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `DetectExamSectorsTool amplia vocabulario laboratorial sem usar eco isolado`() = runTest {
        val tool = DetectExamSectorsTool()
        listOf("tireoide", "funcao renal", "ureia", "acido urico", "perfil lipidico", "enzimas hepaticas").forEach { term ->
            val root = Json.parseToJsonElement(tool.execute("""{"title": "$term", "topics": []}""")).jsonObject
            assertEquals(term, "Laboratorial", root["sector"]?.jsonPrimitive?.content)
        }
        val ultrasound = Json.parseToJsonElement(
            tool.execute("""{"title": "Ultrassonografia pélvica", "topics": ["Ultrassonografia"]}""")
        ).jsonObject
        assertEquals("Imagem", ultrasound["sector"]?.jsonPrimitive?.content)
    }

    @Test
    fun `ExtractPatientNameTool usa apenas rotulos explicitos e elimina duplicatas`() = runTest {
        val text = """
            Paciente: Joaquim Ficticio Testes
            Médico responsável: Dr. Ficticio Modelo
            Responsável: Heloisa Ficticia Exemplar
            PACIENTE: joaquim ficticio testes
            Paciente: Heloisa Ficticia Exemplar
            Paciente internado por cinco dias
        """.trimIndent()
        val page = ExtractedPage(pageIndex = 0, text = text, blocks = emptyList(), isDigital = true)
        val document = ExtractedDocument(pages = listOf(page), fullText = text)

        val root = Json.parseToJsonElement(ExtractPatientNameTool { document }.execute("{}")).jsonObject
        val names = root["patient_names"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()

        assertEquals(listOf("Heloisa Ficticia Exemplar", "Joaquim Ficticio Testes"), names)
        assertEquals("Heloisa Ficticia Exemplar", root["patient_name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `ExtractPatientNameTool ignora linhas sem identificacao confiavel`() = runTest {
        val text = "Paciente internado por cinco dias\nMédico responsável: Dr. Ficticio Modelo"
        val page = ExtractedPage(pageIndex = 0, text = text, blocks = emptyList(), isDigital = true)
        val document = ExtractedDocument(pages = listOf(page), fullText = text)

        val root = Json.parseToJsonElement(ExtractPatientNameTool { document }.execute("{}")).jsonObject

        assertTrue(root["patient_names"]?.jsonArray?.isEmpty() == true)
        assertEquals("", root["patient_name"]?.jsonPrimitive?.content)
    }
}
