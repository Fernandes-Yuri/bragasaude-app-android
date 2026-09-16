package br.com.bragasaude.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.XpGrantService

/**
 * Fase 3 — Feedback global e imediato de gamificação.
 *
 * Escuta os eventos do [XpGrantService] e mostra uma Snackbar positiva
 * sempre que o usuário ganha XP, sobe de nível ou completa uma sequência.
 * Mensagens seguem o tom da UX geriátrica: encorajadoras e sem punição.
 */
@Composable
fun XpToastHost(
    xpGrantService: XpGrantService,
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        xpGrantService.xpEvents.collect { result ->
            if (result.awarded && result.xp > 0) {
                snackbarHostState.showSnackbar(result.displayMessage)
            } else if (result.reason.isNotBlank()) {
                snackbarHostState.showSnackbar(result.reason)
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier.padding(bottom = 72.dp) // acima da barra de navegação inferior
    ) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
