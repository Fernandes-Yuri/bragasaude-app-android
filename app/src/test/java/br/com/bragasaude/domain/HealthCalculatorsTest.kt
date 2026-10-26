package br.com.bragasaude.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthCalculatorsTest {

    @Test
    fun `calculateIMC should return correct value`() {
        val weight = 70f
        val height = 1.70f
        val expected = weight / (height * height)
        val result = HealthCalculators.calculateIMC(weight, height)
        assertEquals(expected, result, 0.01f)
    }

    @Test
    fun `calculateIMC with zero height should return zero`() {
        val result = HealthCalculators.calculateIMC(70f, 0f)
        assertEquals(0f, result)
    }

}
