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
    fun `calculateBMR should calculate Mifflin-St Jeor accurately for men`() {
        // Exemplo clínico canônico: Homem, 30 anos, 80kg, 178cm
        // TMB = (10 * 80) + (6.25 * 178) - (5 * 30) + 5
        // TMB = 800 + 1112.5 - 150 + 5 = 1767.5 kcal
        val bmr = HealthCalculators.calculateBMR(weightKg = 80f, heightCm = 178f, ageYears = 30, isMale = true)
        assertEquals(1767.5f, bmr, 0.05f)
    }

    @Test
    fun `calculateBMR should calculate Mifflin-St Jeor accurately for women`() {
        // Mulher, 30 anos, 60kg, 165cm
        // TMB = (10 * 60) + (6.25 * 165) - (5 * 30) - 161
        // TMB = 600 + 1031.25 - 150 - 161 = 1320.25 kcal
        val bmr = HealthCalculators.calculateBMR(weightKg = 60f, heightCm = 165f, ageYears = 30, isMale = false)
        assertEquals(1320.25f, bmr, 0.05f)
    }

    @Test
    fun `calculateTDEE should multiply BMR by physical activity factor`() {
        val bmr = 1767.5f
        val fafModeratelyActive = HealthCalculators.parseActivityFactor("Moderadamente Ativo")
        assertEquals(1.55f, fafModeratelyActive, 0.001f)

        val tdee = HealthCalculators.calculateTDEE(bmr, fafModeratelyActive)
        assertEquals(2739.625f, tdee, 0.1f)
    }

    @Test
    fun `calculateTargetCalories should adjust for deficit maintenance and surplus`() {
        val bmr = 1767.5f
        val tdee = 2740f

        val deficit = HealthCalculators.calculateTargetCalories(tdee, bmr, DietaryGoal.LOSE_WEIGHT)
        assertEquals(2340f, deficit, 0.01f) // 2740 - 400

        val maintain = HealthCalculators.calculateTargetCalories(tdee, bmr, DietaryGoal.MAINTAIN)
        assertEquals(2740f, maintain, 0.01f)

        val surplus = HealthCalculators.calculateTargetCalories(tdee, bmr, DietaryGoal.GAIN_MUSCLE)
        assertEquals(3090f, surplus, 0.01f) // 2740 + 350
    }

    @Test
    fun `calculateMacroDistribution should balance protein fat and carbs`() {
        val targetKcal = 2240f
        val weightKg = 80f
        val macros = HealthCalculators.calculateMacroDistribution(targetKcal, weightKg, DietaryGoal.LOSE_WEIGHT)

        // Proteína: 2.0g/kg para déficit -> 160g * 4 = 640 kcal
        assertEquals(160f, macros.proteinG, 0.1f)
        assertEquals(640f, macros.proteinKcal, 0.1f)

        // Gordura: 25% de 2240 = 560 kcal -> ~62.2g
        assertEquals(560f, macros.fatKcal, 0.1f)
        assertEquals(62.22f, macros.fatG, 0.5f)

        // Carboidratos: 2240 - 640 - 560 = 1040 kcal -> 260g
        assertEquals(1040f, macros.carbsKcal, 0.1f)
        assertEquals(260f, macros.carbsG, 0.5f)
    }

    @Test
    fun `calculateProfileCalorieTarget should use Mifflin-St Jeor when profile has metrics`() {
        val profile = RemoteProfile(
            id = "user_yuri",
            weight = 80.0,
            height = 178.0,
            birthDate = "1996-01-01",
            gender = "Masculino",
            activityLevel = "Moderadamente Ativo",
            weightGoal = 75.0 // Peso menor que o atual -> LOSE_WEIGHT (déficit de 400 kcal)
        )
        val target = HealthCalculators.calculateProfileCalorieTarget(profile)
        // Idade em 2026: 30 anos
        // TMB = 1767.5, GET = 1767.5 * 1.55 = 2739.6, Meta = 2739.6 - 400 = 2339.6 kcal
        assertTrue("Meta calculada deve estar próxima de 2340 kcal", target in 2330f..2350f)
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
