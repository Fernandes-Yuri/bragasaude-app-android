package br.com.bragasaude.ui.nutrition

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.WeeklyGroceryPlanResult
import br.com.bragasaude.domain.WeeklyGroceryEngine
import java.util.Locale

@Composable
fun WeeklyGrocerySummaryCard(plan: WeeklyGroceryPlanResult, onResize: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (plan.isManuallyModified) "Último planejamento • cobertura invalidada" else "Resumo estimado para 7 dias",
                style = MaterialTheme.typography.titleMedium)
            Text("${plan.plannedWeeklyCalories.toInt()} kcal planejadas / ${plan.targetWeeklyCalories.toInt()} kcal de meta")
            if (!plan.isManuallyModified) {
                LinearProgressIndicator(progress = { (plan.coveragePercent / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(String.format(Locale.getDefault(), "Cobertura energética estimada: %.1f%%", plan.coveragePercent))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Proteínas" to plan.plannedProteinGrams, "Carboidratos" to plan.plannedCarbsGrams,
                    "Gorduras" to plan.plannedFatGrams).forEach { (label, grams) ->
                    Surface(modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Text("$label\n${grams.toInt()} g", Modifier.padding(8.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text("${plan.foodVarietyCount} alimentos no planejamento. Valores de consumo, sem margem de compra.", style = MaterialTheme.typography.bodySmall)
            if (plan.isManuallyModified || plan.coveragePercent < WeeklyGroceryEngine.MIN_ENERGY_COVERAGE_PERCENT) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(if (plan.isManuallyModified) "Modificada manualmente ou com nova meta. A cobertura precisa ser revista."
                            else "Déficit energético: ${(plan.targetWeeklyCalories - plan.plannedWeeklyCalories).toInt()} kcal. Veja as limitações abaixo.")
                        TextButton(onClick = onResize) { Text("Redimensionar lista") }
                    }
                }
            }
            if (plan.limitations.isNotEmpty()) Text("Há limitações de dados ou conversões. A cobertura energética do plano não garante quantidade de compra completa.", style = MaterialTheme.typography.bodySmall)
            plan.limitations.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
