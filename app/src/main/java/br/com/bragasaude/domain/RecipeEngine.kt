package br.com.bragasaude.domain

import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.model.HealthyRecipe
import br.com.bragasaude.data.local.model.RecipeCatalog
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultado do cruzamento de uma receita com a despensa do usuário.
 *
 * @param recipe A receita recomendada.
 * @param availableIngredients Ingredientes que o usuário tem na despensa.
 * @param missingIngredients Ingredientes que faltam.
 * @param hasAll True se o usuário tem TODOS os ingredientes.
 * @param missingCount Número de ingredientes faltando.
 */
data class RecipePantryMatch(
    val recipe: HealthyRecipe,
    val availableIngredients: List<String>,
    val missingIngredients: List<String>,
    val hasAll: Boolean,
    val missingCount: Int
)

/**
 * Motor de Busca e Cruzamento de Receitas com a Despensa.
 *
 * Cruza a lista de alimentos da despensa do usuário com o catálogo de 20 receitas,
 * aplicando filtros clínicos baseados no perfil (diabetes, hipertensão, alergias).
 *
 * **Regra de ouro (D3/D4 / DECISOES.md):** As receitas são sugestões de autocuidado
 * nutricional, com ingredientes naturais e preparo simples. Nenhuma alegação diagnóstica
 * ou prescritiva é feita.
 */
@Singleton
class RecipeEngine @Inject constructor() {

    /**
     * Encontra as melhores receitas para cozinhar com o que o usuário tem em casa.
     *
     * @param pantryItems Itens marcados como presentes na despensa (isCheckedInPantry = true).
     * @param profile Perfil do usuário com condições clínicas e alergias.
     * @param mealType Tipo de refeição desejado (BREAKFAST, LUNCH, SNACK, DINNER) ou vazio para todas.
     * @return Lista de receitas ordenadas por maior número de ingredientes disponíveis.
     */
    fun findBestRecipes(
        pantryItems: List<GroceryListItemEntity>,
        profile: ProfileEntity?,
        mealType: String? = null
    ): List<RecipePantryMatch> {
        // Extrai nomes de alimentos da despensa (normalizados para comparação case-insensitive)
        val pantryNames = pantryItems
            .map { normalizeFoodName(it.foodName) }
            .toSet()

        // Filtra receitas pelo tipo de refeição (se especificado)
        val candidates = if (mealType.isNullOrBlank()) {
            RecipeCatalog.getAll()
        } else {
            RecipeCatalog.getByMealType(mealType)
        }

        // Aplica filtro clínico zero-risco
        val clinicallySafe = candidates.filter { recipe ->
            isClinicallySafe(recipe, profile)
        }

        // Cruza com a despensa e calcula score
        val matches = clinicallySafe.map { recipe ->
            calculateMatch(recipe, pantryNames)
        }

        // Filtra apenas receitas com pelo menos 1 ingrediente disponível
        return matches
            .filter { it.availableIngredients.isNotEmpty() }
            .sortedByDescending { it.availableIngredients.size }
    }

    /**
     * Verifica se uma receita é segura para o perfil clínico do usuário.
     *
     * Regras de segurança (baseadas nos perfis de FoodEntity):
     * - Diabetes: exclui receitas marcadas como isDiabetesSafe = false
     * - Hipertensão: exclui receitas marcadas como isHypertensionSafe = false
     * - Alergias: exclui receitas que contenham ingredientes listados em foodAllergies
     */
    private fun isClinicallySafe(recipe: HealthyRecipe, profile: ProfileEntity?): Boolean {
        if (profile == null) return true

        // Filtro por diabetes
        if (profile.hasDiabetes && !recipe.isDiabetesSafe) return false

        // Filtro por hipertensão
        if (profile.hasHypertension && !recipe.isHypertensionSafe) return false

        // Filtro por alergias alimentares
        val allergies = profile.foodAllergies
        if (allergies.isNotEmpty()) {
            val normalizedAllergies = allergies.map { normalizeFoodName(it) }.toSet()
            val recipeIngredients = recipe.ingredientNames.map { normalizeFoodName(it) }.toSet()

            // Se alguma alergia coincide com ingrediente da receita, não é segura
            if (recipeIngredients.intersect(normalizedAllergies).isNotEmpty()) {
                return false
            }
        }

        return true
    }

    /**
     * Calcula o cruzamento entre uma receita e a despensa do usuário.
     */
    private fun calculateMatch(
        recipe: HealthyRecipe,
        pantryNames: Set<String>
    ): RecipePantryMatch {
        val available = mutableListOf<String>()
        val missing = mutableListOf<String>()

        for (ingredient in recipe.ingredientNames) {
            val normalizedIngredient = normalizeFoodName(ingredient)
            if (pantryNames.contains(normalizedIngredient) || pantryNames.any { it.contains(normalizedIngredient, ignoreCase = true) }) {
                available.add(ingredient)
            } else {
                missing.add(ingredient)
            }
        }

        return RecipePantryMatch(
            recipe = recipe,
            availableIngredients = available,
            missingIngredients = missing,
            hasAll = missing.isEmpty(),
            missingCount = missing.size
        )
    }

    /**
     * Normaliza o nome do alimento para comparação case-insensitive e sem acentos.
     */
    private fun normalizeFoodName(name: String): String {
        return java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            .lowercase(Locale.ROOT)
            .trim()
    }

    /**
     * Sugere adicionar ingredientes faltantes à lista de compras.
     */
    fun getMissingIngredientsForRecipe(
        match: RecipePantryMatch
    ): List<String> = match.missingIngredients
}
