package br.com.bragasaude.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "grocery_publication_cache")
data class GroceryPublicationCache(@PrimaryKey val id: Int = 1, val payload: String, val fetchedAt: Long)

@Entity(tableName = "grocery_adopted_pack")
data class GroceryAdoptedPack(@PrimaryKey val userId: String, val payload: String, val adoptedAt: Long)

@Dao
interface GroceryPublicationDao {
    @Query("SELECT * FROM grocery_publication_cache WHERE id=1")
    fun observe(): Flow<GroceryPublicationCache?>
    @Query("SELECT * FROM grocery_publication_cache WHERE id=1")
    suspend fun cached(): GroceryPublicationCache?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(cache: GroceryPublicationCache)
    @Query("SELECT * FROM grocery_adopted_pack WHERE userId=:userId")
    fun adopted(userId: String): Flow<GroceryAdoptedPack?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun adopt(pack: GroceryAdoptedPack)
    @Query("DELETE FROM grocery_adopted_pack WHERE userId=:userId")
    suspend fun clearAdopted(userId: String)
}
