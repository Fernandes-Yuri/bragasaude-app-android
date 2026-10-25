package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.remote.api.BragaApiClient
import androidx.room.withTransaction
import kotlinx.coroutines.flow.map
import br.com.bragasaude.data.remote.model.PublishedGroceryPack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroceryRetailRepository @Inject constructor(private val db: BragaDatabase, private val api: BragaApiClient) {
    // Kept only to read/clear legacy adoption metadata without rewriting existing lists.
    fun adopted(userId: String) = db.groceryPublicationDao().adopted(userId).map { it?.let { row -> PublishedGroceryPack.parse(row.payload) } }
    suspend fun clearAdopted(userId: String) = db.groceryPublicationDao().clearAdopted(userId)
    suspend fun clearAdoptedList(userId: String) = db.withTransaction {
        db.groceryListDao().clearGroceryList(userId)
        db.groceryPublicationDao().clearAdopted(userId)
        db.weeklyGrocerySummaryDao().clear(userId)
        // Purchased stock remains available independently from the shopping list.
    }

    suspend fun compareBasket(items: List<br.com.bragasaude.data.remote.model.GroceryBasketItem>) = api.compareGroceryBasket(items)

    fun observe() = db.groceryRetailCardDao().observe()
    suspend fun refresh() = db.groceryRetailCardDao().replace(api.getGroceryRetailCards())
}
