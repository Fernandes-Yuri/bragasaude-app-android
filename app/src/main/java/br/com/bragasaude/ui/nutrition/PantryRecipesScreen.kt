package br.com.bragasaude.ui.nutrition

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.domain.RecipePantryMatch
import kotlinx.coroutines.launch

/**
 * Icones vetoriais para cada aba de refeicao.
 * Zero emojis — apenas Material Icons.
 */
private data class MealTabIcon(
    val key: String,
    val icon: ImageVector
)

private val MEAL_TAB_ICONS = mapOf(
    "BREAKFAST" to Icons.Default.WbSunny,
    "LUNCH" to Icons.Default.Restaurant,
    "SNACK" to Icons.Default.Spa,
    "DINNER" to Icons.Default.Bedtime
)

/**
 * Tela "Receitas da Minha Despensa".
 *
 * Design sobrio e contemporaneo:
 * - Abas com icones vetoriais (zero emojis)
 * - Cards com elevacao sutil e cantos 16dp
 * - Badges de preparo com Timer icon
 * - Passo a passo com checklists interativos
 * - Nota nutricional com borda esquerda colorida
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantryRecipesScreen(
    onBack: () -> Unit,
    viewModel: RecipesViewModel = hiltViewModel()
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val mealTabs = viewModel.mealTabs
    val pagerState = rememberPagerState(pageCount = { mealTabs.size })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Receitas da Despensa",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Abas de navegacao com icones vetoriais
            ScrollableTabRow(
                selectedTabIndex = pagerState.currentPage,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                divider = {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )
                }
            ) {
                mealTabs.forEachIndexed { index, tab ->
                    val isSelected = pagerState.currentPage == index
                    val tabIcon = MEAL_TAB_ICONS[tab.key]

                    Tab(
                        selected = isSelected,
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 14.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (tabIcon != null) {
                                Icon(
                                    imageVector = tabIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isSelected)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = tab.label,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Conteudo das abas
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val mealType = mealTabs[page].key
                    val recipes = viewModel.getRecipesForMealType(mealType)

                    if (recipes.isEmpty()) {
                        EmptyRecipesView(mealType = mealTabs[page].label)
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(recipes, key = { it.recipe.id }) { match ->
                                RecipeCard(match = match)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Card de receita com design sobrio e contemporaneo.
 *
 * - Cantos arredondados 16dp
 * - Elevacao sutil 1.5dp
 * - Tipografia estruturada
 * - Badges de preparo com Timer icon
 * - Passo a passo com checklists interativos
 * - Nota nutricional com borda esquerda colorida
 */
