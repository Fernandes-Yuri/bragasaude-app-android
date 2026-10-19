package br.com.bragasaude.ui.nutrition

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import br.com.bragasaude.domain.MealLogItem
import br.com.bragasaude.domain.NutritionalSuggestionGroup
import br.com.bragasaude.domain.SuggestedFoodOption
import br.com.bragasaude.ui.theme.BragasaudeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NutritionContentTest {
    @get:Rule val compose = createComposeRule()

    private val option = SuggestedFoodOption(
        foodId = "whole-grain", name = "Pão integral com sementes de girassol e castanhas sem adição de açúcar",
        category = "Cereais", portionTip = "Consuma uma fatia junto da sua refeição.",
        functionalBenefit = "Opção com fibras para variar a refeição.", kcal = 240.0,
        servingSizeGrams = 40, servingUnit = "1 fatia (40 g)"
    )
    private val group = NutritionalSuggestionGroup(
        id = "fiber", title = "Mais fibras", subtitle = "Opções para variar o café",
        observedMarker = "", targetNutrientGoal = "Fibras",
        whyItMatters = "Fibras fazem parte de uma alimentação variada.", options = listOf(option)
    )

    @Test fun registration_is_first_action_and_opens_search() {
        var registered = false
        render(onRegister = { registered = true })
        compose.onNodeWithTag("registerFood").assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(registered) }
        val action = compose.onNodeWithTag("registerFood").fetchSemanticsNode().boundsInRoot
        val summary = compose.onNodeWithText("Total de hoje").fetchSemanticsNode().boundsInRoot
        assertTrue("Registro deve vir antes das calorias", action.bottom < summary.top)
        compose.onNodeWithText("Criar Manual").assertDoesNotExist()
        compose.onNodeWithText("Ainda sem registros").assertIsDisplayed()
        screenshot("01-primeiro-acesso")
    }

    @Test fun switching_meals_and_removing_food_keeps_registration_available() {
        val state = mutableStateOf(NutritionContentState(meals = listOf(
            MealLogItem(id = "lunch", mealType = "Almoço", foodName = "Arroz com feijão", portionGrams = 150, kcal = 180.0)
        )))
        render(state = state, onSelectMeal = { state.value = state.value.copy(selectedMeal = it) },
            onRemoveMeal = { id -> state.value = state.value.copy(meals = state.value.meals.filterNot { it.id == id }) })
        compose.onNodeWithText("Almoço").performClick()
        compose.onNodeWithText("Arroz com feijão").assertIsDisplayed()
        compose.onNodeWithTag("registerFood").assertIsDisplayed()
        screenshot("02-refeicao-registrada")
        compose.onNodeWithContentDescription("Remover Arroz com feijão").performClick()
        compose.onNodeWithText("Ainda sem registros").assertIsDisplayed()
    }

    @Test fun suggestions_wrap_long_names_at_large_font_and_register_the_shown_portion() {
        var added: SuggestedFoodOption? = null
        render(state = mutableStateOf(NutritionContentState(suggestions = listOf(group))), fontScale = 1.5f,
            onAdd = { added = it })
        compose.onNodeWithTag("registerFood").assertIsDisplayed()
        screenshot("03-fonte-ampliada")
        compose.onNodeWithTag("nutritionContent").performScrollToNode(hasText("Sugestões para esta refeição"))
        compose.onNodeWithText("Sugestões para esta refeição").performClick()
        compose.onNodeWithTag("nutritionContent").performScrollToNode(hasText(option.name))
        compose.onNodeWithText(option.name).assertIsDisplayed()
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag("suggestionName-fiber-whole-grain").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        assertTrue(results.isNotEmpty())
        assertFalse("O nome completo não pode ser cortado", results.single().hasVisualOverflow)
        compose.onNodeWithText(option.functionalBenefit).assertDoesNotExist()
        compose.onNodeWithTag("nutritionContent").performScrollToNode(hasText("Registrar porção"))
        screenshot("04-sugestao-nome-longo")
        compose.onNodeWithText("Registrar porção").performClick()
        compose.runOnIdle { assertEquals(option, added) }
    }

    @Test fun explanation_and_disclaimer_are_accessible_without_filling_the_list() {
        render(state = mutableStateOf(NutritionContentState(suggestions = listOf(group))))
        compose.onNodeWithText("Sugestões para esta refeição").performClick()
        compose.onNodeWithTag("nutritionContent").performScrollToNode(hasText("Por que estas opções?"))
        compose.onNodeWithText("Por que estas opções?").performClick()
        compose.onNodeWithText(group.whyItMatters).assertExists()
        compose.onNodeWithText(option.functionalBenefit).assertExists()
        compose.onNodeWithText(group.disclaimer).assertExists()
        screenshot("05-detalhes-da-sugestao")
    }

    @Test fun secondary_links_and_plan_remain_reachable_in_dark_mode() {
        var recipes = false
        var grocery = false
        render(dark = true, onRecipes = { recipes = true }, onGrocery = { grocery = true })
        screenshot("06-modo-escuro")
        compose.onNodeWithTag("nutritionContent").performScrollToNode(hasText("Receitas"))
        compose.onNodeWithText("Receitas").performClick()
        compose.onNodeWithText("Lista de compras").performClick()
        compose.runOnIdle { assertTrue(recipes); assertTrue(grocery) }
        compose.onNodeWithTag("nutritionContent").performScrollToNode(hasText("Sobre seu plano"))
        compose.onNodeWithText("Sobre seu plano").performClick()
        compose.onNodeWithTag("nutritionContent").performScrollToNode(hasText("Meta calculada a partir do seu perfil"))
        compose.onNodeWithText("Meta calculada a partir do seu perfil").assertIsDisplayed()
    }

    private fun render(
        state: MutableState<NutritionContentState> = mutableStateOf(NutritionContentState()),
        fontScale: Float = 1f, dark: Boolean = false,
        onRegister: () -> Unit = {}, onSelectMeal: (String) -> Unit = {},
        onRemoveMeal: (String) -> Unit = {}, onAdd: (SuggestedFoodOption) -> Unit = {},
        onRecipes: () -> Unit = {}, onGrocery: () -> Unit = {}
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BragasaudeTheme(forceThemeMode = if (dark) "DARK" else "LIGHT") {
                    NutritionContent(state.value, onSelectMeal, onRegister, onRemoveMeal,
                        onEditGoal = {}, onOpenRecipes = onRecipes, onOpenGrocery = onGrocery,
                        onAddSuggestion = onAdd, onDismissSuggestion = {})
                }
            }
        }
    }

    private fun screenshot(name: String) {
        // PixelCopy espera um frame de hardware que o Robolectric não produz.
        // Desenha a janela real com o Canvas nativo, inclusive o painel de detalhes.
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last()
            assertTrue(view.width > 0 && view.height > 0)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/outputs/nutrition-review/$name.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
