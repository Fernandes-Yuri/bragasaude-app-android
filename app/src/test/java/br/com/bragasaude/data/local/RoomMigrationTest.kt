package br.com.bragasaude.data.local

import androidx.room.Database
import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoomMigrationTest {
    @Test
    fun `database advances from 47 to 48 and registers migration`() {
        val annotation = BragaDatabase::class.java.getAnnotation(Database::class.java)
        assertEquals(48, annotation.version)
        assertEquals(47, Migrations.MIGRATION_47_48.startVersion)
        assertEquals(48, Migrations.MIGRATION_47_48.endVersion)
        assertTrue(Migrations.ALL.contains(Migrations.MIGRATION_47_48))
    }

    @Test
    fun `migration creates all Care OS columns tables and indexes`() {
        val database = mockk<SupportSQLiteDatabase>()
        val sql = mutableListOf<String>()
        every { database.execSQL(capture(sql)) } just runs

        Migrations.MIGRATION_47_48.migrate(database)

        assertTrue(sql.any { it.contains("ALTER TABLE medications_local ADD COLUMN currentUnits") })
        assertTrue(sql.any { it.contains("ALTER TABLE medication_logs_local ADD COLUMN idempotencyKey") })
        assertTrue(sql.any { it.contains("CREATE TABLE IF NOT EXISTS symptoms_diary_local") })
        assertTrue(sql.any { it.contains("CREATE TABLE IF NOT EXISTS care_audit_local") })
        assertTrue(sql.any { it.contains("CREATE TABLE IF NOT EXISTS ble_telemetry_receipts_local") })
        assertTrue(sql.any { it.contains("index_medication_logs_local_userId_idempotencyKey") })
        assertEquals(20, sql.size)
    }

    @Test
    fun `exported schema 48 contains the Care OS contract`() {
        val relative = "schemas/br.com.bragasaude.data.local.BragaDatabase/48.json"
        val schema = sequenceOf(File(relative), File("app/$relative")).firstOrNull(File::isFile)
        assertTrue("Schema 48.json deve estar exportado", schema != null)
        val text = schema!!.readText()
        listOf(
            "eanBarcode", "currentUnits", "idempotencyKey",
            "symptoms_diary_local", "care_audit_local", "ble_telemetry_receipts_local"
        ).forEach { assertTrue("Schema v48 sem $it", text.contains(it)) }
    }
}
