package br.com.bragasaude.domain

import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.GroceryPantryStockEntity
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.model.HealthyRecipe
import br.com.bragasaude.data.local.model.RecipeCatalog
import br.com.bragasaude.data.local.model.RecipeIngredientRequirement
import br.com.bragasaude.data.local.model.RecipeMissingRequirement
import br.com.bragasaude.data.local.model.RecipeReadinessStatus
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resultado do cruzamento de uma receita com a lista de compras e a despensa do usuário.
 *
 * @param recipe A receita recomendada.
 * @param availableIngredients Ingredientes que o usuário tem na despensa ou incluiu na lista de compras.
 * @param missingIngredients Ingredientes que faltam tanto na despensa quanto na lista de compras.
 * @param hasAll True se o usuário tem todos os ingredientes (no estoque da despensa ou na lista).
 * @param missingCount Número de ingredientes faltando totalmente.
 * @param readinessStatus Status de prontidão física (READY_TO_COOK, PLANNED_ON_LIST, MISSING_INGREDIENTS).
 * @param missingRequirements Detalhes quantitativos dos requisitos pendentes ou planejados.
 */
data class RecipePantryMatch(
    val recipe: HealthyRecipe,
    val availableIngredients: List<String>,
    val missingIngredients: List<String>,
    val hasAll: Boolean,
    val missingCount: Int,
    val readinessStatus: RecipeReadinessStatus = RecipeReadinessStatus.READY_TO_COOK,
    val missingRequirements: List<RecipeMissingRequirement> = emptyList()
)

/**
 * Motor de Busca e Cruzamento de Receitas com a Despensa e Lista de Compras.
 *
 * Cruza a lista de alimentos da lista de compras e o estoque real da despensa do usuário
 * com o catálogo de receitas, aplicando filtros clínicos baseados no perfil (diabetes, hipertensão, alergias).
 *
 * **Regra de ouro (D3/D4 / DECISOES.md):** As receitas são sugestões de autocuidado
 * nutricional, com ingredientes naturais e preparo simples. Nenhuma alegação diagnóstica
 * ou prescritiva é feita.
 */
@Singleton
class RecipeEngine @Inject constructor() {

    /**
     * Encontra as melhores receitas para cozinhar com os alimentos da despensa e lista de compras.
     *
     * @param pantryItems Todos os itens da lista de compras, comprados ou ainda não comprados.
     * @param profile Perfil do usuário com condições clínicas e alergias.
     * @param mealType Tipo de refeição desejado (BREAKFAST, LUNCH, SNACK, DINNER) ou vazio para todas.
     * @param includeMissing Se true, inclui sugestões com ingredientes faltando (ex: quando o usuário montou sua própria lista).
     * @param pantryStock Estoque físico real armazenado na despensa local.
     * @return Lista de receitas ordenadas por nível de prontidão e maior número de ingredientes disponíveis.
     */
    fun findBestRecipes(
        pantryItems: List<GroceryListItemEntity>,
        profile: ProfileEntity?,
        mealType: String? = null,
        includeMissing: Boolean = false,
        pantryStock: List<GroceryPantryStockEntity> = emptyList()
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

        // Cruza com o estoque da despensa e a lista de compras e calcula score
        val matches = clinicallySafe.map { recipe ->
            calculateMatch(recipe, pantryNames, pantryStock)
        }

        // Sugestões incompletas são opcionais; receitas prontas e completas aparecem primeiro.
        return matches
            .filter { it.availableIngredients.isNotEmpty() && (includeMissing || it.hasAll) }
            .sortedWith(
                compareBy<RecipePantryMatch> {
                    when (it.readinessStatus) {
                        RecipeReadinessStatus.READY_TO_COOK -> 0
                        RecipeReadinessStatus.PLANNED_ON_LIST -> 1
                        RecipeReadinessStatus.MISSING_INGREDIENTS -> 2
                    }
                }
                    .thenByDescending { it.hasAll }
                    .thenByDescending { it.availableIngredients.size.toDouble() / it.recipe.ingredientNames.size }
                    .thenBy { it.missingCount }
                    .thenBy { it.recipe.id }
            )
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

        // Filtro por alergias alimentares com ontologia e sinônimos (corrige P11)
        val allergies = profile.foodAllergies
        if (allergies.isNotEmpty()) {
            val declaredFamilies = br.com.bragasaude.domain.nutrition.core.AllergenFamily.parseDeclaredAllergens(allergies)
            for (ingredient in recipe.ingredientNames) {
                // Checagem por famílias ontológicas
                for (family in declaredFamilies) {
                    if (br.com.bragasaude.domain.nutrition.core.AllergenFamily.matchesFood(ingredient, null, emptyList(), family)) {
                        return false
                    }
                }
                // Fallback por igualdade de substring normalizada
                val normIng = normalizeFoodName(ingredient)
                for (allergy in allergies) {
                    val normAllergy = normalizeFoodName(allergy)
                    if (normIng.contains(normAllergy) || normAllergy.contains(normIng)) {
                        return false
                    }
                }
            }
        }

        return true
    }

