package br.com.bragasaude.domain

import br.com.bragasaude.data.local.ExamItemEntity
import br.com.bragasaude.data.local.FoodEntity
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.data.remote.model.RemoteProfile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.ceil

/**
 * Resultado estruturado do dimensionamento semanal de compras.
 * Informa as quantidades consolidadas, calorias planejadas para 7 dias,
 * distribuição estimada de macronutrientes, cobertura percentual e limitações reais.
 */
data class WeeklyGroceryPlanResult(
    val items: List<GroceryListItemEntity>,
    val targetWeeklyCalories: Double,
    val plannedWeeklyCalories: Double,
    val coveragePercent: Double,
    val plannedProteinGrams: Double,
    val plannedCarbsGrams: Double,
    val plannedFatGrams: Double,
    val foodVarietyCount: Int,
    val limitations: List<String> = emptyList(),
    val statusMessage: String,
    val isManuallyModified: Boolean = false,
    val profileWeight: Double? = null,
    val structuredLimitations: List<GroceryLimitation> = emptyList(),
    val purchaseStatus: PurchaseCalculationStatus = PurchaseCalculationStatus.CALCULABLE
)

object WeeklyGroceryEngine {

    const val CORRIDOR_HORTIFRUTI = "Hortifruti e Feira"
    const val CORRIDOR_GRAOS = "Cereais, Sementes e Graos"
    const val CORRIDOR_PROTEINAS = "Proteinas, Ovos e Laticinios"
    const val CORRIDOR_MERCEARIA = "Mercearia, Temperos e Chas"

    /** Margem de segurança de compra para cobrir perdas, aparas e cocção (15%). */
    private const val PURCHASE_SAFETY_MARGIN = 1.15

    /** Tolerância inferior de cobertura energética semanal para declaração de cumprimento. */
    const val MIN_ENERGY_COVERAGE_PERCENT = 90.0

    /** Tolerância superior de cobertura energética semanal. */
    const val MAX_ENERGY_COVERAGE_PERCENT = 110.0

    /**
     * Gera a lista semanal consolidada por ingrediente, mantendo compatibilidade
     * com chamadas existentes no aplicativo e nos testes.
     */
    fun generateWeeklyList(
        userId: String,
        exams: List<ExamItemEntity>,
        vitals: List<VitalSignEntity>,
        profile: RemoteProfile?,
        catalog: List<FoodEntity>,
        dislikedFoodNames: Set<String> = emptySet(),
        ingredientCatalog: GroceryIngredientCatalog,
        targetCalories: Double = 1800.0,
        preferences: WeeklyGroceryPreferences = WeeklyGroceryPreferences()
    ): List<GroceryListItemEntity> {
        return planWeeklyGrocery(
            userId = userId,
            exams = exams,
            vitals = vitals,
            profile = profile,
            catalog = catalog,
            dislikedFoodNames = dislikedFoodNames,
            ingredientCatalog = ingredientCatalog,
            targetCalories = targetCalories,
            preferences = preferences
        ).items
    }

