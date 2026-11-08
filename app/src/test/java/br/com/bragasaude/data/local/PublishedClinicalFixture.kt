package br.com.bragasaude.data.local

import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Os testes que simulam downgrade partem de um DB novo; retiram só os objetos
 * clínicos novos antes de anunciar uma versão antiga, preservando os registros. */
internal fun restorePublishedClinicalFixture(db: SQLiteDatabase) {
    val relative = "schemas/br.com.bragasaude.data.local.BragaDatabase/66.json"
    val source = sequenceOf(File(relative), File("app/$relative")).first { it.isFile }
    val entities = JSONObject(source.readText()).getJSONObject("database").getJSONArray("entities")
    for (index in 0 until entities.length()) {
        val entity = entities.getJSONObject(index)
        val table = entity.getString("tableName")
        if (table !in setOf("vital_signs_local", "consultations_local")) continue
        val temporary = "${table}_before_fixture"
        db.execSQL("ALTER TABLE `$table` RENAME TO `$temporary`")
        db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
        val columns = mutableListOf<String>()
        db.rawQuery("PRAGMA table_info(`$table`)", null).use { cursor ->
            while (cursor.moveToNext()) columns.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
        }
        val names = columns.joinToString(",") { "`$it`" }
        db.execSQL("INSERT INTO `$table`($names) SELECT $names FROM `$temporary`")
        db.execSQL("DROP TABLE `$temporary`")
        val indices = entity.optJSONArray("indices") ?: JSONArray()
        for (i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table))
    }
    db.execSQL("DROP TABLE IF EXISTS clinical_sync_cursors")
    db.execSQL("DROP TABLE IF EXISTS family_read_receipts")
}
