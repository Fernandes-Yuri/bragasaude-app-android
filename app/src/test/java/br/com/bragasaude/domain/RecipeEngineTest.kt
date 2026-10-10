package br.com.bragasaude.domain

import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.model.RecipeCatalog
import org.junit.Assert.*
import org.junit.Test

class RecipeEngineTest {
    private val engine = RecipeEngine()

    @Test fun everyRecipeRequiresEveryIngredientIncludingUncheckedItems() {
        for (recipe in RecipeCatalog.getAll()) {
            val items = recipe.ingredientNames.map(::item)
            val result = engine.findBestRecipes(items, null).single { it.recipe.id == recipe.id }
            assertTrue(result.hasAll)
            assertTrue(result.missingIngredients.isEmpty())
            for (index in items.indices) {
                assertFalse("${recipe.id} accepted missing ${items[index].foodName}",
                    engine.findBestRecipes(items.filterIndexed { i, _ -> i != index }, null)
                        .any { it.recipe.id == recipe.id })
            }
        }
        assertTrue(engine.findBestRecipes(emptyList(), null).isEmpty())
        assertTrue(engine.findBestRecipes(listOf(item("Tomate")), null).isEmpty())
    }

    @Test fun catalogNamesMatchButDifferentFoodsContainingTheNameDoNot() {
        val valid = listOf("Abacate Manteiga", "Suco de Limão Tahiti Fresco", "Semente de Chia")
        assertTrue(engine.findBestRecipes(valid.map(::item), null).any { it.recipe.id == "recipe_003" })
        for (wrong in listOf("Óleo de Abacate Extravirgem", "Creme de Abacate com açúcar")) {
            assertFalse(engine.findBestRecipes((listOf(wrong) + valid.drop(1)).map(::item), null)
                .any { it.recipe.id == "recipe_003" })
        }
        for ((ingredient, wrong) in listOf("Banana" to "Farinha de Banana Verde",
            "Couve" to "Couve-Flor no Vapor", "Alho" to "Alho-Poró Fatiado Refogado",
            "Quinoa" to "Quinoa em Flocos", "Limão" to "Chá de Gengibre com Casca de Limão")) {
            for (recipe in RecipeCatalog.getAll().filter { ingredient in it.ingredientNames }) {
                val items = recipe.ingredientNames.map { item(if (it == ingredient) wrong else it) }
                assertFalse(engine.findBestRecipes(items, null).any { it.recipe.id == recipe.id })
            }
        }
    }

    @Test fun clinicalAndMealFiltersStillApplyToCompleteLists() {
        val recipe = RecipeCatalog.getAll().single { it.id == "recipe_005" }
        val items = recipe.ingredientNames.map(::item)
        assertTrue(engine.findBestRecipes(items, null, "BREAKFAST").any { it.recipe.id == recipe.id })
        assertTrue(engine.findBestRecipes(items, null, "DINNER").isEmpty())
        assertFalse(engine.findBestRecipes(items, ProfileEntity(userId = "user", hasDiabetes = true))
            .any { it.recipe.id == recipe.id })
        assertTrue(engine.findBestRecipes(items, ProfileEntity(userId = "user", foodAllergies = listOf("Ovos"))).isEmpty())
    }


    @Test fun everydayEggRecipeNeedsOnlyEggsAndNoSpecialPreparation() {
        val recipe = RecipeCatalog.getAll().single { it.id == "recipe_021" }
        assertEquals(listOf("Ovos"), recipe.ingredientNames)
        assertEquals("Ovo Cozido", recipe.title)
        assertTrue(engine.findBestRecipes(listOf(item("Ovo Caipira Cozido")), null)
            .any { it.recipe.id == recipe.id })
        assertTrue(RecipeCatalog.getAll().none { it.title.contains("Poch", ignoreCase = true) })
    }

    @Test fun simpleCrepiocaAndQuinoaRemainAvailableWithCompleteIngredients() {
        for (id in listOf("recipe_005", "recipe_017")) {
            val recipe = RecipeCatalog.getAll().single { it.id == id }
            assertTrue(engine.findBestRecipes(recipe.ingredientNames.map(::item), null)
                .any { it.recipe.id == id })
        }
    }

