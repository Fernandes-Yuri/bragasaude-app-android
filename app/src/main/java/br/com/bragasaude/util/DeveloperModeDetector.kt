package br.com.bragasaude.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

import br.com.bragasaude.BuildConfig

/**
 * Utilitário de segurança para detecção e mitigação de execução sob Modo Desenvolvedor / Depuração USB.
 * Blindagem obrigatória para proteção de dados clínicos, sensíveis e LGPD.
 */
object DeveloperModeDetector {

    private const val TAG = "DeveloperModeDetector"

    /**
     * Retorna true se as Opções do Desenvolvedor ou a Depuração USB (ADB) estiverem ativas no dispositivo.
     */
    fun isDeveloperModeEnabled(context: Context): Boolean {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Build DEBUG: verificação de modo desenvolvedor desativada para testes locais.")
            return false
        }
        return try {
            val resolver = context.contentResolver
            val dev = Settings.Global.getInt(
                resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
            val adb = Settings.Global.getInt(
                resolver, Settings.Global.ADB_ENABLED, 0) == 1
            if (dev || adb) {
                Log.w(TAG, "Modo desenvolvedor ativo (dev=$dev, adb=$adb). Bloqueio aplicado.")
            }
            dev || adb
        } catch (e: Exception) {
            Log.w(TAG, "Falha ao verificar modo desenvolvedor: ${e.message}")
            false
        }
    }

    /**
     * Redireciona o usuário diretamente para as Opções do Desenvolvedor do Android
     * para que possa desativar a chave de desenvolvimento com facilidade.
     */
    fun openDeveloperSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Action APPLICATION_DEVELOPMENT_SETTINGS não suportada pela OEM, tentando fallback geral: ${e.message}")
            try {
                val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (fallbackError: Exception) {
                Log.e(TAG, "Não foi possível abrir as configurações do sistema: ${fallbackError.message}")
            }
        }
    }
}
