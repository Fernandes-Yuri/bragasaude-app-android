package br.com.bragasaude.ui.nutrition

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.bragasaude.domain.*
import br.com.bragasaude.ui.components.BragaBottomSheet
import br.com.bragasaude.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionGoalWizardSheet(
    initialSetup: UserNutritionGoalSetup,
    userWeight: Double?,
    userHeight: Double?,
    userBirthDate: String?,
    userGender: String?,
    onDismissRequest: () -> Unit,
    onApplySetup: (UserNutritionGoalSetup) -> Unit
) {
    var currentStep by remember { mutableIntStateOf(1) } // 1: Objetivo, 2: Rotina, 3: Orçamento, 4: Resumo

    var selectedGoal by remember { mutableStateOf(initialSetup.goal) }
    var selectedActivity by remember { mutableStateOf(initialSetup.activityLevel) }
    var selectedBudgetTier by remember { mutableStateOf(initialSetup.budgetTier) }
    var hasPantryStaples by remember { mutableStateOf(initialSetup.hasPantryStaples) }

    var isManualMode by remember { mutableStateOf(initialSetup.isCustomManual) }
    var manualKcalInput by remember {
        mutableStateOf(initialSetup.manualKcal?.toInt()?.toString() ?: "")
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val currentSetup = remember(selectedGoal, selectedActivity, selectedBudgetTier, hasPantryStaples, isManualMode, manualKcalInput) {
        val parsedManual = manualKcalInput.toDoubleOrNull()
        UserNutritionGoalSetup(
            goal = selectedGoal,
            activityLevel = selectedActivity,
            budgetTier = selectedBudgetTier,
            hasPantryStaples = hasPantryStaples,
            isCustomManual = isManualMode && parsedManual != null && parsedManual > 500,
            manualKcal = parsedManual
        )
    }

    val calculatedTargetKcal = remember(currentSetup, userWeight, userHeight, userBirthDate, userGender) {
        HealthCalculators.calculateGoalSetupKcal(
            weight = userWeight,
            height = userHeight,
            birthDate = userBirthDate,
            gender = userGender,
            setup = currentSetup
        )
    }

    val macroSplit = remember(calculatedTargetKcal, userWeight, selectedGoal) {
        HealthCalculators.calculateMacroDistribution(
            targetKcal = calculatedTargetKcal,
            weightKg = (userWeight?.toFloat() ?: 70f),
            goal = selectedGoal.toDietaryGoal()
        )
    }

    BragaBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cabeçalho com indicador de progresso
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Planejamento Nutricional",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary
                    )
                    Text(
                        text = "Passo $currentStep de 4 • Diretrizes Clínicas",
                        style = MaterialTheme.typography.bodySmall,
                        color = BragaTextSecondary
                    )
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Fechar", tint = BragaTextSecondary)
                }
            }

            // Barra de Progresso
            LinearProgressIndicator(
                progress = { currentStep / 4f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = BragaEmerald,
                trackColor = BragaMintBorder
            )

            // Conteúdo animado por etapa
            AnimatedContent(
                targetState = currentStep,
                label = "WizardStepTransition"
            ) { step ->
                when (step) {
                    1 -> StepGoalSelection(
                        selected = selectedGoal,
                        onSelect = { selectedGoal = it }
                    )
                    2 -> StepActivitySelection(
                        selected = selectedActivity,
                        onSelect = { selectedActivity = it }
                    )
                    3 -> StepBudgetSelection(
                        selectedBudget = selectedBudgetTier,
                        onSelectBudget = { selectedBudgetTier = it },
                        hasStaples = hasPantryStaples,
                        onToggleStaples = { hasPantryStaples = it }
                    )
                    4 -> StepSummaryAndConfirm(
                        calculatedKcal = calculatedTargetKcal.toInt(),
                        goal = selectedGoal,
                        activity = selectedActivity,
                        budget = selectedBudgetTier,
                        hasStaples = hasPantryStaples,
                        macroSplit = macroSplit,
                        isManualMode = isManualMode,
                        manualKcalText = manualKcalInput,
                        onToggleManual = { isManualMode = it },
                        onManualKcalChange = { manualKcalInput = it }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Navegação inferior (Voltar / Avançar / Confirmar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = { currentStep-- },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, BragaMintBorder)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Voltar", color = BragaTextPrimary)
                    }
                } else {
                    Spacer(Modifier.width(8.dp))
                }

                if (currentStep < 4) {
                    Button(
                        onClick = { currentStep++ },
                        colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Continuar", color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            onApplySetup(currentSetup)
                            onDismissRequest()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(if (currentStep > 1) 0.7f else 1f)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Aplicar Estratégia", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun StepGoalSelection(
    selected: ClinicalDietaryGoal,
    onSelect: (ClinicalDietaryGoal) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Qual é o seu objetivo principal no momento?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BragaTextPrimary
        )
        Text(
            text = "Isso define se faremos um ajuste calórico de déficit saudável, manutenção estável ou superávit proteico.",
            style = MaterialTheme.typography.bodySmall,
            color = BragaTextSecondary
        )

        ClinicalDietaryGoal.entries.forEach { goal ->
            val isChosen = goal == selected
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(goal) },
                shape = RoundedCornerShape(16.dp),
                color = if (isChosen) BragaMintSurface else Color.White,
                border = BorderStroke(
                    width = if (isChosen) 2.dp else 1.dp,
                    color = if (isChosen) BragaEmerald else BragaMintBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(if (isChosen) BragaEmerald else BragaSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        val icon = when (goal) {
                            ClinicalDietaryGoal.WEIGHT_LOSS -> Icons.Default.TrendingDown
                            ClinicalDietaryGoal.MAINTENANCE -> Icons.Default.CheckCircleOutline
                            ClinicalDietaryGoal.HYPERTROPHY -> Icons.Default.FitnessCenter
                        }
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (isChosen) Color.White else BragaTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = goal.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary
                        )
                        Text(
                            text = goal.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = BragaEmeraldDark
                        )
                        Text(
                            text = goal.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = BragaTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }

                    RadioButton(
                        selected = isChosen,
                        onClick = { onSelect(goal) },
                        colors = RadioButtonDefaults.colors(selectedColor = BragaEmerald)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepActivitySelection(
    selected: DailyActivityLevel,
    onSelect: (DailyActivityLevel) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Como é sua rotina e esforço físico no dia a dia?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BragaTextPrimary
        )
        Text(
            text = "Seja sincero com seu ritmo atual. Isso garante que a meta não deixe você com fome nem desacelere o progresso.",
            style = MaterialTheme.typography.bodySmall,
            color = BragaTextSecondary
        )

        DailyActivityLevel.entries.forEach { level ->
            val isChosen = level == selected
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(level) },
                shape = RoundedCornerShape(16.dp),
                color = if (isChosen) BragaMintSurface else Color.White,
                border = BorderStroke(
                    width = if (isChosen) 2.dp else 1.dp,
                    color = if (isChosen) BragaEmerald else BragaMintBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isChosen,
                        onClick = { onSelect(level) },
                        colors = RadioButtonDefaults.colors(selectedColor = BragaEmerald)
                    )

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = level.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = BragaTextPrimary
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isChosen) BragaEmerald.copy(alpha = 0.15f) else BragaSurface
                            ) {
                                Text(
                                    text = "FAF ${level.factor}",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isChosen) BragaEmeraldDark else BragaTextSecondary
                                )
                            }
                        }
                        Text(
                            text = level.routineDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = BragaTextSecondary,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepBudgetSelection(
    selectedBudget: GroceryBudgetTier,
    onSelectBudget: (GroceryBudgetTier) -> Unit,
    hasStaples: Boolean,
    onToggleStaples: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Qual padrão de compras você prefere para a semana?",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BragaTextPrimary
        )
        Text(
            text = "Dimensionamos os alimentos para bater suas calorias respeitando o seu orçamento.",
            style = MaterialTheme.typography.bodySmall,
            color = BragaTextSecondary
        )

        val tiers = listOf(
            GroceryBudgetTier.ULTRA_ECONOMIC,
            GroceryBudgetTier.ECONOMIC,
            GroceryBudgetTier.MODERATE,
            GroceryBudgetTier.FREE
        )

        tiers.forEach { tier ->
            val isChosen = tier == selectedBudget
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectBudget(tier) },
                shape = RoundedCornerShape(16.dp),
                color = if (isChosen) BragaMintSurface else Color.White,
                border = BorderStroke(
                    width = if (isChosen) 2.dp else 1.dp,
                    color = if (isChosen) BragaEmerald else BragaMintBorder
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isChosen,
                        onClick = { onSelectBudget(tier) },
                        colors = RadioButtonDefaults.colors(selectedColor = BragaEmerald)
                    )

                    Spacer(Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tier.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary
                        )
                        Text(
                            text = tier.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = BragaEmeraldDark
                        )
                        Text(
                            text = tier.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = BragaTextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Opção de despensa básica
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleStaples(!hasStaples) },
            shape = RoundedCornerShape(14.dp),
            color = BragaMintSurface,
            border = BorderStroke(1.dp, BragaMintBorder)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = hasStaples,
                    onCheckedChange = { onToggleStaples(it) },
                    colors = CheckboxDefaults.colors(checkedColor = BragaEmerald)
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Já possuo alimentos básicos em casa",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary
                    )
                    Text(
                        text = "Não inclui arroz, feijão, café e temperos se já estiverem estocados, diminuindo o custo da feira.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BragaTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun StepSummaryAndConfirm(
    calculatedKcal: Int,
    goal: ClinicalDietaryGoal,
    activity: DailyActivityLevel,
    budget: GroceryBudgetTier,
    hasStaples: Boolean,
    macroSplit: MacroSplit,
    isManualMode: Boolean,
    manualKcalText: String,
    onToggleManual: (Boolean) -> Unit,
    onManualKcalChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Resumo da sua Estratégia",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = BragaTextPrimary
        )

        // Card Destaque de Calorias
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = BragaEmerald,
            shadowElevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "META CALÓRICA DIÁRIA",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$calculatedKcal kcal",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${goal.title} • ${activity.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Distribuição de Macronutrientes sugerida
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = BragaSurface,
            border = BorderStroke(1.dp, BragaMintBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                MacroColumn("Proteínas", "${macroSplit.proteinG.toInt()}g", BragaEmerald)
                MacroColumn("Carboidratos", "${macroSplit.carbsG.toInt()}g", Color(0xFF2E7D32))
                MacroColumn("Gorduras", "${macroSplit.fatG.toInt()}g", Color(0xFFE65100))
            }
        }

        // Informações da Compra
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = BragaMintSurface,
            border = BorderStroke(1.dp, BragaMintBorder)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = BragaEmerald, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Padrão de Feira: ${budget.title}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaEmeraldDark
                    )
                }
                Text(
                    text = "A lista semanal de compras será calibrada para cobrir estas $calculatedKcal kcal/dia com até ${budget.maxBasketSize} itens essenciais.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BragaTextSecondary
                )
            }
        }

        // Opção Manual Discreta
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleManual(!isManualMode) },
            shape = RoundedCornerShape(12.dp),
            color = Color.Transparent
        ) {
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isManualMode,
                    onCheckedChange = { onToggleManual(it) },
                    colors = CheckboxDefaults.colors(checkedColor = BragaEmerald)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Tenho uma meta específica prescrita por nutricionista",
                    style = MaterialTheme.typography.bodySmall,
                    color = BragaTextSecondary
                )
            }
        }

        if (isManualMode) {
            OutlinedTextField(
                value = manualKcalText,
                onValueChange = { onManualKcalChange(it.filter { ch -> ch.isDigit() }) },
                label = { Text("Digitar meta manual (kcal/dia)") },
                placeholder = { Text("Ex: 2200") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun MacroColumn(title: String, amount: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = BragaTextSecondary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = amount,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}
