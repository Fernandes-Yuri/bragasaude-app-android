package br.com.bragasaude.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/** A liberação de bateria depende da confirmação do usuário e não substitui outras permissões. */
object BatteryOptimizationHelper {

    enum class ResultadoLiberacao {
        CONFIRMACAO_ABERTA, CONFIG_APP_ABERTA, JA_LIBERADO, INDISPONIVEL
    }

    /** Verdadeiro se o app ja esta liberado da otimizacao de bateria. */
    fun estaLiberado(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        return pm?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    /**
     * Solicita a confirmação específica deste app. Em sistemas que bloqueiam
     * essa tela, mantém o usuário nas configurações do Braga Saúde.
     */
    fun pedirIgnorarOtimizacao(context: Context): ResultadoLiberacao {
        if (estaLiberado(context)) return ResultadoLiberacao.JA_LIBERADO
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        if (tentarAbrir(context, intent)) return ResultadoLiberacao.CONFIRMACAO_ABERTA
        return if (abrirConfigApp(context)) ResultadoLiberacao.CONFIG_APP_ABERTA
            else ResultadoLiberacao.INDISPONIVEL
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
    private fun abrirConfigApp(context: Context): Boolean = tentarAbrir(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    )

    private fun tentarAbrir(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
