package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.model.BasketItemDto
import androidx.room.withTransaction
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import br.com.bragasaude.data.local.GroceryPublicationCache
import br.com.bragasaude.data.local.GroceryAdoptedPack
import br.com.bragasaude.data.remote.model.PublishedGroceryPack
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroceryRetailRepository @Inject constructor(private val db: BragaDatabase, private val api: BragaApiClient) {
    private val publicationMutex = Mutex()
    fun publications() = db.groceryPublicationDao().observe().map { cache ->
        cache?.let { PublishedGroceryPack.fromFeed(JSONObject(it.payload)) }.orEmpty()
    }
    fun adopted(userId: String) = db.groceryPublicationDao().adopted(userId).map { it?.let { row -> PublishedGroceryPack.parse(row.payload) } }
    suspend fun refreshPublications() = publicationMutex.withLock {
        val revision = api.getGroceryPublicationRevision()
        val cache = db.groceryPublicationDao().cached()
        val previous = cache?.let { JSONObject(it.payload) }
        if (previous == null || previous.getLong("revision") != revision.getLong("revision") ||
            previous.getString("business_date") != revision.getString("business_date")) {
            val feed = api.getGroceryPublications()
            PublishedGroceryPack.fromFeed(feed) // Validate the whole snapshot before replacing the cache.
            db.groceryPublicationDao().save(GroceryPublicationCache(payload = feed.toString(), fetchedAt = System.currentTimeMillis()))
        }
    }
    suspend fun adopt(userId: String, pack: PublishedGroceryPack) = db.withTransaction {
        require(pack.validToday()) { "Este pack não está mais no período de validade." }
        val current = db.groceryPublicationDao().cached()?.let { PublishedGroceryPack.fromFeed(JSONObject(it.payload)) }.orEmpty()
        require(current.any { it.publicationId == pack.publicationId && it.id == pack.id }) { "Este pack foi atualizado ou retirado. Atualize a vitrine." }
        db.groceryListDao().rewriteGroceryList(userId) { pack.listItems(userId) }
        db.groceryPublicationDao().adopt(GroceryAdoptedPack(userId, pack.rawJson, System.currentTimeMillis()))
        db.weeklyGrocerySummaryDao().clear(userId)
    }
    suspend fun clearAdopted(userId: String) = db.groceryPublicationDao().clearAdopted(userId)
    suspend fun clearAdoptedList(userId: String) = db.withTransaction {
        db.groceryListDao().clearGroceryList(userId)
        db.groceryPublicationDao().clearAdopted(userId)
        db.weeklyGrocerySummaryDao().clear(userId)
        // Purchased stock remains available independently from the shopping list.
    }

    fun observe() = db.groceryRetailCardDao().observe()
    suspend fun refresh() = db.groceryRetailCardDao().replace(api.getGroceryRetailCards())
    suspend fun compare(items: List<BasketItemDto>) = api.compareGroceryBasket(items)
}
