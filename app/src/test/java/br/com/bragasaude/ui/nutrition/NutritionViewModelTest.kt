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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import kotlinx.coroutines.withTimeout
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
        val dummyManifest = br.com.bragasaude.domain.GroceryIngredientCatalog(ingredients = emptyList(), components = emptyMap())
        coEvery { catalogRepo.fetchGroceryIngredients(any()) } returns dummyManifest
        coEvery { catalogRepo.fetchGroceryIngredients() } returns dummyManifest
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun foodSearchReturnsRestrictedItemsWithClinicalWarningForFactualLogging() = runTest(testDispatcher) {
        var vm: NutritionViewModel? = null
        try {
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
                ProfileEntity(userId = "test_user_id", hasDiabetes = true, hasHypertension = false)
            )
            every { profileRepo.getProfile("test_user_id") } returns profileFlow

            val model = NutritionViewModel(
                catalogRepo, nutritionRepo, profileRepo, vitalsRepo, groceryRepo, weeklyRepo, auth, context
            )
            vm = model

            testScheduler.advanceTimeBy(300)
            runCurrent()

            model.onSearchQueryChanged("Pão")
            testScheduler.advanceTimeBy(100)
            runCurrent()

            val results = model.searchResults.value
            assertEquals(1, results.size)
            val bread = results.first()
            assertEquals("Pão Francês", bread.name)
            assertNotNull(bread.clinicalWarning)
            assertTrue(bread.clinicalWarning!!.contains("diabetes"))
        } finally {
            vm?.viewModelScope?.cancel()
        }
    }

    @Test
    fun functionalSuggestionsEmitEvenWhenGroceryListIsEmpty() = runTest(testDispatcher) {
        var vm: NutritionViewModel? = null
        try {
            every { nutritionRepo.getFullFoodCatalog() } returns flowOf(sampleFoodCatalog)
            every { nutritionRepo.getSafeFoodCatalog(any()) } returns flowOf(sampleFoodCatalog)
            every { groceryRepo.getGroceryList("test_user_id") } returns flowOf(emptyList())
            every { weeklyRepo.stock("test_user_id") } returns flowOf(emptyList())
            every { profileRepo.getProfile("test_user_id") } returns flowOf(null)

            val model = NutritionViewModel(
                catalogRepo, nutritionRepo, profileRepo, vitalsRepo, groceryRepo, weeklyRepo, auth, context
            )
            vm = model

            Thread.sleep(150)
            runCurrent()

            // Grocery list is empty
            assertTrue(model.groceryList.value.isEmpty())

            // Suggestions should still be generated from catalog
            val suggestions = model.functionalSuggestionGroups.value
            assertNotNull(suggestions)
            assertTrue("Sugestões não devem estar bloqueadas quando a lista for vazia", suggestions.isNotEmpty())
        } finally {
            vm?.viewModelScope?.cancel()
        }
    }

    @Test
    fun functionalSuggestionsRecomputeWhenPantryStockEmits() = runTest(testDispatcher) {
        var vm: NutritionViewModel? = null
        try {
            val stockFlow = MutableStateFlow<List<GroceryPantryStockEntity>>(emptyList())
            every { nutritionRepo.getFullFoodCatalog() } returns flowOf(sampleFoodCatalog)
            every { nutritionRepo.getSafeFoodCatalog(any()) } returns flowOf(sampleFoodCatalog)
            every { groceryRepo.getGroceryList("test_user_id") } returns flowOf(emptyList())
            every { weeklyRepo.stock("test_user_id") } returns stockFlow
            every { profileRepo.getProfile("test_user_id") } returns flowOf(null)

            val model = NutritionViewModel(
                catalogRepo, nutritionRepo, profileRepo, vitalsRepo, groceryRepo, weeklyRepo, auth, context
            )
            vm = model

            Thread.sleep(150)
            runCurrent()

            val initialSuggestions = model.functionalSuggestionGroups.value
            assertNotNull(initialSuggestions)
            assertTrue(initialSuggestions.isNotEmpty())

            // Adding stock for Aveia
            stockFlow.value = listOf(
                GroceryPantryStockEntity(userId = "test_user_id", ingredientSlug = "food_aveia", unit = "g", availableAmount = 500.0)
            )
            Thread.sleep(150)
            runCurrent()

            val updatedSuggestions = model.functionalSuggestionGroups.value
            assertNotNull(updatedSuggestions)
            assertTrue(updatedSuggestions.isNotEmpty())
        } finally {
            vm?.viewModelScope?.cancel()
        }
    }
}
