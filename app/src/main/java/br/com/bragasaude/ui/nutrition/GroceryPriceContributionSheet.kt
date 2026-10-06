package br.com.bragasaude.ui.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.domain.GroceryContributionState
import br.com.bragasaude.ui.components.BragaFormSheet
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun GroceryPriceContributionSheet(
    item: GroceryListItemEntity,
    status: GroceryContributionState,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String, String, String) -> Unit
) {
    var amount by remember(item.remoteId) { mutableStateOf("") }
    var quantity by remember(item.remoteId) { mutableStateOf("") }
    var unit by remember(item.remoteId) { mutableStateOf("g") }
    var state by remember(item.remoteId) { mutableStateOf("") }
    var date by remember(item.remoteId) { mutableStateOf(LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))) }
    val enabled = !status.submitting && !status.success
    BragaFormSheet(
        onDismissRequest = onDismiss,
        dismissEnabled = !status.submitting,
        title = { Text("Informar quanto paguei") },
        confirmButton = {
            Button(onClick = { onSubmit(amount, quantity, unit, state, date) },
                enabled = enabled && amount.isNotBlank() && quantity.isNotBlank(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (status.submitting) "Enviando..." else "Enviar contribuição")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !status.submitting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (status.success) "Fechar" else "Agora não")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(item.foodName, style = MaterialTheme.typography.titleMedium)
                Text("Opcional: sua compra ajuda a melhorar as referências de custo. Compartilhamos apenas médias, sem mostrar seu nome.")
                OutlinedTextField(amount, { amount = it }, label = { Text("Valor total pago (R$)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = enabled,
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(quantity, { quantity = it }, label = { Text("Peso ou quantidade total comprada") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), enabled = enabled,
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Unidade da quantidade informada")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("g", "kg", "ml", "L", "un").forEach { option ->
                        FilterChip(selected = unit == option, onClick = { unit = option }, enabled = enabled,
                            label = { Text(if (option == "ml") "mL" else option) })
                    }
                }
                Text("Exemplos: R$ 8 por 500 g; R$ 12 por 12 un. Informe o peso do produto, sem a embalagem.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(date, { date = it }, label = { Text("Data da compra (dd/mm/aaaa)") },
                    enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(state, { state = it.take(2) }, label = { Text("UF onde comprou (opcional)") },
                    enabled = enabled, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("A referência reúne compras dos últimos 90 dias e só aparece com pelo menos cinco participantes. Ela pode variar conforme região e loja.",
                    style = MaterialTheme.typography.bodySmall)
                status.message?.let { Text(it, color = if (status.success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
            }
        }
    )
}
