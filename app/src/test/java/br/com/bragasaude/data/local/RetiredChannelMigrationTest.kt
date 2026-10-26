package br.com.bragasaude.data.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, application = Application::class)
class RetiredChannelMigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val name = "retired-channel-migration.db"
    private fun open() = Room.databaseBuilder(context, BragaDatabase::class.java, name)
        .allowMainThreadQueries().addMigrations(*Migrations.ALL).build()

    @After fun cleanup() { context.deleteDatabase(name) }

    @Test fun upgradeRemovesChannelColumnsAndPreservesLocalProfile() = runBlocking<Unit> {
        context.deleteDatabase(name)
        val profile = ProfileEntity(userId = "test-user", fullName = "Perfil de teste",
            pendingSync = true, communityNickname = "Comunidade",
            emergencyContactPhone = "synthetic", customPhotoUri = "content://test/avatar")
        val initial = open()
        try { initial.profileDao().insert(profile) } finally { initial.close() }
        // Reconstitui a estrutura 58 da tabela, com os dois campos antigos.
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            db.execSQL("ALTER TABLE profiles_local ADD COLUMN whatsappTotpSecret TEXT")
            db.execSQL("ALTER TABLE profiles_local ADD COLUMN whatsappPhone TEXT")
            db.execSQL("UPDATE profiles_local SET whatsappTotpSecret='synthetic', whatsappPhone='synthetic'")
            db.execSQL("ALTER TABLE profiles_local ADD COLUMN dailyCalorieTarget REAL")
            db.version = 58
        }
        val db = open()
        try {
            assertEquals(profile, db.profileDao().getProfileOneShot("test-user"))
            val columns = mutableSetOf<String>()
            db.openHelper.readableDatabase.query("PRAGMA table_info(profiles_local)").use { cursor ->
                while (cursor.moveToNext()) columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertFalse(columns.contains("whatsappTotpSecret"))
            assertFalse(columns.contains("whatsappPhone"))
            assertTrue(columns.contains("emergencyContactPhone"))
        } finally { db.close() }
    }
}
