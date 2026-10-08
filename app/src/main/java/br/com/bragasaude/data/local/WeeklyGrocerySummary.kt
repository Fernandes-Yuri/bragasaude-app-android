package br.com.bragasaude.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import br.com.bragasaude.domain.WeeklyGroceryPlanResult
import org.json.JSONArray

@Entity(tableName = "weekly_grocery_summary_local", primaryKeys = ["userId", "weekStartDate"])
data class WeeklyGrocerySummaryEntity(
    val userId: String,
    val weekStartDate: String,
    val targetWeeklyCalories: Double,
    val plannedWeeklyCalories: Double,
    val coveragePercent: Double,
    val plannedProteinGrams: Double,
    val plannedCarbsGrams: Double,
    val plannedFatGrams: Double,
    val foodVarietyCount: Int,
    val limitationsJson: String,
    val isManuallyModified: Boolean,
    val generatedAt: Long,
    val statusMessage: String
) {
    fun result(items: List<GroceryListItemEntity>): WeeklyGroceryPlanResult {
        val values = JSONArray(limitationsJson)
        return WeeklyGroceryPlanResult(items, targetWeeklyCalories, plannedWeeklyCalories, coveragePercent,
            plannedProteinGrams, plannedCarbsGrams, plannedFatGrams, foodVarietyCount,
            (0 until values.length()).map { values.getString(it) }, statusMessage, isManuallyModified)
    }
    companion object {
        fun from(userId: String, week: String, result: WeeklyGroceryPlanResult) = WeeklyGrocerySummaryEntity(
            userId, week, result.targetWeeklyCalories, result.plannedWeeklyCalories, result.coveragePercent,
            result.plannedProteinGrams, result.plannedCarbsGrams, result.plannedFatGrams,
            result.foodVarietyCount, JSONArray(result.limitations).toString(), result.isManuallyModified,
            System.currentTimeMillis(), result.statusMessage)
    }
}

@Dao
interface WeeklyGrocerySummaryDao {
    @Query("SELECT * FROM weekly_grocery_summary_local WHERE userId = :userId AND weekStartDate = :week LIMIT 1")
    fun observe(userId: String, week: String): Flow<WeeklyGrocerySummaryEntity?>
    @Query("SELECT * FROM weekly_grocery_summary_local WHERE userId = :userId AND weekStartDate = :week LIMIT 1")
    suspend fun get(userId: String, week: String): WeeklyGrocerySummaryEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(summary: WeeklyGrocerySummaryEntity)
    @Query("UPDATE weekly_grocery_summary_local SET isManuallyModified = 1, statusMessage = :message WHERE userId = :userId AND weekStartDate = :week")
    suspend fun invalidate(userId: String, week: String, message: String)
    @Query("DELETE FROM weekly_grocery_summary_local WHERE userId = :userId") suspend fun clear(userId: String)
}

@Entity(tableName = "grocery_pantry_stock_local", primaryKeys = ["userId", "ingredientSlug"])
data class GroceryPantryStockEntity(val userId: String, val ingredientSlug: String, val unit: String, val availableAmount: Double)

@Entity(tableName = "grocery_meal_local")
data class GroceryMealEntity(@PrimaryKey val id: String, val userId: String, val day: String,
    val foodId: String, val foodName: String, val mealType: String, val portionGrams: Int, val kcal: Double)

@Entity(tableName = "grocery_consumption_local", primaryKeys = ["mealId", "ingredientSlug"])
data class GroceryConsumptionEntity(val mealId: String, val ingredientSlug: String, val userId: String, val consumedAmount: Double)

@Dao
interface GroceryPantryDao {
    @Query("SELECT * FROM grocery_pantry_stock_local WHERE userId = :userId")
    fun observe(userId: String): Flow<List<GroceryPantryStockEntity>>
    @Query("SELECT * FROM grocery_pantry_stock_local WHERE userId = :userId AND ingredientSlug = :slug")
    suspend fun stock(userId: String, slug: String): GroceryPantryStockEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(stock: GroceryPantryStockEntity)
    @Query("SELECT * FROM grocery_meal_local WHERE userId = :userId AND day = :day")
    fun meals(userId: String, day: String): Flow<List<GroceryMealEntity>>
    @Query("SELECT * FROM grocery_meal_local WHERE userId = :userId AND id = :id")
    suspend fun meal(userId: String, id: String): GroceryMealEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(meal: GroceryMealEntity)
    @Query("DELETE FROM grocery_meal_local WHERE userId = :userId AND id = :id") suspend fun deleteMeal(userId: String, id: String)
    @Query("SELECT * FROM grocery_consumption_local WHERE userId = :userId AND mealId = :id")
    suspend fun consumption(userId: String, id: String): List<GroceryConsumptionEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(consumption: GroceryConsumptionEntity)
    @Query("DELETE FROM grocery_consumption_local WHERE userId = :userId AND mealId = :id") suspend fun deleteConsumption(userId: String, id: String)
}
