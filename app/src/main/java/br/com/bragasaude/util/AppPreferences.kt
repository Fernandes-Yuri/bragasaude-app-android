package br.com.bragasaude.util

import android.content.Context
import androidx.work.WorkManager

object AppPreferences {

    private const val BRAGA_PREFS = "braga_prefs"
    private const val BRAGA_MOVEMENT_PREFS = "braga_movement_prefs"
    private const val BRAGA_SYNC_PREFS = "braga_sync_prefs"

    private val PREFERENCE_FILES = listOf(BRAGA_PREFS, BRAGA_MOVEMENT_PREFS, BRAGA_SYNC_PREFS)

    // TASK-UI-06: preferências da assistente de voz
    const val KEY_VOICE_ASSISTANT_ENABLED = "voice_assistant_enabled"
    const val KEY_VOICE_CONFIRMATION_ENABLED = "voice_confirmation_enabled"

    fun isVoiceAssistantEnabled(context: Context): Boolean =
        context.getSharedPreferences(BRAGA_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_VOICE_ASSISTANT_ENABLED, true)

    fun setVoiceAssistantEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(BRAGA_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VOICE_ASSISTANT_ENABLED, enabled).apply()
    }

    fun isVoiceConfirmationEnabled(context: Context): Boolean =
        context.getSharedPreferences(BRAGA_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_VOICE_CONFIRMATION_ENABLED, true)

    fun setVoiceConfirmationEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(BRAGA_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_VOICE_CONFIRMATION_ENABLED, enabled).apply()
    }

    // Preferência de Tema (LIGHT, DARK, SYSTEM) — Padrão: LIGHT (Modo Claro)
    const val KEY_APP_THEME_MODE = "app_theme_mode"
    const val THEME_MODE_LIGHT = "LIGHT"
    const val THEME_MODE_DARK = "DARK"
    const val THEME_MODE_SYSTEM = "SYSTEM"

    fun getThemeMode(context: Context): String =
        context.getSharedPreferences(BRAGA_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_APP_THEME_MODE, THEME_MODE_LIGHT) ?: THEME_MODE_LIGHT

    fun setThemeMode(context: Context, mode: String) {
        context.getSharedPreferences(BRAGA_PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_APP_THEME_MODE, mode).apply()
    }

    // Preferência de Foto do Google no Avatar
    const val KEY_USE_GOOGLE_PHOTO = "use_google_photo"

    fun isUseGooglePhotoEnabled(context: Context): Boolean =
        context.getSharedPreferences(BRAGA_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_GOOGLE_PHOTO, true)

    fun setUseGooglePhotoEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(BRAGA_PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USE_GOOGLE_PHOTO, enabled).apply()
    }

    fun clearAllPreferences(context: Context) {
        for (prefName in PREFERENCE_FILES) {
            try {
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear().apply()
            } catch (e: Exception) {
                android.util.Log.w("AppPreferences", "Erro ao limpar prefs $prefName: ${e.message}")
            }
        }
    }

    fun cancelAllWorkers(context: Context) {
        try {
            WorkManager.getInstance(context).cancelAllWork()
        } catch (e: Exception) {
            android.util.Log.w("AppPreferences", "Erro ao cancelar workers: ${e.message}")
        }
    }

    fun clearDatabase(context: Context) {
        try {
            val dbPath = context.getDatabasePath("braga_saude_db").absolutePath
            context.deleteDatabase("braga_saude_db")
            android.util.Log.d("AppPreferences", "Banco Room apagado: $dbPath")
        } catch (e: Exception) {
            android.util.Log.w("AppPreferences", "Erro ao apagar banco Room: ${e.message}")
        }
    }

    fun performFullLogout(context: Context) {
        clearAllPreferences(context)
        cancelAllWorkers(context)
        clearDatabase(context)
        android.util.Log.i("AppPreferences", "Logout completo executado — prefs, workers e banco removidos.")
    }
}