@Composable
private fun RecipeCard(match: RecipePantryMatch) {
    val recipe = match.recipe

    // Estado local dos checklists por receita
    val checkedSteps = remember(recipe.id) { mutableStateMapOf<Int, Boolean>() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 1.5.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = Color.Black.copy(alpha = 0.08f)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Titulo da receita
            Text(
                text = recipe.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(12.dp))

            // Badges de preparo
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Tempo de preparo com Timer icon
                PrepBadge(
                    icon = Icons.Default.Timer,
                    label = "${recipe.prepTimeMinutes} min",
                    tint = MaterialTheme.colorScheme.primary
                )

                // Nivel de dificuldade
                PrepBadge(
                    icon = Icons.Default.Restaurant,
                    label = recipe.difficulty,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            // Selo de despensa (zero emojis)
            PantryBadge(match = match)

            Spacer(Modifier.height(14.dp))

            // Ingredientes
            IngredientRow(
                label = "Disponivel",
                items = match.availableIngredients,
                color = MaterialTheme.colorScheme.primary
            )

            if (match.missingIngredients.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                IngredientRow(
                    label = "Faltando",
                    items = match.missingIngredients,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(18.dp))

            // Nota Nutricional — caixa com borda esquerda colorida
            NutritionNote(benefit = recipe.clinicalBenefit)

            Spacer(Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Spacer(Modifier.height(16.dp))

            // Modo de Preparo com Checklists interativos
            Text(
                text = "Modo de Preparo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(12.dp))

            recipe.instructions.forEachIndexed { index, rawStep ->
                // Remove numeros do inicio do passo (ex: "1. ") pois ja exibimos o indice
                val cleanStep = rawStep.replace(Regex("^\\d+\\.\\s*"), "")
                val isChecked = checkedSteps[index] == true

                RecipeStepItem(
                    stepNumber = index + 1,
                    stepText = cleanStep,
                    isChecked = isChecked,
                    onToggle = { checkedSteps[index] = !isChecked }
                )

                if (index < recipe.instructions.lastIndex) {
                    Spacer(Modifier.height(4.dp))
                }
            }

            // Resumo de progresso
            val totalSteps = recipe.instructions.size
            val completedSteps = checkedSteps.count { it.value }
            if (completedSteps > 0) {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (completedSteps == totalSteps) Icons.Default.CheckCircle else Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (completedSteps == totalSteps)
                                Color(0xFF2E7D32)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (completedSteps == totalSteps) "Receita concluida"
                            else "$completedSteps de $totalSteps passos concluidos",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (completedSteps == totalSteps)
                                Color(0xFF2E7D32)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Badge de preparacao (tempo ou dificuldade).
 */
@Composable
private fun PrepBadge(
    icon: ImageVector,
    label: String,
    tint: Color
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = tint.copy(alpha = 0.06f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = tint,
                fontSize = 13.sp
            )
        }
    }
}

/**
 * Linha de ingredientes (disponiveis ou faltando).
 */
@Composable
private fun IngredientRow(
    label: String,
    items: List<String>,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = color.copy(alpha = 0.08f),
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color,
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = items.joinToString(", "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Nota nutricional com borda esquerda colorida e icone Spa.
 *
 * Design sobrio: surface com borda esquerda de 3dp em primary.
 */
@Composable
private fun NutritionNote(benefit: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        // Bordas esquerda colorida
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(48.dp)
                .clip(RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp))
                .background(MaterialTheme.colorScheme.primary)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Spa,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Beneficio nutricional",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                benefit,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

/**
 * Item individual do passo a passo com checkbox interativo.
 *
 * Ao marcar, o texto recebe tachado suave e opacity reduzida.
 */
@Composable
private fun RecipeStepItem(
    stepNumber: Int,
    stepText: String,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Surface(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = if (isChecked)
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        else
            Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.Top
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = { onToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    checkmarkColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.size(22.dp)
            )

            Spacer(Modifier.width(10.dp))

            // Numero do passo
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isChecked)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f)
                else
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.padding(top = 1.dp)
            ) {
                Text(
                    text = stepNumber.toString(),
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isChecked)
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.width(10.dp))

            // Texto do passo
            Text(
                text = stepText,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
                color = if (isChecked)
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                else
                    MaterialTheme.colorScheme.onSurface,
                textDecoration = if (isChecked) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ==================== SELO DE DESPENSA (ZERO EMOJIS) ====================

private data class PantryBadgeConfig(
    val backgroundColor: Color,
    val borderColor: Color,
    val textColor: Color,
    val text: String,
    val icon: ImageVector
)

/**
 * Badge indicando status da despensa.
 * Zero emojis — icones vetoriais CheckCircle, Info, ShoppingCart.
 */
@Composable
private fun PantryBadge(match: RecipePantryMatch) {
    val config = when {
        match.hasAll -> PantryBadgeConfig(
            backgroundColor = Color(0xFFE8F5E9),
            borderColor = Color(0xFF4CAF50).copy(alpha = 0.4f),
            textColor = Color(0xFF2E7D32),
            text = "Voce tem tudo na despensa",
            icon = Icons.Default.CheckCircle
        )
        match.missingCount == 1 -> PantryBadgeConfig(
            backgroundColor = Color(0xFFFFF8E1),
            borderColor = Color(0xFFFFA000).copy(alpha = 0.4f),
            textColor = Color(0xFFE65100),
            text = "Falta apenas 1 ingrediente",
            icon = Icons.Default.Info
        )
        else -> PantryBadgeConfig(
            backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            textColor = MaterialTheme.colorScheme.onSurfaceVariant,
            text = "${match.availableIngredients.size}/${match.availableIngredients.size + match.missingCount} ingredientes disponiveis",
            icon = Icons.Default.ShoppingCart
        )
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = config.backgroundColor,
        border = BorderStroke(1.dp, config.borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                config.icon,
                contentDescription = null,
                tint = config.textColor,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                config.text,
                style = MaterialTheme.typography.labelMedium,
                color = config.textColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
        }
    }
}

// ==================== ESTADO VAZIO ====================

@Composable
private fun EmptyRecipesView(mealType: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Restaurant,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Nenhuma receita de $mealType",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Adicione mais itens a sua despensa para receber sugestoes de receitas.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}
