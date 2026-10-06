package br.com.bragasaude.ui.nutrition

import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.remote.repository.GroceryRepository
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.domain.RecipeEngine
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecipesViewModelTest {
    @Test fun removingAnUncheckedShoppingItemImmediatelyRemovesDependentRecipes() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var vm: RecipesViewModel? = null
        try {
            val grocery = mockk<GroceryRepository>()
            val profile = mockk<ProfileRepository>()
            val auth = mockk<FirebaseAuth>(relaxed = true)
            every { auth.currentUser?.uid } returns "user"
            val items = listOf("Abacate", "Limão").map { name ->
                GroceryListItemEntity(name, "user", "2026-10-06", name, name, "Feira",
                    100, 100, "1 un", 0.0, false)
            }
            val list = MutableStateFlow(items)
            every { grocery.getGroceryList("user") } returns list
            every { profile.getProfile("user") } returns flowOf(null)
            val model = RecipesViewModel(grocery, profile, RecipeEngine(), auth)
            vm = model
            advanceUntilIdle()
            assertTrue(model.snackRecipes.value.any { it.recipe.id == "recipe_003" })
            list.value = items.dropLast(1)
            advanceUntilIdle()
            assertTrue(model.snackRecipes.value.isEmpty())
            list.value = items
            advanceUntilIdle()
            assertTrue(model.snackRecipes.value.any { it.recipe.id == "recipe_003" })
            list.value = emptyList()
            advanceUntilIdle()
            assertTrue(model.snackRecipes.value.isEmpty())
            verify(exactly = 0) { grocery.getPantryItems(any()) }
        } finally {
            vm?.viewModelScope?.cancel()
            Dispatchers.resetMain()
        }
    }
}
