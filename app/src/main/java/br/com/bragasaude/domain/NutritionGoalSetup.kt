package br.com.bragasaude.domain

/**
 * Objetivo clínico nutricional selecionado pelo usuário.
 */
enum class ClinicalDietaryGoal(
    val title: String,
    val subtitle: String,
    val description: String,
    val calorieAdjustment: Int
) {
    WEIGHT_LOSS(
        title = "Emagrecimento",
        subtitle = "Perda de gordura sustentável",
        description = "Aplica déficit moderado de -400 kcal/dia para redução de gordura preservando massa magra.",
        calorieAdjustment = -400
    ),
    MAINTENANCE(
        title = "Manutenção e Saúde",
        subtitle = "Equilíbrio diário",
        description = "Consumo calórico equilibrado ao seu gasto diário total para estabilidade e vitalidade.",
        calorieAdjustment = 0
    ),
    HYPERTROPHY(
        title = "Ganho de Massa",
        subtitle = "Força e hipertrofia",
        description = "Superávit limpo de +350 kcal/dia para fornecer energia à síntese proteica muscular.",
        calorieAdjustment = 350
    );

    fun toDietaryGoal(): DietaryGoal = when (this) {
        WEIGHT_LOSS -> DietaryGoal.LOSE_WEIGHT
        MAINTENANCE -> DietaryGoal.MAINTAIN
        HYPERTROPHY -> DietaryGoal.GAIN_MUSCLE
    }

    companion object {
        fun fromDietaryGoal(goal: DietaryGoal): ClinicalDietaryGoal = when (goal) {
            DietaryGoal.LOSE_WEIGHT -> WEIGHT_LOSS
            DietaryGoal.MAINTAIN -> MAINTENANCE
            DietaryGoal.GAIN_MUSCLE -> HYPERTROPHY
        }
    }
}

/**
 * Nível de atividade física real ancorado na rotina semanal do usuário.
 */
enum class DailyActivityLevel(
    val title: String,
    val routineDescription: String,
    val factor: Float
) {
    SEDENTARY(
        title = "Sedentário",
        routineDescription = "Trabalho sentado, rotina parada e pouco ou nenhum exercício programado",
        factor = 1.20f
    ),
    LIGHTLY_ACTIVE(
        title = "Levemente ativo",
        routineDescription = "Caminhadas rotineiras ou exercícios leves de 1 a 2 vezes na semana",
        factor = 1.375f
    ),
    MODERATELY_ACTIVE(
        title = "Moderadamente ativo",
        routineDescription = "Treino regular (musculação, corrida ou esportes) de 3 a 5 dias por semana",
        factor = 1.55f
    ),
    VERY_ACTIVE(
        title = "Muito ativo",
        routineDescription = "Treino intenso 6 a 7 dias por semana ou trabalho com esforço físico pesado",
        factor = 1.725f
    );

    companion object {
        fun fromFactor(factor: Float): DailyActivityLevel = when {
            factor >= 1.7f -> VERY_ACTIVE
            factor >= 1.5f -> MODERATELY_ACTIVE
            factor >= 1.35f -> LIGHTLY_ACTIVE
            else -> SEDENTARY
        }
    }
}

/**
 * Estrutura completa de configuração de metas nutricionais e compras do usuário.
 */
data class UserNutritionGoalSetup(
    val goal: ClinicalDietaryGoal = ClinicalDietaryGoal.MAINTENANCE,
    val activityLevel: DailyActivityLevel = DailyActivityLevel.LIGHTLY_ACTIVE,
    val budgetTier: GroceryBudgetTier = GroceryBudgetTier.ULTRA_ECONOMIC,
    val hasPantryStaples: Boolean = true,
    val isCustomManual: Boolean = false,
    val manualKcal: Double? = null,
    val maxWeeklyBudgetReais: Double? = null
)
