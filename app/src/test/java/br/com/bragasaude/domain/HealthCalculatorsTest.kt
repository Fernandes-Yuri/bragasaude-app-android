package br.com.bragasaude.domain

import br.com.bragasaude.data.remote.model.RemoteFood
import br.com.bragasaude.data.remote.model.RemoteProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

    @Test
    fun `calculateSmartMeal should respect diabetes restrictions`() {
        val profile = RemoteProfile(id = "1", hasDiabetes = true)
        val catalog = listOf(
            RemoteFood(name = "Sugar", isDiabetesSafe = false),
            RemoteFood(name = "Lettuce", isDiabetesSafe = true)
        )
        
        val recommendation = HealthCalculators.calculateSmartMeal(profile, 0.25f, catalog)
        
        assertTrue(recommendation.suggestedFoods.any { it.name == "Lettuce" })
        assertTrue(recommendation.suggestedFoods.none { it.name == "Sugar" })
    }

    @Test
    fun `calculateSmartMeal should calculate calories correctly`() {
        val profile = RemoteProfile(id = "1")
        val catalog = emptyList<RemoteFood>()
        val caloriePercentage = 0.20f // 20%
        
        val recommendation = HealthCalculators.calculateSmartMeal(profile, caloriePercentage, catalog)
        
        val expectedAvg = 1800f * 0.20f
        assertEquals(expectedAvg, recommendation.calories.avg, 0.01f)
        assertEquals(expectedAvg * 0.85f, recommendation.calories.min, 0.01f)
        assertEquals(expectedAvg * 1.15f, recommendation.calories.max, 0.01f)
    }
}
