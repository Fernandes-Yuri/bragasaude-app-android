package br.com.bragasaude.data.local

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Mantém leitura separada do descarte e sincroniza as telas que usam os mesmos avisos. */
internal class AlertReadStateStore(private val preferences: SharedPreferences, private val key: String) {
    private val _readAlerts = MutableStateFlow(load())
    val readAlerts = _readAlerts.asStateFlow()
    private var activeAlerts = emptySet<String>()
    private val _identifiedAt = MutableStateFlow<Map<String, Long>>(emptyMap())
    val identifiedAt = _identifiedAt.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (changedKey == key) _readAlerts.value = load()
        if (changedKey?.startsWith("$key:identified:") == true) refreshIdentifiedAt()
    }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    fun markRead(alerts: Set<String>) {
        val updated = load() + alerts
        _readAlerts.value = updated
        preferences.edit().putStringSet(key, updated).apply()
    }

    /** Horário da primeira identificação pelo app, não o horário de um evento clínico. */
    fun identify(alerts: Set<String>, now: Long = System.currentTimeMillis()) {
        activeAlerts = alerts
        val missingKeys = alerts.map(::timestampKey).filter { preferences.getLong(it, 0L) == 0L }
        if (missingKeys.isNotEmpty()) {
            val editor = preferences.edit()
            missingKeys.forEach { editor.putLong(it, now) }
            editor.apply()
        }
        refreshIdentifiedAt()
    }

    private fun refreshIdentifiedAt() {
        _identifiedAt.value = activeAlerts.mapNotNull { alert ->
            preferences.getLong(timestampKey(alert), 0L).takeIf { it > 0 }?.let { alert to it }
        }.toMap()
    }

    private fun timestampKey(alert: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(alert.toByteArray(Charsets.UTF_8))
        return "$key:identified:" + digest.joinToString("") { "%02x".format(it) }
    }

    fun close() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun load(): Set<String> = preferences.getStringSet(key, emptySet())?.toSet().orEmpty()
}
