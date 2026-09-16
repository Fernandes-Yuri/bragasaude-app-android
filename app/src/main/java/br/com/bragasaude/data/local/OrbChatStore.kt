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
    @Query("SELECT * FROM orb_conversations WHERE userId=:uid ORDER BY updatedAt DESC LIMIT 20")
    abstract fun observe(uid: String): Flow<List<OrbConversation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(conversation: OrbConversation)

    @Query("DELETE FROM orb_conversations WHERE userId=:uid AND id NOT IN (SELECT id FROM orb_conversations WHERE userId=:uid ORDER BY updatedAt DESC LIMIT 20)")
    abstract suspend fun prune(uid: String)

    @Query("DELETE FROM orb_conversations WHERE id = :id AND userId = :uid")
    abstract suspend fun delete(id: String, uid: String)

    @Query("DELETE FROM orb_conversations WHERE userId = :uid")
    abstract suspend fun deleteAll(uid: String)

    @Transaction
    open suspend fun save(conversation: OrbConversation) {
        upsert(conversation)
        prune(conversation.userId)
    }
}

class OrbChatStore @Inject constructor(private val database: BragaDatabase) {
    fun observe(uid: String) = database.orbChatDao().observe(uid)
    suspend fun save(conversation: OrbConversation) = database.orbChatDao().save(conversation)
    suspend fun delete(id: String, uid: String) = database.orbChatDao().delete(id, uid)
    suspend fun deleteAll(uid: String) = database.orbChatDao().deleteAll(uid)
}