    /**
     * Planejamento explícito de consumo semanal para 7 dias.
     * Separa quantidades planejadas para consumo, demanda de preparo e compra final.
     */
    fun planWeeklyGrocery(
        userId: String,
        exams: List<ExamItemEntity>,
        vitals: List<VitalSignEntity>,
        profile: RemoteProfile?,
        catalog: List<FoodEntity>,
        dislikedFoodNames: Set<String> = emptySet(),
        ingredientCatalog: GroceryIngredientCatalog,
        targetCalories: Double = 1800.0,
        shuffleSeed: Long? = null,
        previousFoodIds: Set<String> = emptySet(),
        preferences: WeeklyGroceryPreferences = WeeklyGroceryPreferences()
    ): WeeklyGroceryPlanResult {
        val effectivePrefs = preferences.normalized()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val weekStartDate = GroceryWeek.start()

        val limitations = mutableListOf<String>()
        val structuredLimitations = mutableListOf<GroceryLimitation>()

        val effectiveDailyCalories = targetCalories.takeIf { it.isFinite() && it > 0 }?.coerceAtLeast(500.0) ?: 1800.0
        val targetWeeklyCalories = effectiveDailyCalories * 7.0

        // 1. Filtragem rigorosa por segurança clínica, alergias e preferências
        val safeCatalog = catalog.filter { food ->
            if (ingredientCatalog.forFood(food.remoteId, food.name).isEmpty()) return@filter false
            val allergySafe = NutritionSuggestionEngine.isSafeFromAllergies(
                food,
                profile?.foodAllergies ?: emptyList(),
                profile?.customFoodRestrictions
            )
            allergySafe &&
                (profile?.hasDiabetes != true || food.isDiabetesSafe) &&
                (profile?.hasHypertension != true || food.isHypertensionSafe) &&
                (profile?.hasThyroidIssue != true || food.isThyroidSafe)
        }

        val available = safeCatalog.filter { food ->
            dislikedFoodNames.none { groceryNameKey(it) == groceryNameKey(food.name) }
        }

        if (available.isEmpty() && safeCatalog.isEmpty()) {
            val emptyLimitation = GroceryLimitation(
                type = GroceryLimitationType.NO_ELIGIBLE_FOODS,
                affectedItems = emptyList(),
                impact = GroceryCalculationImpact.NOT_CALCULABLE,
                userSummary = "Nenhum alimento elegível encontrado para as restrições alimentares informadas."
            )
            return WeeklyGroceryPlanResult(
                items = emptyList(),
                targetWeeklyCalories = targetWeeklyCalories,
                plannedWeeklyCalories = 0.0,
                coveragePercent = 0.0,
                plannedProteinGrams = 0.0,
                plannedCarbsGrams = 0.0,
                plannedFatGrams = 0.0,
                foodVarietyCount = 0,
                limitations = listOf(emptyLimitation.userSummary),
                structuredLimitations = listOf(emptyLimitation),
                purchaseStatus = PurchaseCalculationStatus.INCOMPLETE,
                statusMessage = "Não foi possível gerar uma lista para o seu perfil. Você pode montar sua lista manualmente."
            )
        }

        // 2. Análise de marcadores clínicos laboratoriais e vitais
        val glucose = exams.firstOrNull { it.itemKey == "glucose" }?.valueNumeric
            ?: vitals.firstOrNull { it.glucoseLevel != null }?.glucoseLevel?.toDouble()
        val hasHighGlucose = (glucose != null && glucose > 100.0) || profile?.hasDiabetes == true

        val cholesterol = exams.firstOrNull { it.itemKey == "total_cholesterol" }?.valueNumeric
        val hasHighCholesterol = cholesterol != null && cholesterol > 190.0

        val sys = vitals.firstOrNull { it.systolicPressure != null }?.systolicPressure ?: 0
        val hasHighBp = sys > 130 || profile?.hasHypertension == true

        // 3. Distribuição energética entre os pilares nutricionais (7 dias)
        val grainTargetKcal = targetWeeklyCalories * 0.45
        val proteinTargetKcal = targetWeeklyCalories * 0.25
        val produceTargetKcal = targetWeeklyCalories * 0.15
        val pantryTargetKcal = targetWeeklyCalories * 0.15

        val plannedConsumptions = mutableListOf<PlannedConsumption>()
        val selectedCanonicalGroups = mutableSetOf<String>()

        // 4. Preservação de grupos básicos obrigatórios da política do gateway (arroz e feijão)
        val selectedRequiredFoodIds = mutableSetOf<String>()
        ingredientCatalog.requiredGroups.forEach { group ->
            val selected = group.firstNotNullOfOrNull { id -> available.firstOrNull { it.remoteId == id } }
                ?: group.firstNotNullOfOrNull { id -> safeCatalog.firstOrNull { it.remoteId == id } }
            if (selected != null) {
                selectedRequiredFoodIds.add(selected.remoteId)
                val corridor = classifyPillar(selected)
                val days = if (catalog.size <= 5) 5 else 7
                val basePortion = selected.servingSizeGrams.takeIf { it > 0 } ?: 80
                val scale = (effectiveDailyCalories / 1800.0).coerceIn(0.7, 2.5)
                val portion = (basePortion * scale).toInt().coerceIn(
                    selected.minServingGrams.takeIf { it > 0 } ?: (basePortion / 2).coerceAtLeast(20),
                    selected.maxServingGrams.takeIf { it > 0 } ?: (basePortion * 3)
                )
                val weeklyGrams = portion * days
                plannedConsumptions.add(PlannedConsumption(selected, weeklyGrams, corridor))
                canonicalGroupFor(selected, ingredientCatalog)?.let { selectedCanonicalGroups.add(it) }
            }
        }

        // Separação em pools taxonômicos normalizados (sem fragilidade de acentos)
        val random = shuffleSeed?.let { java.util.Random(it) }

        fun isPreviousFood(food: FoodEntity): Boolean {
            if (previousFoodIds.isEmpty()) return false
            if (food.remoteId in previousFoodIds) return true
            val slugs = ingredientCatalog.forFood(food.remoteId, food.name).map { it.slug }
            return slugs.any { it in previousFoodIds }
        }

        fun orderPool(candidates: List<FoodEntity>): List<FoodEntity> {
            if (candidates.isEmpty()) return emptyList()
            if (shuffleSeed == null && previousFoodIds.isEmpty()) return candidates

            val (fresh, previous) = candidates.partition { !isPreviousFood(it) }

            val orderedFresh = if (random != null) fresh.shuffled(random) else fresh
            val orderedPrevious = if (random != null) previous.shuffled(random) else previous

            return orderedFresh + orderedPrevious
        }

        fun poolFor(corridor: String): List<FoodEntity> {
            val list = available.filter { classifyPillar(it) == corridor && it.remoteId !in selectedRequiredFoodIds }
            return if (list.isNotEmpty()) list else safeCatalog.filter { classifyPillar(it) == corridor && it.remoteId !in selectedRequiredFoodIds }
        }

        fun filterCost(list: List<FoodEntity>): List<FoodEntity> {
            if (!effectivePrefs.isEconomic) return list
            val filtered = list.filterNot { isHighCostFood(it, effectivePrefs.isUltraEconomic) }
            return if (filtered.isNotEmpty()) filtered else list
        }

        val grainsPool = orderPool(filterCost(poolFor(CORRIDOR_GRAOS)))

        val candidateProteins = poolFor(CORRIDOR_PROTEINAS).let { list ->
            val byPref = list.filter { matchesProteinPreference(it, effectivePrefs.selectedProteins) }
            if (byPref.isNotEmpty()) byPref else list
        }.let { filterCost(it) }
        val proteinsPool = orderPool(candidateProteins)

        val producePool = filterCost(poolFor(CORRIDOR_HORTIFRUTI))

        val candidatePantry = poolFor(CORRIDOR_MERCEARIA).let { list ->
            if (effectivePrefs.hasPantryStaples) {
                list.filterNot { isPantryStaple(it) }
            } else {
                list
            }
        }.let { filterCost(it) }
        val pantryPool = orderPool(candidatePantry)

        // Verificação de alimentos sem kcal
        val foodsWithoutKcal = (available + safeCatalog).filter { it.kcal == null || it.kcal <= 0.0 }.distinctBy { it.remoteId }
        if (foodsWithoutKcal.isNotEmpty()) {
            val msg = "Existem alimentos no catálogo com valor calórico ausente (${foodsWithoutKcal.size} item(ns))."
            limitations.add(msg)
            structuredLimitations.add(
                GroceryLimitation(
                    type = GroceryLimitationType.MISSING_NUTRITIONAL_DATA,
                    affectedItems = foodsWithoutKcal.map { it.name },
                    impact = GroceryCalculationImpact.APPROXIMATED,
                    userSummary = msg
                )
            )
        }

        // 5. Dimensionamento do Pilar 1: Grãos, Cereais, Tubérculos e Raízes
        val existingGrainKcal = plannedConsumptions.filter { it.corridor == CORRIDOR_GRAOS }.sumOf { it.plannedCalories }
        val remainingGrainKcal = (grainTargetKcal - existingGrainKcal).coerceAtLeast(0.0)
        distributePillarEnergy(
            targetKcal = remainingGrainKcal,
            candidates = grainsPool,
            corridor = CORRIDOR_GRAOS,
            dailyCalorieTarget = effectiveDailyCalories,
            ingredientCatalog = ingredientCatalog,
            selectedCanonicalGroups = selectedCanonicalGroups,
            destConsumptions = plannedConsumptions,
            isEconomic = effectivePrefs.isEconomic,
            isUltraEconomic = effectivePrefs.isUltraEconomic
        )

        // 6. Dimensionamento do Pilar 2: Proteínas (Animais/Vegetais, Leguminosas, Laticínios, Ovos)
        val existingProteinKcal = plannedConsumptions.filter { it.corridor == CORRIDOR_PROTEINAS }.sumOf { it.plannedCalories }
        val remainingProteinKcal = (proteinTargetKcal - existingProteinKcal).coerceAtLeast(0.0)
        distributePillarEnergy(
            targetKcal = remainingProteinKcal,
            candidates = proteinsPool,
            corridor = CORRIDOR_PROTEINAS,
            dailyCalorieTarget = effectiveDailyCalories,
            ingredientCatalog = ingredientCatalog,
            selectedCanonicalGroups = selectedCanonicalGroups,
            destConsumptions = plannedConsumptions,
            preferFish = hasHighCholesterol || hasHighBp,
            isEconomic = effectivePrefs.isEconomic,
            isUltraEconomic = effectivePrefs.isUltraEconomic
        )

        // 7. Dimensionamento do Pilar 3: Hortifruti (Frutas, Folhosos, Legumes)
        distributeProduceEnergy(
            targetKcal = produceTargetKcal,
            candidates = producePool,
            dailyCalorieTarget = effectiveDailyCalories,
            hasHighGlucose = hasHighGlucose,
            hasHighBp = hasHighBp,
            ingredientCatalog = ingredientCatalog,
            selectedCanonicalGroups = selectedCanonicalGroups,
            destConsumptions = plannedConsumptions,
            orderFn = ::orderPool,
            isEconomic = effectivePrefs.isEconomic,
            isUltraEconomic = effectivePrefs.isUltraEconomic
        )

        // 8. Dimensionamento do Pilar 4: Mercearia, Gorduras Boas, Sementes, Temperos e Chás
        distributePillarEnergy(
            targetKcal = pantryTargetKcal,
            candidates = pantryPool,
            corridor = CORRIDOR_MERCEARIA,
            dailyCalorieTarget = effectiveDailyCalories,
            ingredientCatalog = ingredientCatalog,
            selectedCanonicalGroups = selectedCanonicalGroups,
            destConsumptions = plannedConsumptions,
            isEconomic = effectivePrefs.isEconomic,
            isUltraEconomic = effectivePrefs.isUltraEconomic,
            isPantry = true
        )

        // 9. Calibração fina da meta calórica semanal
        val allEligibleCandidates = (grainsPool + proteinsPool + producePool + pantryPool).distinctBy { it.remoteId }
        calibrateTotalEnergy(
            targetWeeklyKcal = targetWeeklyCalories,
            plannedConsumptions = plannedConsumptions,
            candidatesPool = allEligibleCandidates,
            isUltraEconomic = effectivePrefs.isUltraEconomic,
            maxBasketSize = effectivePrefs.budgetTier.maxBasketSize
        )

        // 10. Conversão de consumo em demanda de ingredientes e consolidação canônica
        val demandsBySlug = mutableMapOf<String, MutableList<IngredientDemand>>()

        plannedConsumptions.forEach { pc ->
            val food = pc.food
            val weeklyGrams = pc.weeklyGrams
            val ingredients = ingredientCatalog.forFood(food.remoteId, food.name)
            if (ingredients.isEmpty()) {
                val msg = "Alimento '${food.name}' não possui ingrediente correspondente mapeado."
                limitations.add(msg)
                structuredLimitations.add(
                    GroceryLimitation(
                        type = GroceryLimitationType.MISSING_INGREDIENT_MAPPING,
                        affectedItems = listOf(food.name),
                        impact = GroceryCalculationImpact.NOT_CALCULABLE,
                        userSummary = msg
                    )
                )
                return@forEach
            }

            val conversion = GroceryConsumption.amounts(food.remoteId, food.name, weeklyGrams.toDouble(), ingredientCatalog)
            limitations.addAll(conversion.limitations)
            structuredLimitations.addAll(conversion.structuredLimitations)
            conversion.amounts.forEach { (slug, amount) ->
                val ingredient = ingredients.first { it.slug == slug }
                demandsBySlug.getOrPut(slug) { mutableListOf() }.add(
                    IngredientDemand(ingredient, amount, weeklyGrams, pc.corridor)
                )
            }
        }

        // Consolidação sem duplicações: uma única linha por ingrediente canônico
        val consolidatedItems = demandsBySlug.map { (slug, demands) ->
            val ingredient = demands.first().ingredient
            val totalPrepDemand = demands.sumOf { it.prepDemandAmount }
            val totalSuggestedServingGrams = demands.sumOf { it.plannedServingWeekGrams }
            val preferredCorridor = demands.groupBy { it.corridor }.maxByOrNull { it.value.size }?.key ?: CORRIDOR_GRAOS

            val demandWithMargin = ceil(totalPrepDemand * PURCHASE_SAFETY_MARGIN).toInt().coerceAtLeast(1)
            val purchaseAmount = (ceil(maxOf(demandWithMargin, ingredient.minimum).toDouble() / ingredient.step) * ingredient.step).toInt()

            GroceryListItemEntity(
                remoteId = UUID.randomUUID().toString(),
                userId = userId,
                weekStartDate = weekStartDate,
                foodId = ingredient.slug,
                foodName = ingredient.name,
                category = preferredCorridor,
                suggestedServingWeekGrams = totalSuggestedServingGrams,
                purchaseWeightGrams = if (ingredient.unit == "kg") purchaseAmount else 0,
                purchaseUnitText = GroceryPurchasePlanner.text(ingredient, purchaseAmount),
                estimatedPriceBrl = GroceryPurchasePlanner.cost(ingredient, purchaseAmount),
                plannedWeeklyAmount = totalPrepDemand
            )
        }

        // Consolidação das limitações estruturadas para evitar repetições
        val consolidatedLimitations = structuredLimitations.groupBy { it.type }.map { (type, groupList) ->
            val affected = groupList.flatMap { it.affectedItems }.distinct()
            val hasIncomplete = groupList.any { it.impact == GroceryCalculationImpact.NOT_CALCULABLE }
            val impact = if (hasIncomplete) GroceryCalculationImpact.NOT_CALCULABLE else GroceryCalculationImpact.APPROXIMATED
            val summary = when (type) {
                GroceryLimitationType.MISSING_YIELD -> "Rendimento de cocção não cadastrado para: ${affected.joinToString(", ")} (estimado em 1:1)."
                GroceryLimitationType.MISSING_RECIPE_PROPORTIONS -> "Proporções dos ingredientes não cadastradas para: ${affected.joinToString(", ")} (compra não calculada)."
                GroceryLimitationType.MISSING_DENSITY -> "Densidade não cadastrada para: ${affected.joinToString(", ")} (compra em litros não calculada)."
                GroceryLimitationType.MISSING_UNIT_WEIGHT -> "Peso por unidade não cadastrado para: ${affected.joinToString(", ")} (unidades não calculadas)."
                GroceryLimitationType.MISSING_INGREDIENT_MAPPING -> "Sem ingrediente de compra mapeado para: ${affected.joinToString(", ")}."
                GroceryLimitationType.MISSING_NUTRITIONAL_DATA -> "Valor calórico ausente no catálogo para: ${affected.joinToString(", ")}."
                GroceryLimitationType.NO_ELIGIBLE_FOODS -> "Nenhum alimento elegível encontrado para as restrições informadas."
            }
            GroceryLimitation(type, affected, impact, summary)
        }

        // Estado explícito de dimensionamento da compra
        val purchaseStatus = when {
            consolidatedLimitations.any { it.impact == GroceryCalculationImpact.NOT_CALCULABLE } ->
                PurchaseCalculationStatus.INCOMPLETE
            consolidatedLimitations.any { it.impact == GroceryCalculationImpact.APPROXIMATED } ->
                PurchaseCalculationStatus.APPROXIMATED
            else ->
                PurchaseCalculationStatus.CALCULABLE
        }

        // 11. Totalização nutricional real (calorias e macronutrientes do consumo planejado)
        val totalPlannedKcal = plannedConsumptions.sumOf { it.plannedCalories }
        val totalProteinG = plannedConsumptions.sumOf { it.plannedProtein }
        val totalCarbsG = plannedConsumptions.sumOf { it.plannedCarbs }
        val totalFatG = plannedConsumptions.sumOf { it.plannedFat }
        val coveragePercent = if (targetWeeklyCalories > 0) (totalPlannedKcal / targetWeeklyCalories) * 100.0 else 100.0

        val distinctFoodsCount = plannedConsumptions.map { it.food.remoteId }.distinct().size

        // 12. Mensagem informativa na interface
        val statusMessage = buildStatusMessage(
            targetDailyCalories = effectiveDailyCalories,
            targetWeeklyCalories = targetWeeklyCalories,
            plannedWeeklyCalories = totalPlannedKcal,
            coveragePercent = coveragePercent,
            itemCount = consolidatedItems.size,
            varietyCount = distinctFoodsCount,
            limitations = limitations
        )

        return WeeklyGroceryPlanResult(
            items = consolidatedItems,
            targetWeeklyCalories = targetWeeklyCalories,
            plannedWeeklyCalories = totalPlannedKcal,
            coveragePercent = coveragePercent,
            plannedProteinGrams = totalProteinG,
            plannedCarbsGrams = totalCarbsG,
            plannedFatGrams = totalFatG,
            foodVarietyCount = distinctFoodsCount,
            limitations = limitations.distinct(),
            structuredLimitations = consolidatedLimitations,
            purchaseStatus = purchaseStatus,
            statusMessage = statusMessage,
            profileWeight = profile?.weight
        )
    }