    /**
     * Calcula o cruzamento entre uma receita, a lista de compras e o estoque real da despensa.
     */
    private fun calculateMatch(
        recipe: HealthyRecipe,
        pantryNames: Set<String>,
        pantryStock: List<GroceryPantryStockEntity>
    ): RecipePantryMatch {
        val available = mutableListOf<String>()
        val missing = mutableListOf<String>()
        val missingRequirements = mutableListOf<RecipeMissingRequirement>()

        val requirements = if (recipe.structuredIngredients.isNotEmpty()) {
            recipe.structuredIngredients
        } else {
            recipe.ingredientNames.zip(
                if (recipe.ingredientFoodIds.size == recipe.ingredientNames.size) recipe.ingredientFoodIds
                else List(recipe.ingredientNames.size) { "" }
            ).map { (name, foodId) ->
                RecipeIngredientRequirement(name, foodId, 100.0, "g")
            }
        }

        var allPhysicallyInStock = pantryStock.isNotEmpty()

        for (req in requirements) {
            val stockAmount = getAvailableStockAmount(req, pantryStock)
            val isPhysicallyAvailable = pantryStock.isNotEmpty() && stockAmount >= req.requiredAmount
            val isPlanned = RecipeIngredientMatcher.acceptedNames(req.ingredientName).any { it in pantryNames }

            if (isPhysicallyAvailable) {
                available.add(req.ingredientName)
            } else {
                if (pantryStock.isNotEmpty()) {
                    allPhysicallyInStock = false
                }
                if (isPlanned) {
                    available.add(req.ingredientName)
                    missingRequirements.add(
                        RecipeMissingRequirement(
                            ingredientName = req.ingredientName,
                            requiredAmount = req.requiredAmount,
                            unit = req.unit,
                            availableInPantry = stockAmount,
                            isPlannedOnList = true
                        )
                    )
                } else {
                    missing.add(req.ingredientName)
                    missingRequirements.add(
                        RecipeMissingRequirement(
                            ingredientName = req.ingredientName,
                            requiredAmount = req.requiredAmount,
                            unit = req.unit,
                            availableInPantry = stockAmount,
                            isPlannedOnList = false
                        )
                    )
                }
            }
        }

        val hasAll = missing.isEmpty()
        val readinessStatus = when {
            allPhysicallyInStock && hasAll -> RecipeReadinessStatus.READY_TO_COOK
            hasAll -> RecipeReadinessStatus.PLANNED_ON_LIST
            else -> RecipeReadinessStatus.MISSING_INGREDIENTS
        }

        return RecipePantryMatch(
            recipe = recipe,
            availableIngredients = available,
            missingIngredients = missing,
            hasAll = hasAll,
            missingCount = missing.size,
            readinessStatus = readinessStatus,
            missingRequirements = missingRequirements
        )
    }

    /**
     * Calcula a quantidade disponível de um ingrediente no estoque real da despensa.
     */
    private fun getAvailableStockAmount(
        requirement: RecipeIngredientRequirement,
        pantryStock: List<GroceryPantryStockEntity>
    ): Double {
        if (pantryStock.isEmpty()) return 0.0
        return pantryStock
            .filter { matchesStock(requirement, it) }
            .sumOf { it.availableAmount }
    }

    /**
     * Verifica correspondência de um requisito de receita com uma entrada do estoque da despensa.
     */
    private fun matchesStock(
        requirement: RecipeIngredientRequirement,
        stock: GroceryPantryStockEntity
    ): Boolean {
        if (requirement.ingredientFoodId.isNotBlank()) {
            val cleanReqId = requirement.ingredientFoodId.removePrefix("food_").lowercase(Locale.ROOT)
            val cleanStockSlug = stock.ingredientSlug.removePrefix("food_").lowercase(Locale.ROOT)
            if (cleanStockSlug == cleanReqId || cleanStockSlug.replace("-", "_") == cleanReqId.replace("-", "_")) {
                return true
            }
        }
        val normalizedStockSlug = normalizeFoodName(stock.ingredientSlug.replace("-", " ").replace("_", " "))
        val accepted = RecipeIngredientMatcher.acceptedNames(requirement.ingredientName)
        return accepted.any { it == normalizedStockSlug || normalizedStockSlug.contains(it) || it.contains(normalizedStockSlug) }
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
