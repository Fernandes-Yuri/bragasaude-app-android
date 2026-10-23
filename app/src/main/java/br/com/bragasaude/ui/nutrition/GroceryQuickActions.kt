package br.com.bragasaude.ui.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.GroceryPlanningPeriod

/** Segunda linha do topo: período e duas ações, com alternativa para fonte ampliada. */
@Composable
internal fun GroceryQuickActions(days: Int, loading: Boolean, onDays: (Int) -> Unit,
    onChoosePeriod: () -> Unit, onAdd: () -> Unit, onPlan: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth().testTag("groceryQuickActions")) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (maxWidth >= 300.dp && fontScale <= 1.1f) {
                GroceryPlanningPeriod.options.forEach { option ->
                    FilterChip(selected = days == option, enabled = !loading, onClick = { onDays(option) },
                        label = { Text("$option dias", style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp))
                }
            } else {
                TextButton(onClick = onChoosePeriod, enabled = !loading, modifier = Modifier.weight(1f)) { Text("$days dias") }
            }
            TextButton(onClick = onAdd, modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)) { Text("Adicionar", style = MaterialTheme.typography.labelMedium) }
            IconButton(onClick = onPlan, enabled = !loading) {
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.Default.AutoAwesome, contentDescription = "Opções de planejamento com IA")
            }
        }
    }
}
