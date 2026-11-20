package br.com.bragasaude.data.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.local.FamilyMessageEntity
import br.com.bragasaude.data.local.OrbChatStore
import br.com.bragasaude.data.local.OrbConversation
import br.com.bragasaude.data.local.b2b.B2BDirectMessageEntity
import br.com.bragasaude.data.remote.b2b.B2BContextRepository
import br.com.bragasaude.data.remote.sync.DriveChatBackupWorker
import br.com.bragasaude.domain.backup.BackupB2BDirectMessage
import br.com.bragasaude.domain.backup.BackupFamilyMessage
import br.com.bragasaude.domain.backup.BackupOrbConversation
import br.com.bragasaude.domain.backup.ChatBackupCodec
import br.com.bragasaude.domain.backup.ChatBackupPayload
import br.com.bragasaude.domain.backup.ChatBackupStats
import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class ChatBackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: BragaDatabase,
    private val orbChatStore: OrbChatStore,
    private val b2bContextRepository: B2BContextRepository,
    private val driveClient: GoogleDriveAppDataClient,
    private val auth: FirebaseAuth,
    private val workManager: WorkManager
) {
    private val prefs: SharedPreferences? by lazy {
        runCatching {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }.getOrNull()
    }

    fun isAutoBackupEnabled(): Boolean =
        prefs?.getBoolean(KEY_AUTO_ENABLED, false) ?: false

    fun getLastBackupTimestampMs(): Long? =
        prefs?.getLong(KEY_LAST_BACKUP_MS, 0L)?.takeIf { it > 0L }

    fun getSavedAutoBackupPassphrase(): String? =
        prefs?.getString(KEY_AUTO_PASSPHRASE, null)?.takeIf { it.isNotBlank() }

    fun setAutoBackupConfig(enabled: Boolean, passphrase: String? = null) {
        if (enabled) {
            val clean = passphrase?.trim().orEmpty()
            ChatBackupCodec.validatePassphrase(clean)?.let { throw IllegalArgumentException(it) }
            prefs?.edit()
                ?.putBoolean(KEY_AUTO_ENABLED, true)
                ?.putString(KEY_AUTO_PASSPHRASE, clean)
                ?.apply()
            schedulePeriodicDriveBackup()
        } else {
            prefs?.edit()
                ?.putBoolean(KEY_AUTO_ENABLED, false)
                ?.remove(KEY_AUTO_PASSPHRASE)
                ?.apply()
            workManager.cancelUniqueWork(UNIQUE_DRIVE_BACKUP_WORK)
        }
    }

    fun schedulePeriodicDriveBackup() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .build()
        val request = PeriodicWorkRequestBuilder<DriveChatBackupWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
            .build()
        workManager.enqueueUniquePeriodicWork(
            UNIQUE_DRIVE_BACKUP_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun clearOnSignOut() {
        runCatching { workManager.cancelUniqueWork(UNIQUE_DRIVE_BACKUP_WORK) }
        runCatching { prefs?.edit()?.clear()?.apply() }
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
        }
    }

    suspend fun exportEncryptedBytes(passphrase: String): Result<Pair<ByteArray, ChatBackupStats>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uid = requireAuthenticatedUid()
                val family = database.familyDao().getAllActiveMessagesOneShot()
                    .filter { it.deletedAt == null }
                    .map { msg ->
                        BackupFamilyMessage(
                            id = msg.id,
                            patientUserId = msg.patientUserId,
                            senderName = msg.senderName,
                            messageText = msg.messageText,
                            iconType = msg.iconType,
                            isRead = msg.isRead,
                            sentAt = msg.sentAt,
                            senderUserId = msg.senderUserId,
                            remoteId = msg.remoteId,
                            expiresAt = msg.expiresAt,
                            awaitingDecryption = msg.awaitingDecryption
                        )
                    }
                val orb = orbChatStore.getAllForUser(uid).map { conv ->
                    BackupOrbConversation(
                        id = conv.id,
                        userId = conv.userId,
                        title = conv.title,
                        updatedAt = conv.updatedAt,
                        messagesJson = conv.messagesJson
                    )
                }
                val b2b = b2bContextRepository.getAllDirectMessagesForBackup(uid).map { item ->
                    BackupB2BDirectMessage(
                        programId = item.programId,
                        programName = item.programName,
                        primaryColor = item.primaryColor,
                        patientUserId = item.patientUserId,
                        patientName = item.patientName,
                        messageId = item.messageId,
                        senderUserId = item.senderUserId,
                        senderRole = item.senderRole,
                        body = item.body,
                        createdAt = item.createdAt,
                        createdAtMillis = item.createdAtMillis,
                        readAt = item.readAt,
                        isClosedProgram = item.isClosedProgram
                    )
                }

                val now = System.currentTimeMillis()
                val payload = ChatBackupPayload(
                    schemaVersion = 1,
                    exportedAtMillis = now,
                    ownerUserId = uid,
                    familyMessages = family,
                    orbConversations = orb,
                    b2bDirectMessages = b2b
                )
                val encrypted = ChatBackupCodec.encrypt(payload, passphrase)
                encrypted to ChatBackupStats(
                    familyCount = family.size,
                    orbConversationCount = orb.size,
                    b2bMessageCount = b2b.size,
                    timestampMillis = now
                )
            }
        }

    suspend fun restoreFromEncryptedBytes(
        rawBytes: ByteArray,
        passphrase: String
    ): Result<ChatBackupStats> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = requireAuthenticatedUid()
            val payload = ChatBackupCodec.decrypt(rawBytes, passphrase)

            val familyEntities = payload.familyMessages.map { item ->
                FamilyMessageEntity(
                    id = item.id,
                    patientUserId = item.patientUserId,
                    senderName = item.senderName,
                    messageText = item.messageText,
                    iconType = item.iconType,
                    isRead = item.isRead,
                    sentAt = item.sentAt,
                    senderUserId = item.senderUserId,
                    remoteId = item.remoteId,
                    pendingSync = false,
                    expiresAt = item.expiresAt,
                    deletedAt = null,
                    deletionAttempts = 0,
                    awaitingDecryption = item.awaitingDecryption
                )
            }
            if (familyEntities.isNotEmpty()) {
                database.familyDao().insertMessages(familyEntities)
            }

            val orbEntities = payload.orbConversations.map { item ->
                OrbConversation(
                    id = item.id,
                    userId = uid,
                    title = item.title,
                    updatedAt = item.updatedAt,
                    messagesJson = item.messagesJson
                )
            }
            if (orbEntities.isNotEmpty()) {
                orbChatStore.restoreAll(orbEntities)
            }

            val b2bEntities = payload.b2bDirectMessages.map { item ->
                B2BDirectMessageEntity(
                    ownerUserId = uid,
                    programId = item.programId,
                    programName = item.programName,
                    primaryColor = item.primaryColor,
                    patientUserId = item.patientUserId,
                    patientName = item.patientName,
                    messageId = item.messageId,
                    senderUserId = item.senderUserId,
                    senderRole = item.senderRole,
                    body = item.body,
                    createdAt = item.createdAt,
                    createdAtMillis = item.createdAtMillis,
                    readAt = item.readAt,
                    isClosedProgram = item.isClosedProgram
                )
            }
            if (b2bEntities.isNotEmpty()) {
                b2bContextRepository.restoreDirectMessagesFromBackup(b2bEntities)
            }

            ChatBackupStats(
                familyCount = familyEntities.size,
                orbConversationCount = orbEntities.size,
                b2bMessageCount = b2bEntities.size
            )
        }
    }

    suspend fun backupToGoogleDrive(
        accessToken: String,
        passphrase: String
    ): Result<ChatBackupStats> = withContext(Dispatchers.IO) {
        runCatching {
            val (bytes, stats) = exportEncryptedBytes(passphrase).getOrThrow()
            val existing = driveClient.findLatestBackupFile(accessToken)
            driveClient.uploadEncryptedBackup(accessToken, bytes, existing?.fileId)
            prefs?.edit()?.putLong(KEY_LAST_BACKUP_MS, stats.timestampMillis)?.apply()
            stats
        }
    }

    suspend fun restoreFromGoogleDrive(
        accessToken: String,
        passphrase: String
    ): Result<ChatBackupStats> = withContext(Dispatchers.IO) {
        runCatching {
            val existing = driveClient.findLatestBackupFile(accessToken)
                ?: throw IllegalStateException("Nenhum backup encontrado no seu Google Drive.")
            val bytes = driveClient.downloadBackup(accessToken, existing.fileId)
            restoreFromEncryptedBytes(bytes, passphrase).getOrThrow()
        }
    }

    private fun requireAuthenticatedUid(): String {
        val uid = auth.currentUser?.uid?.trim().orEmpty()
        check(uid.isNotEmpty() && uid != BragaConstants.GUEST_UID) {
            "Faça login na sua conta para fazer backup ou restaurar conversas."
        }
        return uid
    }

    companion object {
        const val PREFS_NAME = "braga_chat_backup_prefs"
        const val UNIQUE_DRIVE_BACKUP_WORK = "braga_drive_chat_backup"
        private const val KEY_AUTO_ENABLED = "auto_backup_enabled"
        private const val KEY_AUTO_PASSPHRASE = "auto_backup_passphrase"
        private const val KEY_LAST_BACKUP_MS = "last_backup_ms"
    }
}
