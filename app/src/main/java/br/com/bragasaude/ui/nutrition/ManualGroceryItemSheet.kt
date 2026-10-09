package br.com.bragasaude.ui.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.domain.GroceryIngredient
import br.com.bragasaude.domain.GroceryPurchasePlanner
import br.com.bragasaude.domain.groceryNameKey
import br.com.bragasaude.ui.components.BragaFormSheet
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMintBorder
import br.com.bragasaude.ui.theme.BragaMintSurface
import kotlinx.coroutines.delay

@Composable
internal fun ManualGroceryItemSheet(
    ingredients: List<GroceryIngredient>,
    editing: GroceryListItemEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, String?) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

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
    val valid = selected?.let { ingredient: GroceryIngredient ->
        GroceryPurchasePlanner.parseAmount(amount, ingredient.unit) != null
    } == true

    val matches = remember(ingredients, query) {
        if (query.isBlank()) emptyList()
        else ingredients.filter { groceryNameKey(it.name).contains(groceryNameKey(query)) }
    }

    LaunchedEffect(query) {
        val clean = groceryNameKey(query)
        if (clean.length >= 2 && query.endsWith(" ") && matches.isNotEmpty()) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else if (clean.length >= 3 && matches.isNotEmpty()) {
            val isFullMatch = matches.any { ingredient ->
                val fullName = groceryNameKey(ingredient.name)
                fullName == clean || fullName.split(" ", "-", "/").any { it == clean }
            }
            if (isFullMatch) {
                delay(400)
                keyboardController?.hide()
                focusManager.clearFocus()
            }
        }
    }

    BragaFormSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Adicionar à lista de compras" else "Editar quantidade do item") },
        confirmButton = {
            Button(
                onClick = { selected?.let { onSave(it.slug, amount, editing?.remoteId); onDismiss() } },
                enabled = valid,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
            ) {
                Text(if (editing == null) "Incluir na lista" else "Atualizar quantidade")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Pesquise o alimento do catálogo e informe quanto deseja comprar. " +
                    "Nomes e medidas corretos liberam o motor inteligente de receitas sugeridas.",
                    style = MaterialTheme.typography.bodyMedium
                )

                if (editing != null && selected == null) {
                    Text("Este item não está no catálogo atual. Escolha um alimento para substituí-lo na sua lista.")
                }

                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar alimento no catálogo") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null)
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = {
                                query = ""
                                keyboardController?.hide()
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "Limpar busca")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (ingredients.isEmpty()) {
                    Text("O catálogo compatível com seu perfil está sendo carregado...")
                }

                if (ingredients.isNotEmpty() && matches.isEmpty() && query.isNotBlank()) {
                    Text("Nenhum alimento encontrado. Digite parte do nome para buscar no catálogo.")
                }

                // Lista de alimentos sugeridos pela busca
                matches.take(8).forEach { ingredient ->
                    val isCurrent = selectedSlug == ingredient.slug
                    OutlinedButton(
                        onClick = {
                            selectedSlug = ingredient.slug
                            query = ingredient.name
                            keyboardController?.hide()
                            focusManager.clearFocus()
                            if (amount.isBlank()) {
                                amount = if (ingredient.unit == "un") "12" else "1"
                            }
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = if (isCurrent) BorderStroke(2.dp, BragaEmerald) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = if (isCurrent) ButtonDefaults.outlinedButtonColors(containerColor = BragaMintSurface) else ButtonDefaults.outlinedButtonColors()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(ingredient.name, style = MaterialTheme.typography.bodyMedium)
                            Text("(${ingredient.unit})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }

                if (matches.size > 8) {
                    Text("Mais alimentos disponíveis. Refine o termo digitado para encontrar mais opções.", style = MaterialTheme.typography.bodySmall)
                }

                // Configuração da quantidade do alimento selecionado
                selected?.let { ingredient ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        "Alimento selecionado: ${ingredient.name}",
                        style = MaterialTheme.typography.titleSmall,
                        color = BragaEmerald
                    )

                    // Chips de quantidade rápida
                    val quickAmounts = when (ingredient.unit) {
                        "kg" -> listOf("0.25", "0.5", "1", "2")
                        "L" -> listOf("0.5", "1", "2", "3")
                        else -> listOf("6", "12", "20", "30")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickAmounts.forEach { quickVal ->
                            FilterChip(
                                selected = amount == quickVal,
                                onClick = { amount = quickVal },
                                label = { Text("$quickVal ${ingredient.unit}") }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Quantidade a comprar (${ingredient.unit})") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = {
                            Text(
                                if (ingredient.unit == "un") "Exemplo: 12 ovos. Digite a quantidade inteira."
                                else "Exemplo: 0.5 para meio ${ingredient.unit} ou 1 para um ${ingredient.unit}."
                            )
                        },
                        isError = amount.isNotBlank() && !valid
                    )

                    if (amount.isNotBlank() && !valid) {
                        Text(
                            "Informe uma quantidade positiva válida para ${ingredient.unit}.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Text(
                    br.com.bragasaude.domain.GROCERY_PRICE_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    )
}
