package br.com.bragasaude.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthFormatterTest {
    @Test fun `atividade fisica usa rotulos acolhedores para codigos e dados legados`() {
        mapOf("SEDENTARY" to "Rotina leve / inicial", "Sedentário" to "Rotina leve / inicial",
            "LIGHTLY_ACTIVE" to "Levemente ativo", "MODERATELY_ACTIVE" to "Moderadamente ativo",
            "VERY_ACTIVE" to "Muito ativo", "Muito Ativo" to "Muito ativo").forEach { (value, label) ->
            assertEquals(label, HealthFormatter.formatActivityLevel(value))
        }
        assertEquals("Personalizado", HealthFormatter.formatActivityLevel("Personalizado"))
    }
}
