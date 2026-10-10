package br.com.bragasaude.ui.nutrition

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.FoodEntity
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.GroceryPantryStockEntity
import br.com.bragasaude.data.remote.model.*
import br.com.bragasaude.data.remote.repository.CatalogRepository
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
    private val vitalsRepository: VitalsRepository,
    private val groceryRepository: GroceryRepository,
    private val weeklyRepository: br.com.bragasaude.data.remote.repository.WeeklyGrocerySummaryRepository,
    private val auth: FirebaseAuth,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) : ViewModel() {
    private val userId = auth.currentUser?.uid ?: BragaConstants.GUEST_UID
    private val calendarDay = flow {
        while (true) {
            emit(java.time.LocalDate.now())
            kotlinx.coroutines.delay(60_000)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.Eagerly, java.time.LocalDate.now())
    private val _showResizeDialog = MutableStateFlow(false)
    val showResizeDialog = _showResizeDialog.asStateFlow()
    val pantryStock = weeklyRepository.stock(userId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private var observedTarget: Float? = null
    private var observedWeight: Double? = null
    private var hasObservedProfile = false
    private suspend fun invalidateWeekly(message: String) {
        weeklyRepository.invalidate(userId, message)
        _groceryMessage.value = message
    }
    private suspend fun changedTarget(target: Double) {
        if (groceryRepository.getGroceryList(userId).first().isNotEmpty()) {
            generateWeeklyGroceryList(preserveManual = true)
        }
    }
    fun keepCurrentWeeklyList() { _showResizeDialog.value = false }
    fun resizeWeeklyList() {
        _showResizeDialog.value = false
        generateWeeklyGroceryList(preserveManual = true)
    }

    private val _weeklyPreferences = MutableStateFlow(loadStoredWeeklyPreferences())
    val weeklyPreferences = _weeklyPreferences.asStateFlow()

    private fun loadStoredWeeklyPreferences(): br.com.bragasaude.domain.WeeklyGroceryPreferences {
        val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
        val tierName = prefs.getString("weekly_budget_tier_$userId", br.com.bragasaude.domain.GroceryBudgetTier.ECONOMIC.name)
        val tier = try {
            br.com.bragasaude.domain.GroceryBudgetTier.valueOf(tierName ?: br.com.bragasaude.domain.GroceryBudgetTier.ECONOMIC.name)
        } catch (_: Exception) {
            br.com.bragasaude.domain.GroceryBudgetTier.ECONOMIC
        }
        val proteinSet = prefs.getStringSet("weekly_proteins_$userId", null)?.mapNotNull { name ->
            try { br.com.bragasaude.domain.GroceryProteinPreference.valueOf(name) } catch (_: Exception) { null }
        }?.toSet() ?: setOf(br.com.bragasaude.domain.GroceryProteinPreference.EGGS, br.com.bragasaude.domain.GroceryProteinPreference.POULTRY)
        val hasStaples = prefs.getBoolean("weekly_has_staples_$userId", true)
        val maxBudget = if (prefs.contains("weekly_max_budget_$userId")) {
            prefs.getFloat("weekly_max_budget_$userId", 0f).toDouble().takeIf { it > 0 }
        } else null
        return br.com.bragasaude.domain.WeeklyGroceryPreferences(
            budgetTier = tier,
            selectedProteins = if (proteinSet.isEmpty()) setOf(br.com.bragasaude.domain.GroceryProteinPreference.EGGS) else proteinSet,
            hasPantryStaples = hasStaples,
            maxWeeklyBudgetReais = maxBudget
        )
    }

    fun updateWeeklyPreferences(newPrefs: br.com.bragasaude.domain.WeeklyGroceryPreferences) {
        val normalized = newPrefs.normalized()
        _weeklyPreferences.value = normalized
        val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString("weekly_budget_tier_$userId", normalized.budgetTier.name)
            .putStringSet("weekly_proteins_$userId", normalized.selectedProteins.map { it.name }.toSet())
            .putBoolean("weekly_has_staples_$userId", normalized.hasPantryStaples)
        if (normalized.maxWeeklyBudgetReais != null && normalized.maxWeeklyBudgetReais > 0) {
            editor.putFloat("weekly_max_budget_$userId", normalized.maxWeeklyBudgetReais.toFloat())
        } else {
            editor.remove("weekly_max_budget_$userId")
        }
        editor.apply()
    }

    private val _autoRecommendedCalories = MutableStateFlow(1800)
    val autoRecommendedCalories = _autoRecommendedCalories.asStateFlow()

    private val _isCustomCalorieTarget = MutableStateFlow(false)
    val isCustomCalorieTarget = _isCustomCalorieTarget.asStateFlow()

    private val _dailyCalories = MutableStateFlow(1800f)
    val dailyCalories = _dailyCalories.asStateFlow()

    private val _goalSetup = MutableStateFlow(loadStoredNutritionGoalSetup())
    val goalSetup = _goalSetup.asStateFlow()

    private fun loadStoredNutritionGoalSetup(): br.com.bragasaude.domain.UserNutritionGoalSetup {
        val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
        val goalStr = prefs.getString("nutrition_goal_$userId", br.com.bragasaude.domain.ClinicalDietaryGoal.MAINTENANCE.name)
        val goal = try {
            br.com.bragasaude.domain.ClinicalDietaryGoal.valueOf(goalStr ?: br.com.bragasaude.domain.ClinicalDietaryGoal.MAINTENANCE.name)
        } catch (_: Exception) {
            br.com.bragasaude.domain.ClinicalDietaryGoal.MAINTENANCE
        }

        val actStr = prefs.getString("nutrition_activity_$userId", br.com.bragasaude.domain.DailyActivityLevel.LIGHTLY_ACTIVE.name)
        val activity = try {
            br.com.bragasaude.domain.DailyActivityLevel.valueOf(actStr ?: br.com.bragasaude.domain.DailyActivityLevel.LIGHTLY_ACTIVE.name)
        } catch (_: Exception) {
            br.com.bragasaude.domain.DailyActivityLevel.LIGHTLY_ACTIVE
        }

        val isCustom = prefs.getBoolean("calorie_target_is_custom_$userId", false)
        val currentWeekly = _weeklyPreferences.value
        val manualKcal = if (isCustom) {
            val savedKcal = prefs.getFloat("calorie_target_manual_kcal_$userId", 0f).toDouble()
            if (savedKcal > 500.0) savedKcal else _dailyCalories.value.toDouble()
        } else null

        return br.com.bragasaude.domain.UserNutritionGoalSetup(
            goal = goal,
            activityLevel = activity,
            budgetTier = currentWeekly.budgetTier,
            hasPantryStaples = currentWeekly.hasPantryStaples,
            isCustomManual = isCustom,
            manualKcal = manualKcal,
            maxWeeklyBudgetReais = currentWeekly.maxWeeklyBudgetReais
        )
    }

    fun applyNutritionGoalSetup(setup: br.com.bragasaude.domain.UserNutritionGoalSetup) {
        _goalSetup.value = setup
        val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit()
            .putString("nutrition_goal_$userId", setup.goal.name)
            .putString("nutrition_activity_$userId", setup.activityLevel.name)
            .putBoolean("calorie_target_is_custom_$userId", setup.isCustomManual)
        if (setup.isCustomManual && setup.manualKcal != null) {
            editor.putFloat("calorie_target_manual_kcal_$userId", setup.manualKcal.toFloat())
        } else {
            editor.remove("calorie_target_manual_kcal_$userId")
        }
        editor.apply()

        val updatedWeeklyPrefs = _weeklyPreferences.value.copy(
            budgetTier = setup.budgetTier,
            hasPantryStaples = setup.hasPantryStaples,
            maxWeeklyBudgetReais = setup.maxWeeklyBudgetReais ?: _weeklyPreferences.value.maxWeeklyBudgetReais
        )
        updateWeeklyPreferences(updatedWeeklyPrefs)
        updateAdjustments(_profile.value, setup)

        viewModelScope.launch {
            try {
                val profile = profileRepository.getProfile(userId).first()
                val calculatedKcal = br.com.bragasaude.domain.HealthCalculators.calculateGoalSetupKcal(
                    weight = profile?.weight,
                    height = profile?.height,
                    birthDate = profile?.birthDate,
                    gender = profile?.gender,
                    setup = setup
                )

                _isCustomCalorieTarget.value = setup.isCustomManual
                _dailyCalories.value = calculatedKcal

                profile?.let {
                    val updated = it.toRemote().copy(
                        dailyCalorieTarget = calculatedKcal.toDouble(),
                        activityLevel = setup.activityLevel.name
                    )
                    profileRepository.saveProfile(updated)
                }

                if (observedTarget != calculatedKcal) {
                    changedTarget(calculatedKcal.toDouble())
                }
                observedTarget = calculatedKcal

                generateWeeklyGroceryList(preserveManual = true, preferences = updatedWeeklyPrefs)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


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

    private var hasPreparedGroceryData = false

    fun prepareGroceryData() {
        if (hasPreparedGroceryData && _groceryIngredients.value?.ingredients?.any { it.price != null && it.price > 0 } == true) return
        hasPreparedGroceryData = true
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (_communityPrices.value.isEmpty()) {
                    _communityPrices.value = catalogRepository.fetchCommunityGroceryPrices()
                }
                val priced = catalogRepository.fetchGroceryIngredients()
                _groceryIngredients.value = priced
                groceryRepository.standardizeIngredients(userId, priced)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("NutritionVM", "Falha ao sincronizar dados da lista de compras: ${e.message}")
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
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _groceryPlanResult = MutableStateFlow<br.com.bragasaude.domain.WeeklyGroceryPlanResult?>(null)
    val groceryPlanResult = _groceryPlanResult.asStateFlow()

    private val _groceryMessage = MutableStateFlow<String?>(null)
    val groceryMessage = _groceryMessage.asStateFlow()

    fun saveManualItem(slug: String, amountText: String, replacingId: String? = null) {
        val ingredient = manualIngredients.value.firstOrNull { it.slug == slug } ?: return
        val amount = br.com.bragasaude.domain.GroceryPurchasePlanner.parseAmount(amountText, ingredient.unit) ?: return
        viewModelScope.launch {
            try {
                weeklyRepository.modify(userId, "Lista modificada manualmente; redimensione para revisar a cobertura. Itens manuais serão preservados.") {
                    groceryRepository.putManualItem(userId, ingredient, amount, "Minha lista", replacingId)
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { _groceryMessage.value = "Não foi possível salvar o item. Tente novamente." }
        }
    }

    fun removeGroceryItem(id: String) {
        viewModelScope.launch {
            weeklyRepository.modify(userId, "Lista modificada manualmente; redimensione para revisar a cobertura.") {
                groceryRepository.removeItem(userId, id)
            }
        }
    }

    fun clearGroceryList() {
        viewModelScope.launch {
            weeklyRepository.clear(userId)
            _groceryPlanResult.value = null
            _groceryMessage.value = null
        }
    }

    private val _mealRules = MutableStateFlow<List<RemoteMealRule>>(emptyList())
    private val _foodCatalog = MutableStateFlow<List<RemoteFood>>(emptyList())
    private val _searchFoodCatalog = MutableStateFlow<List<RemoteFood>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<RemoteFood>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val todayLoggedMeals: StateFlow<List<MealLogItem>> = calendarDay.flatMapLatest { day ->
        weeklyRepository.meals(userId, day.toString()).map { rows ->
            rows.map { MealLogItem(it.id, it.mealType, it.foodName, it.portionGrams, it.kcal) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _selectedMealTab = MutableStateFlow("Café da Manhã")
    val selectedMealTab = _selectedMealTab.asStateFlow()

    fun selectMealTab(tab: String) {
        _selectedMealTab.value = tab
    }

    private val _dislikedFoodNames = MutableStateFlow<Set<String>>(emptySet())
    val dislikedFoodNames = _dislikedFoodNames.asStateFlow()


    val groceryList: StateFlow<List<GroceryListItemEntity>> = groceryRepository.getGroceryList(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pantryItems: StateFlow<List<GroceryListItemEntity>> = groceryRepository.getPantryItems(userId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class ClinicalSnapshot(
        val vitals: List<VitalSignEntity>,
        val profile: ProfileEntity?,
        val loggedFoods: Set<String>,
        val pantryFoods: Set<String>,
        val groceryList: List<GroceryListItemEntity>,
        val pantryStock: List<GroceryPantryStockEntity>
    )

    private val clinicalDataFlow = combine(
        vitalsRepository.getVitalSigns(userId),
        profileRepository.getProfile(userId),
        todayLoggedMeals,
        groceryRepository.getGroceryList(userId),
        weeklyRepository.stock(userId)
    ) { vitals, profileEntity, todayMeals, allGroceryList, stockList ->
        ClinicalSnapshot(
            vitals = vitals,
            profile = profileEntity,
            loggedFoods = todayMeals.map { it.foodName }.toSet(),
            pantryFoods = allGroceryList.filter { it.isCheckedInPantry }.map { it.foodName }.toSet(),
            groceryList = allGroceryList,
            pantryStock = stockList
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .distinctUntilChanged()

    val functionalSuggestionGroups: StateFlow<List<NutritionalSuggestionGroup>> = combine(
        clinicalDataFlow,
        catalogRepository.getFoodCatalog().distinctUntilChanged(),
        _selectedMealTab,
        _dislikedFoodNames,
        _groceryIngredients
    ) { snapshot, catalog, currentTab, dislikes, manifest ->
        val groceryNames = snapshot.groceryList.map { it.foodName.trim().lowercase() }.toSet()
        val groceryFoodIds = snapshot.groceryList.map { it.foodId }.filter { it.isNotBlank() }.toSet()
        val checkedPantryNames = snapshot.pantryFoods.map { it.trim().lowercase() }.toSet()

        val physicalPantrySlugs = snapshot.pantryStock.filter { it.availableAmount > 0 }.map { it.ingredientSlug.trim().lowercase() }.toSet()

        val physicalPantryFoodNames = catalog.filter { food ->
            val foodId = food.remoteId.lowercase()
            val foodName = food.name.trim().lowercase()
            physicalPantrySlugs.contains(foodId) || physicalPantrySlugs.contains(foodName) ||
                (manifest?.forFood(food.remoteId, food.name)?.any { it.slug.lowercase() in physicalPantrySlugs } == true)
        }.map { it.name.trim().lowercase() }.toSet()

        val allPantryNames = checkedPantryNames + physicalPantryFoodNames
        val toBuyNames = groceryNames - allPantryNames

        val hasPantryOrGrocery = groceryNames.isNotEmpty() || physicalPantrySlugs.isNotEmpty() || checkedPantryNames.isNotEmpty()

        val candidateCatalog = if (!hasPantryOrGrocery) {
            catalog
        } else {
            val filtered = catalog.filter { food ->
                val foodName = food.name.trim().lowercase()
                val foodId = food.remoteId
                val isPantryPhysical = physicalPantrySlugs.isNotEmpty() && (
                    physicalPantrySlugs.contains(foodId.lowercase()) ||
                    physicalPantrySlugs.contains(foodName) ||
                    (manifest?.forFood(foodId, food.name)?.any { it.slug.lowercase() in physicalPantrySlugs } == true)
                )
                val isPantryChecked = allPantryNames.contains(foodName) || allPantryNames.any { pName -> foodName.contains(pName) || pName.contains(foodName) }
                val isGrocery = (
                    (manifest?.forFood(foodId, food.name)?.any { it.slug in groceryFoodIds } == true) ||
                    (foodId in groceryFoodIds) ||
                    (foodName in groceryNames) ||
                    groceryNames.any { gName -> foodName.contains(gName) || gName.contains(foodName) }
                )
                isPantryPhysical || isPantryChecked || isGrocery
            }
            if (filtered.isNotEmpty()) filtered else catalog
        }

        NutritionSuggestionEngine.generateSuggestions(
            vitals = snapshot.vitals,
            profile = snapshot.profile?.toRemote(),
            catalog = candidateCatalog,
            selectedMealType = currentTab,
            dislikedFoodNames = dislikes,
            loggedFoodNamesToday = snapshot.loggedFoods,
            pantryFoodNames = allPantryNames,
            groceryFoodNames = toBuyNames
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .distinctUntilChanged()
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _userWeight = MutableStateFlow<Double?>(null)
    val userWeight = _userWeight.asStateFlow()

    val totalCaloriesConsumed = todayLoggedMeals.map { list ->
        list.sumOf { it.kcal }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    private val _activeAdjustments = MutableStateFlow<List<String>>(emptyList())
    val activeAdjustments = _activeAdjustments.asStateFlow()

    private fun updateAdjustments(profile: RemoteProfile?, setup: br.com.bragasaude.domain.UserNutritionGoalSetup) {
        val adjustments = mutableListOf<String>()
        when (setup.goal) {
            br.com.bragasaude.domain.ClinicalDietaryGoal.WEIGHT_LOSS -> adjustments.add("Déficit Calórico")
            br.com.bragasaude.domain.ClinicalDietaryGoal.HYPERTROPHY -> adjustments.add("Hipertrofia")
            br.com.bragasaude.domain.ClinicalDietaryGoal.MAINTENANCE -> {}
        }
        if (profile?.hasHypertension == true) {
            adjustments.add("Controle de Sódio")
        }
        if (profile?.hasDiabetes == true) {
            adjustments.add("Glicose Controlada")
        }
        _activeAdjustments.value = adjustments
    }


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
        if (!newTargetKcal.isFinite() || newTargetKcal <= 500) return
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("calorie_target_is_custom_$userId", true)
                    .putFloat("calorie_target_manual_kcal_$userId", newTargetKcal.toFloat())
                    .apply()
                _isCustomCalorieTarget.value = true
                _dailyCalories.value = newTargetKcal.toFloat()

                profileRepository.getProfile(userId).first()?.let { profile ->
                    val updated = profile.toRemote().copy(dailyCalorieTarget = newTargetKcal)
                    profileRepository.saveProfile(updated)
                }
                if (observedTarget != newTargetKcal.toFloat()) changedTarget(newTargetKcal)
                observedTarget = newTargetKcal.toFloat()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun resetCalorieTargetToRecommended() {
        viewModelScope.launch {
            try {
                val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("calorie_target_is_custom_$userId", false)
                    .remove("calorie_target_manual_kcal_$userId")
                    .apply()
                _isCustomCalorieTarget.value = false
                val autoTarget = _autoRecommendedCalories.value.toFloat()
                _dailyCalories.value = autoTarget

                profileRepository.getProfile(userId).first()?.let { profile ->
                    val updated = profile.toRemote().copy(dailyCalorieTarget = autoTarget.toDouble())
                    profileRepository.saveProfile(updated)
                }
                if (observedTarget != autoTarget) changedTarget(autoTarget.toDouble())
                observedTarget = autoTarget
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    init {
        viewModelScope.launch {
            calendarDay.map { br.com.bragasaude.domain.GroceryWeek.start(it) }.distinctUntilChanged().collectLatest { week ->
                weeklyRepository.observe(userId, week).collect { result ->
                    _groceryPlanResult.value = result
                    _groceryMessage.value = result?.statusMessage
                    val target = observedTarget
                    if (result != null && target != null &&
                        br.com.bragasaude.domain.WeeklyGroceryPlanValidity.needsResize(result, target.toDouble(), observedWeight)) changedTarget(target.toDouble())
                }
            }
        }
        val prefs = context.getSharedPreferences("braga_prefs", Context.MODE_PRIVATE)
        val initialCustom = prefs.getBoolean("calorie_target_is_custom_$userId", false)
        _isCustomCalorieTarget.value = initialCustom

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val offline = catalogRepository.fetchGroceryIngredients(includePrices = false)
            _groceryIngredients.value = offline
            // Agenda atualização de preços e catálogo em segundo plano após a tela estabilizar
            kotlinx.coroutines.delay(2500)
            prepareGroceryData()
        }

        viewModelScope.launch {
            profileRepository.getProfile(userId).collectLatest { entity ->
                val remProfile = entity?.toRemote()
                _profile.value = remProfile
                updateAdjustments(remProfile, _goalSetup.value)

                val weight = entity?.weight ?: remProfile?.weight
                _userWeight.value = weight

                // Cálculo clínico: TMB (Mifflin-St Jeor) * FAF (Atividade Física) + Ajuste de Objetivo
                val goalSetupValue = _goalSetup.value
                val calculatedAuto = HealthCalculators.calculateProfileCalorieTarget(
                    weight = weight,
                    height = entity?.height ?: remProfile?.height,
                    birthDate = entity?.birthDate ?: remProfile?.birthDate,
                    gender = entity?.gender ?: remProfile?.gender,
                    activityLevel = entity?.activityLevel ?: remProfile?.activityLevel,
                    weightGoal = entity?.weightGoal ?: remProfile?.weightGoal,
                    dietaryGoalStr = goalSetupValue.goal.name
                ).toInt()
                _autoRecommendedCalories.value = calculatedAuto

                val isCustom = prefs.getBoolean("calorie_target_is_custom_$userId", false)
                _isCustomCalorieTarget.value = isCustom

                val target = if (isCustom && remProfile?.dailyCalorieTarget != null && remProfile.dailyCalorieTarget > 0) {
                    remProfile.dailyCalorieTarget.toFloat()
                } else {
                    calculatedAuto.toFloat()
                }
                val previousTarget = observedTarget
                val changedWeight = hasObservedProfile && observedWeight != weight
                observedWeight = weight
                hasObservedProfile = true
                observedTarget = target
                _dailyCalories.value = target
                val persisted = _groceryPlanResult.value
                if (changedWeight || (previousTarget != null && previousTarget != target) ||
                    (previousTarget == null && persisted != null && !persisted.isManuallyModified &&
                     (kotlin.math.abs(persisted.targetWeeklyCalories - target * 7) > 1.0 ||
                      (persisted.profileWeight != null && persisted.profileWeight != weight)))) {
                    changedTarget(target.toDouble())
                }

                // Atualiza catálogo seguro quando o perfil clínico mudar (ex: diabetes marcado)
                loadSafeCatalog(remProfile)
                loadFullSearchCatalog(remProfile)
            }
        }
        
        loadFullSearchCatalog(null)

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
                        preparationRule = food.preparationRule,
                        servingSizeGrams = food.servingSizeGrams,
                        servingUnit = food.servingUnit,
                        minServingGrams = food.minServingGrams,
                        maxServingGrams = food.maxServingGrams
                    )
                }
                _foodCatalog.value = list
            }
        }
    }

    private var fullCatalogJob: kotlinx.coroutines.Job? = null

    private fun loadFullSearchCatalog(userProfile: RemoteProfile?) {
        fullCatalogJob?.cancel()
        fullCatalogJob = viewModelScope.launch {
            nutritionRepository.getFullFoodCatalog().collectLatest { entities ->
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
                        preparationRule = food.preparationRule,
                        servingSizeGrams = food.servingSizeGrams,
                        servingUnit = food.servingUnit,
                        minServingGrams = food.minServingGrams,
                        maxServingGrams = food.maxServingGrams,
                        clinicalWarning = nutritionRepository.evaluateFoodClinicalWarning(food, userProfile)
                    )
                }
                _searchFoodCatalog.value = list
                applySearchFilter(_searchQuery.value)
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        applySearchFilter(query)
    }

    private fun applySearchFilter(query: String) {
        val baseList = _searchFoodCatalog.value
        if (query.isBlank()) {
            _searchResults.value = baseList
        } else {
            _searchResults.value = baseList.filter {
                it.name.contains(query, ignoreCase = true) || (it.category?.contains(query, ignoreCase = true) == true)
            }
        }
    }

    fun logMeal(mealType: String, food: RemoteFood, portionGrams: Int) {
        viewModelScope.launch {
            val portion = portionGrams.coerceIn(1, 2000)
            val catalog = _groceryIngredients.value ?: catalogRepository.fetchGroceryIngredients()
            val meal = br.com.bragasaude.data.local.GroceryMealEntity(
                java.util.UUID.randomUUID().toString(), userId, java.time.LocalDate.now().toString(),
                food.id.orEmpty(), food.name, mealType, portion, (food.kcal ?: 0.0) * portion / 100.0)
            val limitations = weeklyRepository.log(meal, catalog)
            if (limitations.isNotEmpty()) _groceryMessage.value = limitations.joinToString(" ")
        }
    }

    fun removeLoggedMeal(itemId: String) {
        viewModelScope.launch { weeklyRepository.removeMeal(userId, itemId) }
    }

    fun updateMealPortion(itemId: String, newPortionGrams: Int) {
        viewModelScope.launch {
            val catalog = _groceryIngredients.value ?: catalogRepository.fetchGroceryIngredients()
            val limitations = weeklyRepository.updatePortion(userId, itemId, newPortionGrams, catalog)
            if (limitations.isNotEmpty()) _groceryMessage.value = limitations.joinToString(" ")
        }
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


    val recommendations: StateFlow<List<Pair<String, HealthCalculators.MealRecommendation>>> = combine(
        _profile,
        _foodCatalog,
        _mealRules
    ) { profile, catalog, rules ->
        val userProfile = profile ?: RemoteProfile(id = "")
        rules.map { rule ->
            val recommendation = HealthCalculators.calculateSmartMeal(
                profile = userProfile,
                caloriePercentage = rule.caloriePercentage?.toFloat() ?: 0.25f,
                catalog = catalog
            )
            rule.mealName to recommendation
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getRecommendations(): List<Pair<String, HealthCalculators.MealRecommendation>> {
        return recommendations.value.ifEmpty {
            val profile = _profile.value ?: RemoteProfile(id = "")
            val catalog = _foodCatalog.value

            _mealRules.value.map { rule ->
                val recommendation = HealthCalculators.calculateSmartMeal(
                    profile = profile,
                    caloriePercentage = rule.caloriePercentage?.toFloat() ?: 0.25f,
                    catalog = catalog
                )
                rule.mealName to recommendation
            }
        }
    }

    fun generateWeeklyGroceryList(
        preserveManual: Boolean = true,
        preferences: br.com.bragasaude.domain.WeeklyGroceryPreferences? = null
    ) {
        if (_isLoading.value) return
        preferences?.let { updateWeeklyPreferences(it) }
        val effectivePrefs = preferences ?: _weeklyPreferences.value
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val vitals = vitalsRepository.getVitalSigns(userId).first()
                val profileEntity = profileRepository.getProfile(userId).first()
                val ingredients = catalogRepository.fetchGroceryIngredients()
                _groceryIngredients.value = ingredients
                val catalog = catalogRepository.getFoodCatalog().first()
                val dislikes = _dislikedFoodNames.value
                val currentDailyCal = _dailyCalories.value.toDouble()

                val previousFoods = if (!preserveManual) {
                    groceryList.value.map { it.foodId }.toSet()
                } else {
                    emptySet()
                }
                val seed = if (!preserveManual) System.currentTimeMillis() else null

                val plan = WeeklyGroceryEngine.planWeeklyGrocery(
                    userId = userId,
                    vitals = vitals,
                    profile = profileEntity?.toRemote(),
                    catalog = catalog,
                    dislikedFoodNames = dislikes,
                    ingredientCatalog = ingredients,
                    targetCalories = currentDailyCal,
                    shuffleSeed = seed,
                    previousFoodIds = previousFoods,
                    preferences = effectivePrefs
                )
                if (plan.items.isNotEmpty()) {
                    weeklyRepository.save(userId, plan, preserveManual)
                } else {
                    invalidateWeekly(plan.statusMessage)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun togglePantryItem(itemId: String, isChecked: Boolean) {
        viewModelScope.launch {
            weeklyRepository.check(userId, itemId, isChecked, _groceryIngredients.value ?: catalogRepository.fetchGroceryIngredients())
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
                weeklyRepository.modify(userId, "Lista modificada manualmente; redimensione para revisar a cobertura. Itens manuais serão preservados.") {
                    clean.filter { it.lowercase() !in existing }.forEach { name -> groceryRepository.addItem(userId, name) }
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
