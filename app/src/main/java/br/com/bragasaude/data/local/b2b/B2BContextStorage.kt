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
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import br.com.bragasaude.domain.b2b.B2BContextDescriptor
import br.com.bragasaude.domain.b2b.DirectChatMessageDto
import br.com.bragasaude.domain.b2b.ProgramExperienceConfigDto
import java.time.Instant
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
    val updatedAt: Long = System.currentTimeMillis(),
    val templateKey: String? = null,
    val experienceConfigJson: String? = null,
    val familyShareEnabled: Boolean = false
) {
    fun toDescriptor(json: Json = DEFAULT_JSON): B2BContextDescriptor {
        val categories = runCatching {
            json.decodeFromString<List<String>>(sharedCategoriesJson)
        }.getOrDefault(emptyList())
        val parsedExperience = experienceConfigJson
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching { json.decodeFromString<ProgramExperienceConfigDto>(raw) }.getOrNull()
            }
        return B2BContextDescriptor(
            mode = mode,
            userId = ownerUserId,
            programId = programId,
            programName = programName,
            companyName = companyName,
            primaryColor = primaryColor,
            logoUrl = logoUrl,
            approvalStatus = approvalStatus,
            sharedCategories = categories,
            templateKey = templateKey,
            experienceConfig = parsedExperience,
            familyShareEnabled = familyShareEnabled
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
            val encodedExperience = descriptor.experienceConfig?.let {
                runCatching { json.encodeToString(it) }.getOrNull()
            }
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
                updatedAt = nowMs,
                templateKey = descriptor.templateKey,
                experienceConfigJson = encodedExperience,
                familyShareEnabled = descriptor.familyShareEnabled
            )
        }
    }
}

/**
 * Persistencia local isolada de mensagens diretas B2B no SQLCipher (`braga_b2b_context_db`),
 * permitindo manter historico >7 dias no aparelho apos o expurgo do buffer de relay do RDS (CHAT-01)
 * e identificar `programId` / `programName` / marca por thread no backup (CHAT-02).
 */
@Entity(
    tableName = "b2b_direct_messages_local",
    primaryKeys = ["ownerUserId", "programId", "patientUserId", "messageId"],
    indices = [Index(value = ["ownerUserId", "programId", "patientUserId", "createdAtMillis"])]
)
data class B2BDirectMessageEntity(
    val ownerUserId: String,
    val programId: String,
    val programName: String = "",
    val primaryColor: String? = null,
    val patientUserId: String,
    val patientName: String? = null,
    val messageId: String,
    val senderUserId: String,
    val senderRole: String,
    val body: String,
    val createdAt: String,
    val createdAtMillis: Long,
    val readAt: String? = null,
    val isClosedProgram: Boolean = false
) {
    fun toDto(): DirectChatMessageDto = DirectChatMessageDto(
        messageId = messageId,
        programId = programId,
        patientUserId = patientUserId,
        senderUserId = senderUserId,
        senderRole = senderRole,
        body = body,
        createdAt = createdAt,
        readAt = readAt
    )

    companion object {
        fun parseIsoMillisOrNow(raw: String): Long =
            runCatching { Instant.parse(raw.trim()).toEpochMilli() }
                .getOrElse { System.currentTimeMillis() }

        fun fromDto(
            ownerUserId: String,
            dto: DirectChatMessageDto,
            programName: String,
            primaryColor: String?,
            patientName: String? = null,
            isClosedProgram: Boolean = false
        ): B2BDirectMessageEntity = B2BDirectMessageEntity(
            ownerUserId = ownerUserId,
            programId = dto.programId,
            programName = programName,
            primaryColor = primaryColor,
            patientUserId = dto.patientUserId,
            patientName = patientName,
            messageId = dto.messageId,
            senderUserId = dto.senderUserId,
            senderRole = dto.senderRole,
            body = dto.body,
            createdAt = dto.createdAt,
            createdAtMillis = parseIsoMillisOrNow(dto.createdAt),
            readAt = dto.readAt,
            isClosedProgram = isClosedProgram
        )
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDirectMessages(items: List<B2BDirectMessageEntity>)

    @Query(
        "SELECT * FROM b2b_direct_messages_local " +
            "WHERE ownerUserId = :ownerUserId AND programId = :programId AND patientUserId = :patientUserId " +
            "ORDER BY createdAtMillis DESC, messageId DESC"
    )
    suspend fun getDirectMessagesForThread(
        ownerUserId: String,
        programId: String,
        patientUserId: String
    ): List<B2BDirectMessageEntity>

    @Query(
        "SELECT * FROM b2b_direct_messages_local " +
            "WHERE ownerUserId = :ownerUserId " +
            "ORDER BY programId ASC, patientUserId ASC, createdAtMillis ASC, messageId ASC"
    )
    suspend fun getAllDirectMessagesForUser(ownerUserId: String): List<B2BDirectMessageEntity>

    @Query("DELETE FROM b2b_direct_messages_local")
    suspend fun clearAllDirectMessages()
}

@Database(
    entities = [B2BContextCacheEntity::class, B2BDirectMessageEntity::class],
    version = 3,
    exportSchema = false
)
abstract class B2BContextDatabase : RoomDatabase() {
    abstract fun b2bContextDao(): B2BContextDao

    companion object {
        const val DATABASE_NAME = "braga_b2b_context_db"

        /**
         * Migracao aditiva v1 -> v2 (B2B-16): preserva cache local pre-existente ao gravar
         * template_key, experience_config jsonb e opt-in familiar sem destruir dados.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE b2b_context_cache ADD COLUMN templateKey TEXT")
                db.execSQL("ALTER TABLE b2b_context_cache ADD COLUMN experienceConfigJson TEXT")
                db.execSQL("ALTER TABLE b2b_context_cache ADD COLUMN familyShareEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migracao aditiva v2 -> v3 (CHAT-01 / CHAT-02): adiciona historico local de mensagens diretas
         * B2B isolado para retencao >7 dias no aparelho e backup/restauracao multi-clinica.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `b2b_direct_messages_local` (" +
                        "`ownerUserId` TEXT NOT NULL, " +
                        "`programId` TEXT NOT NULL, " +
                        "`programName` TEXT NOT NULL, " +
                        "`primaryColor` TEXT, " +
                        "`patientUserId` TEXT NOT NULL, " +
                        "`patientName` TEXT, " +
                        "`messageId` TEXT NOT NULL, " +
                        "`senderUserId` TEXT NOT NULL, " +
                        "`senderRole` TEXT NOT NULL, " +
                        "`body` TEXT NOT NULL, " +
                        "`createdAt` TEXT NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, " +
                        "`readAt` TEXT, " +
                        "`isClosedProgram` INTEGER NOT NULL DEFAULT 0, " +
                        "PRIMARY KEY(`ownerUserId`, `programId`, `patientUserId`, `messageId`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_b2b_direct_messages_local_ownerUserId_programId_patientUserId_createdAtMillis` " +
                        "ON `b2b_direct_messages_local` (`ownerUserId`, `programId`, `patientUserId`, `createdAtMillis`)"
                )
            }
        }
    }
}
