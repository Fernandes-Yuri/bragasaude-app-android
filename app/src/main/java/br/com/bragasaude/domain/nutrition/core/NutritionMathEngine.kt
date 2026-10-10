package br.com.bragasaude.domain.nutrition.core

/**
 * Motor matemático determinístico para cálculos de gasto energético e macronutrientes.
 * Garante a Invariante de Conservação da Energia:
 * Kcal Total == (Proteína * 4) + (Gordura * 9) + (Carboidrato * 4) (± 1 kcal).
 */
object NutritionMathEngine {

    /**
     * Taxa Metabólica Basal (TMB) — Equação de Mifflin-St Jeor
     * Homens: TMB = (10 * peso_kg) + (6.25 * altura_cm) - (5 * idade) + 5
     * Mulheres: TMB = (10 * peso_kg) + (6.25 * altura_cm) - (5 * idade) - 161
     */
    fun calculateBmr(
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
     * Gasto Energético Total (GET / TDEE)
     * GET = TMB * FAF
     */
    fun calculateTdee(bmr: Float, activityLevel: CanonicalActivityLevel): Float {
        return bmr * activityLevel.factor.coerceIn(1.0f, 2.5f)
    }

    /**
     * Meta Calórica Diária ajustada ao objetivo clínico
     */
    fun calculateTargetCalories(
        tdee: Float,
        bmr: Float,
        goal: CanonicalDietaryGoal
    ): Float {
        return when (goal) {
            CanonicalDietaryGoal.LOSE_WEIGHT -> {
                val target = tdee + goal.calorieAdjustment // -400 kcal
                // Piso clínico de segurança: não prescrever abaixo de 90% da TMB ou 1200 kcal
                target.coerceAtLeast(bmr * 0.9f).coerceAtLeast(1200f)
            }
            CanonicalDietaryGoal.MAINTAIN -> tdee
            CanonicalDietaryGoal.GAIN_MUSCLE -> tdee + goal.calorieAdjustment // +350 kcal
        }
    }

    /**
     * Distribuição matematicamente conservada de macronutrientes.
     * Resolve P04 e P06 garantindo que a soma dos macronutrientes fecha 100% da meta calórica.
     */
    fun calculateBalancedMacros(
        targetKcal: Float,
        weightKg: Float,
        goal: CanonicalDietaryGoal,
        hasDiabetes: Boolean = false
    ): BalancedMacroSplit {
        val safeTargetKcal = targetKcal.coerceAtLeast(500f)
        val safeWeight = weightKg.coerceIn(20f, 250f)

        // 1. Meta inicial de proteína
        val initialProteinG = (safeWeight * goal.targetProteinPerKg).coerceIn(45f, 260f)
        var proteinKcal = initialProteinG * 4f

        // 2. Meta inicial de gordura (25% do total para geral, 30% para perfil com diabetes)
        val targetFatPercent = if (hasDiabetes) 0.30f else 0.25f
        val minFatByWeightKcal = safeWeight * 0.75f * 9f
        var fatKcal = maxOf(safeTargetKcal * targetFatPercent, minFatByWeightKcal)

        // 3. Verificação de viabilidade energética
        val nonCarbKcal = proteinKcal + fatKcal
        val remainingForCarbs = safeTargetKcal - nonCarbKcal

        val finalProteinKcal: Float
        val finalFatKcal: Float
        val finalCarbsKcal: Float

        if (remainingForCarbs >= safeTargetKcal * 0.10f) {
            // Caso padrão viável: saldo restante vai para carboidratos
            finalProteinKcal = proteinKcal
            finalFatKcal = fatKcal
            finalCarbsKcal = remainingForCarbs
        } else if (remainingForCarbs >= 0f) {
            // Saldo baixo de carboidratos, mas positivo
            finalProteinKcal = proteinKcal
            finalFatKcal = fatKcal
            finalCarbsKcal = remainingForCarbs
        } else {
            // Proteína + Gordura excedem a meta calórica total! (Caso P04)
            // Redistribuição proporcional preservando o mínimo fisiológico:
            val totalWanted = proteinKcal + fatKcal
            val pRatio = proteinKcal / totalWanted
            val fRatio = fatKcal / totalWanted

            finalProteinKcal = safeTargetKcal * pRatio
            finalFatKcal = safeTargetKcal * fRatio
            finalCarbsKcal = 0f
        }

        val proteinG = finalProteinKcal / 4f
        val fatG = finalFatKcal / 9f
        val carbsG = finalCarbsKcal / 4f

        return BalancedMacroSplit(
            proteinG = proteinG,
            proteinKcal = finalProteinKcal,
            fatG = fatG,
            fatKcal = finalFatKcal,
            carbsG = carbsG,
            carbsKcal = finalCarbsKcal,
            targetKcal = safeTargetKcal
        )
    }
}
