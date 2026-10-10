package br.com.bragasaude.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.repository.GroceryRepository
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.remote.repository.WeeklyGrocerySummaryRepository
import br.com.bragasaude.domain.RecipeEngine
import br.com.bragasaude.domain.RecipePantryMatch
import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tipo de refeição com label para exibição na UI.
 */
data class MealTab(
    val key: String,
    val label: String,
    val icon: String
)

/**
 * ViewModel do módulo "O Que Cozinhar Hoje?".
 *
 * Coleta os itens da lista de compras do usuário e o estoque real da despensa,
 * aplica o filtro clínico do perfil e cruza com o catálogo de receitas para sugerir
 * preparações caseiras priorizando o que já está na despensa (READY_TO_COOK).
 */
@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val groceryRepository: GroceryRepository,
    private val profileRepository: ProfileRepository,
    private val recipeEngine: RecipeEngine,
    private val auth: FirebaseAuth,
    private val weeklyGrocerySummaryRepository: WeeklyGrocerySummaryRepository
) : ViewModel() {

    private val currentUserId: String
        get() = auth.currentUser?.uid ?: BragaConstants.GUEST_UID

    // Estado de carregamento
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Receitas por tipo de refeição
    private val _breakfastRecipes = MutableStateFlow<List<RecipePantryMatch>>(emptyList())
    val breakfastRecipes: StateFlow<List<RecipePantryMatch>> = _breakfastRecipes.asStateFlow()

    private val _lunchRecipes = MutableStateFlow<List<RecipePantryMatch>>(emptyList())
    val lunchRecipes: StateFlow<List<RecipePantryMatch>> = _lunchRecipes.asStateFlow()

    private val _snackRecipes = MutableStateFlow<List<RecipePantryMatch>>(emptyList())
    val snackRecipes: StateFlow<List<RecipePantryMatch>> = _snackRecipes.asStateFlow()

    private val _dinnerRecipes = MutableStateFlow<List<RecipePantryMatch>>(emptyList())
    val dinnerRecipes: StateFlow<List<RecipePantryMatch>> = _dinnerRecipes.asStateFlow()

    // Abas disponíveis para navegação
    val mealTabs = listOf(
        MealTab("BREAKFAST", "Café", ""),
        MealTab("LUNCH", "Almoço", ""),
        MealTab("SNACK", "Lanche", ""),
        MealTab("DINNER", "Jantar", "")
    )

    init {
        loadRecipes()
    }

    /**
     * Carrega as receitas recomendadas cruzando lista de compras, estoque físico da despensa e perfil.
     */
    private fun loadRecipes() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Combina lista de compras, perfil e estoque físico de forma reativa
                kotlinx.coroutines.flow.combine(
                    groceryRepository.getGroceryList(currentUserId),
                    profileRepository.getProfile(currentUserId),
                    weeklyGrocerySummaryRepository.stock(currentUserId)
                ) { pantryItems, profile, stock ->
                    Triple(pantryItems, profile, stock)
                }.collectLatest { (pantryItems, profile, stock) ->
                    val hasManualList = pantryItems.any {
                        it.isManual || it.category == "Minha lista" || it.remoteId.contains(":manual:")
                    }
                    // Calcula receitas para cada tipo de refeição com estoque real
                    _breakfastRecipes.value = recipeEngine.findBestRecipes(pantryItems, profile, "BREAKFAST", includeMissing = hasManualList, pantryStock = stock)
                    _lunchRecipes.value = recipeEngine.findBestRecipes(pantryItems, profile, "LUNCH", includeMissing = hasManualList, pantryStock = stock)
                    _snackRecipes.value = recipeEngine.findBestRecipes(pantryItems, profile, "SNACK", includeMissing = hasManualList, pantryStock = stock)
                    _dinnerRecipes.value = recipeEngine.findBestRecipes(pantryItems, profile, "DINNER", includeMissing = hasManualList, pantryStock = stock)

                    _isLoading.value = false
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _breakfastRecipes.value = emptyList()
                _lunchRecipes.value = emptyList()
                _snackRecipes.value = emptyList()
                _dinnerRecipes.value = emptyList()
                android.util.Log.e("RecipesVM", "Erro ao carregar receitas: ${e.message}")
                _isLoading.value = false
            }
        }
    }

    /**
     * Retorna as receitas do tipo de refeição especificado.
     */
    fun getRecipesForMealType(mealType: String): List<RecipePantryMatch> {
        return when (mealType) {
            "BREAKFAST" -> _breakfastRecipes.value
            "LUNCH" -> _lunchRecipes.value
            "SNACK" -> _snackRecipes.value
            "DINNER" -> _dinnerRecipes.value
            else -> emptyList()
        }
    }

    /**
     * Sugere adicionar ingredientes faltantes à lista de compras.
     */
    fun getMissingIngredientsForRecipe(match: RecipePantryMatch): List<String> {
        return recipeEngine.getMissingIngredientsForRecipe(match)
    }
}
