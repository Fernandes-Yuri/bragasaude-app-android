package br.com.bragasaude.ui.smoking

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmokeFree
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.components.BragaAlertDialog

/** Convite contextual; mantém acesso ao histórico de quem já iniciou um plano. */
@Composable
fun SmokingSupportEntry(onOpen: () -> Unit, viewModel: SmokingSupportViewModel = hiltViewModel()) {
    val state by viewModel.snapshot.collectAsState()
    val visible by viewModel.entryVisible.collectAsState()
    val eligible by viewModel.offerEligible.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    if (!visible) return
    SupportCard {
        Text(if (eligible) "Quer um apoio para parar de fumar, no seu ritmo?" else if (state.plan != null) "Meu caminho sem cigarro" else "Apoio para parar de fumar", style = MaterialTheme.typography.titleMedium)
        Text(if (state.enabled) "Seu plano, diário e pausa de apoio." else "Apoio opcional para parar de fumar. Conheça os recursos e escolha se quer ativar.")
        Button(onClick = { if (eligible) viewModel.answerOffer(onOpen) else onOpen() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.enabled) "Abrir meu apoio" else if (eligible) "Conhecer e ativar" else "Conhecer apoio para parar de fumar")
        }
        if (eligible) TextButton(onClick = { viewModel.answerOffer() }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Agora não") }
        message?.let { Text(it) }
    }
}

/** Mounted in the main navigation, after consent and registration have finished. */
@Composable
fun SmokingProfileSaveOffer(onOpen: () -> Unit, viewModel: SmokingSupportViewModel) {
    val show by viewModel.automaticOffer.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    if (show) BragaAlertDialog(
        onDismissRequest = { if (!busy) viewModel.answerOffer() },
        title = { Text("Quer um apoio para parar de fumar, no seu ritmo?") },
        text = { Column { Text("Seu perfil foi salvo. Este apoio é opcional e só será ativado se você confirmar na apresentação."); message?.let { Text(it) } } },
        confirmButton = { TextButton(enabled = !busy, onClick = { viewModel.answerOffer(onOpen) }) { Text("Conhecer e ativar") } },
        dismissButton = { TextButton(enabled = !busy, onClick = { viewModel.answerOffer() }) { Text("Agora não") } }
    )
}

/** Header action appears only after activation and does not push the health cards down. */
@Composable
fun SmokingHomeAction(onOpen: () -> Unit, viewModel: SmokingSupportViewModel = hiltViewModel()) {
    val state by viewModel.snapshot.collectAsState()
    if (state.enabled) IconButton(onClick = onOpen) {
        Icon(Icons.Default.SmokeFree, contentDescription = "Meu caminho sem cigarro", tint = Color.White)
    }
}
