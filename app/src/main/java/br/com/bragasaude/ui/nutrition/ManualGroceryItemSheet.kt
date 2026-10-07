package br.com.bragasaude.ui.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.domain.GroceryIngredient
import br.com.bragasaude.domain.GroceryPurchasePlanner
import br.com.bragasaude.domain.groceryNameKey
import br.com.bragasaude.ui.components.BragaFormSheet

@Composable
internal fun ManualGroceryItemSheet(
    ingredients: List<GroceryIngredient>,
    editing: GroceryListItemEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selectedSlug by rememberSaveable(editing?.remoteId) { mutableStateOf(editing?.foodId.orEmpty()) }
    val selected = ingredients.firstOrNull { it.slug == selectedSlug }
    var amount by rememberSaveable(editing?.remoteId) {
        mutableStateOf(editing?.let {
            val value = it.purchaseUnitText.substringBefore(' ').toDoubleOrNull()
            when {
                it.purchaseUnitText.endsWith(" g") && value != null -> (value / 1000).toString()
                value != null -> value.toString()
                else -> ""
            }
        }.orEmpty())
    }
    val valid = selected?.let { GroceryPurchasePlanner.parseAmount(amount, it.unit) } != null
    BragaFormSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Montar minha lista" else "Editar item") },
        confirmButton = {
            Button(
                onClick = { selected?.let { onSave(it.slug, amount, editing?.remoteId); onDismiss() } },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            ) {
                Text("Salvar item")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Voltar à lista")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Escolha o alimento e informe a quantidade que pretende comprar. " +
                    "Nomes e quantidades corretos ajudam nas sugestões de receitas práticas e no planejamento alimentar."
                )
                if (editing != null && selected == null) {
                    Text("Este item não está no catálogo atual. Escolha um alimento para substituí-lo na sua lista.")
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar alimento") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                val matches = ingredients.filter { groceryNameKey(it.name).contains(groceryNameKey(query)) }
                if (ingredients.isEmpty()) {
                    Text("O catálogo compatível com seu perfil está sendo carregado.")
                }
                if (ingredients.isNotEmpty() && matches.isEmpty()) {
                    Text("Nenhum alimento compatível encontrado. Confira o nome e as restrições no seu perfil.")
                }
                matches.take(8).forEach { ingredient ->
                    OutlinedButton(
                        onClick = { selectedSlug = ingredient.slug; query = ingredient.name },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) {
                        Text(if (selectedSlug == ingredient.slug) "Selecionado: ${ingredient.name}" else ingredient.name)
                    }
                }
                if (matches.size > 8) {
                    Text("Digite mais letras para encontrar outros alimentos.", style = MaterialTheme.typography.bodySmall)
                }
                selected?.let { ingredient ->
                    Text("Alimento: ${ingredient.name}", style = MaterialTheme.typography.titleSmall)
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Quantidade em ${ingredient.unit}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(
                                if (ingredient.unit == "un") "Exemplo: 12 ovos. Use unidades inteiras."
                                else "Exemplo: 0,5 para meio ${ingredient.unit}."
                            )
                        },
                        isError = amount.isNotBlank() && !valid
                    )
                    if (amount.isNotBlank() && !valid) {
                        Text("Informe uma quantidade positiva de até 1000 ${ingredient.unit}. Para kg e L, use até três casas decimais.")
                    }
                }
                Text(
                    br.com.bragasaude.domain.GROCERY_PRICE_NOTICE,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    )
}
