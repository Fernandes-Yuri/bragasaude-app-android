package br.com.bragasaude.ui.nutrition

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.FoodEntity
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.GroceryPantryStockEntity
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.remote.model.RemoteFood
import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.data.remote.repository.*
import com.google.firebase.auth.FirebaseAuth
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NutritionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var catalogRepo: CatalogRepository
    private lateinit var nutritionRepo: NutritionRepository
    private lateinit var profileRepo: ProfileRepository
    private lateinit var vitalsRepo: VitalsRepository
    private lateinit var groceryRepo: GroceryRepository
    private lateinit var weeklyRepo: WeeklyGrocerySummaryRepository
    private lateinit var auth: FirebaseAuth
    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private val sampleFoodCatalog = listOf(
        FoodEntity(
            remoteId = "food_aveia",
            name = "Aveia em Flocos",
            category = "Cereais",
            isDiabetesSafe = true,
            isHypertensionSafe = true,
            suitableMeals = listOf("Café da Manhã")
        ),
        FoodEntity(
            remoteId = "food_pao",
            name = "Pão Francês",
            category = "Panificados",
            isDiabetesSafe = false,
            isHypertensionSafe = true,
            suitableMeals = listOf("Café da Manhã")
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        catalogRepo = mockk(relaxed = true)
        nutritionRepo = mockk(relaxed = true)
        profileRepo = mockk(relaxed = true)
        vitalsRepo = mockk(relaxed = true)
        groceryRepo = mockk(relaxed = true)
        weeklyRepo = mockk(relaxed = true)
        auth = mockk(relaxed = true)
        context = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        editor = mockk(relaxed = true)

        every { auth.currentUser?.uid } returns "test_user_id"
        every { context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } returns editor
        every { editor.putStringSet(any(), any()) } returns editor
        every { editor.putBoolean(any(), any()) } returns editor
        every { editor.putFloat(any(), any()) } returns editor
        every { editor.remove(any()) } returns editor

        every { vitalsRepo.getVitalSigns(any()) } returns flowOf(emptyList())
        every { groceryRepo.getGroceryList(any()) } returns flowOf(emptyList())
        every { groceryRepo.getPantryItems(any()) } returns flowOf(emptyList())
        every { weeklyRepo.stock(any()) } returns flowOf(emptyList())
        every { weeklyRepo.meals(any(), any()) } returns flowOf(emptyList())
        every { weeklyRepo.observe(any(), any()) } returns flowOf(null)
        every { catalogRepo.getMealRules() } returns flowOf(emptyList())
        every { catalogRepo.getFoodCatalog() } returns flowOf(sampleFoodCatalog)
        coEvery { catalogRepo.fetchGroceryIngredients(any()) } returns null
        coEvery { catalogRepo.fetchGroceryIngredients() } returns null
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun foodSearchReturnsRestrictedItemsWithClinicalWarningForFactualLogging() = runTest(testDispatcher) {
        val fullCatalogFlow = MutableStateFlow(sampleFoodCatalog)
        every { nutritionRepo.getFullFoodCatalog() } returns fullCatalogFlow
        every { nutritionRepo.getSafeFoodCatalog(any()) } returns flowOf(listOf(sampleFoodCatalog.first()))
        every {
            nutritionRepo.evaluateFoodClinicalWarning(match { it.remoteId == "food_pao" }, any())
        } returns "Atenção: alto índice glicêmico para diabetes"
        every {
            nutritionRepo.evaluateFoodClinicalWarning(match { it.remoteId == "food_aveia" }, any())
        } returns null

        val profileFlow = MutableStateFlow<ProfileEntity?>(
            ProfileEntity(id = "test_user_id", hasDiabetes = true, hasHypertension = false)
        )
        every { profileRepo.getProfile("test_user_id") } returns profileFlow

        val vm = NutritionViewModel(
            catalogRepo, nutritionRepo, profileRepo, vitalsRepo, groceryRepo, weeklyRepo, auth, context
        )

        advanceUntilIdle()

        vm.onSearchQueryChanged("Pão")
        advanceUntilIdle()

        val results = vm.searchResults.value
        assertEquals(1, results.size)
        val bread = results.first()
        assertEquals("Pão Francês", bread.name)
        assertNotNull(bread.clinicalWarning)
        assertTrue(bread.clinicalWarning!!.contains("diabetes"))

        vm.viewModelScope.cancel()
    }

    @Test
    fun functionalSuggestionsEmitEvenWhenGroceryListIsEmpty() = runTest(testDispatcher) {
        every { nutritionRepo.getFullFoodCatalog() } returns flowOf(sampleFoodCatalog)
        every { nutritionRepo.getSafeFoodCatalog(any()) } returns flowOf(sampleFoodCatalog)
        every { groceryRepo.getGroceryList("test_user_id") } returns flowOf(emptyList())
        every { weeklyRepo.stock("test_user_id") } returns flowOf(emptyList())
        every { profileRepo.getProfile("test_user_id") } returns flowOf(null)

        val vm = NutritionViewModel(
            catalogRepo, nutritionRepo, profileRepo, vitalsRepo, groceryRepo, weeklyRepo, auth, context
        )

        advanceUntilIdle()

        // Grocery list is empty
        assertTrue(vm.groceryList.value.isEmpty())

        // Suggestions should still be generated from catalog
        val suggestions = vm.functionalSuggestionGroups.value
        assertNotNull(suggestions)
        assertTrue("Sugestões não devem estar bloqueadas quando a lista for vazia", suggestions.isNotEmpty())

        vm.viewModelScope.cancel()
    }

    @Test
    fun functionalSuggestionsRecomputeWhenPantryStockEmits() = runTest(testDispatcher) {
        val stockFlow = MutableStateFlow<List<GroceryPantryStockEntity>>(emptyList())
        every { nutritionRepo.getFullFoodCatalog() } returns flowOf(sampleFoodCatalog)
        every { nutritionRepo.getSafeFoodCatalog(any()) } returns flowOf(sampleFoodCatalog)
        every { groceryRepo.getGroceryList("test_user_id") } returns flowOf(emptyList())
        every { weeklyRepo.stock("test_user_id") } returns stockFlow
        every { profileRepo.getProfile("test_user_id") } returns flowOf(null)

        val vm = NutritionViewModel(
            catalogRepo, nutritionRepo, profileRepo, vitalsRepo, groceryRepo, weeklyRepo, auth, context
        )

        advanceUntilIdle()

        val initialSuggestions = vm.functionalSuggestionGroups.value
        assertNotNull(initialSuggestions)

        // Adding stock for Aveia
        stockFlow.value = listOf(
            GroceryPantryStockEntity(userId = "test_user_id", ingredientSlug = "food_aveia", unit = "g", availableAmount = 500.0)
        )
        advanceUntilIdle()

        val updatedSuggestions = vm.functionalSuggestionGroups.value
        assertNotNull(updatedSuggestions)
        assertTrue(updatedSuggestions.isNotEmpty())

        vm.viewModelScope.cancel()
    }
}
