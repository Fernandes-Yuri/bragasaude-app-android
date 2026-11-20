package br.com.bragasaude.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.b2b.PartnerInvitePreviewDto
import br.com.bragasaude.ui.b2b.PartnerTechnologyCredit
import br.com.bragasaude.ui.b2b.partnerButtonColors

/** Prévia sem autenticação, com a mesma identidade e os mesmos tokens do login real. */
@Composable
fun PartnerLoginPreview(invite: PartnerInvitePreviewDto) {
    var signup by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        LoginIdentity(invite = invite, companySignup = false, showIntroduction = true)
        TabRow(selectedTabIndex = if (signup) 1 else 0, containerColor = MaterialTheme.colorScheme.surface) {
            Tab(selected = !signup, onClick = { signup = false }, text = { Text("Entrar") })
            Tab(selected = signup, onClick = { signup = true }, text = { Text("Criar Nova Conta") })
        }
        listOfNotNull("E-mail", "Senha", "Confirmar senha".takeIf { signup }).forEach { label ->
            OutlinedTextField(value = "", onValueChange = {}, readOnly = true,
                label = { Text(label) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = {}, colors = partnerButtonColors(), shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Text(if (signup) "Receber código" else "Entrar")
        }
        OutlinedButton(onClick = {}, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Text("Continuar com o Google")
        }
        Text("Termos de Uso e Privacidade", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        PartnerTechnologyCredit()
    }
}