    private fun distributePillarEnergy(
        targetKcal: Double,
        candidates: List<FoodEntity>,
        corridor: String,
        dailyCalorieTarget: Double,
        ingredientCatalog: GroceryIngredientCatalog,
        selectedCanonicalGroups: MutableSet<String>,
        destConsumptions: MutableList<PlannedConsumption>,
        preferFish: Boolean = false,
        isEconomic: Boolean = false,
        isUltraEconomic: Boolean = false,
        isPantry: Boolean = false
    ) {
        if (candidates.isEmpty() || targetKcal <= 0.0) return

        val sortedCandidates = if (preferFish) {
            candidates.sortedByDescending { if (it.category?.contains("Peixes", ignoreCase = true) == true) 1 else 0 }
        } else {
            candidates
        }

        val countToTake = when {
            isPantry && isUltraEconomic -> 0
            isPantry && isEconomic -> 1
            isUltraEconomic && corridor == CORRIDOR_GRAOS -> 2
            isUltraEconomic && corridor == CORRIDOR_PROTEINAS -> 2
            isUltraEconomic -> 1
            isEconomic -> 2
            dailyCalorieTarget >= 3500.0 -> 6
            dailyCalorieTarget >= 2500.0 -> 5
            else -> 4
        }.coerceAtMost(sortedCandidates.size).coerceAtLeast(if (isPantry && isUltraEconomic) 0 else 1)

        if (countToTake <= 0) return

        val selectedPool = selectDiverseFoods(
            candidates = sortedCandidates,
            count = countToTake,
            ingredientCatalog = ingredientCatalog,
            selectedGroups = selectedCanonicalGroups
        )
        if (selectedPool.isEmpty()) return
        val perFoodKcalTarget = targetKcal / selectedPool.size

        selectedPool.forEach { food ->
            val kcalPer100 = food.kcal ?: 0.0
            val baseServing = food.servingSizeGrams.takeIf { it > 0 } ?: 60
            val minPortion = food.minServingGrams.takeIf { it > 0 } ?: (baseServing / 2).coerceAtLeast(10)
            val baseMaxPortion = food.maxServingGrams.takeIf { it > 0 } ?: (baseServing * 3)
            val mealMultiplier = when {
                isUltraEconomic && dailyCalorieTarget >= 2800.0 -> 3
                isUltraEconomic || dailyCalorieTarget >= 2400.0 -> 2
                else -> 1
            }
            val maxPortion = baseMaxPortion * mealMultiplier

            val days = when (corridor) {
                CORRIDOR_GRAOS -> if (isUltraEconomic) 7 else 5
                CORRIDOR_PROTEINAS -> if (isUltraEconomic) 7 else 6
                else -> 4
            }

            val maxPantryCap = maxWeeklyGramsForFood(food, isUltraEconomic)
            val weeklyGrams = if (kcalPer100 > 0.0) {
                val neededGrams = ((perFoodKcalTarget / kcalPer100) * 100.0).toInt()
                val portion = (neededGrams / days).coerceIn(minPortion, maxPortion)
                (portion * days).coerceAtMost(maxPantryCap)
            } else {
                val scale = (dailyCalorieTarget / 1800.0).coerceIn(0.7, 2.5)
                val portion = (baseServing * scale).toInt().coerceIn(minPortion, maxPortion)
                (portion * days).coerceAtMost(maxPantryCap)
            }

            destConsumptions.add(PlannedConsumption(food, weeklyGrams, corridor))
        }
    }

