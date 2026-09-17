package br.com.bragasaude.util

object BragaConstants {
    /** UID padrão utilizado para navegação como convidado sem conta vinculada. */
    const val GUEST_UID = "00000000-0000-0000-0000-000000000000"

    /** Intervalo mínimo padrão entre sincronizações completas em background (horas). */
    const val DEFAULT_SYNC_INTERVAL_HOURS = 4L

    /** Tag padrão de logs do sistema de sincronização. */
    const val SYNC_LOG_TAG = "BragaSync"
}
