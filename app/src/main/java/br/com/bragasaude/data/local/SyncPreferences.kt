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

    fun shouldSync(userId: String, maxAgeHours: Long = 12): Boolean {
        val lastSync = getLastSyncTime(userId)
        if (lastSync == 0L) return true
        val maxAgeMillis = maxAgeHours * 60 * 60 * 1000L
        val elapsed = System.currentTimeMillis() - lastSync
        return elapsed >= maxAgeMillis
    }

    fun recordSyncSuccess(userId: String) {
        prefs.edit()
            .putLong("last_sync_$userId", System.currentTimeMillis())
            .apply()
    }

    fun clear(userId: String) {
        prefs.edit().remove("last_sync_$userId").apply()
    }
}
