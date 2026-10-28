package br.com.bragasaude.domain

import br.com.bragasaude.domain.model.BloodPressureCategory
import br.com.bragasaude.domain.util.BloodPressureParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BloodPressureClassificationTest {

    @Test
    fun `test 120 over 80 mmHg is classified as PREHYPERTENSION by SBC 2025`() {
        val result = BloodPressureParser.classify(120, 80)
        assertEquals(BloodPressureCategory.PREHYPERTENSION, result.category)
        org.junit.Assert.assertFalse(result.isNormal)
    }

    @Test
    fun `limites e valores assimetricos seguem o componente mais alto`() {
        val cases = listOf(
            Triple(119, 79, BloodPressureCategory.OPTIMAL),
            Triple(120, 79, BloodPressureCategory.PREHYPERTENSION),
            Triple(119, 80, BloodPressureCategory.PREHYPERTENSION),
            Triple(120, 90, BloodPressureCategory.STAGE_1_HYPERTENSION),
            Triple(140, 80, BloodPressureCategory.STAGE_1_HYPERTENSION),
            Triple(140, 100, BloodPressureCategory.STAGE_2_HYPERTENSION),
            Triple(160, 110, BloodPressureCategory.STAGE_3_HYPERTENSION)
        )
        cases.forEach { (sys, dia, expected) -> assertEquals(expected, BloodPressureParser.classify(sys, dia).category) }
    }

    @Test
    fun `test 115 over 75 mmHg is classified as OPTIMAL by SBC`() {
        val result = BloodPressureParser.classify(115, 75)
        assertEquals(BloodPressureCategory.OPTIMAL, result.category)
        assertTrue(result.isNormal)
    }

    @Test
    fun `test 135 over 85 mmHg is classified as PREHYPERTENSION by SBC`() {
        val result = BloodPressureParser.classify(135, 85)
        assertEquals(BloodPressureCategory.PREHYPERTENSION, result.category)
    }

    @Test
    fun `test 145 over 92 mmHg is classified as STAGE 1 HYPERTENSION by SBC`() {
        val result = BloodPressureParser.classify(145, 92)
        assertEquals(BloodPressureCategory.STAGE_1_HYPERTENSION, result.category)
    }

    @Test
    fun `test 165 over 102 mmHg is classified as STAGE 2 HYPERTENSION by SBC`() {
        val result = BloodPressureParser.classify(165, 102)
        assertEquals(BloodPressureCategory.STAGE_2_HYPERTENSION, result.category)
    }

    @Test
    fun `test 185 over 115 mmHg is classified as STAGE 3 HYPERTENSION by SBC`() {
        val result = BloodPressureParser.classify(185, 115)
        assertEquals(BloodPressureCategory.STAGE_3_HYPERTENSION, result.category)
    }

    @Test
    fun `test 85 over 55 mmHg is classified as HYPOTENSION`() {
        val result = BloodPressureParser.classify(85, 55)
        assertEquals(BloodPressureCategory.HYPOTENSION, result.category)
    }
}