    private fun distributeProduceEnergy(
        targetKcal: Double,
        candidates: List<FoodEntity>,
        dailyCalorieTarget: Double,
        hasHighGlucose: Boolean,
        hasHighBp: Boolean,
        ingredientCatalog: GroceryIngredientCatalog,
        selectedCanonicalGroups: MutableSet<String>,
        destConsumptions: MutableList<PlannedConsumption>,
        orderFn: (List<FoodEntity>) -> List<FoodEntity> = { it },
        isEconomic: Boolean = false,
        isUltraEconomic: Boolean = false
    ) {
        if (candidates.isEmpty()) return

        val frutasPool = orderFn(candidates.filter { groceryNameKey(it.category.orEmpty()).contains("fruta") })
        val folhasPool = orderFn(candidates.filter {
            val c = groceryNameKey(it.category.orEmpty())
            c.contains("verdura") || c.contains("folhoso") || c.contains("hortalica") || c.contains("crucifera")
        })
        val legumesPool = orderFn(candidates.filter { groceryNameKey(it.category.orEmpty()).contains("legume") })

        val frutasCount = when {
            isUltraEconomic -> 1
            isEconomic -> 2
            dailyCalorieTarget >= 2800.0 -> 3
            else -> 2
        }
        val frutasCandidates = if (hasHighGlucose) {
            frutasPool.filter { it.functionalTags.contains("baixo_ig") || it.functionalTags.contains("fibra_soluvel") }
                .ifEmpty { frutasPool }
        } else {
            frutasPool
        }
        val frutas = selectDiverseFoods(
            candidates = frutasCandidates,
            count = frutasCount,
            ingredientCatalog = ingredientCatalog,
            selectedGroups = selectedCanonicalGroups
        )

        val folhasCandidates = if (hasHighBp) {
            folhasPool.filter { it.functionalTags.contains("nitrato_natural") || it.functionalTags.contains("magnesio") || it.functionalTags.contains("potassio") }
                .ifEmpty { folhasPool }
        } else {
            folhasPool
        }
        val folhasCount = 1
        val folhas = selectDiverseFoods(
            candidates = folhasCandidates,
            count = folhasCount,
            ingredientCatalog = ingredientCatalog,
            selectedGroups = selectedCanonicalGroups
        )

        val legumesCount = when {
            isUltraEconomic -> 1
            isEconomic -> 2
            else -> 2
        }
        val legumes = selectDiverseFoods(
            candidates = legumesPool,
            count = legumesCount,
            ingredientCatalog = ingredientCatalog,
            selectedGroups = selectedCanonicalGroups
        )

        val selectedProduce = (frutas + folhas + legumes).distinctBy { it.remoteId }
        val perFoodKcal = if (selectedProduce.isNotEmpty()) targetKcal / selectedProduce.size else 0.0

        selectedProduce.forEach { food ->
            val kcalPer100 = food.kcal ?: 0.0
            val baseServing = food.servingSizeGrams.takeIf { it > 0 } ?: 100
            val minPortion = food.minServingGrams.takeIf { it > 0 } ?: (baseServing / 2).coerceAtLeast(20)
            val maxPortion = food.maxServingGrams.takeIf { it > 0 } ?: (baseServing * 3)
            val days = 6

            val maxPantryCap = maxWeeklyGramsForFood(food, isUltraEconomic)
            val weeklyGrams = if (kcalPer100 > 0.0 && perFoodKcal > 0.0) {
                val neededGrams = ((perFoodKcal / kcalPer100) * 100.0).toInt()
                val portion = (neededGrams / days).coerceIn(minPortion, maxPortion)
                (portion * days).coerceAtMost(maxPantryCap)
            } else {
                val scale = (dailyCalorieTarget / 1800.0).coerceIn(0.7, 2.5)
                val portion = (baseServing * scale).toInt().coerceIn(minPortion, maxPortion)
                (portion * days).coerceAtMost(maxPantryCap)
            }

            destConsumptions.add(PlannedConsumption(food, weeklyGrams, CORRIDOR_HORTIFRUTI))
        }
    }

