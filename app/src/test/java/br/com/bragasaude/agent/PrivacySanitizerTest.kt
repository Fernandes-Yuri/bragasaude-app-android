package br.com.bragasaude.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PrivacySanitizerTest {

    private lateinit var sanitizer: PrivacySanitizer

    @Before
    fun setUp() {
        sanitizer = PrivacySanitizer()
    }

    @Test
    fun `sanitiza CPF formatado e rotulado sem afetar outros numeros`() {
        val input = "Paciente: Carlos Alberto, CPF: 123.456.789-00, outro doc cpf: 98765432100."
        val result = sanitizer.sanitize(input)

        assertFalse(result.contains("123.456.789-00"))
        assertFalse(result.contains("98765432100"))
        assertTrue(result.contains(PrivacySanitizer.REDACTED_CPF))
    }

    @Test
    fun `sanitiza cartao SUS de 15 digitos e rotulado`() {
        val input = "Cartão SUS: 123 4567 8901 2345 e CNS 789123456789012 para atendimento."
        val result = sanitizer.sanitize(input)

        assertFalse(result.contains("123 4567 8901 2345"))
        assertFalse(result.contains("789123456789012"))
        assertTrue(result.contains(PrivacySanitizer.REDACTED_SUS))
    }

    @Test
    fun `sanitiza RG formatado e rotulado`() {
        val input = "RG: 12.345.678-9 e Identidade: 33445566-X emitidos em SP."
        val result = sanitizer.sanitize(input)

        assertFalse(result.contains("12.345.678-9"))
        assertFalse(result.contains("33445566-X"))
        assertTrue(result.contains(PrivacySanitizer.REDACTED_RG))
    }

    @Test
    fun `sanitiza telefones com DDD e celulares`() {
        val input = "Contato: (11) 98765-4321 ou ligue 11 3456-7890 ou WhatsApp: +55 (21) 99999-8888."
        val result = sanitizer.sanitize(input)

        assertFalse(result.contains("98765-4321"))
        assertFalse(result.contains("3456-7890"))
        assertFalse(result.contains("99999-8888"))
        assertTrue(result.contains(PrivacySanitizer.REDACTED_PHONE))
    }

    @Test
    fun `sanitiza CEP e enderecos de carimbo clinico`() {
        val input = "Clínica Saúde Total - Av. Paulista, 1000 - Bela Vista - CEP 01310-100."
        val result = sanitizer.sanitize(input)

        assertFalse(result.contains("01310-100"))
        assertFalse(result.contains("Av. Paulista, 1000"))
        assertTrue(result.contains(PrivacySanitizer.REDACTED_CEP))
        assertTrue(result.contains(PrivacySanitizer.REDACTED_ADDRESS))
    }

    @Test
    fun `sanitiza email do medico ou clinica`() {
        val input = "Dúvidas para dr.joao@clinicasaudetotal.com.br no horário comercial."
        val result = sanitizer.sanitize(input)

        assertFalse(result.contains("dr.joao@clinicasaudetotal.com.br"))
        assertTrue(result.contains(PrivacySanitizer.REDACTED_EMAIL))
    }

    @Test
    fun `preserva integralmente dados clinicos, dosagens e valores laboratoriais`() {
        val clinicalText = """
            Prescrição Médica:
            1. Amoxicilina 500mg - Tomar 1 cápsula de 8/8h por 7 dias.
            2. Dipirona 500 mg/mL - 30 gotas se dor de 6 em 6 horas.
            3. Losartana 50mg - 1 comprimido pela manhã (PA 130/80 mmHg).
            4. Glicemia de jejum: 95 mg/dL (Referência: 70 a 99 mg/dL).
            5. Hemoglobina: 14.2 g/dL.
            6. CID-10: J06.9.
            7. Dr. Carlos Silva - CRM 12345/SP.
        """.trimIndent()

        val result = sanitizer.sanitize(clinicalText)

        // Verifica que termos clínicos continuam 100% íntegros
        assertTrue(result.contains("Amoxicilina 500mg"))
        assertTrue(result.contains("8/8h por 7 dias"))
        assertTrue(result.contains("Dipirona 500 mg/mL"))
        assertTrue(result.contains("30 gotas se dor de 6 em 6 horas"))
        assertTrue(result.contains("Losartana 50mg"))
        assertTrue(result.contains("PA 130/80 mmHg"))
        assertTrue(result.contains("95 mg/dL"))
        assertTrue(result.contains("70 a 99 mg/dL"))
        assertTrue(result.contains("Hemoglobina: 14.2 g/dL"))
        assertTrue(result.contains("CID-10: J06.9"))
        assertTrue(result.contains("CRM 12345/SP"))
    }

    @Test
    fun `lida com texto nulo ou vazio graciosamente`() {
        assertEquals("", sanitizer.sanitize(null))
        assertEquals("", sanitizer.sanitize(""))
        assertEquals("   ", sanitizer.sanitize("   "))
    }
}
