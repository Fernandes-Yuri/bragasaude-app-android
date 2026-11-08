package br.com.bragasaude.ui.care

import org.junit.Assert.assertEquals
import org.junit.Test

class CarePatientAccessLabelTest {
    private fun patient(write: Boolean, take: Boolean, role: String = "CAREGIVER_VIEWER") =
        CarePatientOption("patient", "Maria", write, take, false, false, role)

    @Test fun selfIsIdentifiedAsMyHealth() {
        assertEquals("Minha saúde", carePatientAccessLabel(patient(true, true, "PATIENT")))
    }

    @Test fun labelUsesEffectivePermissionsInsteadOfRoleName() {
        assertEquals("Pode visualizar", carePatientAccessLabel(patient(false, false, "ADMIN_CHILD")))
        assertEquals("Pode confirmar doses", carePatientAccessLabel(patient(false, true)))
        assertEquals("Pode gerenciar medicamentos", carePatientAccessLabel(patient(true, false)))
        assertEquals("Pode gerenciar medicamentos e confirmar doses", carePatientAccessLabel(patient(true, true)))
    }
}
