package br.com.bragasaude.util

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

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
        // TODO: Reativar bloqueio antes do commit/release — temporariamente desativado para testes em device
        Log.d(TAG, "Verificação de modo desenvolvedor desativada temporariamente para testes.")
        return false
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
