package br.com.bragasaude.domain

import br.com.bragasaude.data.remote.model.RemoteFood
import br.com.bragasaude.data.remote.model.RemoteProfile
import java.util.Locale

enum class DietaryGoal {
    LOSE_WEIGHT,
    MAINTAIN,
    GAIN_MUSCLE
}

data class MacroSplit(
    val proteinG: Float,
    val proteinKcal: Float,
    val fatG: Float,
    val fatKcal: Float,
    val carbsG: Float,
    val carbsKcal: Float
)

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

    /**
     * 1. Taxa Metabólica Basal (TMB) — Equação de Mifflin-St Jeor
     * Homens: TMB = (10 * peso_kg) + (6.25 * altura_cm) - (5 * idade) + 5
     * Mulheres: TMB = (10 * peso_kg) + (6.25 * altura_cm) - (5 * idade) - 161
     */
    fun calculateBMR(
        weightKg: Float,
        heightCm: Float,
        ageYears: Int,
        isMale: Boolean
    ): Float {
        val safeWeight = weightKg.coerceIn(20f, 350f)
        val safeHeight = heightCm.coerceIn(50f, 250f)
        val safeAge = ageYears.coerceIn(10, 120)

        val base = (10f * safeWeight) + (6.25f * safeHeight) - (5f * safeAge)
        return if (isMale) {
            base + 5f
        } else {
            base - 161f
        }.coerceAtLeast(800f)
    }

    /**
     * 2. Fator de Atividade Física (FAF)
     * Sedentário: 1.20
     * Levemente ativo: 1.375
     * Moderadamente ativo: 1.55
     * Muito ativo: 1.725
     * Extremamente ativo: 1.90
     */
    fun parseActivityFactor(activityLevel: String?): Float {
        val normalized = activityLevel.orEmpty().lowercase(Locale.ROOT).trim()
        return when {
            normalized.contains("extrem") || normalized.contains("atleta") -> 1.90f
            normalized.contains("muito") || normalized.contains("intenso") -> 1.725f
            normalized.contains("modera") -> 1.55f
            normalized.contains("leve") -> 1.375f
            normalized.contains("sedent") -> 1.20f
            else -> 1.20f
        }
    }

    /**
     * Gasto Energético Total (GET)
     * GET = TMB * FAF
     */
    fun calculateTDEE(bmr: Float, activityFactor: Float): Float {
        return bmr * activityFactor.coerceIn(1.0f, 2.5f)
    }

    /**
     * Determina o objetivo com base em entrada explícita ou comparação peso vs meta de peso.
     */
    fun resolveDietaryGoal(
        weight: Double?,
        weightGoal: Double?,
        explicitGoalStr: String? = null
    ): DietaryGoal {
        val exp = explicitGoalStr.orEmpty().lowercase(Locale.ROOT)
        if (exp.contains("emagrec") || exp.contains("perda") || exp.contains("deficit")) return DietaryGoal.LOSE_WEIGHT
        if (exp.contains("hipertrofia") || exp.contains("ganho") || exp.contains("massa")) return DietaryGoal.GAIN_MUSCLE
        if (exp.contains("manut")) return DietaryGoal.MAINTAIN

        if (weight != null && weightGoal != null && weight > 0 && weightGoal > 0) {
            val diff = weightGoal - weight
            if (diff <= -1.0) return DietaryGoal.LOSE_WEIGHT
            if (diff >= 1.0) return DietaryGoal.GAIN_MUSCLE
        }
        return DietaryGoal.MAINTAIN
    }

    /**
     * 3. Ajuste Conforme o Objetivo
     * - Manutenção: GET
     * - Emagrecimento (Déficit Calórico): GET - 400 kcal (ou ~15-20%), respeitando piso seguro da TMB
     * - Hipertrofia (Superávit Calórico): GET + 350 kcal (~10-15%)
     */
    fun calculateTargetCalories(
        tdee: Float,
        bmr: Float,
        goal: DietaryGoal
    ): Float {
        return when (goal) {
            DietaryGoal.LOSE_WEIGHT -> {
                val deficitKcal = 400f
                val target = tdee - deficitKcal
                // Segurança clínica: não prescrever abaixo de 90% da TMB ou abaixo de 1200 kcal
                target.coerceAtLeast(bmr * 0.9f).coerceAtLeast(1200f)
            }
            DietaryGoal.MAINTAIN -> tdee
            DietaryGoal.GAIN_MUSCLE -> tdee + 350f
        }
    }

    /**
     * 4. Distribuição de Macronutrientes
     * - Proteínas (4 kcal/g): 1.6 a 2.0 g/kg (essencial para preservação muscular em déficit)
     * - Gorduras (9 kcal/g): ~25% do total ou min 0.8 g/kg
     * - Carboidratos (4 kcal/g): restante do saldo
     */
    fun calculateMacroDistribution(
        targetKcal: Float,
        weightKg: Float,
        goal: DietaryGoal
    ): MacroSplit {
        val safeWeight = weightKg.coerceIn(20f, 250f)
        val proteinPerKg = when (goal) {
            DietaryGoal.LOSE_WEIGHT -> 2.0f
            DietaryGoal.GAIN_MUSCLE -> 1.8f
            DietaryGoal.MAINTAIN -> 1.6f
        }
        val proteinG = (safeWeight * proteinPerKg).coerceIn(45f, 260f)
        val proteinKcal = proteinG * 4f

        val fatKcal = (targetKcal * 0.25f).coerceAtLeast(safeWeight * 0.75f * 9f)
        val fatG = fatKcal / 9f

        val carbsKcal = (targetKcal - proteinKcal - fatKcal).coerceAtLeast(0f)
        val carbsG = carbsKcal / 4f

        return MacroSplit(
            proteinG = proteinG,
            proteinKcal = proteinKcal,
            fatG = fatG,
            fatKcal = fatKcal,
            carbsG = carbsG,
            carbsKcal = carbsKcal
        )
    }

    /**
     * Calcula a Meta Calórica Diária recomendada a partir dos dados do perfil do usuário.
     * Utiliza Mifflin-St Jeor + FAF + Ajuste por Objetivo.
     */
    fun calculateProfileCalorieTarget(
        weight: Double?,
        height: Double?,
        birthDate: String?,
        gender: String?,
        activityLevel: String?,
        weightGoal: Double? = null,
        dietaryGoalStr: String? = null
    ): Float {
        // Se não tiver peso informado, fallback seguro de 1800 kcal
        if (weight == null || weight <= 0.0) return 1800f

        val isMale = when (gender?.lowercase(Locale.ROOT)?.trim()) {
            "feminino", "mulher", "female" -> false
            else -> true
        }
        val weightKg = weight.toFloat()

        // Altura: se for informada em metros (<= 2.5), converter para cm; se nula, inferir média nacional
        val rawHeight = height?.toFloat()
        val heightCm = when {
            rawHeight != null && rawHeight in 50f..250f -> rawHeight
            rawHeight != null && rawHeight in 0.5f..2.5f -> rawHeight * 100f
            isMale -> 175f
            else -> 162f
        }

        val age = calculateAge(birthDate)
        val bmr = calculateBMR(weightKg, heightCm, age, isMale)
        val faf = parseActivityFactor(activityLevel)
        val tdee = calculateTDEE(bmr, faf)
        val goal = resolveDietaryGoal(weight, weightGoal, dietaryGoalStr)

        return calculateTargetCalories(tdee, bmr, goal)
    }

    fun calculateProfileCalorieTarget(profile: RemoteProfile?): Float {
        if (profile == null) return 1800f
        return (profile.dailyCalorieTarget?.takeIf { it > 500.0 && it != 1800.0 }?.toFloat())
            ?: calculateProfileCalorieTarget(
                weight = profile.weight,
                height = profile.height,
                birthDate = profile.birthDate,
                gender = profile.gender,
                activityLevel = profile.activityLevel,
                weightGoal = profile.weightGoal
            )
    }

    /**
     * Calcula as calorias com base no questionario de metas e rotina do usuario.
     */
    fun calculateGoalSetupKcal(
        weight: Double?,
        height: Double?,
        birthDate: String?,
        gender: String?,
        setup: UserNutritionGoalSetup
    ): Float {
        if (setup.isCustomManual && setup.manualKcal != null && setup.manualKcal > 500.0) {
            return setup.manualKcal.toFloat()
        }

        val weightKg = (weight?.toFloat() ?: 70f).coerceIn(30f, 300f)
        val isMale = gender?.equals("FEMININO", ignoreCase = true) != true
        val rawHeight = height?.toFloat()
        val heightCm = when {
            rawHeight != null && rawHeight in 50f..250f -> rawHeight
            rawHeight != null && rawHeight in 0.5f..2.5f -> rawHeight * 100f
            isMale -> 175f
            else -> 162f
        }

        val age = calculateAge(birthDate)
        val bmr = calculateBMR(weightKg, heightCm, age, isMale)
        val tdee = calculateTDEE(bmr, setup.activityLevel.factor)
        return calculateTargetCalories(tdee, bmr, setup.goal.toDietaryGoal())
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
        val dailyCal = calculateProfileCalorieTarget(profile)
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
            suggestedFoods = safeFoods.take(3)
        )
    }

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
