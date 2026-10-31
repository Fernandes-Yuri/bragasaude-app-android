package br.com.bragasaude.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.profile.ProfileViewModel
import br.com.bragasaude.ui.theme.BragaEmeraldDark

/** As mesmas ações persistem no chat e no SOS, sem confirmação intermediária. */
@Composable
fun EmergencySupportActions(mentalHealthFirst: Boolean, requestInitialFocus: Boolean = false) {
    val profileViewModel: ProfileViewModel = hiltViewModel()
    val profile by profileViewModel.profile.collectAsState(initial = null)
    EmergencyActionButtons(
        mentalHealthFirst = mentalHealthFirst,
        contactPhone = profile?.emergencyContactPhone,
        contactName = profile?.emergencyContactName,
        requestInitialFocus = requestInitialFocus
    )
}

@Composable
internal fun EmergencyActionButtons(
    mentalHealthFirst: Boolean,
    contactPhone: String? = null,
    contactName: String? = null,
    requestInitialFocus: Boolean = false
) {
    val context = LocalContext.current
    val firstAction = remember { FocusRequester() }
    LaunchedEffect(requestInitialFocus, mentalHealthFirst) {
        if (requestInitialFocus) firstAction.requestFocus()
    }
    fun dial(number: String) {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val numbers = if (mentalHealthFirst) listOf("188", "192") else listOf("192", "188")
        numbers.forEachIndexed { index, number ->
            val emotionalSupport = number == "188"
            Button(
                onClick = { dial(number) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .testTag("emergencyDial$number")
                    .then(if (index == 0) Modifier.focusRequester(firstAction) else Modifier),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (emotionalSupport) Color(0xFFF59E0B) else Color(0xFFDC2626),
                    contentColor = if (emotionalSupport) Color(0xFF451A03) else Color.White
                )
            ) {
                Icon(Icons.Default.Call, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (emotionalSupport) "Ligar para o CVV (188)" else "Ligar para o SAMU (192)")
            }
        }
        Button(
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("geo:0,0?q=UNIDADE+DE+PRONTO+ATENDIMENTO")))
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/search/?api=1&query=UNIDADE+DE+PRONTO+ATENDIMENTO")))
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BragaEmeraldDark, contentColor = Color.White)
        ) {
            Icon(Icons.Default.LocationOn, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Localizar UPA mais próxima")
        }
        if (!contactPhone.isNullOrBlank()) {
            OutlinedButton(
                onClick = { dial(contactPhone) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Ligar para ${contactName?.takeIf { it.isNotBlank() } ?: "contato de emergência"}")
            }
        }
    }
}
