package br.com.bragasaude.ui.vitals

import br.com.bragasaude.domain.VoiceHealthIntent
import org.junit.Assert.*
import org.junit.Test

class VitalVoiceInputTest {
    @Test fun `ditado preenche somente a metrica aberta`() {
        val pressure = VitalVoiceInput.parse("Minha pressão deu 12 por 8", false) as VoiceHealthIntent.BloodPressure
        assertEquals(120, pressure.systolic)
        assertEquals(80, pressure.diastolic)
        assertEquals(105, (VitalVoiceInput.parse("105", true) as VoiceHealthIntent.Glucose).glucoseMgDl)
        assertNull(VitalVoiceInput.parse("glicemia 105", false))
        assertNull(VitalVoiceInput.parse("12 por 8", true))
    }

    @Test fun `perguntas negacoes e recursos removidos nao preenchem campos`() {
        for (text in listOf("Minha pressão foi 12 por 8?", "Não foi 12 por 8", "Dieta para pressão 12 por 8",
            "Registrar pressão 12 por 8 pelo WhatsApp", "Estou com dor no peito", "Bebi dois copos de água")) {
            assertNull(text, VitalVoiceInput.parse(text, false))
        }
    }
}
