package br.com.bragasaude.domain

import br.com.bragasaude.data.local.FoodEntity
import br.com.bragasaude.data.remote.model.RemoteProfile
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteNutritionCatalogTest {
    private fun snapshot() = JSONObject("""{
      "version":9,
      "foods":[{"remoteId":"base_a","name":"Base A","shoppingComponents":["ingredient_a"],"purchaseFactors":{"ingredient_a":0.5}},
               {"remoteId":"base_b","name":"Base B","shoppingComponents":["ingredient_b"],"purchaseFactors":{}}],
      "ingredients":[{"slug":"ingredient_a","name":"Produto A","unit":"kg","step":100,"minimum":100,"aliases":[],"food_ids":["base_a"],"price_avg":null},
                     {"slug":"ingredient_b","name":"Produto B","unit":"kg","step":100,"minimum":100,"aliases":[],"food_ids":["base_b"],"price_avg":10}],
      "policy":{"required_groups":[["base_a"],["base_b"]]}
    }""")

    @Test fun receivesUnknownIdentifiersPolicyAndPurchaseFactorsFromServer() {
        val parsed = RemoteNutritionCatalogParser.parse(snapshot())
        assertEquals(9, parsed.version)
        assertEquals(listOf(listOf("base_a"), listOf("base_b")), parsed.requiredGroups)
        assertEquals(0.5, parsed.purchaseFactors["base_a"]!!["ingredient_a"]!!, 0.001)
        assertNull(parsed.ingredients.first().price)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPartialSnapshotWithDanglingPurchaseMapping() {
        val broken = snapshot()
        broken.getJSONArray("ingredients").remove(1)
        RemoteNutritionCatalogParser.parse(broken)
    }

    private fun food(id: String, tags: List<String> = listOf("diet:vegan", "diet:vegetarian")) =
        FoodEntity(id, "Preparo $id", category = "Grãos", servingSizeGrams = 100, functionalTags = tags)

    @Test fun everyGeneratedListIncludesDatabaseGroupsWithoutHardcodedFoodNames() {
        val parsed = RemoteNutritionCatalogParser.parse(snapshot())
        repeat(30) {
            val list = WeeklyGroceryEngine.generateWeeklyList("u", emptyList(), emptyList(), null,
                listOf(food("base_a"), food("base_b")), ingredientCatalog = parsed)
            assertEquals(setOf("ingredient_a", "ingredient_b"), list.map { it.foodId }.toSet())
            assertEquals(300, list.first { it.foodId == "ingredient_a" }.purchaseWeightGrams)
        }
    }

    @Test fun respectsAllergensClinicalRestrictionsAndDietBeforeRequiredGroups() {
        val parsed = RemoteNutritionCatalogParser.parse(snapshot())
        val blocked = food("base_a", listOf("diet:vegan", "diet:vegetarian", "allergen:teste"))
        val list = WeeklyGroceryEngine.generateWeeklyList("u", emptyList(), emptyList(),
            RemoteProfile("u", foodAllergies = listOf("Teste")), listOf(blocked, food("base_b")), ingredientCatalog = parsed)
        assertEquals(listOf("ingredient_b"), list.map { it.foodId })
        val diabetes = WeeklyGroceryEngine.generateWeeklyList("u", emptyList(), emptyList(),
            RemoteProfile("u", hasDiabetes = true), listOf(food("base_a").copy(isDiabetesSafe = false), food("base_b")), ingredientCatalog = parsed)
        assertEquals(listOf("ingredient_b"), diabetes.map { it.foodId })
        assertFalse(NutritionSuggestionEngine.isSafeFromAllergies(food("animal", emptyList()), emptyList(), "vegetariano"))
        assertFalse(NutritionSuggestionEngine.isSafeFromAllergies(food("ovo", listOf("diet:vegetarian")), emptyList(), "vegano"))
        assertTrue(NutritionSuggestionEngine.isSafeFromAllergies(food("planta"), emptyList(), "vegano"))
        assertFalse(NutritionSuggestionEngine.isSafeFromAllergies(
            food("planta", listOf("diet:vegan", "allergen:soja")), listOf("Soja"), null))
    }
}
