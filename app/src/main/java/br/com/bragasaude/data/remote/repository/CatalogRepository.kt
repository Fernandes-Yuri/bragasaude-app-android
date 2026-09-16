package br.com.bragasaude.data.remote.repository

import android.content.Context
import br.com.bragasaude.data.local.*
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
    private val clinicalReferenceSeeder: ClinicalReferenceSeeder
) {
    suspend fun seedDatabaseIfNeeded() {
        try {
            clinicalReferenceSeeder.seedIfNeeded()
            
            val currentCatalog = foodDao.getCatalog().first()
            if (currentCatalog.size < 100) {
                val jsonString = context.assets.open("food_catalog_seed.json").bufferedReader().use { it.readText() }
                val json = Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                }
                val foods = json.decodeFromString<List<FoodEntity>>(jsonString)
                if (foods.isNotEmpty()) {
                    foodDao.insertAll(foods)
                }
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
