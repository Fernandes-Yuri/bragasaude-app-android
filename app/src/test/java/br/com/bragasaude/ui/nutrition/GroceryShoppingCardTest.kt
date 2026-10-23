package br.com.bragasaude.ui.nutrition

import android.app.Application
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.GroceryRetailCardEntity
import br.com.bragasaude.ui.theme.BragasaudeTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w360dp-h800dp-mdpi")
class GroceryShoppingCardTest {
    @get:Rule val compose = createComposeRule()
    private val row = GroceryListItemEntity("rice", "user", "2026-10-19", "rice", "Arroz integral",
        "Cereais", 500, 1000, "1 pacote", 8.0, retailCardId = "card")
    private val card = GroceryRetailCardEntity("card", "Arroz integral", "Cereais", "", "1 kg", 1.0,
        "kg", 3600.0, 800.0, 70.0, 10.0, 8.0, null, "[]", "[]")

    @Test fun shopping_actions_update_the_same_item_and_quantity_is_reversible() {
        var item by mutableStateOf(row)
        var removed = false
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                GroceryShoppingCard(item, card, { item = item.copy(isCheckedInPantry = it) }, {}, { removed = true },
                    { item = item.copy(quantity = item.quantity + 1, estimatedPriceBrl = item.estimatedPriceBrl + 8.0) },
                    { item = item.copy(quantity = item.quantity - 1, estimatedPriceBrl = item.estimatedPriceBrl - 8.0) })
            }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNode(isToggleable()).performClick().assertIsOn()
        compose.onNodeWithText("Comprado").assertIsDisplayed()
        compose.onNodeWithContentDescription("Editar Arroz integral").assertIsNotEnabled()
        compose.onNode(isToggleable()).performClick().assertIsOff()
        compose.onNodeWithContentDescription("Editar Arroz integral").performClick()
        compose.onNodeWithText("−").assertIsNotEnabled()
        compose.onNodeWithText("+").performClick()
        compose.runOnIdle { assertEquals(2, item.quantity); assertEquals(16.0, item.estimatedPriceBrl, 0.01) }
        compose.onNodeWithText("−").performClick()
        compose.runOnIdle { assertEquals(1, item.quantity) }
        compose.onNodeWithText("Concluir").performClick()
        compose.onNodeWithContentDescription("Remover Arroz integral").performClick()
        compose.runOnIdle { assertTrue(removed) }
    }

    @Test fun manual_item_uses_manual_editor_and_does_not_invent_a_price() {
        var edited = false
        compose.setContent {
            BragasaudeTheme(forceThemeMode = "LIGHT") {
                GroceryShoppingCard(row.copy(retailCardId = null, estimatedPriceBrl = 0.0), null, {}, { edited = true }, {})
            }
        }
        compose.onNodeWithText("Preço não informado").assertIsDisplayed()
        compose.onNodeWithContentDescription("Editar Arroz integral").performClick()
        compose.runOnIdle { assertTrue(edited) }
    }
}
