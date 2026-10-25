package br.com.bragasaude.domain

import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.model.RemoteProfile
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
    val purchaseStatus: PurchaseCalculationStatus = PurchaseCalculationStatus.CALCULABLE,
    val planningDays: Int = 7,
    val periodStartDate: String = items.firstOrNull()?.weekStartDate ?: java.time.LocalDate.now().toString()
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
        vitals: List<VitalSignEntity>,
        profile: RemoteProfile?,
        catalog: List<FoodEntity>,
        dislikedFoodNames: Set<String> = emptySet(),
        ingredientCatalog: GroceryIngredientCatalog,
        targetCalories: Double = 1800.0,
        preferences: WeeklyGroceryPreferences = WeeklyGroceryPreferences(),
        dislikedFoodIds: Set<String> = emptySet()
    ): List<GroceryListItemEntity> {
        return planWeeklyGrocery(
            userId = userId,
            vitals = vitals,
            profile = profile,
            catalog = catalog,
            dislikedFoodNames = dislikedFoodNames,
            ingredientCatalog = ingredientCatalog,
            targetCalories = targetCalories,
            preferences = preferences,
            dislikedFoodIds = dislikedFoodIds
        ).items
    }

    /**
     * Planejamento explícito de consumo semanal para 7 dias.
     * Separa quantidades planejadas para consumo, demanda de preparo e compra final.
     */
    fun planWeeklyGrocery(
        userId: String,
        vitals: List<VitalSignEntity>,
        profile: RemoteProfile?,
        catalog: List<FoodEntity>,
        dislikedFoodNames: Set<String> = emptySet(),
        ingredientCatalog: GroceryIngredientCatalog,
        targetCalories: Double = 1800.0,
        shuffleSeed: Long? = null,
        previousFoodIds: Set<String> = emptySet(),
        preferences: WeeklyGroceryPreferences = WeeklyGroceryPreferences(),
        dislikedFoodIds: Set<String> = emptySet()
    ): WeeklyGroceryPlanResult {
        val effectivePrefs = preferences.normalized()
        val weekStartDate = java.time.LocalDate.now().toString()
        val planningDays = effectivePrefs.planningDays

        val limitations = mutableListOf<String>()
        val structuredLimitations = mutableListOf<GroceryLimitation>()

        val effectiveDailyCalories = targetCalories.takeIf { it.isFinite() && it > 0 }?.coerceAtLeast(500.0) ?: 1800.0
        val baselineCalories = effectiveDailyCalories * 7.0
        val targetWeeklyCalories = effectiveDailyCalories * planningDays

        // 1. Filtragem rigorosa por segurança clínica, alergias e preferências
        val safeCatalog = catalog.filter { food ->
            if (ingredientCatalog.forFood(food.remoteId, "").isEmpty()) return@filter false
            val allergySafe = NutritionSuggestionEngine.isSafeFromAllergies(
                food,
                profile?.foodAllergies ?: emptyList(),
                profile?.customFoodRestrictions,
                semanticOnly = true
            )
            allergySafe &&
                (profile?.hasDiabetes != true || food.isDiabetesSafe) &&
                (profile?.hasHypertension != true || food.isHypertensionSafe) &&
                (profile?.hasThyroidIssue != true || food.isThyroidSafe)
        }

        val available = safeCatalog.filter { food ->
            food.remoteId !in dislikedFoodIds && dislikedFoodNames.none { it == food.remoteId || it == food.name } &&
                (!effectivePrefs.isEconomic || !isHighCostFood(food, effectivePrefs.isUltraEconomic)) &&
                (!effectivePrefs.hasPantryStaples || !isPantryStaple(food))
        }

        if (available.isEmpty()) {
            val emptyLimitation = GroceryLimitation(
                type = GroceryLimitationType.NO_ELIGIBLE_FOODS,
                affectedItems = emptyList(),
                impact = GroceryCalculationImpact.NOT_CALCULABLE,
                userSummary = "Nenhum alimento elegível encontrado para as restrições alimentares informadas."
            )
            return WeeklyGroceryPlanResult(
                items = emptyList(),
                targetWeeklyCalories = targetWeeklyCalories,
                planningDays = planningDays,
                periodStartDate = weekStartDate,
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

        if (available.any { it.semanticMetadata.pillar == NutritionalPillar.UNKNOWN }) {
            limitations.add("Catálogo legado sem metadados semânticos completos; quantidades usam limites conservadores. Atualize o catálogo.")
        }

        // 2. Análise de marcadores clínicos laboratoriais e vitais
        val glucose = vitals.firstOrNull { it.glucoseLevel != null }?.glucoseLevel?.toDouble()
        val hasHighGlucose = (glucose != null && glucose > 100.0) || profile?.hasDiabetes == true

        val sys = vitals.firstOrNull { it.systolicPressure != null }?.systolicPressure ?: 0
        val hasHighBp = sys > 130 || profile?.hasHypertension == true

        // 3. Distribuição energética entre os pilares nutricionais (7 dias)
        val grainTargetKcal = baselineCalories * 0.45
        val proteinTargetKcal = baselineCalories * 0.25
        val produceTargetKcal = baselineCalories * 0.15
        val pantryTargetKcal = baselineCalories * 0.15

        val plannedConsumptions = mutableListOf<PlannedConsumption>()
        val selectedCanonicalGroups = mutableSetOf<String>()

        val selectedRequiredFoodIds = mutableSetOf<String>()
        val requiredGroupsList = ingredientCatalog.requiredGroups.toMutableList()

        requiredGroupsList.forEach { group ->
            val selected = group.firstNotNullOfOrNull { id -> available.firstOrNull { it.remoteId == id } }

            if (selected != null && selected.remoteId !in selectedRequiredFoodIds) {
                selectedRequiredFoodIds.add(selected.remoteId)
                val corridor = classifyPillar(selected)
                val days = 7
                val basePortion = selected.servingSizeGrams.takeIf { it > 0 } ?: 80
                val scale = (effectiveDailyCalories / 1800.0).coerceIn(0.7, 2.5)
                val portion = (basePortion * scale).toInt().coerceIn(
                    selected.minServingGrams.takeIf { it > 0 } ?: (basePortion / 2).coerceAtLeast(20),
                    selected.maxServingGrams.takeIf { it > 0 } ?: (basePortion * 3)
                )
                val weeklyGrams = (portion * days).coerceAtMost(maxWeeklyGramsForFood(selected))
                plannedConsumptions.add(PlannedConsumption(selected, weeklyGrams, corridor))
                canonicalGroupFor(selected, ingredientCatalog)?.let { selectedCanonicalGroups.add(it) }
            }
        }

        // Pools semânticos e rotação por identificadores estáveis.
        val random = shuffleSeed?.let { java.util.Random(it) }

        fun isPreviousFood(food: FoodEntity): Boolean {
            if (previousFoodIds.isEmpty()) return false
            if (food.remoteId in previousFoodIds) return true
            val slugs = ingredientCatalog.forFood(food.remoteId, "").map { it.slug }
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
            return available.filter { classifyPillar(it) == corridor && it.remoteId !in selectedRequiredFoodIds }
        }

        fun filterCost(list: List<FoodEntity>): List<FoodEntity> {
            val shouldFilterCost = effectivePrefs.isEconomic
            if (!shouldFilterCost) return list
            val filtered = list.filterNot { isHighCostFood(it, effectivePrefs.isUltraEconomic) }
            val baseList = filtered

            return baseList.sortedBy { food ->
                val ing = ingredientCatalog.forFood(food.remoteId, "").firstOrNull()
                val price = ing?.price ?: 0.0
                if (price > 0.0 && (food.kcal ?: 0.0) > 0.0) {
                    (100.0 / (food.kcal ?: 100.0)) * price
                } else {
                    50.0
                }
            }
        }

        val grainsPool = orderPool(filterCost(poolFor(CORRIDOR_GRAOS)))

        val candidateProteins = poolFor(CORRIDOR_PROTEINAS).let { list ->
            val hasCarnivorousPref = effectivePrefs.selectedProteins.any {
                it == GroceryProteinPreference.POULTRY || it == GroceryProteinPreference.FISH || it == GroceryProteinPreference.BEEF
            }
            val prefsToApply = if (effectivePrefs.selectedProteins.isNotEmpty() && !hasCarnivorousPref && list.any { isCarnivorousProtein(it) }) {
                effectivePrefs.selectedProteins + setOf(
                    GroceryProteinPreference.POULTRY,
                    GroceryProteinPreference.FISH,
                    GroceryProteinPreference.BEEF
                )
            } else {
                effectivePrefs.selectedProteins
            }
            val byPref = list.filter { matchesProteinPreference(it, prefsToApply) }
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

        val effectiveBasketLimit = effectivePrefs.effectiveMaxBasketSize

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
            isUltraEconomic = effectivePrefs.isUltraEconomic,
            maxBasketSize = effectiveBasketLimit
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
            preferFish = hasHighBp,
            isEconomic = effectivePrefs.isEconomic,
            isUltraEconomic = effectivePrefs.isUltraEconomic,
            maxBasketSize = effectiveBasketLimit
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
            isUltraEconomic = effectivePrefs.isUltraEconomic,
            maxBasketSize = effectiveBasketLimit
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
            isPantry = true,
            maxBasketSize = effectiveBasketLimit
        )

        // 9. Calibração fina da meta calórica semanal
        val allEligibleCandidates = (grainsPool + proteinsPool + producePool + pantryPool).distinctBy { it.remoteId }
        calibrateTotalEnergy(
            targetWeeklyKcal = baselineCalories,
            plannedConsumptions = plannedConsumptions,
            candidatesPool = allEligibleCandidates,
            isUltraEconomic = effectivePrefs.isUltraEconomic,
            maxBasketSize = effectiveBasketLimit
        )

        // Garantia de respeito estrito ao maxBasketSize
        while (plannedConsumptions.size > effectiveBasketLimit && plannedConsumptions.size > 1) {
            val nonStapleMinKcal = plannedConsumptions.filterNot { it.food.remoteId in selectedRequiredFoodIds }
                .minByOrNull { it.plannedCalories }
            if (nonStapleMinKcal != null) {
                plannedConsumptions.remove(nonStapleMinKcal)
            } else {
                break
            }
        }

        // Scale consumption before packaging. Fast produce remains a single fresh week.
        if (planningDays > 7) {
            val weeklyProduceKcal = plannedConsumptions.filter { it.corridor == CORRIDOR_HORTIFRUTI }.sumOf { it.plannedCalories }
            for (i in plannedConsumptions.indices) {
                val pc = plannedConsumptions[i]
                val grams = (pc.weeklyGrams.toLong() * effectiveDays(pc.food, planningDays) / 7)
                    .coerceAtMost(maxWeeklyGramsForFood(pc.food, planningDays = planningDays).toLong()).toInt()
                plannedConsumptions[i] = pc.copy(weeklyGrams = grams)
            }
            val durable = available.filter { it.semanticMetadata.pillar == NutritionalPillar.PRODUCE && it.semanticMetadata.shelfLife == ShelfLife.PERISHABLE_DURABLE }
            val durableIds = durable.map { it.remoteId }.toSet()
            val replacement = plannedConsumptions.filter { it.food.remoteId in durableIds }.toMutableList()
            val fixedProduceKcal = plannedConsumptions.filter { it.corridor == CORRIDOR_HORTIFRUTI && it.food.remoteId !in durableIds }.sumOf { it.plannedCalories }
            calibrateTotalEnergy(
                targetWeeklyKcal = (weeklyProduceKcal * planningDays / 7.0 - fixedProduceKcal).coerceAtLeast(0.0),
                plannedConsumptions = replacement, candidatesPool = durable,
                maxBasketSize = (effectiveBasketLimit - plannedConsumptions.size + replacement.size).coerceAtLeast(0),
                planningDays = planningDays
            )
            plannedConsumptions.removeAll { it.food.remoteId in durableIds }
            plannedConsumptions.addAll(replacement)
            if (plannedConsumptions.any { it.food.semanticMetadata.pillar == NutritionalPillar.PROTEIN && it.food.semanticMetadata.shelfLife == ShelfLife.PERISHABLE_FAST }) {
                limitations.add("Congele preventivamente as porções de proteínas frescas destinadas à segunda semana.")
            }
            if (plannedConsumptions.filter { it.corridor == CORRIDOR_HORTIFRUTI }.sumOf { it.plannedCalories } < weeklyProduceKcal * planningDays / 7.0 - 200) {
                limitations.add("Hortifruti durável insuficiente para cobrir a segunda semana dentro dos limites de porção.")
            }
        }
        val periodTargetCalories = targetWeeklyCalories

        // 10. Conversão de consumo em demanda de ingredientes e consolidação canônica
        val demandsBySlug = mutableMapOf<String, MutableList<IngredientDemand>>()

        plannedConsumptions.forEach { pc ->
            val food = pc.food
            val weeklyGrams = pc.weeklyGrams
            val ingredients = ingredientCatalog.forFood(food.remoteId, "")
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

            val conversion = GroceryConsumption.amounts(food.remoteId, "", weeklyGrams.toDouble(), ingredientCatalog)
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
                planningDays = planningDays,
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
        val coveragePercent = totalPlannedKcal / periodTargetCalories * 100.0

        val distinctFoodsCount = plannedConsumptions.map { it.food.remoteId }.distinct().size

        // 12. Validação do teto orçamentário em Reais (R$)
        val totalEstimatedCost = consolidatedItems.sumOf { it.estimatedPriceBrl }
        if (effectivePrefs.maxWeeklyBudgetReais != null && effectivePrefs.maxWeeklyBudgetReais > 0 && totalEstimatedCost > effectivePrefs.maxWeeklyBudgetReais) {
            val budgetMsg = "Custo estimado da cesta (R$ ${String.format(Locale.ROOT, "%.2f", totalEstimatedCost)}) excedeu o teto orçamentário configurado (R$ ${String.format(Locale.ROOT, "%.2f", effectivePrefs.maxWeeklyBudgetReais)})."
            limitations.add(budgetMsg)
        }

        val itemsWithoutPrice = consolidatedItems.filter { it.estimatedPriceBrl <= 0.0 }
        if (itemsWithoutPrice.isNotEmpty()) {
            val msg = "Preço estimado não disponível para ${itemsWithoutPrice.size} item(ns) (${itemsWithoutPrice.joinToString(", ") { it.foodName }}). O custo total reflete apenas os itens com cotação confirmada."
            limitations.add(msg)
            structuredLimitations.add(
                GroceryLimitation(
                    type = GroceryLimitationType.MISSING_NUTRITIONAL_DATA,
                    affectedItems = itemsWithoutPrice.map { it.foodName },
                    impact = GroceryCalculationImpact.APPROXIMATED,
                    userSummary = msg
                )
            )
        }

        // 13. Mensagem informativa na interface
        val statusMessage = buildStatusMessage(
            targetDailyCalories = effectiveDailyCalories,
            targetWeeklyCalories = periodTargetCalories,
            plannedWeeklyCalories = totalPlannedKcal,
            coveragePercent = coveragePercent,
            itemCount = consolidatedItems.size,
            varietyCount = distinctFoodsCount,
            limitations = limitations,
            planningDays = effectivePrefs.planningDays

        )

        return WeeklyGroceryPlanResult(
            items = consolidatedItems,
            targetWeeklyCalories = periodTargetCalories,
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
            planningDays = planningDays,
            periodStartDate = weekStartDate,
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
        isPantry: Boolean = false,
        maxBasketSize: Int = Int.MAX_VALUE
    ) {
        if (candidates.isEmpty() || targetKcal <= 0.0) return

        val sortedCandidates = if (preferFish) {
            candidates.sortedByDescending { if (it.semanticMetadata.proteinSubtype == ProteinSubtype.FISH) 1 else 0 }
        } else {
            candidates
        }

        val remainingSlots = (maxBasketSize - destConsumptions.size).coerceAtLeast(0)
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
        }.coerceAtMost(sortedCandidates.size)
         .coerceAtMost(if (remainingSlots > 0) remainingSlots else 0)
         .coerceAtLeast(if (isPantry && isUltraEconomic) 0 else if (remainingSlots > 0) 1 else 0)

        if (countToTake <= 0) return

        val selectedPool = if (corridor == CORRIDOR_PROTEINAS && sortedCandidates.any { isCarnivorousProtein(it) }) {
            val chosen = mutableListOf<FoodEntity>()
            val primaryMeat = sortedCandidates.firstOrNull { isCarnivorousProtein(it) }
            if (primaryMeat != null) {
                chosen.add(primaryMeat)
                canonicalGroupFor(primaryMeat, ingredientCatalog)?.let { selectedCanonicalGroups.add(it) }
            }
            if (countToTake > 1) {
                val remainingCandidates = sortedCandidates.filter { it.remoteId != primaryMeat?.remoteId }
                val secondaryChosen = selectDiverseFoods(
                    candidates = remainingCandidates,
                    count = countToTake - 1,
                    ingredientCatalog = ingredientCatalog,
                    selectedGroups = selectedCanonicalGroups
                )
                chosen.addAll(secondaryChosen)
            }
            chosen
        } else {
            selectDiverseFoods(
                candidates = sortedCandidates,
                count = countToTake,
                ingredientCatalog = ingredientCatalog,
                selectedGroups = selectedCanonicalGroups
            )
        }
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

            val maxPantryCap = maxWeeklyGramsForFood(food)
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
        isUltraEconomic: Boolean = false,
        maxBasketSize: Int = Int.MAX_VALUE
    ) {
        if (candidates.isEmpty()) return

        val frutasPool = orderFn(candidates.filter { it.semanticMetadata.mealRole in setOf(MealRole.FRUIT_DENSE, MealRole.FRUIT_HYDRATING) })
        val folhasPool = orderFn(candidates.filter { it.semanticMetadata.mealRole == MealRole.VEGETABLE_LEAFY })
        val legumesPool = orderFn(candidates.filter { it.semanticMetadata.mealRole == MealRole.VEGETABLE_SOLID })

        val remainingSlots = (maxBasketSize - destConsumptions.size).coerceAtLeast(0)
        val frutasCount = when {
            isUltraEconomic -> 1
            isEconomic -> 2
            dailyCalorieTarget >= 2800.0 -> 3
            else -> 2
        }.coerceAtMost(maxOf(1, remainingSlots / 3))
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
        val folhasCount = 1.coerceAtMost(maxOf(1, (maxBasketSize - destConsumptions.size - frutas.size).coerceAtLeast(0)))
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
        }.coerceAtMost(maxOf(1, (maxBasketSize - destConsumptions.size - frutas.size - folhas.size).coerceAtLeast(0)))
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

            val maxPantryCap = maxWeeklyGramsForFood(food)
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

    private fun isStapleEnergyFood(food: FoodEntity): Boolean =
        food.semanticMetadata.mealRole in setOf(MealRole.STAPLE_BASE, MealRole.BREAKFAST_BASE, MealRole.PRIMARY_PROTEIN)

    private fun maxWeeklyGramsForFood(food: FoodEntity, planningDays: Int = 7): Int {
        val days = effectiveDays(food, planningDays)
        val dailyCap = food.maxDailyGrams?.takeIf { it > 0 }?.toLong()
            ?: (food.servingSizeGrams.coerceAtLeast(1).toLong() * 2)
        return (dailyCap * days).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun effectiveDays(food: FoodEntity, planningDays: Int): Int {
        val meta = food.semanticMetadata
        return if (meta.isPantryStaple ||
            (meta.shelfLife == ShelfLife.PERISHABLE_FAST && meta.pillar != NutritionalPillar.PROTEIN))
            minOf(planningDays, 7) else planningDays
    }

    private fun calibrateTotalEnergy(
        targetWeeklyKcal: Double,
        plannedConsumptions: MutableList<PlannedConsumption>,
        candidatesPool: List<FoodEntity>,
        isUltraEconomic: Boolean = false,
        maxBasketSize: Int = Int.MAX_VALUE,
        planningDays: Int = 7
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
                        val maxAllowedGrams = maxWeeklyGramsForFood(pc.food, planningDays)
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
                val weeklyGrams = (portion * days).coerceAtMost(maxWeeklyGramsForFood(food, planningDays))
                val foodTotalKcal = (weeklyGrams * foodKcal) / 100.0
                plannedConsumptions.add(PlannedConsumption(food, weeklyGrams, corridor))
                deficit -= foodTotalKcal
            }
        }

        currentKcal = plannedConsumptions.sumOf { it.plannedCalories }

        // Passo C: Redução de excesso (superávit acima de 110% da meta semanal)
        val maxTolerableKcal = targetWeeklyKcal * 1.10
        if (currentKcal > maxTolerableKcal) {
            var excessToTrim = currentKcal - (targetWeeklyKcal * 1.05)
            for (i in plannedConsumptions.indices.reversed()) {
                if (excessToTrim <= 50.0) break
                val pc = plannedConsumptions[i]
                val foodKcal = pc.food.kcal ?: 0.0
                if (foodKcal <= 0.0) continue

                val baseServing = pc.food.servingSizeGrams.takeIf { it > 0 } ?: 60
                val minPortion = pc.food.minServingGrams.takeIf { it > 0 } ?: (baseServing / 2).coerceAtLeast(10)
                val minWeeklyGrams = minPortion * (if (pc.corridor == CORRIDOR_GRAOS) 5 else 4)
                val reducibleGrams = (pc.weeklyGrams - minWeeklyGrams).coerceAtLeast(0)

                if (reducibleGrams > 0) {
                    val gramsToCut = minOf(reducibleGrams, ((excessToTrim / foodKcal) * 100.0).toInt())
                    if (gramsToCut > 0) {
                        plannedConsumptions[i] = pc.copy(weeklyGrams = pc.weeklyGrams - gramsToCut)
                        excessToTrim -= (gramsToCut * foodKcal) / 100.0
                    }
                }
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
        limitations: List<String>,
        planningDays: Int = 7

    ): String {
        return if (coveragePercent < MIN_ENERGY_COVERAGE_PERCENT) {
            val deficit = (targetWeeklyCalories - plannedWeeklyCalories).toInt()
            "Lista planejada para $planningDays dias com ${plannedWeeklyCalories.toInt()} kcal (${String.format(Locale.ROOT, "%.1f%%", coveragePercent)} da meta de ${targetWeeklyCalories.toInt()} kcal). Déficit de $deficit kcal devido a limites de porção e restrições do catálogo."
        } else if (coveragePercent > MAX_ENERGY_COVERAGE_PERCENT) {
            "Lista planejada para $planningDays dias com ${plannedWeeklyCalories.toInt()} kcal (${String.format(Locale.ROOT, "%.1f%%", coveragePercent)} da meta de ${targetWeeklyCalories.toInt()} kcal) com $itemCount itens variados."
        } else {
            "Quantidades calculadas para a sua meta diária de ${targetDailyCalories.toInt()} kcal ($planningDays dias: ${plannedWeeklyCalories.toInt()} kcal planejadas, $itemCount itens)."
        }
    }

    /**
     * Classificação baseada exclusivamente no pilar declarado pelo servidor.
     */
    fun classifyPillar(food: FoodEntity): String = when (food.semanticMetadata.pillar) {
        NutritionalPillar.GRAINS_TUBERS -> CORRIDOR_GRAOS
        NutritionalPillar.PROTEIN -> CORRIDOR_PROTEINAS
        NutritionalPillar.PRODUCE -> CORRIDOR_HORTIFRUTI
        else -> CORRIDOR_MERCEARIA
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
     * dos ingredientes associados ou, como fallback, do identificador estável.
     */
    fun canonicalGroupFor(food: FoodEntity, catalog: GroceryIngredientCatalog): String? {
        val ingredient = catalog.forFood(food.remoteId, "").firstOrNull()
        val fromIngredient = ingredient?.canonicalGroupSlug
        if (!fromIngredient.isNullOrBlank()) return fromIngredient
        return ingredient?.slug ?: food.remoteId
    }

    /**
     * Seleciona candidatos priorizando diversidade de grupos canônicos para evitar
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

    private fun isHighCostFood(food: FoodEntity, isUltraEconomic: Boolean = false): Boolean =
        if (isUltraEconomic) food.semanticMetadata.costTier != CostTier.ESSENTIAL
        else food.semanticMetadata.costTier == CostTier.PREMIUM

    private fun isPantryStaple(food: FoodEntity): Boolean =
        food.semanticMetadata.mealRole == MealRole.PANTRY_SEASONING

    private fun matchesProteinPreference(food: FoodEntity, preferences: Set<GroceryProteinPreference>): Boolean =
        preferences.isEmpty() || food.semanticMetadata.proteinSubtype == ProteinSubtype.DAIRY ||
            preferences.any { it.name == food.semanticMetadata.proteinSubtype.name }

    fun isCarnivorousProtein(food: FoodEntity): Boolean = food.semanticMetadata.proteinSubtype in
        setOf(ProteinSubtype.POULTRY, ProteinSubtype.BEEF, ProteinSubtype.PORK, ProteinSubtype.FISH)

    fun isEggProtein(food: FoodEntity): Boolean = food.semanticMetadata.proteinSubtype == ProteinSubtype.EGGS
}
