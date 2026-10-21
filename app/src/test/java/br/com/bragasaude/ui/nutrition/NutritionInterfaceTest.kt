package br.com.bragasaude.ui.nutrition

import android.app.Application
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
                RecipeMealTabs(labels.map { MealTab("", it, "") }, selected) { selected = it }
            }
        }
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
        assertTrue(compose.onNodeWithText("Arroz da lista").fetchSemanticsNode().boundsInRoot.top >= actionAfter.bottom)
        compose.onNodeWithContentDescription("Fechar lista de compras").assertIsDisplayed()
    }

    @Test fun catalog_products_share_scroll_with_the_planning_header() {
        renderGrocery()
        compose.onNodeWithTag("groceryCatalog").performScrollToNode(hasText("Arroz do catálogo"))
        compose.onNodeWithText("Arroz do catálogo").assertIsDisplayed()
        compose.onNodeWithText("Período da lista").assertIsNotDisplayed()
        compose.onNodeWithText("Minha lista").assertIsDisplayed()
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
    }

    @Test fun quick_budgets_remain_single_line_and_update_the_selection() {
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                WeeklyGroceryPreferencesSheet(WeeklyGroceryPreferences(), {}, {})
            }
        }
        listOf("80", "120", "160", "200").forEach { amount ->
            compose.onNodeWithText("R$ $amount").performScrollTo().assertIsDisplayed().performClick()
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
        compose.onNodeWithText("Tenho uma meta específica prescrita por nutricionista")
            .performScrollTo().performClick()
        compose.onAllNodes(isToggleable()).filter(isOn()).assertCountEquals(1)
    }

    private fun assertSingleLine(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertEquals(1, layouts.single().lineCount)
        assertFalse(layouts.single().hasVisualOverflow)
    }

    private fun renderGrocery() {
        val row = GroceryListItemEntity("row", "user", "2026-10-19", "rice", "Arroz da lista",
            "Cereais", 500, 1000, "1 pacote", 8.0, retailCardId = "card")
        val card = GroceryRetailCardEntity("card", "Arroz do catálogo", "Cereais", "", "1 kg", 1.0,
            "kg", 3600.0, 800.0, 70.0, 10.0, 8.0, null, "[]", "[]")
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                GroceryListBottomSheet(listOf(row), {}, { _, _ -> }, {}, {},
                    retailCards = listOf(card), weeklyGoal = GroceryWeeklyGoal(12600.0))
            }
        }
    }
}
