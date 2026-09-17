package br.com.bragasaude.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("braga_sync_prefs", Context.MODE_PRIVATE)

    fun getLastSyncTime(userId: String): Long {
        return prefs.getLong("last_sync_$userId", 0L)
    }

    /** Retorna o último timestamp sincronizado de uma entidade específica (ex: vitals, metrics). */
    fun getEntityLastSync(userId: String, entityKey: String): Long {
        return prefs.getLong("last_sync_${entityKey}_$userId", 0L)
    }

    fun recordEntitySyncSuccess(userId: String, entityKey: String, timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("last_sync_${entityKey}_$userId", timestamp).apply()
    }

    fun shouldSync(userId: String, maxAgeHours: Long = 4): Boolean {
        val lastSync = getLastSyncTime(userId)
        if (lastSync == 0L) return true
        val maxAgeMillis = maxAgeHours * 60 * 60 * 1000L
        return (System.currentTimeMillis() - lastSync) >= maxAgeMillis
    }

    fun recordSyncSuccess(userId: String) {
        prefs.edit()
            .putLong("last_sync_$userId", System.currentTimeMillis())
            .apply()
    }

    fun clear(userId: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.endsWith(userId) }.forEach { key ->
            editor.remove(key)
        }
        editor.apply()
    }
}
