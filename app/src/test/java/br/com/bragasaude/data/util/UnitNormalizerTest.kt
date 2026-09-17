package br.com.bragasaude.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitNormalizerTest {

    @Test
    fun `converte vitamina D de nmol_L para ng_mL dividindo por 2,496`() {
        // Exemplo: 74.88 nmol/L / 2.496 = 30.0 ng/mL
        val result = UnitNormalizer.normalize("vitamin_d", 74.88, "nmol/L")
        assertEquals(30.0, result.normalizedValue, 0.01)
        assertEquals("ng/mL", result.canonicalUnit)
        assertTrue(result.wasConverted)

        // Se já for ng/mL, mantém inalterado
        val alreadyCanonical = UnitNormalizer.normalize("vitamin_d", 32.5, "ng/mL")
        assertEquals(32.5, alreadyCanonical.normalizedValue, 0.001)
        assertEquals("ng/mL", alreadyCanonical.canonicalUnit)
        assertFalse(alreadyCanonical.wasConverted)
    }

    @Test
    fun `converte glicose de mmol_L para mg_dL multiplicando por 18,018`() {
        // Exemplo: 5.5 mmol/L * 18.018 = 99.1 mg/dL
        val result = UnitNormalizer.normalize("glucose", 5.5, "mmol/L")
        assertEquals(99.1, result.normalizedValue, 0.05)
        assertEquals("mg/dL", result.canonicalUnit)
        assertTrue(result.wasConverted)

        // Se já for mg/dL
        val alreadyCanonical = UnitNormalizer.normalize("glucose", 95.0, "mg/dL")
        assertEquals(95.0, alreadyCanonical.normalizedValue, 0.001)
        assertEquals("mg/dL", alreadyCanonical.canonicalUnit)
        assertFalse(alreadyCanonical.wasConverted)
    }

    @Test
    fun `converte perfil lipidico de mmol_L para mg_dL multiplicando por 38,67`() {
        // Colesterol Total: 5.0 mmol/L * 38.67 = 193.35 mg/dL
        val totalChol = UnitNormalizer.normalize("total_cholesterol", 5.0, "mmol/L")
        assertEquals(193.35, totalChol.normalizedValue, 0.05)
        assertEquals("mg/dL", totalChol.canonicalUnit)
        assertTrue(totalChol.wasConverted)

        // HDL
        val hdl = UnitNormalizer.normalize("hdl", 1.2, "mmol/L")
        assertEquals(46.4, hdl.normalizedValue, 0.05)
        assertEquals("mg/dL", hdl.canonicalUnit)
        assertTrue(hdl.wasConverted)

        // LDL
        val ldl = UnitNormalizer.normalize("ldl", 3.0, "mmol/L")
        assertEquals(116.01, ldl.normalizedValue, 0.05)
        assertEquals("mg/dL", ldl.canonicalUnit)
        assertTrue(ldl.wasConverted)

        // Triglicerídeos
        val trig = UnitNormalizer.normalize("triglycerides", 1.7, "mmol/L")
        assertEquals(65.74, trig.normalizedValue, 0.05)
        assertEquals("mg/dL", trig.canonicalUnit)
        assertTrue(trig.wasConverted)
    }

    @Test
    fun `converte creatinina de umol_L para mg_dL dividindo por 88,4`() {
        // 88.4 µmol/L / 88.4 = 1.0 mg/dL
        val result = UnitNormalizer.normalize("creatinine", 88.4, "µmol/L")
        assertEquals(1.0, result.normalizedValue, 0.01)
        assertEquals("mg/dL", result.canonicalUnit)
        assertTrue(result.wasConverted)

        // Variação em ASCII "umol/l"
        val asciiResult = UnitNormalizer.normalize("creatinine", 106.08, "umol/l")
        assertEquals(1.2, asciiResult.normalizedValue, 0.01)
        assertEquals("mg/dL", asciiResult.canonicalUnit)
        assertTrue(asciiResult.wasConverted)
    }

    @Test
    fun `mantem inalterado parametro desconhecido ou sem regra de conversao`() {
        val result = UnitNormalizer.normalize("unknown_marker", 42.0, "UI/L")
        assertEquals(42.0, result.normalizedValue, 0.001)
        assertEquals("UI/L", result.canonicalUnit)
        assertFalse(result.wasConverted)
    }
}
