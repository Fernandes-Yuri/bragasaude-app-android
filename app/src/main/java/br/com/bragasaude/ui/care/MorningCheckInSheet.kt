package br.com.bragasaude.ui.care

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Bed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.theme.*
import br.com.bragasaude.ui.util.createVoiceInputIntent
import br.com.bragasaude.ui.util.rememberVoiceInputLauncher

/**
 * Cena C37 — Check-in matinal por voz.
 *
 * Braga pergunta com voz acolhedora: "Bom dia, [Nome]! Como foi sua noite e
 * como você está se sentindo hoje?". Captura por microfone (botão grande de
 * 64dp) ou seleção rápida: qualidade do sono (1-5) e disposição (1-5). A
 * transcrição vai para POST /api/symptoms/check-in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MorningCheckInSheet(
    onDismiss: () -> Unit,
    viewModel: CareOsViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsState()

    var spokenText by remember { mutableStateOf("") }
    var sleepQuality by remember { mutableStateOf(0) }
    var disposition by remember { mutableStateOf(0) }

    val voiceLauncher = rememberVoiceInputLauncher { transcript ->
        spokenText = transcript
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = BragaCardSurface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Saudação acolhedora da assistente
            Text(
                "Bom dia! Como foi sua noite e como você está se sentindo hoje?",
                fontWeight = FontWeight.SemiBold,
                fontSize = 21.sp,
                color = BragaTextPrimary
            )

            // Microfone acessível — alvo de toque de 64dp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                FilledTonalIconButton(
                    onClick = { voiceLauncher.launch(createVoiceInputIntent("CHECKIN")) },
                    enabled = !ui.loading,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = BragaEmerald
                    ),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        Icons.Filled.Mic,
                        contentDescription = "Falar como estou me sentindo",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Text(
                "Toque no microfone e fale. Depois pode corrigir o texto.",
                fontSize = 14.sp,
                color = BragaTextSecondary,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            OutlinedTextField(
                value = spokenText,
                onValueChange = { spokenText = it },
                label = { Text("Como estou me sentindo") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )

            // Qualidade do sono — 1 a 5
            RatingSelector(
                label = "Qualidade do sono",
                iconVector = Icons.Outlined.Bed,
                value = sleepQuality,
                onValueChange = { sleepQuality = it }
            )

            // Disposição — 1 a 5
            RatingSelector(
                label = "Disposição hoje",
                iconVector = Icons.Filled.Star,
                value = disposition,
                onValueChange = { disposition = it }
            )

            Button(
                onClick = {
                    viewModel.submitCheckIn(
                        symptomsText = spokenText,
                        sleepQuality = sleepQuality.takeIf { it in 1..5 },
                        disposition = disposition.takeIf { it in 1..5 },
                        inputMethod = "VOICE"
                    )
                    onDismiss()
                },
                enabled = spokenText.isNotBlank() && !ui.loading,
                colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
            ) {
                Icon(Icons.Filled.Send, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Enviar check-in", color = Color.White, fontSize = 17.sp)
            }
        }
    }
}

@Composable
private fun RatingSelector(
    label: String,
    iconVector: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    onValueChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(iconVector, contentDescription = null, tint = BragaEmeraldDark, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = BragaTextPrimary)
        }
        Row {
            for (i in 1..5) {
                IconButton(
                    onClick = { onValueChange(i) },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (i <= value) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = "$label: $i de 5",
                        tint = if (i <= value) BragaEmerald else BragaTextSecondary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}
