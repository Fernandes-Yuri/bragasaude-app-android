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
import br.com.bragasaude.domain.HealthCalculators
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
import br.com.bragasaude.util.BragaConstants

@HiltViewModel
class NutritionViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val nutritionRepository: NutritionRepository,
    private val profileRepository: ProfileRepository,
    private val examsRepository: ExamsRepository,
    private val vitalsRepository: VitalsRepository,
    private val groceryRepository: GroceryRepository,
    private val auth: FirebaseAuth,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {

    private val _contributionState = MutableStateFlow(br.com.bragasaude.domain.GroceryContributionState())
    val contributionState = _contributionState.asStateFlow()
    private val _communityPrices = MutableStateFlow<List<br.com.bragasaude.domain.CommunityGroceryPrice>>(emptyList())
    val communityPrices = _communityPrices.asStateFlow()

    fun resetPriceContribution() {
        if (!_contributionState.value.submitting) _contributionState.value = br.com.bragasaude.domain.GroceryContributionState()
    }

    fun contributeGroceryPrice(item: GroceryListItemEntity, amount: String, quantity: String, unit: String, state: String, date: String) {
        if (_contributionState.value.submitting || _contributionState.value.success) return
        val value = br.com.bragasaude.domain.GroceryPriceContribution.parse(amount, quantity, unit, state, date)
        if (value == null) {
            _contributionState.value = br.com.bragasaude.domain.GroceryContributionState(message = "Confira o valor pago, a quantidade, a unidade, a data e a UF. A compra deve ser dos últimos 90 dias.")
            return
        }
        if (auth.currentUser == null) {
            _contributionState.value = br.com.bragasaude.domain.GroceryContributionState(message = "Entre na sua conta para enviar uma contribuição.")
            return
        }
        _contributionState.value = br.com.bragasaude.domain.GroceryContributionState(submitting = true)
        viewModelScope.launch {
            try {
                val response = catalogRepository.contributeGroceryPrice(item.foodName, value)
                val success = response.code in 200..299 && response.body?.optBoolean("success") == true
                _contributionState.value = br.com.bragasaude.domain.GroceryContributionState(success = success,
                    message = if (success) "Contribuição recebida. Obrigado por ajudar!" else when (response.code) {
                        401 -> "Entre na sua conta novamente para contribuir."
                        429 -> "Limite diário atingido. Você pode contribuir novamente amanhã."
                        409 -> "Sincronize seu perfil antes de contribuir."
                        422 -> "Confira os dados informados. A contribuição precisa ser de um alimento do catálogo."
                        else -> "Não foi possível enviar. Confira sua conexão e tente novamente."
                    })
                if (success) {
                    _communityPrices.value = catalogRepository.fetchCommunityGroceryPrices()
                    groceryRepository.standardizeIngredients(userId, catalogRepository.fetchGroceryIngredients())
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) {
                _contributionState.value = br.com.bragasaude.domain.GroceryContributionState(message = "Não foi possível enviar. Tente novamente depois.")
            }
        }
    }

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _profile = MutableStateFlow<RemoteProfile?>(null)
    val profile = _profile.asStateFlow()

    private val _groceryIngredients = MutableStateFlow<br.com.bragasaude.domain.GroceryIngredientCatalog?>(null)
    val manualIngredients: StateFlow<List<br.com.bragasaude.domain.GroceryIngredient>> = combine(
        _groceryIngredients, catalogRepository.getFoodCatalog(), _profile
    ) { manifest, foods, profile ->
        manifest?.ingredients.orEmpty().filter { ingredient ->
            val related = foods.filter { it.remoteId in ingredient.foodIds }
            related.any { food ->
                NutritionSuggestionEngine.isSafeFromAllergies(food, profile?.foodAllergies.orEmpty(), profile?.customFoodRestrictions) &&
                    (profile?.hasDiabetes != true || food.isDiabetesSafe) &&
                    (profile?.hasHypertension != true || food.isHypertensionSafe)
            } || (related.isEmpty() && ingredient.foodIds.isEmpty() &&
                NutritionSuggestionEngine.isSafeFromAllergies(FoodEntity(ingredient.slug, ingredient.name),
                    profile?.foodAllergies.orEmpty(), profile?.customFoodRestrictions))
        }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _groceryPlanResult = MutableStateFlow<br.com.bragasaude.domain.WeeklyGroceryPlanResult?>(null)
    val groceryPlanResult = _groceryPlanResult.asStateFlow()

    private val _groceryMessage = MutableStateFlow<String?>(null)
    val groceryMessage = _groceryMessage.asStateFlow()

    fun saveManualItem(slug: String, amountText: String, replacingId: String? = null) {
        val ingredient = manualIngredients.value.firstOrNull { it.slug == slug } ?: return
        val amount = br.com.bragasaude.domain.GroceryPurchasePlanner.parseAmount(amountText, ingredient.unit) ?: return
        viewModelScope.launch {
            try {
                groceryRepository.putManualItem(userId, ingredient, amount, "Minha lista", replacingId)
                _groceryPlanResult.value = null
                _groceryMessage.value = "Item salvo. Lista editada manualmente; gere novamente para recalcular a cobertura calórica semanal pela meta vigente."
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { _groceryMessage.value = "Não foi possível salvar o item. Tente novamente." }
        }
    }

    fun removeGroceryItem(id: String) {
        viewModelScope.launch {
            groceryRepository.removeItem(userId, id)
            _groceryPlanResult.value = null
            _groceryMessage.value = "Item removido. Lista editada manualmente; gere novamente para recalcular a cobertura calórica semanal pela meta vigente."
        }
    }

    fun clearGroceryList() {
        viewModelScope.launch {
            groceryRepository.clearGroceryList(userId)
            _groceryPlanResult.value = null
            _groceryMessage.value = null
        }
    }

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

    private val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID

    val groceryList: StateFlow<List<GroceryListItemEntity>> = groceryRepository.getGroceryList(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pantryItems: StateFlow<List<GroceryListItemEntity>> = groceryRepository.getPantryItems(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class ClinicalSnapshot(
        val exams: List<ExamItemEntity>,
        val vitals: List<VitalSignEntity>,
        val profile: ProfileEntity?,
        val loggedFoods: Set<String>,
        val pantryFoods: Set<String>,
        val groceryList: List<GroceryListItemEntity>
    )

    private val clinicalDataFlow = combine(
        examsRepository.getExamItems(userId),
        vitalsRepository.getVitalSigns(userId),
        profileRepository.getProfile(userId),
        nutritionRepository.todayLoggedMeals,
        groceryRepository.getGroceryList(userId)
    ) { exams, vitals, profileEntity, todayMeals, allGroceryList ->
        ClinicalSnapshot(
            exams = exams,
            vitals = vitals,
            profile = profileEntity,
            loggedFoods = todayMeals.map { it.foodName }.toSet(),
            pantryFoods = allGroceryList.filter { it.isCheckedInPantry }.map { it.foodName }.toSet(),
            groceryList = allGroceryList
        )
    }.distinctUntilChanged()

    val functionalSuggestionGroups: StateFlow<List<NutritionalSuggestionGroup>> = combine(
        clinicalDataFlow,
        catalogRepository.getFoodCatalog().distinctUntilChanged(),
        _selectedMealTab,
        _dislikedFoodNames
    ) { snapshot, catalog, currentTab, dislikes ->
        if (snapshot.groceryList.isEmpty()) {
            emptyList()
        } else {
            val groceryNames = snapshot.groceryList.map { it.foodName.trim().lowercase() }.toSet()
            val groceryFoodIds = snapshot.groceryList.map { it.foodId }.filter { it.isNotBlank() }.toSet()
            val pantryNames = snapshot.pantryFoods.map { it.trim().lowercase() }.toSet()
            val toBuyNames = groceryNames - pantryNames

            val userCatalog = catalog.filter { food ->
                (_groceryIngredients.value?.forFood(food.remoteId, food.name)?.any { it.slug in groceryFoodIds } == true) ||
                (food.remoteId in groceryFoodIds) ||
                (food.name.trim().lowercase() in groceryNames) ||
                groceryNames.any { gName ->
                    val fName = food.name.trim().lowercase()
                    fName.contains(gName) || gName.contains(fName)
                }
            }

            NutritionSuggestionEngine.generateSuggestions(
                exams = snapshot.exams,
                vitals = snapshot.vitals,
                profile = snapshot.profile?.toRemote(),
                catalog = userCatalog,
                selectedMealType = currentTab,
                dislikedFoodNames = dislikes,
                loggedFoodNamesToday = snapshot.loggedFoods,
                pantryFoodNames = snapshot.pantryFoods,
                groceryFoodNames = toBuyNames
            )
        }
    }.distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _userWeight = MutableStateFlow<Double?>(null)
    val userWeight = _userWeight.asStateFlow()

    private val _autoRecommendedCalories = MutableStateFlow(1800)
    val autoRecommendedCalories = _autoRecommendedCalories.asStateFlow()

    private val _isCustomCalorieTarget = MutableStateFlow(false)
    val isCustomCalorieTarget = _isCustomCalorieTarget.asStateFlow()

    private val _dailyCalories = MutableStateFlow(1800f)
    val dailyCalories = _dailyCalories.asStateFlow()

    val totalCaloriesConsumed = nutritionRepository.todayLoggedMeals.map { list ->
        list.sumOf { it.kcal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _activeAdjustments = MutableStateFlow<List<String>>(emptyList())
    val activeAdjustments = _activeAdjustments.asStateFlow()

    val nutritionDisclaimer = MutableStateFlow(
        "Sugestões de autocuidado alimentar baseadas no seu perfil e nas suas escolhas. Não constituem prescrição médica nem substituem nutricionista."
    ).asStateFlow()

    fun dismissOrSwapSuggestion(foodName: String) {
        _dislikedFoodNames.update { it + foodName }
    }

    fun resetDismissedSuggestions() {
        _dislikedFoodNames.value = emptySet()
    }

    fun setCustomCalorieTarget(newTargetKcal: Double) {
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("calorie_target_is_custom_$userId", true).apply()
                _isCustomCalorieTarget.value = true
                _dailyCalories.value = newTargetKcal.toFloat()

                profileRepository.getProfile(userId).first()?.let { profile ->
                    val updated = profile.toRemote().copy(dailyCalorieTarget = newTargetKcal)
                    profileRepository.saveProfile(updated)
                }
                if (groceryList.value.isNotEmpty()) {
                    _groceryMessage.value = "Meta diária alterada para ${newTargetKcal.toInt()} kcal. Gere a lista novamente para redimensionar as compras para a nova meta semanal."
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resetCalorieTargetToRecommended() {
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("calorie_target_is_custom_$userId", false).apply()
                _isCustomCalorieTarget.value = false
                val autoTarget = _autoRecommendedCalories.value.toFloat()
                _dailyCalories.value = autoTarget

                profileRepository.getProfile(userId).first()?.let { profile ->
                    val updated = profile.toRemote().copy(dailyCalorieTarget = autoTarget.toDouble())
                    profileRepository.saveProfile(updated)
                }
                if (groceryList.value.isNotEmpty()) {
                    _groceryMessage.value = "Meta diária restaurada para ${autoTarget.toInt()} kcal. Gere a lista novamente para redimensionar as compras para a nova meta semanal."
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
        val initialCustom = prefs.getBoolean("calorie_target_is_custom_$userId", false)
        _isCustomCalorieTarget.value = initialCustom

        viewModelScope.launch {
            val offline = catalogRepository.fetchGroceryIngredients(includePrices = false)
            _groceryIngredients.value = offline
            groceryRepository.standardizeIngredients(userId, offline)
            _communityPrices.value = catalogRepository.fetchCommunityGroceryPrices()
            val priced = catalogRepository.fetchGroceryIngredients()
            _groceryIngredients.value = priced
            groceryRepository.standardizeIngredients(userId, priced)
        }
        val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
        
        viewModelScope.launch {
            catalogRepository.seedDatabaseIfNeeded()
        }

        viewModelScope.launch {
            profileRepository.getProfile(userId).collectLatest { entity ->
                val remProfile = entity?.toRemote()
                _profile.value = remProfile

                val weight = entity?.weight ?: remProfile?.weight
                _userWeight.value = weight

                // Diretrizes FAO/OMS e Guia Alimentar: ~25 a 30 kcal/kg (média prática de 28 kcal/kg)
                val calculatedAuto = if (weight != null && weight > 0.0) {
                    (weight * 28).toInt()
                } else {
                    1800
                }
                _autoRecommendedCalories.value = calculatedAuto

                val isCustom = prefs.getBoolean("calorie_target_is_custom_$userId", false)
                _isCustomCalorieTarget.value = isCustom

                val target = if (isCustom && remProfile?.dailyCalorieTarget != null && remProfile.dailyCalorieTarget > 0) {
                    remProfile.dailyCalorieTarget.toFloat()
                } else {
                    calculatedAuto.toFloat()
                }
                _dailyCalories.value = target

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
                val ingredients = catalogRepository.fetchGroceryIngredients()
                _groceryIngredients.value = ingredients
                val catalog = catalogRepository.getFoodCatalog().first()
                val dislikes = _dislikedFoodNames.value
                val currentDailyCal = _dailyCalories.value.toDouble()

                val plan = WeeklyGroceryEngine.planWeeklyGrocery(
                    userId = userId,
                    exams = exams,
                    vitals = vitals,
                    profile = profileEntity?.toRemote(),
                    catalog = catalog,
                    dislikedFoodNames = dislikes,
                    ingredientCatalog = ingredients,
                    targetCalories = currentDailyCal
                )
                _groceryPlanResult.value = plan
                if (plan.items.isNotEmpty()) {
                    groceryRepository.replaceList(userId, plan.items)
                }
                _groceryMessage.value = plan.statusMessage
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
                _groceryPlanResult.value = null
                _groceryMessage.value = "Itens adicionados. Lista editada manualmente; gere novamente para recalcular a cobertura calórica semanal pela meta vigente."
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
