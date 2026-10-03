package br.com.bragasaude.ui.components


import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.profile.ProfileViewModel
import br.com.bragasaude.ui.theme.Success

@Composable
fun RiskNotificationDialog(
    type: String,
    message: String,
    onDismiss: () -> Unit,
    severity: br.com.bragasaude.data.local.slm.TriageSeverity = br.com.bragasaude.data.local.slm.TriageSeverity.EMERGENCIA
) {
    val context = LocalContext.current
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val profileState by profileViewModel.profile.collectAsState(initial = null)
    
    val contactPhone = profileState?.emergencyContactPhone
    val contactName = profileState?.emergencyContactName ?: "Contato de Emergência"

    val red = severity == br.com.bragasaude.data.local.slm.TriageSeverity.EMERGENCIA
    val orange = severity == br.com.bragasaude.data.local.slm.TriageSeverity.URGENCIA
    val yellow = severity == br.com.bragasaude.data.local.slm.TriageSeverity.GRAVE
    val tint = when { red -> Color(0xFFDC2626); orange -> Color(0xFFC2410C); yellow -> Color(0xFF8A6500); else -> Color(0xFF166534) }
    LaunchedEffect(message, severity) { if (red) br.com.bragasaude.ui.util.ClinicalAlertFeedback.play(context) }

    BragaAlertDialog(
        onDismissRequest = onDismiss,

        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Fechar")
                    }
                }
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    when { red -> "Emergência: SAMU 192"; orange -> "Acidente: peça ajuda"; yellow -> "Procure avaliação hoje"; else -> "Cuidado e conforto" },
                    color = tint,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Text(
                message,
                modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (red || orange || yellow) {
                // BOTÃO 1: Ligar para Emergência (192)
                Button(
                    onClick = { 
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:192"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Emergência — 192")
                }

                }
                if (orange) {
                    OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:193"))) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Bombeiros — 193") }
                }
                if (red || orange || yellow) {
                // BOTÃO 2: Encontrar UPA mais próxima
                Button(
                    onClick = {
                        val gmmIntentUri = Uri.parse("geo:0,0?q=UNIDADE+DE+PRONTO+ATENDIMENTO")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        try {
                            context.startActivity(mapIntent)
                        } catch (_: android.content.ActivityNotFoundException) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=UPA")))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("UPA mais próxima")
                }

                }
                if (red || orange || yellow) {
                // BOTÃO 3: Contato de Emergência Cadastrado
                OutlinedButton(
                    onClick = {
                        val number = contactPhone ?: return@OutlinedButton
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                        context.startActivity(intent)
                    },
                    enabled = !contactPhone.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (contactPhone.isNullOrBlank()) "Nenhum contato familiar cadastrado" else contactName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                }
                if (yellow) Text("Confira seus sinais, se puder fazer isso com segurança, sem adiar o atendimento hoje.")
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Entendi") }
            }
        }
    )
}
