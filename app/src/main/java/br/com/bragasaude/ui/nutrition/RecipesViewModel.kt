package br.com.bragasaude.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.domain.RecipePantryMatch
import br.com.bragasaude.domain.RecipeEngine
import br.com.bragasaude.data.remote.repository.GroceryRepository
import br.com.bragasaude.data.remote.repository.ProfileRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import br.com.bragasaude.util.BragaConstants

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
 * Coleta os itens da despensa do usuário, aplica o filtro clínico do perfil
 * e cruzar com o catálogo de receitas para sugerir preparações caseiras.
 */
@HiltViewModel
class RecipesViewModel @Inject constructor(
    private val groceryRepository: GroceryRepository,
    private val profileRepository: ProfileRepository,
    private val recipeEngine: RecipeEngine,
    private val auth: FirebaseAuth
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
        MealTab("BREAKFAST", "Café", "☀️"),
        MealTab("LUNCH", "Almoço", "🍲"),
        MealTab("SNACK", "Lanche", "🥪"),
        MealTab("DINNER", "Jantar", "🌙")
    )

    init {
        loadRecipes()
    }

    /**
     * Carrega as receitas recomendadas cruzando despensa e perfil.
     */
    private fun loadRecipes() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Combina despensa e perfil de forma reativa sem aninhamento
                kotlinx.coroutines.flow.combine(
                    groceryRepository.getPantryItems(currentUserId),
                    profileRepository.getProfile(currentUserId)
                ) { pantryItems, profile ->
                    pantryItems to profile
                }.collectLatest { (pantryItems, profile) ->
                    // Calcula receitas para cada tipo de refeição
                    _breakfastRecipes.value = recipeEngine.findBestRecipes(pantryItems, profile, "BREAKFAST")
                    _lunchRecipes.value = recipeEngine.findBestRecipes(pantryItems, profile, "LUNCH")
                    _snackRecipes.value = recipeEngine.findBestRecipes(pantryItems, profile, "SNACK")
                    _dinnerRecipes.value = recipeEngine.findBestRecipes(pantryItems, profile, "DINNER")

                    _isLoading.value = false
                }
            } catch (e: Exception) {
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
