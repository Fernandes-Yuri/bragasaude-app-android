package br.com.bragasaude.ui.util

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Fabricantes (Xiaomi/MIUI, Samsung OneUI, Motorola, Huawei) matam apps em
 * segundo plano de forma agressiva quando o usuario fecha o app ou a bateria
 * esta em modo de economia. O push chega, mas o Android nao acorda o processo
 * para desenhar a notificacao.
 *
 * Nao existe API para forcar; o caminho eh orientar o usuario a liberar:
 *  - "Ignorar otimizacao de bateria" (API oficial, todo Android 6+)
 *  - "Inicio automatico" (Xiaomi/MIUI — settings especifica)
 *  - "Apps suspensos" (Samsung OneUI)
 *
 * A orientacao so aparece QUANDO necessario: se a bateria ja esta ignorando
 * otimizacao, nada eh mostrado.
 */
object BatteryOptimizationHelper {

    /** Verdadeiro se o app ja esta liberado da otimizacao de bateria. */
    fun estaLiberado(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Abre a tela de "Ignorar otimizacao de bateria" do sistema.
     * Intent oficial — funciona em qualquer Android 6+.
     */
    fun pedirIgnorarOtimizacao(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fabricante customizou/removel a intent: cai na tela generica.
            abrirConfigBateria(context)
        }
    }

    /**
     * Lista de fabricantes conhecidos por matar apps em background. Usa
     * Build.MANUFACTURER (nao requer permissao).
     */
    fun fabricanteAgressivo(): Boolean {
        val m = (Build.MANUFACTURER ?: "").lowercase()
        return m.contains("xiaomi") || m.contains("redmi") ||
               m.contains("samsung") || m.contains("motorola") ||
               m.contains("huawei") || m.contains("oppo") ||
               m.contains("vivo") || m.contains("realme")
    }

    /** Tela generica de bateria (fallback). */
    private fun abrirConfigBateria(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) { }
        }
    }

    /**
     * Xiaomi/MIUI: "Inicio automatico". Nao ha intent oficial; este e o melhor
     * atalho conhecido. Se falhar, cai nas Configuracoes do app.
     */
    fun abrirInicioAutomaticoXiaomi(context: Context) {
        try {
            val intent = Intent().apply {
                component = android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            abrirConfigApp(context)
        }
    }

    /** Configuracoes do proprio app (onde o usuario acha "Bateria"). */
    private fun abrirConfigApp(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) { }
    }
}
