package br.com.bragasaude.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@Entity(tableName = "orb_conversations", indices = [Index("userId")])
data class OrbConversation(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val updatedAt: Long,
    val messagesJson: String
)

@Dao
abstract class OrbChatDao {
    @Query("SELECT * FROM orb_conversations WHERE userId=:uid ORDER BY updatedAt DESC")
    abstract fun observe(uid: String): Flow<List<OrbConversation>>

    @Query("SELECT * FROM orb_conversations WHERE userId=:uid ORDER BY updatedAt DESC")
    abstract suspend fun getAllForUserOneShot(uid: String): List<OrbConversation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(conversation: OrbConversation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(conversations: List<OrbConversation>)

    @Query("DELETE FROM orb_conversations WHERE id = :id AND userId = :uid")
    abstract suspend fun delete(id: String, uid: String)

    @Query("DELETE FROM orb_conversations WHERE userId = :uid")
    abstract suspend fun deleteAll(uid: String)

    @Transaction
    open suspend fun save(conversation: OrbConversation) {
        upsert(conversation)
    }
}

class OrbChatStore @Inject constructor(private val database: BragaDatabase) {
    fun observe(uid: String) = database.orbChatDao().observe(uid)
    suspend fun getAllForUser(uid: String) = database.orbChatDao().getAllForUserOneShot(uid)
    suspend fun save(conversation: OrbConversation) = database.orbChatDao().save(conversation)
    suspend fun restoreAll(conversations: List<OrbConversation>) {
        if (conversations.isNotEmpty()) database.orbChatDao().upsertAll(conversations)
    }
    suspend fun delete(id: String, uid: String) = database.orbChatDao().delete(id, uid)
    suspend fun deleteAll(uid: String) = database.orbChatDao().deleteAll(uid)
}
