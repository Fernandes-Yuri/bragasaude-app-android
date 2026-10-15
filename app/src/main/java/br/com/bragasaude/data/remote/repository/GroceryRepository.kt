package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.GroceryListDao
import br.com.bragasaude.data.local.GroceryListItemEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroceryRepository @Inject constructor(
    private val groceryListDao: GroceryListDao
) {
    fun getGroceryList(userId: String): Flow<List<GroceryListItemEntity>> =
        groceryListDao.getGroceryList(userId)

    fun getPantryItems(userId: String): Flow<List<GroceryListItemEntity>> =
        groceryListDao.getPantryItems(userId)

    suspend fun standardizeIngredients(userId: String, catalog: br.com.bragasaude.domain.GroceryIngredientCatalog) {
        groceryListDao.rewriteGroceryList(userId) { br.com.bragasaude.domain.GroceryPurchasePlanner.canonicalize(it, catalog) }
    }

    suspend fun saveGroceryList(items: List<GroceryListItemEntity>) {
        groceryListDao.insertAll(items)
    }

    suspend fun addItem(
        userId: String,
        foodName: String,
        category: String = "Feira & Mercado",
        purchaseUnitText: String = "1 un"
    ) {
        val now = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val item = GroceryListItemEntity(
            remoteId = UUID.randomUUID().toString(),
            userId = userId,
            weekStartDate = now,
            foodId = UUID.randomUUID().toString(),
            foodName = foodName,
            category = category,
            suggestedServingWeekGrams = 0,
            purchaseWeightGrams = 0,
            purchaseUnitText = purchaseUnitText,
            estimatedPriceBrl = 0.0,
            isCheckedInPantry = false,
            createdAt = System.currentTimeMillis(),
            isManual = true
        )
        groceryListDao.insertAll(listOf(item))
    }

    suspend fun putManualItem(
        userId: String,
        ingredient: br.com.bragasaude.domain.GroceryIngredient,
        amount: Int,
        category: String,
        replacingId: String? = null
    ) {
        groceryListDao.rewriteGroceryList(userId) { existing ->
            val old = existing.firstOrNull { it.remoteId == replacingId }
                ?: existing.firstOrNull { it.foodId == ingredient.slug }
            val item = GroceryListItemEntity(
                remoteId = old?.remoteId ?: UUID.randomUUID().toString(),
                userId = userId,
                weekStartDate = old?.weekStartDate ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()),
                foodId = ingredient.slug,
                foodName = ingredient.name,
                category = category,
                suggestedServingWeekGrams = 0,
                purchaseWeightGrams = if (ingredient.unit == "kg") amount else 0,
                purchaseUnitText = br.com.bragasaude.domain.GroceryPurchasePlanner.text(ingredient, amount),
                estimatedPriceBrl = br.com.bragasaude.domain.GroceryPurchasePlanner.cost(ingredient, amount),
                isManual = true,
                isCheckedInPantry = old?.takeIf { it.foodId == ingredient.slug }?.isCheckedInPantry ?: false
            )
            existing.filterNot { it.remoteId == item.remoteId || it.foodId == ingredient.slug } + item
        }
    }

    suspend fun changeCard(userId: String, card: br.com.bragasaude.data.local.GroceryRetailCardEntity, delta: Int) {
        groceryListDao.rewriteGroceryList(userId) { rows ->
            val old = rows.firstOrNull { it.retailCardId == card.id }
            check(old?.isCheckedInPantry != true) { "Desmarque o produto comprado antes de alterar a quantidade." }
            val count = ((old?.quantity ?: 0) + delta).coerceIn(0, 999)
            val kept = rows.filterNot { it.remoteId == old?.remoteId }
            if (count == 0) kept else kept + GroceryListItemEntity(
                remoteId = old?.remoteId ?: UUID.randomUUID().toString(), userId = userId,
                weekStartDate = old?.weekStartDate ?: br.com.bragasaude.domain.GroceryWeek.start(),
                foodId = card.id, foodName = card.name, category = card.category,
                suggestedServingWeekGrams = 0, purchaseWeightGrams = if (card.packageUnit == "g") (card.packageNetValue * count).toInt() else 0,
                purchaseUnitText = "$count × ${card.packageDisplay}", estimatedPriceBrl = card.priceAvg * count,
                isManual = true, retailCardId = card.id, quantity = count, packageDisplay = card.packageDisplay,
                kcalPerUnit = card.kcalPerPackage, packageNetValue = card.packageNetValue,
                packageUnit = card.packageUnit, linkedIngredientSlug = card.linkedIngredientSlug, imageUrl = card.imageUrl
            )
        }
    }

    suspend fun removeItem(userId: String, id: String) {
        groceryListDao.rewriteGroceryList(userId) { rows -> rows.filterNot { it.remoteId == id } }
    }

    suspend fun replaceList(userId: String, items: List<GroceryListItemEntity>) {
        groceryListDao.rewriteGroceryList(userId) { items }
    }

    suspend fun updateCheckedStatus(id: String, isChecked: Boolean) {
        groceryListDao.updateCheckedStatus(id, isChecked)
    }

    suspend fun clearGroceryList(userId: String) {
        groceryListDao.clearGroceryList(userId)
    }
}
