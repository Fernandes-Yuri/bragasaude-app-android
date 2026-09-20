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
     *
     * AUD-AN25: antes retornava `false` hardcoded ("desativado explicitamente para
     * testes no dispositivo físico") — a blindagem LGPD anunciada na doc e no
     * comentário de classe não existia. Agora detecta de verdade em RELEASE; em
     * DEBUG continua desligado para não travar o desenvolvimento no aparelho.
     */
    fun isDeveloperModeEnabled(context: Context): Boolean {
        if (BuildConfig.DEBUG) {
            // Desenvolvimento: não atrapalha testes no aparelho físico.
            return false
        }
        return try {
            // Opções do Desenvolvedor ativas (global settings).
            val devOptions = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED,
                0
            ) == 1
            // Depuração USB ativa.
            val adbEnabled = Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.ADB_ENABLED,
                0
            ) == 1
            val detected = devOptions || adbEnabled
            if (detected) {
                Log.w(TAG, "Modo desenvolvedor/ADB detectado em build de release — dado clínico sob risco.")
            }
            detected
        } catch (e: Exception) {
            Log.e(TAG, "Falha ao ler configurações de desenvolvedor: ${e.message}")
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
