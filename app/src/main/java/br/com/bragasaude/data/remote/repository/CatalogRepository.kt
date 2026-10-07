package br.com.bragasaude.data.remote.repository

import android.content.Context
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.api.BragaApiClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodDao: FoodDao,
    private val mealRuleDao: MealRuleDao,
    private val clinicalReferenceSeeder: ClinicalReferenceSeeder,
    private val apiClient: BragaApiClient
) {
    suspend fun contributeGroceryPrice(foodName: String, value: br.com.bragasaude.domain.GroceryPriceContribution) =
        apiClient.contributeGroceryPrice(foodName, value)

    suspend fun fetchCommunityGroceryPrices() = apiClient.getCommunityGroceryPrices()

    suspend fun fetchGroceryIngredients(includePrices: Boolean = true): br.com.bragasaude.domain.GroceryIngredientCatalog = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val manifest = org.json.JSONObject(context.assets.open("grocery_ingredients.json").bufferedReader().use { it.readText() })
        val arr = manifest.getJSONArray("ingredients")
        val prices = if (includePrices) apiClient.getCanonicalGroceryPrices() else emptyMap()
        fun strings(array: org.json.JSONArray): List<String> = (0 until array.length()).map { array.getString(it) }
        val ingredients = (0 until arr.length()).map { i ->
            val item = arr.getJSONObject(i)
            val reference = prices[item.getString("slug")]
            val unit = item.getString("unit")
            br.com.bragasaude.domain.GroceryIngredient(item.getString("slug"), item.getString("name"), unit,
                item.getInt("step"), item.getInt("minimum"), strings(item.getJSONArray("aliases")), strings(item.getJSONArray("food_ids")),
                reference?.takeIf { it.unit == unit }?.average,
                reference?.takeIf { it.unit == unit }?.source ?: "unavailable")
        }
        val foods = manifest.getJSONArray("foods")
        val components = (0 until foods.length()).associate { i ->
            val food = foods.getJSONObject(i)
            food.getString("food_id") to strings(food.getJSONArray("shopping_components"))
        }
        br.com.bragasaude.domain.GroceryIngredientCatalog(ingredients, components)
    }

    suspend fun fetchGroceryPrices(): Map<String, Double> {
        return try {
            apiClient.getGroceryPrices()
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun seedDatabaseIfNeeded() {
        try {
            clinicalReferenceSeeder.seedIfNeeded()
            
            val jsonString = context.assets.open("food_catalog_seed.json").bufferedReader().use { it.readText() }
            val foods = Json { ignoreUnknownKeys = true }.decodeFromString<List<FoodEntity>>(jsonString)
            if (foods.isNotEmpty()) {
                foodDao.refreshBundledCatalog(foods, br.com.bragasaude.domain.AffordableFoodPolicy.replacements.keys.toList())
            }
        } catch (e: Exception) {
            android.util.Log.e("CatalogRepo", "Erro ao semear catálogo de alimentos: ${e.message}")
        }
    }

    fun getFoodCatalog(): Flow<List<FoodEntity>> = foodDao.getCatalog()
    fun searchFoods(query: String): Flow<List<FoodEntity>> = foodDao.search(query)
    fun getMealRules(): Flow<List<MealRuleEntity>> = mealRuleDao.getRules()

    suspend fun addCustomFood(
        name: String,
        category: String,
        kcal: Double,
        carbsG: Double? = null,
        proteinG: Double? = null,
        fatG: Double? = null
    ) {
        val entity = FoodEntity(
            remoteId = java.util.UUID.randomUUID().toString(),
            name = name,
            category = category,
            kcal = kcal,
            carbsG = carbsG,
            proteinG = proteinG,
            fatG = fatG,
            status = "custom",
            isDiabetesSafe = true,
            isHypertensionSafe = true,
            isThyroidSafe = true
        )
        foodDao.insert(entity)
    }
}
