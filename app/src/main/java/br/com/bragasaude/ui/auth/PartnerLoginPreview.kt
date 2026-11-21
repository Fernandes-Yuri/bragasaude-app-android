package br.com.bragasaude.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import br.com.bragasaude.ui.b2b.PartnerTechnologyCredit

/**
 * Prévia sem autenticação, com a mesma identidade e os mesmos tokens do login real.
 *
 * Não aplica `BragasaudeTheme` aqui: o Estúdio já fornece o `MaterialTheme` simulado
 * (claro ou `darkColorScheme` em "Ver prévia em modo escuro"), e um tema interno
 * sobrescreveria esse esquema.
 */
@Composable
fun PartnerLoginPreview() {
    var signup by remember { mutableStateOf(false) }
    // Espaçamento um pouco menor que o do login real para o bloco legal caber na 1ª dobra
    // da prévia (a prévia é renderizada dentro de um card baixo, não em tela cheia).
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LoginIdentity(showIntroduction = true)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val style = MaterialTheme.typography.labelLarge
            val measurer = rememberTextMeasurer()
            val labels = listOf("Entrar", "Criar Nova Conta")
            val longest = labels.maxOf { measurer.measure(AnnotatedString(it), style = style, softWrap = false).size.width }
            val scale = (constraints.maxWidth.toFloat() / labels.size / longest.coerceAtLeast(1)).coerceAtMost(1f)
            TabRow(selectedTabIndex = if (signup) 1 else 0, modifier = Modifier.height(48.dp),
                containerColor = MaterialTheme.colorScheme.surface) {
                labels.forEachIndexed { index, label ->
                    Tab(selected = signup == (index == 1), onClick = { signup = index == 1 },
                        modifier = Modifier.fillMaxHeight()) {
                        Text(label, maxLines = 1, softWrap = false, style = style, fontSize = style.fontSize * scale)
                    }
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
            Text("Continuar com o Google")
        }
        // Affordance de link: mesma cor de destaque com contraste AA e sublinhado, no lugar do
        // cinza sem ação aparente que parecia texto decorativo.
        Text(
            text = "Termos de Uso e Privacidade",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        PartnerTechnologyCredit()
    }
}
