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
    val statusMessage: String,
    val profileWeight: Double? = null
) {
    fun result(items: List<GroceryListItemEntity>): WeeklyGroceryPlanResult {
        val values = JSONArray(limitationsJson)
        val legacyStrings = mutableListOf<String>()
        val structured = mutableListOf<br.com.bragasaude.domain.GroceryLimitation>()
        for (i in 0 until values.length()) {
            val elem = values.get(i)
            if (elem is org.json.JSONObject) {
                val typeStr = elem.optString("type", br.com.bragasaude.domain.GroceryLimitationType.MISSING_YIELD.name)
                val type = try { br.com.bragasaude.domain.GroceryLimitationType.valueOf(typeStr) } catch (_: Exception) { br.com.bragasaude.domain.GroceryLimitationType.MISSING_YIELD }
                val impactStr = elem.optString("impact", br.com.bragasaude.domain.GroceryCalculationImpact.APPROXIMATED.name)
                val impact = try { br.com.bragasaude.domain.GroceryCalculationImpact.valueOf(impactStr) } catch (_: Exception) { br.com.bragasaude.domain.GroceryCalculationImpact.APPROXIMATED }
                val summary = elem.optString("userSummary", "")
                val itemsArr = elem.optJSONArray("affectedItems")
                val affected = if (itemsArr != null) (0 until itemsArr.length()).map { itemsArr.getString(it) } else emptyList()
                structured.add(br.com.bragasaude.domain.GroceryLimitation(type, affected, impact, summary))
                legacyStrings.add(summary.ifBlank { "${type.userTitle}: ${affected.joinToString(", ")}" })
            } else {
                val text = elem.toString()
                legacyStrings.add(text)
                structured.add(br.com.bragasaude.domain.GroceryLimitation(br.com.bragasaude.domain.GroceryLimitationType.MISSING_YIELD, emptyList(), br.com.bragasaude.domain.GroceryCalculationImpact.APPROXIMATED, text))
            }
        }
        val purchaseStatus = when {
            structured.any { it.impact == br.com.bragasaude.domain.GroceryCalculationImpact.NOT_CALCULABLE } ->
                br.com.bragasaude.domain.PurchaseCalculationStatus.INCOMPLETE
            structured.any { it.impact == br.com.bragasaude.domain.GroceryCalculationImpact.APPROXIMATED } ->
                br.com.bragasaude.domain.PurchaseCalculationStatus.APPROXIMATED
            else ->
                br.com.bragasaude.domain.PurchaseCalculationStatus.CALCULABLE
        }
        return WeeklyGroceryPlanResult(
            items = items,
            targetWeeklyCalories = targetWeeklyCalories,
            plannedWeeklyCalories = plannedWeeklyCalories,
            coveragePercent = coveragePercent,
            plannedProteinGrams = plannedProteinGrams,
            plannedCarbsGrams = plannedCarbsGrams,
            plannedFatGrams = plannedFatGrams,
            foodVarietyCount = foodVarietyCount,
            limitations = legacyStrings,
            structuredLimitations = structured,
            purchaseStatus = purchaseStatus,
            statusMessage = statusMessage,
            isManuallyModified = isManuallyModified,
            profileWeight = profileWeight
        )
    }
    companion object {
        fun from(userId: String, week: String, result: WeeklyGroceryPlanResult): WeeklyGrocerySummaryEntity {
            val jsonArr = JSONArray()
            if (result.structuredLimitations.isNotEmpty()) {
                result.structuredLimitations.forEach { lim ->
                    val obj = org.json.JSONObject()
                    obj.put("type", lim.type.name)
                    obj.put("impact", lim.impact.name)
                    obj.put("userSummary", lim.userSummary)
                    val itemsArr = JSONArray()
                    lim.affectedItems.forEach { itemsArr.put(it) }
                    obj.put("affectedItems", itemsArr)
                    jsonArr.put(obj)
                }
            } else {
                result.limitations.forEach { jsonArr.put(it) }
            }
            return WeeklyGrocerySummaryEntity(
                userId = userId,
                weekStartDate = week,
                targetWeeklyCalories = result.targetWeeklyCalories,
                plannedWeeklyCalories = result.plannedWeeklyCalories,
                coveragePercent = result.coveragePercent,
                plannedProteinGrams = result.plannedProteinGrams,
                plannedCarbsGrams = result.plannedCarbsGrams,
                plannedFatGrams = result.plannedFatGrams,
                foodVarietyCount = result.foodVarietyCount,
                limitationsJson = jsonArr.toString(),
                isManuallyModified = result.isManuallyModified,
                generatedAt = System.currentTimeMillis(),
                statusMessage = result.statusMessage,
                profileWeight = result.profileWeight
            )
        }
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
