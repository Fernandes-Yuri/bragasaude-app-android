package br.com.bragasaude.ui.care

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationDeletionFeedbackTest {

    @Test
    fun `feedback de cuidador menciona o paciente assistido`() {
        val message = medicationDeletionFeedback(
            patientName = "Yuri Fernandes",
            isOtherPatient = true,
            synced = true
        )

        assertEquals("Medicamento excluído do plano de Yuri Fernandes.", message)
    }

    @Test
    fun `feedback do proprio paciente usa primeira pessoa`() {
        val message = medicationDeletionFeedback(
            patientName = "Maria",
            isOtherPatient = false,
            synced = true
        )

        assertEquals("Medicamento excluído com sucesso.", message)
    }

    @Test
    fun `feedback offline preserva contexto e informa pendencia de sincronizacao`() {
        val message = medicationDeletionFeedback(
            patientName = "Yuri Fernandes",
            isOtherPatient = true,
            synced = false
        )

        assertTrue(message.startsWith("Medicamento excluído do plano de Yuri Fernandes."))
        assertTrue(message.contains("sincronização com a nuvem ficará pendente"))
    }
}
