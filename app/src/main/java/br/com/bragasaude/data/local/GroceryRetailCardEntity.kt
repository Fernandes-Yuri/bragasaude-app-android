package br.com.bragasaude.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "grocery_retail_cards_local")
data class GroceryRetailCardEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val imageUrl: String,
    val packageDisplay: String,
    val packageNetValue: Double,
    val packageUnit: String,
    val kcalPerPackage: Double,
    val carbsG: Double,
    val proteinG: Double,
    val fatG: Double,
    val priceAvg: Double,
    val linkedIngredientSlug: String?,
    val marketsJson: String,
    val clinicalTagsJson: String,
    val fiberG: Double? = null,
    @ColumnInfo(defaultValue = "'[]'") val nutritionFactsJson: String = "[]",
    val imageCredit: String? = null,
    val imageCreditUrl: String? = null
)

@Dao
interface GroceryRetailCardDao {
    @Query("SELECT * FROM grocery_retail_cards_local ORDER BY category, name")
    fun observe(): Flow<List<GroceryRetailCardEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cards: List<GroceryRetailCardEntity>)
    @Query("DELETE FROM grocery_retail_cards_local")
    suspend fun clear()
    @Transaction
    suspend fun replace(cards: List<GroceryRetailCardEntity>) {
        clear()
        insert(cards)
    }
}
