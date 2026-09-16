package br.com.bragasaude

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.bragasaude.ui.legal.PrivacyPolicyScreen
import br.com.bragasaude.ui.theme.BragasaudeTheme

/** A política deve abrir sem depender de login, cadastro ou permissões do aplicativo. */
class HealthPermissionsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BragasaudeTheme { PrivacyPolicyScreen(onBack = { finish() }) } }
    }
}