    private fun isStapleEnergyFood(food: FoodEntity): Boolean {
        val name = groceryNameKey(food.name.orEmpty())
        val cat = groceryNameKey(food.category.orEmpty())
        return name.contains("arroz") || name.contains("feijao") || name.contains("aveia") ||
               name.contains("ovo") || name.contains("pao") || name.contains("tapioca") ||
               name.contains("banana") || name.contains("cuscuz") || name.contains("mandioca") ||
               name.contains("batata") || name.contains("frango") || cat.contains("carne") ||
               cat.contains("ave")
    }

    private fun maxWeeklyGramsForFood(food: FoodEntity, isUltraEconomic: Boolean = false): Int {
        val name = groceryNameKey(food.name.orEmpty())
        val cat = groceryNameKey(food.category.orEmpty())
        return when {
            // Brotos e folhosos leves: nunca excedem 350g por semana (evita sacas de 3kg de broto de feijão)
            name.contains("broto") || name.contains("alface") || name.contains("rucula") ||
            name.contains("espinafre") || name.contains("agriao") || name.contains("couve") -> 350

            // Frutas frescas: porção semanal equilibrada (máx 1.5kg)
            cat.contains("fruta") -> 1500

            // Tubérculos nobres: teto estrito para evitar compras caras desnecessárias (máx 1kg de mandioquinha)
            name.contains("mandioquinha") || name.contains("batata-baroa") -> 1000

            // Tubérculos comuns (mandioca, aipim, batata): até 2kg em modo ultraeconômico
            name.contains("mandioca") || name.contains("batata") || name.contains("aipim") -> if (isUltraEconomic) 2000 else 1500

            // Legumes e hortaliças gerais: máx 1.2kg de consumo semanal
            cat.contains("legume") || cat.contains("hortalica") || name.contains("abobora") || name.contains("abobrinha") -> 1200

            // Massas (macarrão / espaguete): máx 700g por semana (evita 5kg de massa cara a R$ 100)
            name.contains("massa") || name.contains("macarrao") -> 700

            // Grãos básicos: pilares energéticos essenciais brasileiros
            name.contains("arroz") -> if (isUltraEconomic) 3500 else 2500
            name.contains("feijao") || name.contains("lentilha") || name.contains("grao-de-bico") -> if (isUltraEconomic) 2000 else 1500
            name.contains("aveia") -> if (isUltraEconomic) 1200 else 800
            name.contains("cuscuz") || name.contains("milho") -> if (isUltraEconomic) 1200 else 800

            // Proteínas e ovos
            name.contains("ovo") -> if (isUltraEconomic) 2100 else 1400 // ~30 a 42 unidades
            cat.contains("carne") || cat.contains("ave") || cat.contains("peixe") || name.contains("frango") -> if (isUltraEconomic) 2200 else 1600

            // Panificados e cafés
            name.contains("pao") || name.contains("torrada") || name.contains("tapioca") -> 800

            // Padrão de segurança
            else -> 1000
        }
    }

