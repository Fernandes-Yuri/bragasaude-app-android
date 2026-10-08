package br.com.bragasaude.data.remote.repository

import androidx.room.withTransaction
import br.com.bragasaude.data.local.*
import br.com.bragasaude.domain.*
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeeklyGrocerySummaryRepository @Inject constructor(private val database: BragaDatabase) {
    private val summaries get() = database.weeklyGrocerySummaryDao()
    private val groceries get() = database.groceryListDao()
    private val pantry get() = database.groceryPantryDao()

    fun observe(userId: String, week: String) = combine(summaries.observe(userId, week), groceries.getGroceryList(userId)) { summary, items ->
        summary?.result(items)
    }
    fun stock(userId: String) = pantry.observe(userId)
    fun meals(userId: String, day: String) = pantry.meals(userId, day)

    suspend fun save(userId: String, plan: WeeklyGroceryPlanResult, preserveManual: Boolean) = database.withTransaction {
        val existing = groceries.getGrocerySnapshot(userId)
        val manual = if (preserveManual) existing.filter { it.isManual } else emptyList()
        val manualSlugs = manual.map { it.foodId }.toSet()
        val generated = plan.items.filterNot { it.foodId in manualSlugs }.map { row ->
            val old = existing.firstOrNull { it.foodId == row.foodId }
            row.copy(isCheckedInPantry = old?.isCheckedInPantry == true)
        }
        val result = plan.copy(items = generated + manual,
            isManuallyModified = manual.isNotEmpty(),
            statusMessage = if (manual.isEmpty()) plan.statusMessage else "Itens manuais preservados. Cobertura anterior não validada para a lista modificada.")
        groceries.rewriteGroceryList(userId) { result.items }
        summaries.upsert(WeeklyGrocerySummaryEntity.from(userId, GroceryWeek.start(), result))
    }

    suspend fun invalidate(userId: String, message: String) = summaries.invalidate(userId, GroceryWeek.start(), message)
    suspend fun clear(userId: String) = database.withTransaction {
        groceries.clearGroceryList(userId)
        summaries.clear(userId)
    }

    suspend fun check(userId: String, id: String, checked: Boolean, catalog: GroceryIngredientCatalog) = database.withTransaction {
        val item = groceries.getGrocerySnapshot(userId).firstOrNull { it.remoteId == id } ?: return@withTransaction
        groceries.updateCheckedStatus(id, checked)
        if (checked) {
            val ingredient = catalog.ingredients.firstOrNull { it.slug == item.foodId } ?: return@withTransaction
            // Marcar novamente não reabastece um estoque já acompanhado.
            if (pantry.stock(userId, item.foodId) == null) {
                val amount = GroceryPurchasePlanner.parseAmount(item.purchaseUnitText.substringBefore(' '), ingredient.unit)
                    ?: item.purchaseWeightGrams.takeIf { ingredient.unit == "kg" && it > 0 }
                // Texto em kg é exibido em gramas; parseAmount recebe kg na montagem manual.
                val internalAmount = if (ingredient.unit == "kg") item.purchaseWeightGrams else amount ?: 0
                pantry.upsert(GroceryPantryStockEntity(userId, item.foodId, ingredient.unit, internalAmount.toDouble()))
            }
        }
    }

    private suspend fun undo(userId: String, mealId: String) {
        pantry.consumption(userId, mealId).forEach { entry ->
            pantry.stock(userId, entry.ingredientSlug)?.let { stock ->
                pantry.upsert(stock.copy(availableAmount = stock.availableAmount + entry.consumedAmount))
            }
        }
        pantry.deleteConsumption(userId, mealId)
    }

    suspend fun log(meal: GroceryMealEntity, catalog: GroceryIngredientCatalog): List<String> = database.withTransaction {
        undo(meal.userId, meal.id)
        pantry.upsert(meal)
        val conversion = GroceryConsumption.amounts(meal.foodId, meal.foodName, meal.portionGrams.toDouble(), catalog)
        val checked = groceries.getGrocerySnapshot(meal.userId).filter { it.isCheckedInPantry }.map { it.foodId }.toSet()
        val limitations = conversion.limitations.toMutableList()
        conversion.amounts.forEach { (slug, amount) ->
            if (slug in checked) {
                val stock = pantry.stock(meal.userId, slug)
                if (stock != null) {
                    val consumed = minOf(amount, stock.availableAmount).coerceAtLeast(0.0)
                    pantry.upsert(stock.copy(availableAmount = (stock.availableAmount - consumed).coerceAtLeast(0.0)))
                    pantry.upsert(GroceryConsumptionEntity(meal.id, slug, meal.userId, consumed))
                    if (consumed < amount) limitations.add("Saldo insuficiente na despensa para ${catalog.ingredients.first { it.slug == slug }.name}.")
                }
            }
        }
        limitations
    }

    suspend fun removeMeal(userId: String, id: String) = database.withTransaction {
        undo(userId, id)
        pantry.deleteMeal(userId, id)
    }

    suspend fun updatePortion(userId: String, id: String, grams: Int, catalog: GroceryIngredientCatalog): List<String> = database.withTransaction {
        val meal = pantry.meal(userId, id) ?: return@withTransaction emptyList<String>()
        val portion = grams.coerceIn(1, 2000)
        log(meal.copy(portionGrams = portion, kcal = meal.kcal / meal.portionGrams * portion), catalog)
    }
}
