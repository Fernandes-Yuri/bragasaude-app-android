package br.com.bragasaude.ui.nutrition

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.GroceryLimitationFormatter
import br.com.bragasaude.domain.PurchaseCalculationStatus
import br.com.bragasaude.domain.WeeklyGroceryEngine
import br.com.bragasaude.domain.WeeklyGroceryPlanResult
import java.util.Locale

@Composable
fun WeeklyGrocerySummaryCard(
    plan: WeeklyGroceryPlanResult,
    onResize: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDetailsExpanded by rememberSaveable { mutableStateOf(false) }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 1. Título do card e valores calóricos
            Text(
                text = if (plan.isManuallyModified) "Último planejamento • cobertura invalidada" else "Resumo estimado para 7 dias",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "${plan.plannedWeeklyCalories.toInt()} kcal planejadas / ${plan.targetWeeklyCalories.toInt()} kcal de meta",
                style = MaterialTheme.typography.bodyMedium
            )

            // 2. Cobertura energética estimada (consumo planejado, sem margem de compra)
            if (!plan.isManuallyModified) {
                LinearProgressIndicator(
                    progress = { (plan.coveragePercent / 100).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = String.format(Locale.getDefault(), "Cobertura energética estimada: %.1f%%", plan.coveragePercent),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // 3. Macronutrientes em chips compactos
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Proteínas" to plan.plannedProteinGrams,
                    "Carboidratos" to plan.plannedCarbsGrams,
                    "Gorduras" to plan.plannedFatGrams
                ).forEach { (label, grams) ->
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            text = "$label\n${grams.toInt()} g",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }

            // 4. Variedade e aviso conceitual
            Text(
                text = "${plan.foodVarietyCount} alimentos no planejamento. Valores de consumo, sem margem de compra.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 5. Estado de dimensionamento da compra (separado da cobertura energética)
            when (plan.purchaseStatus) {
                PurchaseCalculationStatus.CALCULABLE -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Compra dimensionada: quantidades estimadas a partir do catálogo.",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                PurchaseCalculationStatus.APPROXIMATED -> {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Algumas quantidades são aproximadas.",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
                PurchaseCalculationStatus.INCOMPLETE -> {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Compra com quantidades pendentes: faltam conversões para alguns itens.",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // 6. Alerta de edição manual ou déficit energético
            if (plan.isManuallyModified || plan.coveragePercent < WeeklyGroceryEngine.MIN_ENERGY_COVERAGE_PERCENT) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = if (plan.isManuallyModified)
                                "Modificada manualmente ou com nova meta. A cobertura precisa ser revista."
                            else
                                "Déficit energético: ${(plan.targetWeeklyCalories - plan.plannedWeeklyCalories).toInt()} kcal em relação à meta.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = onResize) {
                            Text("Redimensionar lista")
                        }
                    }
                }
            }

            // 7. Detalhes técnicos recolhidos por padrão
            val hasLimitations = plan.structuredLimitations.isNotEmpty() || plan.limitations.isNotEmpty()
            if (hasLimitations) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { isDetailsExpanded = !isDetailsExpanded }) {
                        Text(if (isDetailsExpanded) "Ocultar detalhes" else "Ver detalhes")
                    }
                }

                AnimatedVisibility(visible = isDetailsExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (plan.structuredLimitations.isNotEmpty()) {
                            val grouped = GroceryLimitationFormatter.groupForUi(plan.structuredLimitations)
                            grouped.forEach { (title, items) ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(Modifier.padding(8.dp)) {
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (items.isNotEmpty()) {
                                            Text(
                                                text = items.joinToString(", "),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            plan.limitations.forEach { text ->
                                Text(
                                    text = "• $text",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