    private fun calibrateTotalEnergy(
        targetWeeklyKcal: Double,
        plannedConsumptions: MutableList<PlannedConsumption>,
        candidatesPool: List<FoodEntity>,
        isUltraEconomic: Boolean = false,
        maxBasketSize: Int = Int.MAX_VALUE
    ) {
        var currentKcal = plannedConsumptions.sumOf { it.plannedCalories }
        var deficit = targetWeeklyKcal - currentKcal

        // Passo A: Aumenta porções de alimentos básicos de alta densidade até os tetos semanais
        if (deficit > 200.0) {
            val maxIterations = 4
            var iteration = 0
            while (deficit > 200.0 && iteration < maxIterations) {
                iteration++
                val eligibleForBoost = plannedConsumptions.filter {
                    val k = it.food.kcal ?: 0.0
                    k > 0.0 && (if (isUltraEconomic) isStapleEnergyFood(it.food) else true)
                }
                if (eligibleForBoost.isEmpty()) break
                val boostShare = deficit / eligibleForBoost.size
                var changedAny = false
                for (i in plannedConsumptions.indices) {
                    val pc = plannedConsumptions[i]
                    val foodKcal = pc.food.kcal ?: 0.0
                    val isEligible = if (isUltraEconomic) isStapleEnergyFood(pc.food) else true
                    if (foodKcal > 0.0 && isEligible) {
                        val maxAllowedGrams = maxWeeklyGramsForFood(pc.food, isUltraEconomic)
                        val headroom = (maxAllowedGrams - pc.weeklyGrams).coerceAtLeast(0)
                        if (headroom > 0) {
                            val additionalGrams = ((boostShare / foodKcal) * 100.0).toInt().coerceAtMost(headroom)
                            if (additionalGrams > 0) {
                                plannedConsumptions[i] = pc.copy(weeklyGrams = pc.weeklyGrams + additionalGrams)
                                changedAny = true
                            }
                        }
                    }
                }
                currentKcal = plannedConsumptions.sumOf { it.plannedCalories }
                deficit = targetWeeklyKcal - currentKcal
                if (!changedAny) break
            }
        }

        currentKcal = plannedConsumptions.sumOf { it.plannedCalories }
        deficit = targetWeeklyKcal - currentKcal

        // Passo B: Se ainda houver déficit e houver espaço na cesta (plannedConsumptions.size < maxBasketSize)
        if (deficit > 400.0 && plannedConsumptions.size < maxBasketSize) {
            val alreadySelectedIds = plannedConsumptions.map { it.food.remoteId }.toSet()
            val extraCandidates = candidatesPool.filter { it.remoteId !in alreadySelectedIds && (it.kcal ?: 0.0) > 0.0 }
                .sortedByDescending { it.kcal ?: 0.0 }

            for (food in extraCandidates) {
                if (deficit <= 200.0 || plannedConsumptions.size >= maxBasketSize) break
                val foodKcal = food.kcal ?: continue
                val corridor = classifyPillar(food)
                val baseServing = food.servingSizeGrams.takeIf { it > 0 } ?: 80
                val maxServing = food.maxServingGrams.takeIf { it > 0 } ?: (baseServing * 3)
                val portion = ((baseServing * 1.5).toInt()).coerceIn(baseServing, maxServing)
                val days = 5
                val weeklyGrams = portion * days
                val foodTotalKcal = (weeklyGrams * foodKcal) / 100.0
                plannedConsumptions.add(PlannedConsumption(food, weeklyGrams, corridor))
                deficit -= foodTotalKcal
            }
        }
    }

