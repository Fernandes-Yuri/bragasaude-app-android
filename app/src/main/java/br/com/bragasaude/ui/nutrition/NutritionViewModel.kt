package br.com.bragasaude.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.FoodEntity
import br.com.bragasaude.data.local.ExamItemEntity
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.remote.model.*
import br.com.bragasaude.data.remote.repository.CatalogRepository
import br.com.bragasaude.data.remote.repository.ExamsRepository
import br.com.bragasaude.data.remote.repository.NutritionRepository
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.remote.repository.GroceryRepository
import br.com.bragasaude.data.util.toRemote
import br.com.bragasaude.domain.GamificationActionType
import br.com.bragasaude.domain.GamificationEngine
import br.com.bragasaude.domain.HealthCalculators
import br.com.bragasaude.domain.MealHealthFlags
import br.com.bragasaude.domain.ProfileHealthFlags
import br.com.bragasaude.domain.XpGrantService
import br.com.bragasaude.domain.WeeklyGroceryEngine
import br.com.bragasaude.domain.GroceryPdfExporter
import android.content.Context
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

import br.com.bragasaude.data.remote.repository.VitalsRepository
import br.com.bragasaude.domain.MealLogItem
import br.com.bragasaude.domain.NutritionalSuggestionGroup
import br.com.bragasaude.domain.NutritionSuggestionEngine

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val nutritionRepository: NutritionRepository,
    private val profileRepository: ProfileRepository,
    private val examsRepository: ExamsRepository,
    private val vitalsRepository: VitalsRepository,
    private val groceryRepository: GroceryRepository,
    private val xpGrantService: XpGrantService,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _profile = MutableStateFlow<RemoteProfile?>(null)
    val profile = _profile.asStateFlow()

    private val _mealRules = MutableStateFlow<List<RemoteMealRule>>(emptyList())
    private val _foodCatalog = MutableStateFlow<List<RemoteFood>>(emptyList())
    private val _latestExamItems = MutableStateFlow<List<RemoteExamItem>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<RemoteFood>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    val todayLoggedMeals: StateFlow<List<MealLogItem>> = nutritionRepository.todayLoggedMeals

    private val _selectedMealTab = MutableStateFlow("Café da Manhã")
    val selectedMealTab = _selectedMealTab.asStateFlow()

    fun selectMealTab(tab: String) {
        _selectedMealTab.value = tab
    }

    private val _dislikedFoodNames = MutableStateFlow<Set<String>>(emptySet())
    val dislikedFoodNames = _dislikedFoodNames.asStateFlow()

    private val userId = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"

    val groceryList: StateFlow<List<GroceryListItemEntity>> = groceryRepository.getGroceryList(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pantryItems: StateFlow<List<GroceryListItemEntity>> = groceryRepository.getPantryItems(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class ClinicalSnapshot(
        val exams: List<ExamItemEntity>,
        val vitals: List<VitalSignEntity>,
        val profile: ProfileEntity?,
        val loggedFoods: Set<String>,
        val pantryFoods: Set<String>
    )

    private val clinicalDataFlow = combine(
        examsRepository.getExamItems(userId),
        vitalsRepository.getVitalSigns(userId),
        profileRepository.getProfile(userId),
        nutritionRepository.todayLoggedMeals,
        groceryRepository.getPantryItems(userId)
    ) { exams, vitals, profileEntity, todayMeals, pantryList ->
        ClinicalSnapshot(
            exams = exams,
            vitals = vitals,
            profile = profileEntity,
            loggedFoods = todayMeals.map { it.foodName }.toSet(),
            pantryFoods = pantryList.map { it.foodName }.toSet()
        )
    }.distinctUntilChanged()

    val functionalSuggestionGroups: StateFlow<List<NutritionalSuggestionGroup>> = combine(
        clinicalDataFlow,
        catalogRepository.getFoodCatalog().distinctUntilChanged(),
        _selectedMealTab,
        _dislikedFoodNames
    ) { snapshot, catalog, currentTab, dislikes ->
        NutritionSuggestionEngine.generateSuggestions(
            exams = snapshot.exams,
            vitals = snapshot.vitals,
            profile = snapshot.profile?.toRemote(),
            catalog = catalog,
            selectedMealType = currentTab,
            dislikedFoodNames = dislikes,
            loggedFoodNamesToday = snapshot.loggedFoods,
            pantryFoodNames = snapshot.pantryFoods
        )
    }.distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyCalories = _profile.map { 
        it?.dailyCalorieTarget?.toFloat() ?: 1800f 
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1800f)

    val totalCaloriesConsumed = nutritionRepository.todayLoggedMeals.map { list ->
        list.sumOf { it.kcal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _activeAdjustments = MutableStateFlow<List<String>>(emptyList())
    val activeAdjustments = _activeAdjustments.asStateFlow()

    val nutritionDisclaimer = MutableStateFlow(
        "Sugestões de autocuidado alimentar baseadas no seu perfil e últimos exames registrados. Não constituem prescrição médica nem substituem nutricionista."
    ).asStateFlow()

    fun dismissOrSwapSuggestion(foodName: String) {
        _dislikedFoodNames.update { it + foodName }
    }

    fun resetDismissedSuggestions() {
        _dislikedFoodNames.value = emptySet()
    }

    init {
        val userId = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"
        
        viewModelScope.launch {
            catalogRepository.seedDatabaseIfNeeded()
        }

        viewModelScope.launch {
            profileRepository.getProfile(userId).collectLatest { entity ->
                val remProfile = entity?.toRemote()
                _profile.value = remProfile
                // Atualiza catálogo seguro quando o perfil clínico mudar (ex: diabetes marcado)
                loadSafeCatalog(remProfile)
            }
        }
        
        viewModelScope.launch {
            catalogRepository.getMealRules().collectLatest { entities ->
                _mealRules.value = entities.map { rule ->
                    RemoteMealRule(
                        id = rule.id,
                        mealName = rule.mealName,
                        caloriePercentage = rule.caloriePercentage,
                        vegPercentage = rule.vegPercentage,
                        proteinPercentage = rule.proteinPercentage,
                        carbPercentage = rule.carbPercentage
                    )
                }
            }
        }

        viewModelScope.launch {
            examsRepository.getExamItems(userId).collectLatest { entities ->
                val items = entities.map { it.toRemote() }
                _latestExamItems.value = items
                updateAdjustments(items)
            }
        }
    }

    private var safeCatalogJob: kotlinx.coroutines.Job? = null

    private fun loadSafeCatalog(userProfile: RemoteProfile?) {
        safeCatalogJob?.cancel()
        safeCatalogJob = viewModelScope.launch {
            nutritionRepository.getSafeFoodCatalog(userProfile).collectLatest { entities ->
                val list = entities.map { food ->
                    RemoteFood(
                        id = food.remoteId,
                        name = food.name,
                        category = food.category,
                        kcal = food.kcal,
                        carbsG = food.carbsG,
                        proteinG = food.proteinG,
                        fatG = food.fatG,
                        status = food.status,
                        isDiabetesSafe = food.isDiabetesSafe,
                        isHypertensionSafe = food.isHypertensionSafe,
                        isThyroidSafe = food.isThyroidSafe,
                        preparationRule = food.preparationRule
                    )
                }
                _foodCatalog.value = list
                if (_searchQuery.value.isBlank()) {
                    _searchResults.value = list
                } else {
                    onSearchQueryChanged(_searchQuery.value)
                }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = _foodCatalog.value
        } else {
            _searchResults.value = _foodCatalog.value.filter {
                it.name.contains(query, ignoreCase = true) || (it.category?.contains(query, ignoreCase = true) == true)
            }
        }
    }

    fun logMeal(mealType: String, food: RemoteFood, portionGrams: Int) {
        val safePortion = portionGrams.coerceIn(1, 2000)
        val kcalPer100 = food.kcal ?: 0.0
        val calculatedKcal = (kcalPer100 * safePortion) / 100.0
        val item = MealLogItem(
            mealType = mealType,
            foodName = food.name,
            portionGrams = safePortion,
            kcal = calculatedKcal
        )
        nutritionRepository.logMeal(item)

        // FASE 3 — XP quando a refeição é compatível com as condições clínicas do usuário
        val userId = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"
        val profile = _profile.value
        val mealFlags = MealHealthFlags(
            isDiabetesSafe = food.isDiabetesSafe,
            isHypertensionSafe = food.isHypertensionSafe,
            isThyroidSafe = food.isThyroidSafe
        )
        val profileFlags = ProfileHealthFlags(
            hasDiabetes = profile?.hasDiabetes == true,
            hasHypertension = profile?.hasHypertension == true,
            hasThyroidIssue = profile?.hasThyroidIssue == true
        )
        val isSafe = GamificationEngine.isMealSafeForProfile(mealFlags, profileFlags)
        viewModelScope.launch {
            xpGrantService.grantXp(
                userId = userId,
                action = GamificationActionType.SAFE_MEAL_RECORDED,
                isActionValid = isSafe,
                invalidReason = "Refeição registrada, mas fora do plano para suas condições 🍽️"
            )
        }
    }

    fun removeLoggedMeal(itemId: String) {
        nutritionRepository.removeLoggedMeal(itemId)
    }

    /**
     * Atualiza a porção (em gramas) de um item registrado, recalculando as
     * calorias proporcionalmente. O total diário é derivado do fluxo e se
     * atualiza automaticamente.
     */
    fun updateMealPortion(itemId: String, newPortionGrams: Int) {
        nutritionRepository.updateLoggedMealPortion(itemId, newPortionGrams)
    }

    fun addCustomFood(name: String, category: String, kcalPer100g: Double, carbsG: Double?, proteinG: Double?, fatG: Double?) {
        viewModelScope.launch {
            catalogRepository.addCustomFood(
                name = name,
                category = category,
                kcal = kcalPer100g,
                carbsG = carbsG,
                proteinG = proteinG,
                fatG = fatG
            )
        }
    }

    private fun updateAdjustments(exams: List<RemoteExamItem>) {
        val adjustments = mutableListOf<String>()
        val validExams = exams.filter { it.status == "confirmed" }
        
        if (validExams.any { it.itemKey == "total_cholesterol" && (it.valueNumeric ?: 0.0) > 200 }) {
            adjustments.add("Colesterol")
        }
        if (validExams.any { it.itemKey == "glucose" && (it.valueNumeric ?: 0.0) > 126 }) {
            adjustments.add("Glicose")
        }
        if (validExams.any { it.itemKey == "triglycerides" && (it.valueNumeric ?: 0.0) > 150 }) {
            adjustments.add("Triglicerídeos")
        }
        _activeAdjustments.value = adjustments
    }

    fun getRecommendations(): List<Pair<String, HealthCalculators.MealRecommendation>> {
        val profile = _profile.value ?: RemoteProfile(id = "")
        val catalog = _foodCatalog.value
        val validExams = _latestExamItems.value.filter { it.status == "confirmed" }

        return _mealRules.value.map { rule ->
            var recommendation = HealthCalculators.calculateSmartMeal(
                profile = profile,
                caloriePercentage = rule.caloriePercentage?.toFloat() ?: 0.25f,
                catalog = catalog
            )

            // 1. Ajuste para Colesterol Alto
            val cholesterol = validExams.firstOrNull { it.itemKey == "total_cholesterol" }?.valueNumeric
            if (cholesterol != null && cholesterol > 200) {
                recommendation = recommendation.copy(
                    fat = recommendation.fat.copy(
                        max = recommendation.fat.max * 0.8f,
                        avg = recommendation.fat.avg * 0.8f,
                        min = recommendation.fat.min * 0.8f
                    )
                )
            }

            // 2. Ajuste para Glicose Alta
            val glucose = validExams.firstOrNull { it.itemKey == "glucose" }?.valueNumeric
            if (glucose != null && glucose > 126) {
                recommendation = recommendation.copy(
                    carbs = recommendation.carbs.copy(
                        max = recommendation.carbs.max * 0.75f,
                        avg = recommendation.carbs.avg * 0.75f,
                        min = recommendation.carbs.min * 0.75f
                    )
                )
            }

            rule.mealName to recommendation
        }
    }

    fun generateWeeklyGroceryList() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val exams = examsRepository.getExamItems(userId).first()
                val vitals = vitalsRepository.getVitalSigns(userId).first()
                val profileEntity = profileRepository.getProfile(userId).first()
                val catalog = catalogRepository.getFoodCatalog().first()
                val dislikes = _dislikedFoodNames.value

                val newList = WeeklyGroceryEngine.generateWeeklyList(
                    userId = userId,
                    exams = exams,
                    vitals = vitals,
                    profile = profileEntity?.toRemote(),
                    catalog = catalog,
                    dislikedFoodNames = dislikes
                )
                groceryRepository.clearGroceryList(userId)
                groceryRepository.saveGroceryList(newList)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun togglePantryItem(itemId: String, isChecked: Boolean) {
        viewModelScope.launch {
            groceryRepository.updateCheckedStatus(itemId, isChecked)
        }
    }

    /**
     * Agente B1: adiciona itens sugeridos (ex.: vindos do chat) à lista,
     * ignorando duplicados por nome (case-insensitive). Chamado pelo
     * usuário via chip de confirmação — nunca em silêncio pelo assistente.
     */
    fun addSuggestedItems(items: List<String>) {
        val clean = items.map { it.trim() }.filter { it.length > 1 }.distinct()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            try {
                val existing = groceryList.value.map { it.foodName.lowercase() }.toSet()
                clean.filter { it.lowercase() !in existing }.forEach { name ->
                    groceryRepository.addItem(userId, name)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun exportAndShareGroceryPdf(context: Context) {
        val currentList = groceryList.value
        if (currentList.isNotEmpty()) {
            val uri = GroceryPdfExporter.generateAndShareGroceryPdf(
                context = context,
                items = currentList,
                userName = _profile.value?.fullName
            )
            uri?.let { GroceryPdfExporter.sharePdfUri(context, it) }
        }
    }
}
