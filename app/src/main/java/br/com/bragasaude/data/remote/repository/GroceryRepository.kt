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
            createdAt = System.currentTimeMillis()
        )
        groceryListDao.insertAll(listOf(item))
    }

    suspend fun updateCheckedStatus(id: String, isChecked: Boolean) {
        groceryListDao.updateCheckedStatus(id, isChecked)
    }

    suspend fun clearGroceryList(userId: String) {
        groceryListDao.clearGroceryList(userId)
    }
}
