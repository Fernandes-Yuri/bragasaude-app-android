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
            "Arroz integral" to "Quinoa em Flocos", "Limão" to "Chá de Gengibre com Casca de Limão")) {
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

    @Test fun simpleCrepiocaAndRiceRemainAvailableWithCompleteIngredients() {
        for (id in listOf("recipe_005", "recipe_017")) {
            val recipe = RecipeCatalog.getAll().single { it.id == id }
            assertTrue(engine.findBestRecipes(recipe.ingredientNames.map(::item), null)
                .any { it.recipe.id == id })
        }
    }

    private fun item(name: String) = GroceryListItemEntity(
        remoteId = name, userId = "user", weekStartDate = "2026-10-06", foodId = name,
        foodName = name, category = "Feira", suggestedServingWeekGrams = 100,
        purchaseWeightGrams = 100, purchaseUnitText = "1 un", estimatedPriceBrl = 0.0,
        isCheckedInPantry = false
    )
}
