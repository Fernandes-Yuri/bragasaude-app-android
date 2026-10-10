package br.com.bragasaude.ui.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.util.Locale
import br.com.bragasaude.domain.GroceryBudgetTier
import br.com.bragasaude.domain.GroceryProteinPreference
import br.com.bragasaude.domain.WeeklyGroceryPreferences
import br.com.bragasaude.ui.components.BragaBottomSheet
import br.com.bragasaude.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeeklyGroceryPreferencesSheet(
    initialPreferences: WeeklyGroceryPreferences,
    onDismissRequest: () -> Unit,
    onConfirm: (WeeklyGroceryPreferences) -> Unit
) {
    val effectiveInitial = remember(initialPreferences) {
        if (initialPreferences.selectedProteins.isEmpty() && initialPreferences.budgetTier == GroceryBudgetTier.MODERATE) {
            WeeklyGroceryPreferences.RECOMMENDED
        } else {
            initialPreferences
        }
    }
    var budgetTier by remember { mutableStateOf(effectiveInitial.budgetTier) }
    var selectedProteins by remember { mutableStateOf(effectiveInitial.selectedProteins) }
    var hasPantryStaples by remember { mutableStateOf(effectiveInitial.hasPantryStaples) }
    var maxBudgetInput by remember {
        mutableStateOf(
            effectiveInitial.maxWeeklyBudgetReais?.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else String.format(Locale.ROOT, "%.2f", it)
            } ?: ""
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    BragaBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Cabeçalho
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Planejar Compras da Semana",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary
                    )
                    Text(
                        text = "Defina suas preferências de custo e proteínas para gerar uma lista sob medida.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BragaTextSecondary
                    )
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fechar",
                        tint = BragaTextSecondary
                    )
                }
            }

            // Passo 1: Faixa de Orçamento
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "1. Foco do Orçamento",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BragaTextPrimary
                )

                // 1. Ultraeconômica
                BudgetOptionCard(
                    title = "Ultraeconômica (Cesta Essencial)",
                    description = "Máximo rendimento por real gasto. Cesta supercompacta de 8 a 10 alimentos básicos (arroz, feijão, ovos, aveia, frango acessível/PTS e banana). Menor custo semanal.",
                    badge = "Menor Custo",
                    isSelected = budgetTier == GroceryBudgetTier.ULTRA_ECONOMIC,
                    onClick = { budgetTier = GroceryBudgetTier.ULTRA_ECONOMIC }
                )

                // 2. Econômica
                BudgetOptionCard(
                    title = "Econômica (Custo-Benefício)",
                    description = "Alimentos essenciais e nutritivos (12 a 15 itens) com boa variedade, sem cortes nobres ou ingredientes caros.",
                    badge = null,
                    isSelected = budgetTier == GroceryBudgetTier.ECONOMIC,
                    onClick = { budgetTier = GroceryBudgetTier.ECONOMIC }
                )

                // 3. Média
                BudgetOptionCard(
                    title = "Média (Equilibrada)",
                    description = "Maior diversidade de ingredientes (16 a 20 itens), permitindo carnes magras, peixes e frutas variadas da estação.",
                    badge = null,
                    isSelected = budgetTier == GroceryBudgetTier.MODERATE,
                    onClick = { budgetTier = GroceryBudgetTier.MODERATE }
                )

                // 4. Custo Livre
                BudgetOptionCard(
                    title = "Custo Livre (Variada / Premium)",
                    description = "Catálogo completo liberado (20+ itens) com rotação aberta de frutos do mar, cortes nobres e castanhas.",
                    badge = null,
                    isSelected = budgetTier == GroceryBudgetTier.FREE,
                    onClick = { budgetTier = GroceryBudgetTier.FREE }
                )
            }

            // Teto Orçamentário Semanal (Opcional)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                border = BorderStroke(1.dp, BragaMintBorder)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Teto Máximo Semanal (Opcional)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = BragaTextPrimary
                            )
                            Text(
                                text = "Prioriza alimentos com melhor custo por caloria e alerta se ultrapassar.",
                                style = MaterialTheme.typography.bodySmall,
                                color = BragaTextSecondary
                            )
                        }
                        if (maxBudgetInput.isNotBlank()) {
                            TextButton(
                                onClick = { maxBudgetInput = "" },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text("Limpar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = maxBudgetInput,
                        onValueChange = { input ->
                            val sanitized = input.replace(',', '.')
                            if (sanitized.isEmpty() || sanitized.matches(Regex("""^\d*(\.\d{0,2})?$"""))) {
                                maxBudgetInput = input
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Ex: 120,00", color = BragaTextSecondary) },
                        prefix = {
                            Text(
                                text = "R$ ",
                                fontWeight = FontWeight.Bold,
                                color = BragaEmeraldDark
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BragaEmerald,
                            unfocusedBorderColor = BragaMintBorder,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedTextColor = BragaTextPrimary,
                            unfocusedTextColor = BragaTextPrimary
                        )
                    )

                    // Chips de atalho rápido
                    val quickBudgets = listOf("80", "120", "160", "200")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickBudgets.forEach { amount ->
                            val isSelected = maxBudgetInput.replace(',', '.').toDoubleOrNull() == amount.toDouble()
                            FilterChip(
                                selected = isSelected,
                                onClick = { maxBudgetInput = amount },
                                label = { Text("R$ $amount", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BragaEmerald,
                                    selectedLabelColor = Color.White,
                                    containerColor = Color.White,
                                    labelColor = BragaTextPrimary
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = BragaMintBorder,
                                    selectedBorderColor = BragaEmerald
                                )
                            )
                        }
                    }
                }
            }

            // Passo 2: Proteínas Prioritárias
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "2. Proteínas da Semana",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BragaTextPrimary
                )
                Text(
                    text = "Escolha as opções que você pretende consumir nos próximos 7 dias:",
                    style = MaterialTheme.typography.bodySmall,
                    color = BragaTextSecondary
                )

                val allProteins = GroceryProteinPreference.values()
                val row1 = allProteins.take(2)
                val row2 = allProteins.drop(2).take(2)
                val row3 = allProteins.drop(4)

                listOf(row1, row2, row3).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { protein ->
                            val isSelected = protein in selectedProteins
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedProteins = if (isSelected) {
                                        if (selectedProteins.size > 1) selectedProteins - protein else selectedProteins
                                    } else {
                                        selectedProteins + protein
                                    }
                                },
                                label = { Text(protein.label) },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = BragaMintSurface,
                                    selectedLabelColor = BragaEmeraldDark,
                                    selectedLeadingIconColor = BragaEmerald
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = BragaMintBorder,
                                    selectedBorderColor = BragaEmerald
                                ),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // Passo 3: Despensa Básica
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BragaMintSurface),
                border = BorderStroke(1.dp, BragaMintBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = "Já tenho óleo, sal e temperos em casa",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary
                        )
                        Text(
                            text = "Evita adicionar garrafas de azeite e potes de temperos inteiros na compra semanal.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BragaTextSecondary
                        )
                    }
                    Switch(
                        checked = hasPantryStaples,
                        onCheckedChange = { hasPantryStaples = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BragaEmerald
                        )
                    )
                }
            }

            // Botão de Confirmação
            Button(
                onClick = {
                    val parsedBudget = maxBudgetInput.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 }
                    val finalPrefs = WeeklyGroceryPreferences(
                        budgetTier = budgetTier,
                        selectedProteins = selectedProteins,
                        hasPantryStaples = hasPantryStaples,
                        maxWeeklyBudgetReais = parsedBudget
                    ).normalized()
                    onConfirm(finalPrefs)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Gerar Lista com IA",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun BudgetOptionCard(
    title: String,
    description: String,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) BragaMintSurface else BragaCardSurface
        ),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) BragaEmerald else BragaCardBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = BragaEmerald
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = BragaTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = BragaTextSecondary
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) BragaEmerald else BragaCardBorder,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
