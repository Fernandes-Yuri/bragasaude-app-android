package br.com.bragasaude.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Whatsapp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.data.util.WhatsAppLinkTotp
import kotlinx.coroutines.delay

/**
 * Botão de vincular WhatsApp (arquitetura TOTP temporária).
 *
 * No ato do clique ele lê o código TOTP ATUAL do segredo da conta, monta o link
 * wa.me com a mensagem pronta e abre o WhatsApp. O usuário só aperta enviar.
 *
 * Mostra a validade decrescente: o código vale por 3 minutos e não tem tolerância —
 * se vencer, o usuário volta aqui e toca de novo para pegar um código novo.
 */
@Composable
fun WhatsAppLinkButton(
    totpSecret: String?,
    businessPhone: String = "5511967808252",
    alreadyLinked: Boolean = false,
    onLinkOpened: () -> Unit = {}
) {
    val context = LocalContext.current
    var secondsLeft by remember { mutableStateOf(WhatsAppLinkTotp.secondsUntilRotation()) }

    // Relógio decrescente da janela do código.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            secondsLeft = WhatsAppLinkTotp.secondsUntilRotation()
        }
    }

    if (totpSecret.isNullOrBlank()) {
        // Sem segredo ainda: a conta ainda não sincronizou. Não promete o que não pode.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Text(
                "Assim que seu cadastro for concluído, liberamos o vínculo do WhatsApp aqui. 💚",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = {
                if (totpSecret.isNullOrBlank()) return@Button
                val code = WhatsAppLinkTotp.currentCode(totpSecret)
                val text = Uri.encode("Vincular Braga Saúde $code")
                val uri = Uri.parse("https://wa.me/$businessPhone?text=$text")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                runCatching { context.startActivity(intent) }
                onLinkOpened()
            },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFF00A884),
                contentColor = Color.White
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Whatsapp,
                    contentDescription = "WhatsApp",
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (alreadyLinked) "Vincular novamente" else "Vincular meu WhatsApp",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        if (secondsLeft <= 30L) Color(0xFFD32F2F) else Color(0xFF00A884),
                        CircleShape
                    )
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Código válido por ${secondsLeft}s — se vencer, toque de novo",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
