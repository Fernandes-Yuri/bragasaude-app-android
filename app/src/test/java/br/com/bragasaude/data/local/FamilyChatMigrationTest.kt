package br.com.bragasaude.data.local

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class FamilyChatMigrationTest {
    @Test fun schema65PreservesMessagesAndInitializesDecryptionState() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "family-chat-65.db"
        context.deleteDatabase(name)
        val relative = "schemas/br.com.bragasaude.data.local.BragaDatabase/65.json"
        val source = sequenceOf(File(relative),File("app/$relative")).first { it.isFile }
        val entities = JSONObject(source.readText()).getJSONObject("database").getJSONArray("entities")
        try {
            context.openOrCreateDatabase(name,0,null).use { old ->
                repeat(entities.length()) { i ->
                    val entity = entities.getJSONObject(i)
                    val table = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}",table))
                    val indices = entity.optJSONArray("indices") ?: org.json.JSONArray()
                    repeat(indices.length()) { j ->
                        old.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}",table))
                    }
                }
                old.execSQL("""INSERT INTO family_messages_local
                    (id,patientUserId,senderName,messageText,iconType,isRead,sentAt,pendingSync,expiresAt,deletionAttempts)
                    VALUES ('preserved','patient','Familiar','Recado local','CUSTOM',0,1,1,9999999999999,0)""")
                old.version = 65
            }
            val db = Room.databaseBuilder(context,BragaDatabase::class.java,name)
                .allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
            try {
                val message = requireNotNull(db.familyDao().getMessageById("preserved"))
                assertEquals("Recado local",message.messageText)
                assertTrue(message.pendingSync)
                assertFalse(message.awaitingDecryption)
                db.familyDao().insertMessage(message.copy(awaitingDecryption=true))
                assertTrue(requireNotNull(db.familyDao().getMessageById("preserved")).awaitingDecryption)
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }
    @Test fun schema66PreservesPendingWaterAndLabelsLegacyConsultationForReview() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "clinical-66.db"
        context.deleteDatabase(name)
        val relative = "schemas/br.com.bragasaude.data.local.BragaDatabase/66.json"
        val source = sequenceOf(File(relative), File("app/$relative")).first { it.isFile }
        val entities = JSONObject(source.readText()).getJSONObject("database").getJSONArray("entities")
        val future = System.currentTimeMillis()+86400000
        try {
            context.openOrCreateDatabase(name, 0, null).use { old ->
                repeat(entities.length()) { index ->
                    val table = entities.getJSONObject(index)
                    val tableName = table.getString("tableName")
                    old.execSQL(table.getString("createSql").replace("\${TABLE_NAME}", tableName))
                    val indexes = table.optJSONArray("indices") ?: org.json.JSONArray()
                    repeat(indexes.length()) { j -> old.execSQL(indexes.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName)) }
                }
                old.execSQL("INSERT INTO vital_signs_local(localId,userId,hydrationMl,measuredAt,status,pendingSync) VALUES(1,'patient',250,1,'recorded',1)")
                old.execSQL("INSERT INTO consultations_local(id,userId,title,scheduledDate,status,createdAt,updatedAt,pendingSync) VALUES('legacy','patient','Consulta',$future,'ACCEPTED',1,1,0)")
                old.version = 66
            }
            val db = Room.databaseBuilder(context, BragaDatabase::class.java, name).allowMainThreadQueries().addMigrations(*Migrations.ALL).build()
            try {
                assertEquals(250, db.vitalSignDao().getPendingSync().single().hydrationMl)
                assertFalse(db.vitalSignDao().getPendingSync().single().hydrationUndoPending)
                val legacy = db.familyDao().localConsultationsToReview().single()
                assertEquals("legacy", legacy.id)
                assertFalse(legacy.remoteConfirmed)
                assertFalse(legacy.pendingSync)
                assertNull(db.vitalSignDao().getCursor("patient", "patient"))
            } finally { db.close() }
        } finally { context.deleteDatabase(name) }
    }

}
