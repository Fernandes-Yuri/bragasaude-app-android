package br.com.bragasaude.data.local

import android.content.Context

/** Remove somente os caches e as preferências do módulo encerrado. */
object RetiredNutritionCleanup {
    fun run(context: Context) {
        context.getSharedPreferences("remote_nutrition_catalog", Context.MODE_PRIVATE).edit().clear().apply()
        val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
        val prefixes = listOf("nutrition_goal_", "nutrition_activity_", "calorie_target_is_custom_",
            "calorie_target_manual_kcal_", "weekly_budget_tier_", "weekly_proteins_",
            "weekly_has_staples_", "weekly_max_budget_")
        val editor = prefs.edit()
        prefs.all.keys.filter { key -> prefixes.any { key.startsWith(it) } }.forEach { editor.remove(it) }
        editor.apply()
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.cancel(2003)
        if (android.os.Build.VERSION.SDK_INT >= 26) manager.deleteNotificationChannel("bragasaude_grocery")
    }
}
