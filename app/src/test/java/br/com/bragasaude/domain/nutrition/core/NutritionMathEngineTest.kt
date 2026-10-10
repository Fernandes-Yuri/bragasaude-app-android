package br.com.bragasaude.domain.nutrition.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionMathEngineTest {

    @Test
    fun `calculateBmr should match clinical formula`() {
        // Homem: 80kg, 178cm, 30 anos -> 10*80 + 6.25*178 - 5*30 + 5 = 1767.5
        val bmrMale = NutritionMathEngine.calculateBmr(80f, 178f, 30, true)
        assertEquals(1767.5f, bmrMale, 0.01f)

        // Mulher: 60kg, 165cm, 30 anos -> 10*60 + 6.25*165 - 5*30 - 161 = 1320.25
        val bmrFemale = NutritionMathEngine.calculateBmr(60f, 165f, 30, false)
        assertEquals(1320.25f, bmrFemale, 0.01f)
    }

    @Test
    fun `calculateBalancedMacros should conserve energy across various targets and weights`() {
        val targets = listOf(1200f, 1500f, 1800f, 2200f, 2800f, 3500f)
        val weights = listOf(50f, 70f, 90f, 120f)
        val goals = listOf(CanonicalDietaryGoal.LOSE_WEIGHT, CanonicalDietaryGoal.MAINTAIN, CanonicalDietaryGoal.GAIN_MUSCLE)

        for (target in targets) {
            for (weight in weights) {
                for (goal in goals) {
                    val macros = NutritionMathEngine.calculateBalancedMacros(target, weight, goal, hasDiabetes = false)
                    assertTrue("Energy must be conserved for target=$target, weight=$weight, goal=$goal", macros.isEnergyConserved)
                    assertEquals(target, macros.totalCalculatedKcal, 1.0f)
                }
            }
        }
    }

    @Test
    fun `calculateBalancedMacros for diabetes should balance carbs and maintain 100 percent calories`() {
        val target = 2000f
        val weight = 80f
        val macros = NutritionMathEngine.calculateBalancedMacros(target, weight, CanonicalDietaryGoal.MAINTAIN, hasDiabetes = true)

        assertTrue(macros.isEnergyConserved)
        assertEquals(target, macros.totalCalculatedKcal, 1.0f)
        // Carboidratos devem ser ~40% do total ou menos
        assertTrue(macros.carbsKcal <= target * 0.45f)
    }

    @Test
    fun `AllergenFamily should catch singular, plural and derivative forms solving P11`() {
        // Alergia a Ovo detecta "Ovos", "Omelete", "Clara"
        val eggAllergen = AllergenFamily.EGG
        assertTrue(AllergenFamily.matchesFood("Ovos Mexidos", null, emptyList(), eggAllergen))
        assertTrue(AllergenFamily.matchesFood("Ovo Cozido", null, emptyList(), eggAllergen))
        assertTrue(AllergenFamily.matchesFood("Omelete de Legumes", null, emptyList(), eggAllergen))
        assertFalse(AllergenFamily.matchesFood("Arroz com Feijao", null, emptyList(), eggAllergen))

        // Alergia a Lactose/Leite detecta "Queijo", "Iogurte", "Leite", "Requeijão"
        val milkAllergen = AllergenFamily.MILK_LACTOSE
        assertTrue(AllergenFamily.matchesFood("Queijo Minas Frescal", null, emptyList(), milkAllergen))
        assertTrue(AllergenFamily.matchesFood("Leite Desnatado", null, emptyList(), milkAllergen))
        assertTrue(AllergenFamily.matchesFood("Iogurte Natural", null, emptyList(), milkAllergen))
        assertFalse(AllergenFamily.matchesFood("Suco de Laranja", null, emptyList(), milkAllergen))

        // Alergia a Glúten detecta "Pão Francês", "Farinha de Trigo", "Macarrão"
        val glutenAllergen = AllergenFamily.GLUTEN
        assertTrue(AllergenFamily.matchesFood("Pao Frances", null, emptyList(), glutenAllergen))
        assertTrue(AllergenFamily.matchesFood("Torrada de Trigo", null, emptyList(), glutenAllergen))
        assertFalse(AllergenFamily.matchesFood("Batata Doce", null, emptyList(), glutenAllergen))
    }

    @Test
    fun `AllergenFamily parseDeclaredAllergens should extract families from user input`() {
        val userDeclared = listOf("alergia a ovos", "intolerancia a lactose")
        val families = AllergenFamily.parseDeclaredAllergens(userDeclared)

        assertTrue(families.contains(AllergenFamily.EGG))
        assertTrue(families.contains(AllergenFamily.MILK_LACTOSE))
        assertFalse(families.contains(AllergenFamily.FISH))
    }
}
