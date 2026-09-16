package br.com.bragasaude.domain

import br.com.bragasaude.data.remote.model.RemoteFood
import br.com.bragasaude.data.remote.model.RemoteProfile

object HealthCalculators {

    fun calculateIMC(weight: Float, height: Float): Float {
        if (height <= 0) return 0f
        return weight / (height * height)
    }

    fun calculateAge(birthDateStr: String?): Int {
        if (birthDateStr.isNullOrBlank()) return 30
        return try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val birthDate = sdf.parse(birthDateStr) ?: return 30
            val dob = java.util.Calendar.getInstance().apply { time = birthDate }
            val today = java.util.Calendar.getInstance()
            var age = today.get(java.util.Calendar.YEAR) - dob.get(java.util.Calendar.YEAR)
            if (today.get(java.util.Calendar.DAY_OF_YEAR) < dob.get(java.util.Calendar.DAY_OF_YEAR)) {
                age--
            }
            age.coerceIn(1, 120)
        } catch (_: Exception) {
            30
        }
    }

    data class NutritionTriad(
        val min: Float,
        val avg: Float,
        val max: Float
    )

    data class MealRecommendation(
        val calories: NutritionTriad,
        val carbs: NutritionTriad,
        val protein: NutritionTriad,
        val fat: NutritionTriad,
        val suggestedFoods: List<RemoteFood> = emptyList()
    )

    /**
     * Calcula a recomendação nutricional baseada no perfil e catálogo.
     */
    fun calculateSmartMeal(
        profile: RemoteProfile,
        caloriePercentage: Float,
        catalog: List<RemoteFood>
    ): MealRecommendation {
        val dailyCal = profile.pointsDiscipline.let { 1800f } // Default por enquanto
        val mealAvgCal = dailyCal * caloriePercentage
        
        // Tríade de Escolha (02_ALIMENTACAO_INTELIGENTE.md)
        val mealMinCal = mealAvgCal * 0.85f
        val mealMaxCal = mealAvgCal * 1.15f

        // Filtra o catálogo baseado nas condições do perfil
        val safeFoods = catalog.filter { food ->
            val isDiabetesSafe = !profile.hasDiabetes || food.isDiabetesSafe
            val isHypertensionSafe = !profile.hasHypertension || food.isHypertensionSafe
            val isThyroidSafe = !profile.hasThyroidIssue || food.isThyroidSafe
            
            isDiabetesSafe && isHypertensionSafe && isThyroidSafe
        }

        return MealRecommendation(
            calories = NutritionTriad(mealMinCal, mealAvgCal, mealMaxCal),
            carbs = NutritionTriad(
                (mealMinCal * 0.5f) / 4f,
                (mealAvgCal * 0.5f) / 4f,
                (mealMaxCal * 0.5f) / 4f
            ),
            protein = NutritionTriad(
                (mealMinCal * 0.25f) / 4f,
                (mealAvgCal * 0.25f) / 4f,
                (mealMaxCal * 0.25f) / 4f
            ),
            fat = NutritionTriad(
                (mealMinCal * 0.25f) / 9f,
                (mealAvgCal * 0.25f) / 9f,
                (mealMaxCal * 0.25f) / 9f
            ),
            suggestedFoods = safeFoods.take(3) // Sugere 3 alimentos seguros
        )
    }

    // Função legada para compatibilidade temporária
    fun calculateMealRecommendation(dailyCalories: Float, mealWeight: Float): MealRecommendation {
        val mealAvgCal = dailyCalories * mealWeight
        return MealRecommendation(
            calories = NutritionTriad(mealAvgCal * 0.85f, mealAvgCal, mealAvgCal * 1.15f),
            carbs = NutritionTriad(0f, 0f, 0f),
            protein = NutritionTriad(0f, 0f, 0f),
            fat = NutritionTriad(0f, 0f, 0f)
        )
    }
}