    @Test fun singularAndPluralAndSynonymAllergiesAreProperlyFiltered() {
        val recipeCrepioca = RecipeCatalog.getAll().single { it.id == "recipe_005" } // Ovos, Tapioca, Ricota, Orégano
        val itemsCrepioca = recipeCrepioca.ingredientNames.map(::item)

        // Alergia declarada no singular "Ovo" deve bloquear receita com ingrediente "Ovos"
        assertTrue(engine.findBestRecipes(itemsCrepioca, ProfileEntity(userId = "user", foodAllergies = listOf("Ovo"))).isEmpty())

        // Alergia declarada "Lactose" deve bloquear receita com "Ricota" (derivado lácteo)
        assertTrue(engine.findBestRecipes(itemsCrepioca, ProfileEntity(userId = "user", foodAllergies = listOf("Lactose"))).isEmpty())
    }

    @Test fun recipeHasStructuredIngredientsAndPositiveMacros() {
        val all = RecipeCatalog.getAll()
        assertEquals(21, all.size)
        for (recipe in all) {
            assertTrue("Recipe ${recipe.id} structured ingredients empty", recipe.structuredIngredients.isNotEmpty())
            assertEquals(recipe.ingredientNames.size, recipe.structuredIngredients.size)
            assertTrue("Recipe ${recipe.id} calories should be positive", recipe.servingKcal > 0.0)
            assertTrue("Recipe ${recipe.id} protein should be positive", recipe.servingProteinG > 0.0)
            for (req in recipe.structuredIngredients) {
                assertTrue("Req ${req.ingredientName} in ${recipe.id} requiredAmount > 0", req.requiredAmount > 0.0)
                assertTrue("Req ${req.ingredientName} in ${recipe.id} unit valid", req.unit in listOf("g", "ml", "un"))
            }
        }
    }

    @Test fun physicalPantryStockProvidesReadyToCookStatus() {
        // Recipe 021: Ovo Cozido (needs 2 ovos)
        val eggStock = br.com.bragasaude.data.local.GroceryPantryStockEntity(
            userId = "user",
            ingredientSlug = "food_ovo",
            unit = "un",
            availableAmount = 6.0
        )
        val result = engine.findBestRecipes(
            pantryItems = emptyList(),
            profile = null,
            mealType = "BREAKFAST",
            pantryStock = listOf(eggStock)
        )
        val eggRecipe = result.firstOrNull { it.recipe.id == "recipe_021" }
        assertNotNull(eggRecipe)
        assertEquals(br.com.bragasaude.data.local.model.RecipeReadinessStatus.READY_TO_COOK, eggRecipe!!.readinessStatus)
        assertTrue(eggRecipe.hasAll)
        assertTrue(eggRecipe.missingIngredients.isEmpty())
    }

    @Test fun partialStockFallsBackToPlannedOnListWhenInShoppingList() {
        // Recipe 021: Ovo Cozido (needs 2 ovos, but stock has only 1 egg)
        val lowEggStock = br.com.bragasaude.data.local.GroceryPantryStockEntity(
            userId = "user",
            ingredientSlug = "food_ovo",
            unit = "un",
            availableAmount = 1.0
        )
        val shoppingItem = item("Ovos")
        val result = engine.findBestRecipes(
            pantryItems = listOf(shoppingItem),
            profile = null,
            mealType = "BREAKFAST",
            pantryStock = listOf(lowEggStock)
        )
        val eggRecipe = result.firstOrNull { it.recipe.id == "recipe_021" }
        assertNotNull(eggRecipe)
        assertEquals(br.com.bragasaude.data.local.model.RecipeReadinessStatus.PLANNED_ON_LIST, eggRecipe!!.readinessStatus)
        assertTrue(eggRecipe.hasAll)
    }

    private fun item(name: String) = GroceryListItemEntity(
        remoteId = name, userId = "user", weekStartDate = "2026-10-06", foodId = name,
        foodName = name, category = "Feira", suggestedServingWeekGrams = 100,
        purchaseWeightGrams = 100, purchaseUnitText = "1 un", estimatedPriceBrl = 0.0,
        isCheckedInPantry = false
    )
}
