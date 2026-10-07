package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.GroceryListDao
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.domain.GroceryIngredient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ManualGroceryRepositoryTest {
    private val milk = GroceryIngredient("leite", "Leite", "L", 1000, 1000, emptyList(), emptyList(), 6.0)
    private val rice = GroceryIngredient("arroz", "Arroz", "kg", 1, 50, emptyList(), emptyList(), 8.0)

    @Test fun manualEditsKeepOneCanonicalRowAndRecalculateTheChosenQuantity() = runTest {
        val dao = mockk<GroceryListDao>()
        var rows = emptyList<GroceryListItemEntity>()
        coEvery { dao.rewriteGroceryList("u", any()) } coAnswers {
            rows = secondArg<(List<GroceryListItemEntity>) -> List<GroceryListItemEntity>>()(rows)
        }
        val repository = GroceryRepository(dao)
        repository.putManualItem("u", milk, 500, "Minha lista")
        val first = rows.single()
        assertEquals("0.500 L", first.purchaseUnitText)
        assertEquals(3.0, first.estimatedPriceBrl, 0.001)
        assertFalse(first.isCheckedInPantry)
        rows = listOf(first.copy(isCheckedInPantry = true))
        repository.putManualItem("u", milk, 1500, "Minha lista")
        assertEquals(first.remoteId, rows.single().remoteId)
        assertTrue(rows.single().isCheckedInPantry)
        assertEquals(9.0, rows.single().estimatedPriceBrl, 0.001)
        repository.putManualItem("u", rice, 750, "Minha lista", first.remoteId)
        assertEquals("arroz", rows.single().foodId)
        assertEquals("750 g", rows.single().purchaseUnitText)
        assertEquals(6.0, rows.single().estimatedPriceBrl, 0.001)
        assertFalse(rows.single().isCheckedInPantry)
        repository.removeItem("u", rows.single().remoteId)
        assertTrue(rows.isEmpty())
        coVerify(exactly = 4) { dao.rewriteGroceryList("u", any()) }
    }

    @Test fun replacingWithAnExistingIngredientDoesNotDuplicateItAndUnknownPricesStayUnknown() = runTest {
        val dao = mockk<GroceryListDao>()
        var rows = emptyList<GroceryListItemEntity>()
        coEvery { dao.rewriteGroceryList("u", any()) } coAnswers {
            rows = secondArg<(List<GroceryListItemEntity>) -> List<GroceryListItemEntity>>()(rows)
        }
        val repository = GroceryRepository(dao)
        repository.putManualItem("u", milk, 1000, "Minha lista")
        val milkId = rows.single().remoteId
        repository.putManualItem("u", rice, 500, "Minha lista")
        repository.putManualItem("u", rice.copy(price = null), 1000, "Minha lista", milkId)
        assertEquals(1, rows.size)
        assertEquals("arroz", rows.single().foodId)
        assertEquals(0.0, rows.single().estimatedPriceBrl, 0.001)
    }
}
