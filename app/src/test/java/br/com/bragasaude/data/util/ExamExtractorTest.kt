package br.com.bragasaude.data.util

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ExamExtractorTest {

    private lateinit var extractor: ExamExtractor
    private val mockContext: Context = mockk(relaxed = true)

    @Before
    fun setup() {
        extractor = ExamExtractor(mockContext)
    }

    @Test
    fun `detecta exame laboratorial estruturado Tipo A para exames de sangue`() {
        val labText = """
            LABORATÓRIO CENTRAL
            HEMOGRAMA COMPLETO
            Glicose em Jejum: 92 mg/dL
            Colesterol Total: 175 mg/dL
            Colesterol HDL: 48 mg/dL
            Triglicerídeos: 120 mg/dL
            Creatinina: 0.9 mg/dL
        """.trimIndent()

        val category = extractor.detectExamType(labText)
        assertEquals(ExamCategoryType.STRUCTURED_LAB, category)
    }

    @Test
    fun `detecta documento nao estruturado Tipo B para laudo de ultrassom ou tomografia`() {
        val ultrasoundText = """
            CLÍNICA DE DIAGNÓSTICO POR IMAGEM
            ULTRASSONOGRAFIA DE ABDOME TOTAL
            Fígado com contornos regulares e ecotextura homogênea.
            Vesícula biliar normodistendida, de paredes finas, sem evidência de cálculos.
            Rins tópicos com diferenciação córtico-medular preservada.
            IMPRESSÃO DIAGNÓSTICA:
            Exame ecográfico do abdome total dentro dos limites da normalidade.
        """.trimIndent()

        val category = extractor.detectExamType(ultrasoundText)
        assertEquals(ExamCategoryType.UNSTRUCTURED_DOCUMENT, category)
    }

    @Test
    fun `detecta Tipo B para eletrocardiograma e tomografia computadorizada`() {
        val ecgText = """
            SERVIÇO DE CARDIOLOGIA
            ELETROCARDIOGRAMA DE REPOUSO (12 DERIVAÇÕES)
            Ritmo sinusal regular, FC: 72 bpm. Intervalo PR normal.
            Sem alterações isquêmicas agudas no segmento ST.
            CONCLUSÃO RADIOLÓGICA / CARDIOLÓGICA:
            Traçado dentro dos limites da normalidade para a faixa etária.
        """.trimIndent()

        val category = extractor.detectExamType(ecgText)
        assertEquals(ExamCategoryType.UNSTRUCTURED_DOCUMENT, category)
    }

    @Test
    fun `sanitiza dados pessoais sensiveis expurgando CPF, RG, telefone, CRM e convenio`() {
        val rawDoc = """
            Paciente: João da Silva
            CPF: 123.456.789-00
            RG: 12.345.678-X
            Telefone: (11) 98765-4321
            Convênio: SulAmérica Saúde
            Matrícula: 987654321-00
            Médico Solicitante: Dr. Roberto Santos CRM-SP 123456
            CNPJ: 12.345.678/0001-90
            
            GLICOSE EM JEJUM: 95 mg/dL
            COLESTEROL TOTAL: 180 mg/dL
        """.trimIndent()

        val sanitized = extractor.sanitizeDocumentText(rawDoc)

        // Verifica que os dados sensíveis foram expurgados
        assertFalse("CPF formatado não deve existir", sanitized.contains("123.456.789-00"))
        assertFalse("RG formatado não deve existir", sanitized.contains("12.345.678-X"))
        assertFalse("Telefone não deve existir", sanitized.contains("(11) 98765-4321"))
        assertFalse("Telefone sem máscara não deve existir", sanitized.contains("98765-4321"))
        assertFalse("CRM não deve existir", sanitized.contains("123456"))
        assertFalse("CNPJ não deve existir", sanitized.contains("12.345.678/0001-90"))

        // Verifica que os tokens de proteção estão presentes
        assertTrue("Deve conter token de CPF", sanitized.contains("[CPF_PROTEGIDO_LGPD]"))
        assertTrue("Deve conter token de RG", sanitized.contains("[RG_PROTEGIDO_LGPD]"))
        assertTrue("Deve conter token de Telefone", sanitized.contains("[TELEFONE_PROTEGIDO_LGPD]"))
        assertTrue("Deve conter token de CNPJ", sanitized.contains("[CNPJ_PROTEGIDO_LGPD]"))
        assertTrue("Deve conter token de CRM", sanitized.contains("[CRM_PROTEGIDO_LGPD]"))
        assertTrue("Deve conter token de Convênio", sanitized.contains("[CONVENIO_PROTEGIDO_LGPD]"))

        // Verifica que o conteúdo clínico útil permanece preservado
        assertTrue("Deve preservar o resultado da glicose", sanitized.contains("GLICOSE EM JEJUM: 95 mg/dL"))
        assertTrue("Deve preservar o colesterol", sanitized.contains("COLESTEROL TOTAL: 180 mg/dL"))
    }
}
