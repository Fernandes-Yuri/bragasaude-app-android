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
    private val apiClient: BragaApiClient
) {
    suspend fun contributeGroceryPrice(foodName: String, value: br.com.bragasaude.domain.GroceryPriceContribution) =
        apiClient.contributeGroceryPrice(foodName, value)

    suspend fun fetchCommunityGroceryPrices() = apiClient.getCommunityGroceryPrices()

    private val catalogMutex = kotlinx.coroutines.sync.Mutex()
    private val catalogCache by lazy { context.getSharedPreferences("remote_nutrition_catalog", Context.MODE_PRIVATE) }
    private val catalogJson = Json { ignoreUnknownKeys = true }

    private suspend fun catalogSnapshot(refresh: Boolean): org.json.JSONObject? {
        catalogMutex.lock()
        try {
            if (refresh) {
                val remote = apiClient.getNutritionCatalog()
                if (remote != null) {
                    try {
                        val parsed = br.com.bragasaude.domain.RemoteNutritionCatalogParser.parse(remote)
                        val foods = catalogJson.decodeFromString<List<FoodEntity>>(remote.getJSONArray("foods").toString())
                        require(foods.map { it.remoteId }.toSet() == parsed.components.keys)
                        foodDao.replaceServerCatalog(foods)
                        check(catalogCache.edit().putString("snapshot", remote.toString()).commit())
                        return remote
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { /* Mantém a última revisão completa em caso de resposta inválida. */ }
                }
            }
            return catalogCache.getString("snapshot", null)?.let { raw ->
                try {
                    val cached = org.json.JSONObject(raw)
                    br.com.bragasaude.domain.RemoteNutritionCatalogParser.parse(cached)
                    val foods = catalogJson.decodeFromString<List<FoodEntity>>(cached.getJSONArray("foods").toString())
                    foodDao.replaceServerCatalog(foods)
                    cached
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { null }
            }
        } finally { catalogMutex.unlock() }
    }

    suspend fun fetchGroceryIngredients(includePrices: Boolean = true): br.com.bragasaude.domain.GroceryIngredientCatalog = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val snapshot = catalogSnapshot(refresh = includePrices)
        val catalog = if (snapshot != null) {
            try {
                br.com.bragasaude.domain.RemoteNutritionCatalogParser.parse(snapshot)
            } catch (_: Exception) {
                val manifest = org.json.JSONObject(context.assets.open("grocery_ingredients.json").bufferedReader().use { it.readText() })
                br.com.bragasaude.domain.RemoteNutritionCatalogParser.parseLegacy(manifest)
            }
        } else {
            val manifest = org.json.JSONObject(context.assets.open("grocery_ingredients.json").bufferedReader().use { it.readText() })
            br.com.bragasaude.domain.RemoteNutritionCatalogParser.parseLegacy(manifest)
        }

        if (!includePrices) return@withContext catalog

        val prices = fetchGroceryPrices()
        if (prices.isEmpty()) return@withContext catalog

        val normalizedPrices = prices.mapKeys { br.com.bragasaude.domain.groceryNameKey(it.key) }
        val enrichedIngredients = catalog.ingredients.map { ing ->
            val matchPrice = normalizedPrices[br.com.bragasaude.domain.groceryNameKey(ing.name)]
                ?: ing.aliases.firstNotNullOfOrNull { normalizedPrices[br.com.bragasaude.domain.groceryNameKey(it)] }
                ?: normalizedPrices[br.com.bragasaude.domain.groceryNameKey(ing.slug.replace('-', ' '))]
            if (matchPrice != null && matchPrice > 0.0) {
                ing.copy(price = matchPrice, source = if (ing.source == "manual") "manual" else "estimated")
            } else {
                ing
            }
        }
        catalog.copy(ingredients = enrichedIngredients)
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
            
            if (catalogSnapshot(refresh = true) == null) {
                val currentCatalog = foodDao.getCatalog().first()
                if (currentCatalog.isEmpty()) {
                    val jsonString = context.assets.open("food_catalog_seed.json").bufferedReader().use { it.readText() }
                    val foods = catalogJson.decodeFromString<List<FoodEntity>>(jsonString)
                    if (foods.isNotEmpty()) foodDao.insertAll(foods)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) {
            android.util.Log.w("CatalogRepo", "Catálogo nutricional indisponível")
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
