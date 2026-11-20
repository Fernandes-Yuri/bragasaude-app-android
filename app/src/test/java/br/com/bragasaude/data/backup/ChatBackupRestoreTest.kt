package br.com.bragasaude.data.backup

import android.app.Application
import androidx.room.Room
import androidx.work.WorkManager
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.local.FamilyMessageEntity
import br.com.bragasaude.data.local.OrbChatStore
import br.com.bragasaude.data.local.OrbConversation
import br.com.bragasaude.data.local.b2b.B2BContextDatabase
import br.com.bragasaude.data.local.b2b.B2BDirectMessageEntity
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.b2b.ActiveContextStore
import br.com.bragasaude.data.remote.b2b.B2BContextRepository
import br.com.bragasaude.domain.b2b.DirectChatMessageDto
import com.google.firebase.auth.FirebaseAuth
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class ChatBackupRestoreTest {

    @Test
    fun `CHAT-02 exports from populated DB and restores deterministically into empty new-device DB`() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val oldDb = Room.inMemoryDatabaseBuilder(context, BragaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val oldB2bDb = Room.inMemoryDatabaseBuilder(context, B2BContextDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val oldAuth = mockk<FirebaseAuth>(relaxed = true).apply {
            every { currentUser?.uid } returns "user-device-1"
        }
        val oldRepo = ChatBackupRepository(
            context = context,
            database = oldDb,
            orbChatStore = OrbChatStore(oldDb),
            b2bContextRepository = B2BContextRepository(
                apiClient = mockk<BragaApiClient>(relaxed = true),
                activeContextStore = ActiveContextStore(),
                b2bContextDao = oldB2bDb.b2bContextDao()
            ),
            driveClient = GoogleDriveAppDataClient(),
            auth = oldAuth,
            workManager = mockk<WorkManager>(relaxed = true)
        )

        val encryptedBytes: ByteArray
        try {
            oldDb.familyDao().insertMessages(
                listOf(
                    FamilyMessageEntity(
                        id = "fam-48h",
                        patientUserId = "patient-elder",
                        senderName = "Filho",
                        messageText = "Tomou a água da manhã?",
                        iconType = "WATER",
                        sentAt = 1_763_400_000_000L,
                        senderUserId = "user-device-1",
                        expiresAt = 1_763_486_400_000L
                    ),
                    FamilyMessageEntity(
                        id = "fam-deleted",
                        patientUserId = "patient-elder",
                        senderName = "",
                        messageText = "",
                        iconType = "LOVE",
                        deletedAt = 1_763_450_000_000L
                    )
                )
            )
            OrbChatStore(oldDb).save(
                OrbConversation(
                    id = "orb-1",
                    userId = "user-device-1",
                    title = "Consulta amanhã",
                    updatedAt = 1_763_500_000_000L,
                    messagesJson = "[{\"role\":\"user\",\"text\":\"Que horas tomar água?\"}]"
                )
            )
            oldB2bDb.b2bContextDao().upsertDirectMessages(
                listOf(
                    B2BDirectMessageEntity.fromDto(
                        ownerUserId = "user-device-1",
                        dto = DirectChatMessageDto(
                            messageId = "b2b-1",
                            programId = "prog-ativo",
                            patientUserId = "user-device-1",
                            senderUserId = "owner-1",
                            senderRole = "PARTNER",
                            body = "Mensagem da Clinica Ativa",
                            createdAt = "2026-11-15T10:00:00Z"
                        ),
                        programName = "Cardio Plus",
                        primaryColor = "#1A6B5C",
                        isClosedProgram = false
                    ),
                    B2BDirectMessageEntity.fromDto(
                        ownerUserId = "user-device-1",
                        dto = DirectChatMessageDto(
                            messageId = "b2b-closed",
                            programId = "prog-encerrado",
                            patientUserId = "user-device-1",
                            senderUserId = "owner-2",
                            senderRole = "PARTNER",
                            body = "Orientacao final programa encerrado",
                            createdAt = "2026-11-01T08:00:00Z"
                        ),
                        programName = "Fisio Encerrada",
                        primaryColor = "#0F4C81",
                        isClosedProgram = true
                    )
                )
            )

            val (bytes, exportStats) = oldRepo.exportEncryptedBytes("ChaveRecuperacao2026").getOrThrow()
            encryptedBytes = bytes
            assertEquals(1, exportStats.familyCount)
            assertEquals(1, exportStats.orbConversationCount)
            assertEquals(2, exportStats.b2bMessageCount)
        } finally {
            oldDb.close()
            oldB2bDb.close()
        }

        // Banco limpo simulando aparelho novo
        val newDb = Room.inMemoryDatabaseBuilder(context, BragaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val newB2bDb = Room.inMemoryDatabaseBuilder(context, B2BContextDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val newAuth = mockk<FirebaseAuth>(relaxed = true).apply {
            every { currentUser?.uid } returns "user-device-1"
        }
        val newB2bRepo = B2BContextRepository(
            apiClient = mockk<BragaApiClient>(relaxed = true),
            activeContextStore = ActiveContextStore(),
            b2bContextDao = newB2bDb.b2bContextDao()
        )
        val newRepo = ChatBackupRepository(
            context = context,
            database = newDb,
            orbChatStore = OrbChatStore(newDb),
            b2bContextRepository = newB2bRepo,
            driveClient = GoogleDriveAppDataClient(),
            auth = newAuth,
            workManager = mockk<WorkManager>(relaxed = true)
        )
        try {
            val stats = newRepo.restoreFromEncryptedBytes(encryptedBytes, "ChaveRecuperacao2026").getOrThrow()
            assertEquals(1, stats.familyCount)
            assertEquals(1, stats.orbConversationCount)
            assertEquals(2, stats.b2bMessageCount)

            val restoredFamily = newDb.familyDao().getRecentMessagesForPatient("patient-elder", 20).first()
            assertEquals(listOf("fam-48h"), restoredFamily.map { it.id })

            val restoredOrb = OrbChatStore(newDb).getAllForUser("user-device-1")
            assertEquals(listOf("Consulta amanhã"), restoredOrb.map { it.title })

            val restoredB2b = newB2bRepo.getAllDirectMessagesForBackup("user-device-1")
            assertEquals(2, restoredB2b.size)
            val activeClinic = restoredB2b.first { it.programId == "prog-ativo" }
            val closedClinic = restoredB2b.first { it.programId == "prog-encerrado" }
            assertEquals("Cardio Plus", activeClinic.programName)
            assertEquals("Fisio Encerrada", closedClinic.programName)
            assertTrue(closedClinic.isClosedProgram)
        } finally {
            newDb.close()
            newB2bDb.close()
        }
    }
}
