package br.com.bragasaude.ui.navigation

import android.content.Intent
import br.com.bragasaude.ui.util.Screen
import kotlinx.coroutines.flow.MutableStateFlow

/** Guardado até o NavHost autenticado ficar disponível, inclusive no cold start. */
object ConsultationNavigation {
    val pending = MutableStateFlow<Screen.Consultations?>(null)
    fun receive(intent: Intent?) {
        if (intent?.getBooleanExtra("OPEN_CONSULTATION", false) == true) {
            pending.value = Screen.Consultations(intent.getStringExtra("patientId") ?: "",
                intent.getStringExtra("consultationId") ?: "")
            intent.removeExtra("OPEN_CONSULTATION")
        }
    }
}
