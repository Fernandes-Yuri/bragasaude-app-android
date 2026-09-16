package br.com.bragasaude.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.profile.ProfileViewModel
import br.com.bragasaude.ui.theme.Success

@Composable
fun RiskNotificationDialog(
    type: String,
    message: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val profileState by profileViewModel.profile.collectAsState(initial = null)
    
    val contactPhone = profileState?.emergencyContactPhone
    val contactName = profileState?.emergencyContactName ?: "Contato de Emergência"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar", color = Color.Gray)
            }
        },
        title = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFDC2626),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "ALERTA CRÍTICO",
                    color = Color(0xFFDC2626),
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // BOTÃO 1: Ligar para Emergência (192)
                Button(
                    onClick = { 
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:192"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Icon(Icons.Default.Call, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Emergência — 192")
                }

                // BOTÃO 2: Encontrar UPA mais próxima
                Button(
                    onClick = {
                        val gmmIntentUri = Uri.parse("geo:0,0?q=UNIDADE+DE+PRONTO+ATENDIMENTO")
                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                        mapIntent.setPackage("com.google.android.apps.maps")
                        context.startActivity(mapIntent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Success)
                ) {
                    Icon(Icons.Default.LocationOn, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("UPA mais próxima")
                }

                // BOTÃO 3: Contato de Emergência Cadastrado
                OutlinedButton(
                    onClick = {
                        val number = if (!contactPhone.isNullOrBlank()) contactPhone else "192"
                        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Person, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(contactName)
                }
            }
        }
    )
}
