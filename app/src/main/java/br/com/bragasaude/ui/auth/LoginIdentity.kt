package br.com.bragasaude.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.b2b.PartnerInvitePreviewDto
import br.com.bragasaude.ui.b2b.PartnerIdentityLockup

/** Identidade compartilhada entre entrada real e prévia do Estúdio. */
@Composable
fun LoginIdentity(
    invite: PartnerInvitePreviewDto?,
    companySignup: Boolean,
    showIntroduction: Boolean,
    onChangeInvite: (() -> Unit)? = null
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (invite != null) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PartnerIdentityLockup(invite.companyName, invite.programName, invite.logoUrl, Modifier.fillMaxWidth())
                if (onChangeInvite != null) {
                    TextButton(onClick = onChangeInvite, modifier = Modifier.align(Alignment.End)) {
                        Text("Trocar programa", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (showIntroduction) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = when {
                        invite != null -> "Seu cuidado começa aqui."
                        companySignup -> "Sua empresa. A sua marca."
                        else -> "Acesse sua conta"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        invite != null -> "Entre ou crie sua conta para participar de ${invite.programName}."
                        companySignup -> "Crie seu acesso para configurar o programa da sua empresa."
                        else -> "Entre ou comece seu cadastro."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
