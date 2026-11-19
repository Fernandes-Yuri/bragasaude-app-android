package br.com.bragasaude.data.local.b2b

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import br.com.bragasaude.domain.b2b.B2BContextDescriptor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Cache local isolado no Room/SQLCipher para contextos B2B (B2B-04).
 * Vive em banco SQLCipher dedicado (`braga_b2b_context_db`), separado do
 * banco pessoal (`braga_saude_db`), garantindo isolamento fisico entre o
 * cache pessoal e os contextos de programas parceiros.
 */
@Entity(
    tableName = "b2b_context_cache",
    primaryKeys = ["ownerUserId", "contextKey"],
    indices = [Index(value = ["ownerUserId", "isActive"])]
)
data class B2BContextCacheEntity(
    val ownerUserId: String,
    val contextKey: String,
    val mode: String,
    val programId: String?,
    val programName: String?,
    val companyName: String?,
    val primaryColor: String?,
    val logoUrl: String?,
    val approvalStatus: String?,
    val sharedCategoriesJson: String = "[]",
    val isActive: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDescriptor(json: Json = DEFAULT_JSON): B2BContextDescriptor {
        val categories = runCatching {
            json.decodeFromString<List<String>>(sharedCategoriesJson)
        }.getOrDefault(emptyList())
        return B2BContextDescriptor(
            mode = mode,
            userId = ownerUserId,
            programId = programId,
            programName = programName,
            companyName = companyName,
            primaryColor = primaryColor,
            logoUrl = logoUrl,
            approvalStatus = approvalStatus,
            sharedCategories = categories
        )
    }

    companion object {
        private val DEFAULT_JSON = Json { ignoreUnknownKeys = true }

        fun fromDescriptor(
            ownerUserId: String,
            descriptor: B2BContextDescriptor,
            isActive: Boolean,
            nowMs: Long = System.currentTimeMillis(),
            json: Json = DEFAULT_JSON
        ): B2BContextCacheEntity {
            return B2BContextCacheEntity(
                ownerUserId = ownerUserId,
                contextKey = descriptor.cacheKey(),
                mode = descriptor.parsedMode.name,
                programId = descriptor.programId,
                programName = descriptor.programName,
                companyName = descriptor.companyName,
                primaryColor = descriptor.primaryColor,
                logoUrl = descriptor.logoUrl,
                approvalStatus = descriptor.approvalStatus,
                sharedCategoriesJson = json.encodeToString(descriptor.sharedCategories),
                isActive = isActive,
                updatedAt = nowMs
            )
        }
    }
}

@Dao
interface B2BContextDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<B2BContextCacheEntity>)

    @Query("SELECT * FROM b2b_context_cache WHERE ownerUserId = :ownerUserId ORDER BY updatedAt ASC")
    suspend fun getContextsForUser(ownerUserId: String): List<B2BContextCacheEntity>

    @Query("SELECT * FROM b2b_context_cache WHERE ownerUserId = :ownerUserId AND isActive = 1 LIMIT 1")
    suspend fun getActiveForUser(ownerUserId: String): B2BContextCacheEntity?

    @Query("UPDATE b2b_context_cache SET isActive = 0 WHERE ownerUserId = :ownerUserId")
    suspend fun clearActiveFlags(ownerUserId: String)

    @Query("UPDATE b2b_context_cache SET isActive = 1, updatedAt = :nowMs WHERE ownerUserId = :ownerUserId AND contextKey = :contextKey")
    suspend fun markActive(ownerUserId: String, contextKey: String, nowMs: Long = System.currentTimeMillis()): Int

    @Transaction
    suspend fun replaceUserContexts(ownerUserId: String, items: List<B2BContextCacheEntity>) {
        deleteForUser(ownerUserId)
        if (items.isNotEmpty()) {
            upsertAll(items)
        }
    }

    @Query("DELETE FROM b2b_context_cache WHERE ownerUserId = :ownerUserId")
    suspend fun deleteForUser(ownerUserId: String)

    @Query("DELETE FROM b2b_context_cache")
    suspend fun clearAll()
}

@Database(
    entities = [B2BContextCacheEntity::class],
    version = 1,
    exportSchema = false
)
abstract class B2BContextDatabase : RoomDatabase() {
    abstract fun b2bContextDao(): B2BContextDao

    companion object {
        const val DATABASE_NAME = "braga_b2b_context_db"
    }
}