    private fun buildStatusMessage(
        targetDailyCalories: Double,
        targetWeeklyCalories: Double,
        plannedWeeklyCalories: Double,
        coveragePercent: Double,
        itemCount: Int,
        varietyCount: Int,
        limitations: List<String>
    ): String {
        return if (coveragePercent < MIN_ENERGY_COVERAGE_PERCENT) {
            val deficit = (targetWeeklyCalories - plannedWeeklyCalories).toInt()
            "Lista planejada para 7 dias com ${plannedWeeklyCalories.toInt()} kcal (${String.format(Locale.ROOT, "%.1f%%", coveragePercent)} da meta de ${targetWeeklyCalories.toInt()} kcal). Déficit de $deficit kcal devido a limites de porção e restrições do catálogo."
        } else if (coveragePercent > MAX_ENERGY_COVERAGE_PERCENT) {
            "Lista planejada para 7 dias com ${plannedWeeklyCalories.toInt()} kcal (${String.format(Locale.ROOT, "%.1f%%", coveragePercent)} da meta de ${targetWeeklyCalories.toInt()} kcal) com $itemCount itens variados."
        } else {
            "Quantidades calculadas para a sua meta diária de ${targetDailyCalories.toInt()} kcal (7 dias: ${plannedWeeklyCalories.toInt()} kcal planejadas, $itemCount itens)."
        }
    }

    /**
     * Classificação taxonômica robusta baseada em metadados estruturados e nomes normalizados,
     * imune a diferenças de acentuação gráfica ("Grãos" vs "Graos", "Laticínios" vs "Laticinios").
     */
    fun classifyPillar(food: FoodEntity): String {
        val cat = groceryNameKey(food.category.orEmpty())
        val name = groceryNameKey(food.name)
        val tags = food.functionalTags

        // 1. Hortifruti (Frutas, verduras, folhas, legumes)
        if (cat.contains("fruta") || cat.contains("verdura") || cat.contains("hortalica") ||
            cat.contains("folhoso") || cat.contains("crucifera") ||
            (cat.contains("legume") && !cat.contains("leguminosa"))
        ) {
            return CORRIDOR_HORTIFRUTI
        }

        // 2. Proteínas, Leguminosas, Laticínios e Ovos
        if (cat.contains("carne") || cat.contains("ave") || cat.contains("peixe") ||
            cat.contains("pescado") || cat.contains("frutos do mar") || cat.contains("proteina") ||
            cat.contains("ovo") || cat.contains("laticinio") || cat.contains("queijo") ||
            cat.contains("leite") || cat.contains("iogurte") || cat.contains("leguminosa") ||
            tags.contains("proteina_vegetal") || tags.contains("proteina_magra") ||
            name.contains("feijao") || name.contains("lentilha") || name.contains("grao-de-bico") ||
            name.contains("pts") || name.contains("tofu") || name.contains("soja") ||
            name.contains("frango") || name.contains("bovino") || name.contains("tilapia") ||
            name.contains("sardinha") || name.contains("ovo") || name.contains("queijo")
        ) {
            return CORRIDOR_PROTEINAS
        }

        // 3. Grãos, Cereais, Raízes e Tubérculos (inclui Cuscuz e Massas Integrais)
        if (cat.contains("arroz") || cat.contains("cereal") || cat.contains("grao") ||
            cat.contains("tuberculo") || cat.contains("raiz") || cat.contains("massa") ||
            name.contains("arroz") || name.contains("cuscuz") || name.contains("macarrao") ||
            name.contains("aveia") || name.contains("quinoa") || name.contains("mandioca") ||
            name.contains("batata") || name.contains("inhame") || name.contains("aipim")
        ) {
            return CORRIDOR_GRAOS
        }

        // 4. Mercearia, Gorduras Boas, Oleaginosas, Sementes, Temperos e Chás
        return CORRIDOR_MERCEARIA
    }

