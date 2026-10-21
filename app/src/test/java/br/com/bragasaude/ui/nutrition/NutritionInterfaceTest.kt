package br.com.bragasaude.ui.nutrition

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import java.io.File
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.GroceryRetailCardEntity
import br.com.bragasaude.domain.GroceryWeeklyGoal
import br.com.bragasaude.domain.UserNutritionGoalSetup
import br.com.bragasaude.domain.WeeklyGroceryPreferences
import br.com.bragasaude.ui.theme.BragasaudeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NutritionInterfaceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun all_four_recipe_tabs_fit_and_select_without_horizontal_scrolling() {
        var selected by mutableIntStateOf(0)
        val labels = listOf("Café", "Almoço", "Lanche", "Jantar")
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                RecipeMealTabs(labels.zip(listOf("BREAKFAST", "LUNCH", "SNACK", "DINNER")) { label, key ->
                    MealTab(key, label, "")
                }, selected) { selected = it }
            }
        }
        screenshot("08-abas-receitas")
        labels.forEachIndexed { index, label ->
            compose.onNodeWithText(label).assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(index, selected) }
            assertSingleLine(label)
        }
        val first = compose.onNodeWithText("Café").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithText("Jantar").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, last.top, 1f)
        assertTrue(last.right <= 360f)
    }

    @Test fun recipe_step_has_one_toggle_and_remains_reversible() {
        var checked by mutableStateOf(false)
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                RecipeStepItem(1, "Misture os ingredientes", checked) { checked = !checked }
            }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNode(isToggleable()).assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("1").assertDoesNotExist()
        screenshot("09-passo-concluido")
        compose.onNode(isToggleable()).performClick().assertIsOff()
        compose.onNodeWithText("1").assertIsDisplayed()
    }

    @Test fun grocery_list_scrolls_past_planning_details_while_actions_stay_fixed() {
        renderGrocery()
        compose.onNodeWithText("Minha lista").performClick()
        val actionBefore = compose.onNodeWithText("Adicionar").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("groceryItems").performScrollToNode(hasText("Arroz da lista"))
        compose.onNodeWithText("Arroz da lista").assertIsDisplayed()
        compose.onNodeWithText("Período da lista").assertIsNotDisplayed()
        val actionAfter = compose.onNodeWithText("Adicionar").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals(actionBefore, actionAfter)
        screenshot("10-lista-rolada")
        assertTrue(compose.onNodeWithText("Arroz da lista").fetchSemanticsNode().boundsInRoot.top >= actionAfter.bottom)
        compose.onNodeWithContentDescription("Fechar lista de compras").assertIsDisplayed()
    }

    @Test fun catalog_products_share_scroll_with_the_planning_header() {
        renderGrocery()
        compose.onNodeWithTag("groceryCatalog").performScrollToNode(hasText("Arroz do catálogo"))
        compose.onNodeWithText("Arroz do catálogo").assertIsDisplayed()
        compose.onNodeWithText("Período da lista").assertIsNotDisplayed()
        compose.onNodeWithText("Minha lista").assertIsDisplayed()
        screenshot("11-catalogo-rolado")
        compose.onNodeWithContentDescription("Fechar lista de compras").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w800dp-h360dp-mdpi")
    fun grocery_actions_and_products_remain_accessible_in_landscape() {
        renderGrocery()
        compose.onNodeWithTag("groceryCatalog").performScrollToNode(hasText("Arroz do catálogo"))
        compose.onNodeWithText("Arroz do catálogo").assertIsDisplayed()
        compose.onNodeWithText("Minha lista").performClick()
        compose.onNodeWithTag("groceryItems").performScrollToNode(hasText("Arroz da lista"))
        compose.onNodeWithText("Arroz da lista").assertIsDisplayed()
        compose.onNodeWithText("Adicionar").assertIsDisplayed()
        screenshot("12-lista-paisagem")
    }

    @Test fun catalog_opens_with_two_complete_uniform_cards_and_collapsed_plan() {
        renderGrocery(multipleProducts = true)
        compose.onNodeWithTag("groceryPlanDetails").assertDoesNotExist()
        compose.onNodeWithTag("retailCard-card").assertIsDisplayed()
        compose.onNodeWithTag("retailCard-meat").assertIsDisplayed()
        val first = compose.onNodeWithTag("retailCard-card").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("retailCard-meat").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertEquals(first.height, second.height, 1f)
        assertTrue("Cards should remain compact: $first", first.height <= 320f)
        assertTrue("First row must fit without scrolling: $first", first.bottom < 780f)
        compose.onNodeWithText("1 kg").assertIsDisplayed()
        compose.onNodeWithText("500 g").assertIsDisplayed()
        screenshot("15-catalogo-primeira-tela")
        compose.onNodeWithContentDescription("Ver resumo do plano").performClick()
        compose.onNodeWithTag("groceryPlanDetails").assertExists()
        compose.onNodeWithContentDescription("Recolher resumo do plano").performClick()
        compose.onNodeWithTag("groceryPlanDetails").assertDoesNotExist()
    }

    @Test fun catalog_keeps_sources_and_nutrients_in_product_details() {
        renderGrocery(multipleProducts = true)
        compose.onNodeWithTag("retailCard-meat").performClick()
        compose.onNodeWithText("Embalagem: 500 g").assertIsDisplayed()
        compose.onNodeWithText("Informações nutricionais").assertIsDisplayed()
        compose.onNodeWithText("Fonte: Mercado com nome extenso · 2026-10-18").performScrollTo().assertIsDisplayed()
        screenshot("16-detalhes-do-produto")
        compose.onNodeWithText("Fechar").performClick()
        compose.onNodeWithTag("retailCard-meat").assertIsDisplayed()
    }

    @Test fun quick_budgets_remain_single_line_and_update_the_selection() {
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                WeeklyGroceryPreferencesSheet(WeeklyGroceryPreferences(), {}, {})
            }
        }
        compose.onNodeWithTag("quickBudgets").performScrollTo()
        screenshot("14-orcamento-atalhos")
        listOf("80", "120", "160", "200").forEach { amount ->
            compose.onNodeWithText("R$ $amount").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("R$ $amount").assertIsSelected()
            assertSingleLine("R$ $amount")
        }
    }

    @Test fun final_wizard_actions_have_equal_width_and_manual_goal_is_one_toggle() {
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                NutritionGoalWizardSheet(UserNutritionGoalSetup(), 70.0, 170.0, null, null, {}, {})
            }
        }
        repeat(3) { compose.onNodeWithText("Continuar").performScrollTo().performClick() }
        compose.onNodeWithText("Aplicar Estratégia").performScrollTo().assertIsDisplayed()
        val back = compose.onNodeWithText("Voltar").fetchSemanticsNode().boundsInRoot
        val apply = compose.onNodeWithText("Aplicar Estratégia").fetchSemanticsNode().boundsInRoot
        assertEquals(back.width, apply.width, 1f)
        screenshot("13-planejamento-confirmar")
        compose.onNodeWithText("Tenho uma meta específica prescrita por nutricionista")
            .performScrollTo().performClick()
        compose.onAllNodes(isToggleable()).filter(isOn()).assertCountEquals(1)
    }

    private fun screenshot(name: String) {
        compose.runOnIdle {
            val windows = WindowInspector.getGlobalWindowViews()
            val view = windows.first()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            windows.forEach { it.draw(canvas) }
            val file = File("build/outputs/nutrition-review/$name.png")
            requireNotNull(file.parentFile).mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }

    private fun assertSingleLine(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val dimensions = "$text: box=${layout.size}, line=${layout.getLineLeft(0)}..${layout.getLineRight(0)}, bottom=${layout.getLineBottom(0)}"
        assertEquals(dimensions, 1, layout.lineCount)
        assertFalse(dimensions, layout.isLineEllipsized(0))
        assertEquals(dimensions, text.length, layout.getLineEnd(0, visibleEnd = true))
        // Native glyph metrics may differ from integer pixel bounds by less than one pixel.
        assertTrue(dimensions, layout.getLineLeft(0) >= -1f)
        assertTrue(dimensions, layout.getLineRight(0) <= layout.size.width + 1f)
        assertTrue(dimensions, layout.getLineBottom(0) <= layout.size.height + 1f)
    }

    private fun renderGrocery(multipleProducts: Boolean = false) {
        val row = GroceryListItemEntity("row", "user", "2026-10-19", "rice", "Arroz da lista",
            "Cereais", 500, 1000, "1 pacote", 8.0, retailCardId = "card")
        val card = GroceryRetailCardEntity("card", "Arroz do catálogo", "Cereais", "", "1 kg", 1.0,
            "kg", 3600.0, 800.0, 70.0, 10.0, 8.0, null, "[]", "[]")
        val products = if (multipleProducts) listOf(card,
            card.copy(id = "meat", name = "Músculo Bovino Magro", packageDisplay = "500 g", priceAvg = 16.99,
                marketsJson = """[{"market_name":"Mercado com nome extenso","last_updated":"2026-10-18"}]"""),
            card.copy(id = "coconut", name = "Água de Coco Kero Coco 1l", packageDisplay = "1 L")
        ) else listOf(card)
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                GroceryListBottomSheet(listOf(row), {}, { _, _ -> }, {}, {},
                    retailCards = products, weeklyGoal = GroceryWeeklyGoal(12600.0, planningDays = if (multipleProducts) 30 else 7))
            }
        }
    }
}
