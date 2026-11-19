package br.com.bragasaude.ui.navigation

import android.content.Context
import android.content.Intent
import br.com.bragasaude.domain.b2b.B2BOnboardingValidator
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Gerenciador de Deep Links de convite B2B (`https://bragasaude.online/convite/{codigo}`).
 * Armazena o codigo em memoria e em `braga_prefs` para funcionar tanto em cold start
 * antes do login quanto no usuario ja autenticado.
 */
object PartnerInviteNavigation {
    const val PREF_PENDING_INVITE_CODE = "pending_b2b_invite_code"
    val pendingInviteCode = MutableStateFlow<String?>(null)

    fun receive(intent: Intent?, context: Context) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val rawUri = intent.dataString ?: return
        val code = B2BOnboardingValidator.parseInviteCodeFromUri(rawUri) ?: return
        context.applicationContext
            .getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_PENDING_INVITE_CODE, code)
            .apply()
        pendingInviteCode.value = code
        intent.data = null
    }

    fun consumePending(): String? {
        val current = pendingInviteCode.value
        pendingInviteCode.value = null
        return current
    }
}
