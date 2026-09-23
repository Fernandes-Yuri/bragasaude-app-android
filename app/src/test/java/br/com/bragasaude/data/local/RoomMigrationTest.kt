package br.com.bragasaude.data.local

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RoomMigrationTest {
    @Test
    fun `database advances from 47 to 48 and registers migration`() {
        assertEquals(47, Migrations.MIGRATION_47_48.startVersion)
        assertEquals(48, Migrations.MIGRATION_47_48.endVersion)
        assertTrue(Migrations.ALL.contains(Migrations.MIGRATION_47_48))

        // Room's @Database has BINARY retention and is not available through
        // JVM reflection. The exported schema is Room's authoritative build
        // artifact for the actual database version.
        val schema = findSchema48()
        assertTrue("Schema 48.json deve estar exportado", schema != null)
        assertTrue(schema!!.readText().contains("\"version\": 48"))
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
        val schema = findSchema48()
        assertTrue("Schema 48.json deve estar exportado", schema != null)
        val text = schema!!.readText()
        listOf(
            "eanBarcode", "currentUnits", "idempotencyKey",
            "symptoms_diary_local", "care_audit_local", "ble_telemetry_receipts_local"
        ).forEach { assertTrue("Schema v48 sem $it", text.contains(it)) }
    }

    private fun findSchema48(): File? {
        val relative = "schemas/br.com.bragasaude.data.local.BragaDatabase/48.json"
        return sequenceOf(File(relative), File("app/$relative")).firstOrNull(File::isFile)
    }
}
