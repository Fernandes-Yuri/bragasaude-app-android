package br.com.bragasaude.domain

import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.model.HealthyRecipe
import br.com.bragasaude.data.local.model.RecipeCatalog
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultado do cruzamento de uma receita com a lista de compras do usuário.
 *
 * @param recipe A receita recomendada.
 * @param availableIngredients Ingredientes que o usuário incluiu na lista de compras.
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
 * Cruza a lista de alimentos da lista de compras do usuário com o catálogo de receitas,
 * aplicando filtros clínicos baseados no perfil (diabetes, hipertensão, alergias).
 *
 * **Regra de ouro (D3/D4 / DECISOES.md):** As receitas são sugestões de autocuidado
 * nutricional, com ingredientes naturais e preparo simples. Nenhuma alegação diagnóstica
 * ou prescritiva é feita.
 */
@Singleton
class RecipeEngine @Inject constructor() {

    /**
     * Encontra as melhores receitas para cozinhar com os alimentos da lista de compras.
     *
     * @param pantryItems Todos os itens da lista de compras, comprados ou ainda não comprados.
     * @param profile Perfil do usuário com condições clínicas e alergias.
     * @param mealType Tipo de refeição desejado (BREAKFAST, LUNCH, SNACK, DINNER) ou vazio para todas.
     * @return Lista de receitas ordenadas por maior número de ingredientes disponíveis.
     */
    fun findBestRecipes(
        pantryItems: List<GroceryListItemEntity>,
        profile: ProfileEntity?,
        mealType: String? = null,
        includeMissing: Boolean = false
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

        // Só recomenda receitas com todos os ingredientes na lista de compras.
        return matches
            .filter { it.availableIngredients.isNotEmpty() && (includeMissing || it.hasAll) }
            .sortedWith(compareByDescending<RecipePantryMatch> { it.hasAll }
                .thenByDescending { it.availableIngredients.size.toDouble() / it.recipe.ingredientNames.size }
                .thenBy { it.missingCount }
                .thenBy { it.recipe.id })
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

        // Inclui os ingredientes faltantes: jamais sugerir uma receita insegura para completá-la depois.
        val safe = recipe.ingredientNames.all { name ->
            val ingredient = br.com.bragasaude.data.local.FoodEntity(remoteId = name, name = name)
            NutritionSuggestionEngine.isSafeFromAllergies(ingredient, profile.foodAllergies, profile.customFoodRestrictions) &&
                profile.foodAllergies.none { allergy -> normalizeFoodName(name).contains(normalizeFoodName(allergy)) }
        }
        if (!safe) return false

        return true
    }

    /**
     * Calcula o cruzamento entre uma receita e a lista de compras do usuário.
     */
    private fun calculateMatch(
        recipe: HealthyRecipe,
        pantryNames: Set<String>
    ): RecipePantryMatch {
        val available = mutableListOf<String>()
        val missing = mutableListOf<String>()

        for (ingredient in recipe.ingredientNames) {
            if (RecipeIngredientMatcher.acceptedNames(ingredient).any { it in pantryNames }) {
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
            .replace(Regex("\\s+"), " ")
    }

    /**
     * Sugere adicionar ingredientes faltantes à lista de compras.
     */
    fun getMissingIngredientsForRecipe(
        match: RecipePantryMatch
    ): List<String> = match.missingIngredients
}
