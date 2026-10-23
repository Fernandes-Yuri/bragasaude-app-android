package br.com.bragasaude.ui.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.GroceryRetailCardEntity
import br.com.bragasaude.domain.WeeklyGroceryEngine
import br.com.bragasaude.ui.components.BragaFormSheet
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.text.NumberFormat
import java.util.Locale

/** Produto da lista pessoal: dados da lista, foto do catálogo e ações sem mudar de aba. */
@Composable
internal fun GroceryShoppingCard(
    item: GroceryListItemEntity,
    retailCard: GroceryRetailCardEntity?,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onIncrease: (() -> Unit)? = null,
    onDecrease: (() -> Unit)? = null
) {
    val money = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR")) }
    var editingQuantity by rememberSaveable(item.remoteId) { mutableStateOf(false) }
    var showCredit by rememberSaveable(item.remoteId) { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val image = retailCard?.imageUrl?.takeIf { it.isNotBlank() } ?: item.imageUrl
    val credit = retailCard?.let { card -> card.imageCredit?.takeIf { it.isNotBlank() && image == card.imageUrl } }
    Card(Modifier.fillMaxWidth().testTag("shoppingCard-${item.remoteId}"), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, if (item.isCheckedInPantry) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                GroceryProductImage(image, item.foodName, Modifier.size(96.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(item.foodName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        textDecoration = if (item.isCheckedInPantry) TextDecoration.LineThrough else TextDecoration.None)
                    Text("Para ${item.planningDays} dias: ${item.purchaseUnitText}", style = MaterialTheme.typography.bodyMedium)
                    if (item.retailCardId != null) Text("${item.quantity} embalagem(ns)" +
                        item.packageDisplay.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty(),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (item.estimatedPriceBrl > 0.0) "Estimado: ${money.format(item.estimatedPriceBrl)}" else "Preço não informado",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (credit != null) TextButton(onClick = { showCredit = true }, contentPadding = PaddingValues(0.dp)) {
                Text("Créditos da imagem", style = MaterialTheme.typography.labelMedium)
            }
            if (item.category == WeeklyGroceryEngine.CORRIDOR_HORTIFRUTI && item.planningDays > 7) {
                Text("Planeje reposições semanais deste perecível.", style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).heightIn(min = 48.dp)
                    .toggleable(item.isCheckedInPantry, role = Role.Checkbox, onValueChange = onToggle),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(item.isCheckedInPantry, onCheckedChange = null)
                    Text(if (item.isCheckedInPantry) "Comprado" else "Marcar comprado", style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { if (item.retailCardId != null) editingQuantity = true else onEdit() },
                    enabled = item.retailCardId == null || (onIncrease != null && onDecrease != null)) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar ${item.foodName}")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Remover ${item.foodName}", tint = MaterialTheme.colorScheme.error)
                }
            }
            if (item.retailCardId != null && retailCard == null) Text(
                "Atualize o mercadinho para ajustar a quantidade.", style = MaterialTheme.typography.bodySmall)
        }
    }
    if (editingQuantity) BragaFormSheet(
        onDismissRequest = { editingQuantity = false },
        title = { Text("Quantidade de ${item.foodName}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Período: ${item.planningDays} dias")
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { onDecrease?.invoke() }, enabled = item.quantity > 1 && onDecrease != null,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("−") }
                    Text("${item.quantity} embalagem(ns)")
                    TextButton(onClick = { onIncrease?.invoke() }, enabled = item.quantity < 999 && onIncrease != null,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("+") }
                }
                Text(if (item.estimatedPriceBrl > 0.0) "Estimado: ${money.format(item.estimatedPriceBrl)}" else "Preço não informado")
            }
        },
        confirmButton = { TextButton(onClick = { editingQuantity = false }) { Text("Concluir") } }
    )
    if (showCredit && credit != null) BragaFormSheet(
        onDismissRequest = { showCredit = false }, title = { Text("Crédito da imagem") },
        text = { Text(credit) },
        confirmButton = { TextButton(onClick = { showCredit = false }) { Text("Fechar") } },
        dismissButton = {
            retailCard?.imageCreditUrl?.toHttpUrlOrNull()?.takeIf { it.scheme == "https" && it.username.isEmpty() && it.password.isEmpty() }?.let { url ->
                TextButton(onClick = { uriHandler.openUri(url.toString()) }) { Text("Ver fonte e licença") }
            }
        }
    )
}