    private data class PlannedConsumption(
        val food: FoodEntity,
        val weeklyGrams: Int,
        val corridor: String
    ) {
        val plannedCalories: Double
            get() = (weeklyGrams * (food.kcal ?: 0.0)) / 100.0

        val plannedProtein: Double
            get() = (weeklyGrams * (food.proteinG ?: 0.0)) / 100.0

        val plannedCarbs: Double
            get() = (weeklyGrams * (food.carbsG ?: 0.0)) / 100.0

        val plannedFat: Double
            get() = (weeklyGrams * (food.fatG ?: 0.0)) / 100.0
    }

    private data class IngredientDemand(
        val ingredient: GroceryIngredient,
        val prepDemandAmount: Double,
        val plannedServingWeekGrams: Int,
        val corridor: String
    )

    /**
     * Determina o agrupador canônico de um alimento a partir dos metadados remotos
     * dos ingredientes associados ou, como fallback, da raiz do nome do alimento.
     */
    fun canonicalGroupFor(food: FoodEntity, catalog: GroceryIngredientCatalog): String? {
        val ingredient = catalog.forFood(food.remoteId, food.name).firstOrNull()
        val fromIngredient = ingredient?.canonicalGroupSlug
        if (!fromIngredient.isNullOrBlank()) return fromIngredient
        val norm = groceryNameKey(food.name)
        val firstToken = norm.substringBefore(' ').substringBefore('-')
        return firstToken.takeIf { it.length >= 3 }
    }

    /**
     * Seleciona candidatos priorizando diversidade de grupos canônicos para evitar
     * concentração de múltiplas variantes do mesmo produto (ex.: múltiplas aveias ou linhaças)
     * na mesma semana.
     */
    fun selectDiverseFoods(
        candidates: List<FoodEntity>,
        count: Int,
        ingredientCatalog: GroceryIngredientCatalog,
        selectedGroups: MutableSet<String>
    ): List<FoodEntity> {
        if (candidates.isEmpty() || count <= 0) return emptyList()
        val chosen = mutableListOf<FoodEntity>()

        // 1º passe: escolhe itens de grupos que ainda não foram selecionados
        for (candidate in candidates) {
            if (chosen.size >= count) break
            val group = canonicalGroupFor(candidate, ingredientCatalog)
            if (group == null || group !in selectedGroups) {
                chosen.add(candidate)
                if (group != null) selectedGroups.add(group)
            }
        }

        // 2º passe: se faltarem itens para atingir 'count', preenche com os demais candidatos
        if (chosen.size < count) {
            val alreadyChosenIds = chosen.map { it.remoteId }.toSet()
            for (candidate in candidates) {
                if (chosen.size >= count) break
                if (candidate.remoteId !in alreadyChosenIds) {
                    chosen.add(candidate)
                }
            }
        }
        return chosen
    }

    private fun isHighCostFood(food: FoodEntity, isUltraEconomic: Boolean = false): Boolean {
        val key = groceryNameKey(food.name)
        val isLuxury = key.contains("salmao") || key.contains("camarao") || key.contains("mignon") ||
               key.contains("cardamomo") || key.contains("pistache") || key.contains("noz") ||
               key.contains("amendoa") || key.contains("macadamia") || key.contains("bacalhau")
        if (isLuxury) return true
        if (isUltraEconomic) {
            return key.contains("parmesao") || key.contains("gorgonzola") || key.contains("brie") ||
                   key.contains("alcatra") || key.contains("picanha") || key.contains("contrafile") ||
                   key.contains("whey") || key.contains("iogurte grego") || key.contains("castanha")
        }
        return false
    }

    private fun isPantryStaple(food: FoodEntity): Boolean {
        val key = groceryNameKey(food.name)
        return key.contains("azeite") || key.contains("oleo") || key.contains("sal") ||
               key.contains("canela") || key.contains("cardamomo") || key.contains("oregano") ||
               key.contains("curcuma") || key.contains("pimenta")
    }

    private fun matchesProteinPreference(food: FoodEntity, preferences: Set<GroceryProteinPreference>): Boolean {
        if (preferences.isEmpty()) return true
        val key = groceryNameKey(food.name)
        val cat = groceryNameKey(food.category.orEmpty())

        for (pref in preferences) {
            when (pref) {
                GroceryProteinPreference.EGGS -> {
                    if (key.contains("ovo") || key.contains("ovos")) return true
                }
                GroceryProteinPreference.POULTRY -> {
                    if (key.contains("frango") || key.contains("ave") || key.contains("peru") || key.contains("sobrecoxa")) return true
                }
                GroceryProteinPreference.BEEF -> {
                    if (key.contains("carne") || key.contains("patinho") || key.contains("alcatra") ||
                        key.contains("bovino") || key.contains("moida") || key.contains("acem") || key.contains("musculo")) return true
                }
                GroceryProteinPreference.FISH -> {
                    if (cat.contains("peixe") || key.contains("peixe") || key.contains("tilapia") ||
                        key.contains("sardinha") || key.contains("atum") || key.contains("pescada") || key.contains("salmao")) return true
                }
                GroceryProteinPreference.PLANT_BASED -> {
                    if (key.contains("grao-de-bico") || key.contains("lentilha") || key.contains("soja") || key.contains("tofu")) return true
                }
            }
        }
        if (cat.contains("laticinio") && !cat.contains("queijo")) return true
        return false
    }
}
