package br.com.bragasaude.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "wearable_readings_local", primaryKeys = ["userId", "recordKey"])
data class WearableReading(
    val userId: String,
    val recordKey: String,
    val metric: String,
    val value: Double,
    val measuredAt: Long,
    val sourcePackage: String,
    val deviceModel: String?
)

@Dao
interface WearableReadingDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(readings: List<WearableReading>)
    @Query("SELECT * FROM wearable_readings_local WHERE userId = :userId AND metric = :metric ORDER BY measuredAt DESC LIMIT 200")
    fun observeRecent(userId: String, metric: String): Flow<List<WearableReading>>
    @Query("SELECT * FROM wearable_readings_local WHERE userId = :userId AND measuredAt >= :since ORDER BY measuredAt DESC")
    suspend fun getForReport(userId: String, since: Long): List<WearableReading>
}
