package br.com.bragasaude.domain

import br.com.bragasaude.data.local.FoodEntity
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.model.RecipeCatalog
import br.com.bragasaude.data.remote.model.RemoteProfile
import java.io.File
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class AffordableNutritionTest {
    private fun asset(name: String) = listOf(File("src/main/assets/$name"), File("app/src/main/assets/$name"))
        .first { it.exists() }.readText()
    private val foods get() = Json { ignoreUnknownKeys = true }.decodeFromString<List<FoodEntity>>(asset("food_catalog_seed.json"))
    private val ingredients get(): GroceryIngredientCatalog {
        val manifest = Json.parseToJsonElement(asset("grocery_ingredients.json")).jsonObject
        fun strings(element: JsonElement) = element.jsonArray.map { it.jsonPrimitive.content }
        val items = manifest.getValue("ingredients").jsonArray.map { entry ->
            val o = entry.jsonObject
            GroceryIngredient(o.getValue("slug").jsonPrimitive.content, o.getValue("name").jsonPrimitive.content,
                o.getValue("unit").jsonPrimitive.content, o.getValue("step").jsonPrimitive.int,
                o.getValue("minimum").jsonPrimitive.int, strings(o.getValue("aliases")), strings(o.getValue("food_ids")))
        }
        val components = manifest.getValue("foods").jsonArray.associate { entry ->
            val o = entry.jsonObject
            o.getValue("food_id").jsonPrimitive.content to strings(o.getValue("shopping_components"))
        }
        return GroceryIngredientCatalog(items, components)
    }

    @Test fun upgradedCatalogContainsEveryReplacementAndNoRetiredFoodOrIngredient() {
        val catalog = foods
        val manifest = ingredients
        assertTrue(catalog.none { it.remoteId in AffordableFoodPolicy.replacements })
        assertTrue(AffordableFoodPolicy.replacements.values.all { id -> catalog.any { it.remoteId == id } })
        assertTrue(catalog.all { manifest.forFood(it.remoteId, it.name).isNotEmpty() })
        assertTrue(manifest.components.values.flatten().all { slug -> manifest.ingredients.any { it.slug == slug } })
        assertTrue(manifest.ingredients.flatMap { it.foodIds }.all { id -> catalog.any { it.remoteId == id } })
        assertFalse(manifest.ingredients.any { it.name.contains("Salmão") || it.name.contains("Quinoa") || it.name.contains("Extravirgem") })
        val oil = catalog.single { it.remoteId == "food_221" }
        assertEquals(900.0, oil.kcal!!, 0.001)
        assertEquals(5, oil.servingSizeGrams)
    }

    @Test fun everyRecipeCanBeCompletedWithRealShoppingIngredients() {
        val manifest = ingredients
        for (recipe in RecipeCatalog.getAll()) {
            for (name in recipe.ingredientNames) {
                val accepted = RecipeIngredientMatcher.acceptedNames(name)
                assertTrue("${recipe.id}: missing shopping ingredient $name", manifest.ingredients.any { ingredient ->
                    (ingredient.aliases + ingredient.name).any { groceryNameKey(it) in accepted }
                })
            }
            assertFalse(recipe.ingredientNames.any { it.contains("Quinoa") || it.contains("Azeite") || it.contains("Tilápia") })
        }
    }

    @Test fun randomListsRespectAllergiesAndHealthFlagsEvenWithLegacyFoods() {
        val staleSalmon = FoodEntity("food_161", "Salmão", category = "Peixes")
        val unsafe = FoodEntity("unsafe", "Alimento restrito", isDiabetesSafe = false, isHypertensionSafe = false)
        val profile = RemoteProfile(id = "u", foodAllergies = listOf("Oleaginosas", "Peixes", "Ovos", "Lactose"), hasDiabetes = true, hasHypertension = true)
        repeat(30) {
            val list = WeeklyGroceryEngine.generateWeeklyList("u", emptyList(), emptyList(), profile,
                foods + staleSalmon + unsafe, ingredientCatalog = ingredients)
            assertTrue(list.isNotEmpty())
            assertTrue(list.none { item -> listOf("amendoim", "sardinha", "ovo", "leite", "ricota", "salmao").any { groceryNameKey(item.foodName).contains(it) } })
        }
    }

    @Test fun partialRecipesShowExactMissingItemsAndCompleteRecipesComeFirst() {
        val engine = RecipeEngine()
        val list = listOf("Ovos", "Tomate").map(::item)
        val matches = engine.findBestRecipes(list, null, "BREAKFAST", includeMissing = true)
        assertTrue(matches.first().hasAll)
        val omelette = matches.single { it.recipe.id == "recipe_002" }
        assertFalse(omelette.hasAll)
        assertEquals(listOf("Óleo de soja", "Orégano"), omelette.missingIngredients)
        assertEquals(listOf("Ovos", "Tomate"), omelette.availableIngredients)
        assertTrue(engine.findBestRecipes(emptyList(), null, includeMissing = true).isEmpty())
    }

    @Test fun missingAllergensAndCustomRestrictionsStillBlockRecipes() {
        val engine = RecipeEngine()
        val banana = listOf(item("Banana"))
        assertFalse(engine.findBestRecipes(banana, ProfileEntity(userId = "u", foodAllergies = listOf("Lactose e Derivados do Leite")), includeMissing = true)
            .any { it.recipe.id == "recipe_001" })
        assertFalse(engine.findBestRecipes(banana, ProfileEntity(userId = "u", foodAllergies = listOf("Ovos")), includeMissing = true)
            .any { it.recipe.id == "recipe_016" })
        assertFalse(engine.findBestRecipes(listOf(item("Tomate")), ProfileEntity(userId = "u", customFoodRestrictions = "soja"), includeMissing = true)
            .any { "Óleo de soja" in it.recipe.ingredientNames })
        assertFalse(engine.findBestRecipes(listOf(item("Alho")), ProfileEntity(userId = "u", foodAllergies = listOf("Glúten / Doença Celíaca")), includeMissing = true)
            .any { it.recipe.id in listOf("recipe_019", "recipe_020") })
    }

    private fun item(name: String) = GroceryListItemEntity(name, "u", "2026-10-07", name, name,
        "Minha lista", 0, 0, "1 un", 0.0, false)
}
