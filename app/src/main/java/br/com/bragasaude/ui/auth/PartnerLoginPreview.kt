package br.com.bragasaude.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import br.com.bragasaude.R
import br.com.bragasaude.ui.b2b.PartnerTechnologyCredit

/**
 * Prévia sem autenticação, com a mesma identidade e os mesmos tokens do login real.
 *
 * Não aplica `BragasaudeTheme` aqui: o Estúdio já fornece o `MaterialTheme` simulado
 * (claro ou `darkColorScheme` em "Ver prévia em modo escuro"), e um tema interno
 * sobrescreveria esse esquema.
 *
 * A tab bar não usa altura fixa nem reduz a fonte: em "Texto ampliado" (fontScale 2x)
 * os rótulos quebram em duas linhas e a barra cresce junto com o conteúdo.
 */
@Composable
fun PartnerLoginPreview() {
    var signup by remember { mutableStateOf(false) }
    // Espaçamento um pouco menor que o do login real para o bloco legal caber na 1ª dobra
    // da prévia (a prévia é renderizada dentro de um card baixo, não em tela cheia).
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LoginIdentity(showIntroduction = true)
        val labels = listOf("Entrar", "Criar conta")
        TabRow(
            selectedTabIndex = if (signup) 1 else 0,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            labels.forEachIndexed { index, label ->
                Tab(
                    selected = signup == (index == 1),
                    onClick = { signup = index == 1 },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(
                        text = label,
                        maxLines = 2,
                        softWrap = true,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        listOfNotNull("E-mail", "Senha", "Confirmar senha".takeIf { signup }).forEach { label ->
            OutlinedTextField(value = "", onValueChange = {}, readOnly = true,
                label = { Text(label) }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
        }
        Button(onClick = {}, colors = ButtonDefaults.buttonColors(), shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Text(if (signup) "Receber código" else "Entrar")
        }
        OutlinedButton(onClick = {}, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_google),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = Color.Unspecified
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "Continuar com o Google",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
        // Affordance de link: mesma cor de destaque com contraste AA e sublinhado, no lugar do
        // cinza sem ação aparente que parecia texto decorativo.
        val linkColor = MaterialTheme.colorScheme.primary
        val linkStyle = SpanStyle(
            color = linkColor,
            textDecoration = TextDecoration.Underline,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = buildAnnotatedString {
                append("Ao continuar, você concorda com nossos ")
                withStyle(linkStyle) { append("Termos de Uso") }
                append(" e ")
                withStyle(linkStyle) { append("Privacidade") }
                append(".")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        PartnerTechnologyCredit()
    }
    }
}
