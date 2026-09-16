package br.com.bragasaude.domain

import br.com.bragasaude.domain.model.GlucoseCategory
import br.com.bragasaude.domain.model.GlucoseContext
import br.com.bragasaude.domain.util.GlucoseClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlucoseClassificationTest {

    @Test
    fun `test fasting glucose 85 mg dL is NORMAL by SBD`() {
        val result = GlucoseClassifier.classify(85, GlucoseContext.FASTING)
        assertEquals(GlucoseCategory.NORMAL, result.category)
        assertTrue(result.isNormal)
    }

    @Test
    fun `test fasting glucose 110 mg dL is PREDIABETES by SBD`() {
        val result = GlucoseClassifier.classify(110, GlucoseContext.FASTING)
        assertEquals(GlucoseCategory.PREDIABETES, result.category)
    }

    @Test
    fun `test fasting glucose 135 mg dL is DIABETES by SBD`() {
        val result = GlucoseClassifier.classify(135, GlucoseContext.FASTING)
        assertEquals(GlucoseCategory.DIABETES, result.category)
    }

    @Test
    fun `test postprandial glucose 130 mg dL is NORMAL by SBD`() {
        val result = GlucoseClassifier.classify(130, GlucoseContext.POST_PRANDIAL)
        assertEquals(GlucoseCategory.NORMAL, result.category)
    }

    @Test
    fun `test postprandial glucose 160 mg dL is PREDIABETES by SBD`() {
        val result = GlucoseClassifier.classify(160, GlucoseContext.POST_PRANDIAL)
        assertEquals(GlucoseCategory.PREDIABETES, result.category)
    }

    @Test
    fun `test glucose 60 mg dL is HYPOGLYCEMIA by SBD`() {
        val result = GlucoseClassifier.classify(60, GlucoseContext.FASTING)
        assertEquals(GlucoseCategory.HYPOGLYCEMIA, result.category)
    }

    @Test
    fun `test glucose 45 mg dL is HYPOGLYCEMIA SEVERE by SBD`() {
        val result = GlucoseClassifier.classify(45, GlucoseContext.FASTING)
        assertEquals(GlucoseCategory.HYPOGLYCEMIA_SEVERE, result.category)
    }
}
