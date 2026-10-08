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
    val statusMessage: String
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
        targetCalories: Double = 1800.0
    ): List<GroceryListItemEntity> {
        return planWeeklyGrocery(
            userId = userId,
            exams = exams,
            vitals = vitals,
            profile = profile,
            catalog = catalog,
            dislikedFoodNames = dislikedFoodNames,
            ingredientCatalog = ingredientCatalog,
            targetCalories = targetCalories
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
        targetCalories: Double = 1800.0
    ): WeeklyGroceryPlanResult {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val weekStartDate = dateFormat.format(Date())

        val limitations = mutableListOf<String>()

        val effectiveDailyCalories = targetCalories.coerceAtLeast(500.0)
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
            return WeeklyGroceryPlanResult(
                items = emptyList(),
                targetWeeklyCalories = targetWeeklyCalories,
                plannedWeeklyCalories = 0.0,
                coveragePercent = 0.0,
                plannedProteinGrams = 0.0,
                plannedCarbsGrams = 0.0,
                plannedFatGrams = 0.0,
                foodVarietyCount = 0,
                limitations = listOf("Nenhum alimento elegível encontrado para as restrições alimentares informadas."),
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
        // AMDR/FAO/OMS: ~45% bases/carboidratos, ~25% proteínas/leguminosas, ~15% hortifruti, ~15% mercearia/gorduras
        val grainTargetKcal = targetWeeklyCalories * 0.45
        val proteinTargetKcal = targetWeeklyCalories * 0.25
        val produceTargetKcal = targetWeeklyCalories * 0.15
        val pantryTargetKcal = targetWeeklyCalories * 0.15

        val plannedConsumptions = mutableListOf<PlannedConsumption>()

        // 4. Preservação de grupos básicos obrigatórios da política do gateway (arroz e feijão)
        val selectedRequiredFoodIds = mutableSetOf<String>()
        ingredientCatalog.requiredGroups.forEach { group ->
            val selected = group.firstNotNullOfOrNull { id -> available.firstOrNull { it.remoteId == id } }
                ?: group.firstNotNullOfOrNull { id -> safeCatalog.firstOrNull { it.remoteId == id } }
            if (selected != null) {
                selectedRequiredFoodIds.add(selected.remoteId)
                val corridor = classifyPillar(selected)
                // Arroz e feijão compõem refeições principais ao longo da semana (5 a 7 dias)
                val days = if (catalog.size <= 5) 5 else 7
                val basePortion = selected.servingSizeGrams.takeIf { it > 0 } ?: 80
                val scale = (effectiveDailyCalories / 2000.0).coerceIn(0.7, 2.5)
                val portion = (basePortion * scale).toInt().coerceIn(
                    selected.minServingGrams.takeIf { it > 0 } ?: (basePortion / 2).coerceAtLeast(20),
                    selected.maxServingGrams.takeIf { it > 0 } ?: (basePortion * 3)
                )
                val weeklyGrams = portion * days
                plannedConsumptions.add(PlannedConsumption(selected, weeklyGrams, corridor))
            }
        }

        // Separação em pools taxonômicos normalizados (sem fragilidade de acentos)
        fun poolFor(corridor: String): List<FoodEntity> {
            val list = available.filter { classifyPillar(it) == corridor && it.remoteId !in selectedRequiredFoodIds }
            return if (list.isNotEmpty()) list else safeCatalog.filter { classifyPillar(it) == corridor && it.remoteId !in selectedRequiredFoodIds }
        }

        val grainsPool = poolFor(CORRIDOR_GRAOS)
        val proteinsPool = poolFor(CORRIDOR_PROTEINAS)
        val producePool = poolFor(CORRIDOR_HORTIFRUTI)
        val pantryPool = poolFor(CORRIDOR_MERCEARIA)

        // Verificação de alimentos sem kcal
        val foodsWithoutKcal = (available + safeCatalog).filter { it.kcal == null || it.kcal <= 0.0 }.distinctBy { it.remoteId }
        if (foodsWithoutKcal.isNotEmpty()) {
            limitations.add("Existem alimentos no catálogo com valor calórico ausente (${foodsWithoutKcal.size} item(ns)).")
        }

        // 5. Dimensionamento do Pilar 1: Grãos, Cereais, Tubérculos e Raízes
        val existingGrainKcal = plannedConsumptions.filter { it.corridor == CORRIDOR_GRAOS }.sumOf { it.plannedCalories }
        val remainingGrainKcal = (grainTargetKcal - existingGrainKcal).coerceAtLeast(0.0)
        distributePillarEnergy(
            targetKcal = remainingGrainKcal,
            candidates = grainsPool,
            corridor = CORRIDOR_GRAOS,
            dailyCalorieTarget = effectiveDailyCalories,
            destConsumptions = plannedConsumptions,
            limitations = limitations
        )

        // 6. Dimensionamento do Pilar 2: Proteínas (Animais/Vegetais, Leguminosas, Laticínios, Ovos)
        val existingProteinKcal = plannedConsumptions.filter { it.corridor == CORRIDOR_PROTEINAS }.sumOf { it.plannedCalories }
        val remainingProteinKcal = (proteinTargetKcal - existingProteinKcal).coerceAtLeast(0.0)
        distributePillarEnergy(
            targetKcal = remainingProteinKcal,
            candidates = proteinsPool,
            corridor = CORRIDOR_PROTEINAS,
            dailyCalorieTarget = effectiveDailyCalories,
            destConsumptions = plannedConsumptions,
            limitations = limitations,
            preferFish = hasHighCholesterol || hasHighBp
        )

        // 7. Dimensionamento do Pilar 3: Hortifruti (Frutas, Folhosos, Legumes)
        distributeProduceEnergy(
            targetKcal = produceTargetKcal,
            candidates = producePool,
            dailyCalorieTarget = effectiveDailyCalories,
            hasHighGlucose = hasHighGlucose,
            hasHighBp = hasHighBp,
            destConsumptions = plannedConsumptions,
            limitations = limitations
        )

        // 8. Dimensionamento do Pilar 4: Mercearia, Gorduras Boas, Sementes, Temperos e Chás
        distributePillarEnergy(
            targetKcal = pantryTargetKcal,
            candidates = pantryPool,
            corridor = CORRIDOR_MERCEARIA,
            dailyCalorieTarget = effectiveDailyCalories,
            destConsumptions = plannedConsumptions,
            limitations = limitations
        )

        // 9. Calibração fina da meta calórica semanal
        calibrateTotalEnergy(
            targetWeeklyKcal = targetWeeklyCalories,
            plannedConsumptions = plannedConsumptions,
            limitations = limitations
        )

        // 10. Conversão de consumo em demanda de ingredientes e consolidação canônica
        val demandsBySlug = mutableMapOf<String, MutableList<IngredientDemand>>()

        plannedConsumptions.forEach { pc ->
            val food = pc.food
            val weeklyGrams = pc.weeklyGrams
            val ingredients = ingredientCatalog.forFood(food.remoteId, food.name)
            if (ingredients.isEmpty()) {
                limitations.add("Alimento '${food.name}' não possui ingrediente correspondente mapeado.")
                return@forEach
            }

            if (ingredients.size == 1) {
                val ingredient = ingredients.first()
                val factor = ingredientCatalog.purchaseFactors[food.remoteId]?.get(ingredient.slug) ?: 1.0
                val demandAmount = calculateDemandAmount(ingredient, food, weeklyGrams, factor, limitations)
                demandsBySlug.getOrPut(ingredient.slug) { mutableListOf() }.add(
                    IngredientDemand(ingredient, demandAmount, weeklyGrams, pc.corridor)
                )
            } else {
                // Preparação com múltiplos componentes: divide proporcionalmente
                val share = 1.0 / ingredients.size
                val factorsMap = ingredientCatalog.purchaseFactors[food.remoteId]
                ingredients.forEach { ingredient ->
                    val factor = factorsMap?.get(ingredient.slug) ?: 1.0
                    val compGrams = (weeklyGrams * share).toInt()
                    val demandAmount = calculateDemandAmount(ingredient, food, compGrams, factor, limitations)
                    demandsBySlug.getOrPut(ingredient.slug) { mutableListOf() }.add(
                        IngredientDemand(ingredient, demandAmount, compGrams, pc.corridor)
                    )
                }
            }
        }

        // Consolidação sem duplicações: uma única linha por ingrediente canônico
        val consolidatedItems = demandsBySlug.map { (slug, demands) ->
            val ingredient = demands.first().ingredient
            val totalPrepDemand = demands.sumOf { it.prepDemandAmount }
            val totalSuggestedServingGrams = demands.sumOf { it.plannedServingWeekGrams }
            val preferredCorridor = demands.groupBy { it.corridor }.maxByOrNull { it.value.size }?.key ?: CORRIDOR_GRAOS

            // Margem de segurança de compra (15%) aplicada somente para a compra, sem contar nas calorias
            val demandWithMargin = ceil(totalPrepDemand * PURCHASE_SAFETY_MARGIN).toInt().coerceAtLeast(1)

            val purchaseAmount = GroceryPurchasePlanner.quantity(ingredient, demandWithMargin)

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
                estimatedPriceBrl = GroceryPurchasePlanner.cost(ingredient, purchaseAmount)
            )
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
            statusMessage = statusMessage
        )
    }

    private fun calculateDemandAmount(
        ingredient: GroceryIngredient,
        food: FoodEntity,
        weeklyGrams: Int,
        factor: Double,
        limitations: MutableList<String>
    ): Double {
        return when (ingredient.unit) {
            "kg" -> weeklyGrams * factor
            "L" -> weeklyGrams * factor // Volume em mL (densidade padrão água/leite 1g = 1mL)
            "un" -> {
                // Dedução estruturada a partir do metadado de porção do alimento
                val unitGrams = if (food.servingUnit.contains("unidade", ignoreCase = true) && food.servingSizeGrams > 0) {
                    food.servingSizeGrams.toDouble()
                } else if (food.servingSizeGrams > 0) {
                    food.servingSizeGrams.toDouble()
                } else {
                    50.0 // Média biológica de ovo
                }
                ceil((weeklyGrams * factor) / unitGrams)
            }
            else -> {
                limitations.add("Unidade '${ingredient.unit}' não reconhecida para ${ingredient.name}.")
                weeklyGrams * factor
            }
        }
    }

    private fun distributePillarEnergy(
        targetKcal: Double,
        candidates: List<FoodEntity>,
        corridor: String,
        dailyCalorieTarget: Double,
        destConsumptions: MutableList<PlannedConsumption>,
        limitations: MutableList<String>,
        preferFish: Boolean = false
    ) {
        if (candidates.isEmpty() || targetKcal <= 0.0) return

        val sortedCandidates = if (preferFish) {
            candidates.sortedByDescending { if (it.category?.contains("Peixes", ignoreCase = true) == true) 1 else 0 }
        } else {
            candidates
        }

        // Escolhe um subconjunto variado (2 a 4 alimentos por pilar)
        val selectedPool = sortedCandidates.take(4)
        val perFoodKcalTarget = targetKcal / selectedPool.size

        selectedPool.forEach { food ->
            val kcalPer100 = food.kcal ?: 0.0
            val baseServing = food.servingSizeGrams.takeIf { it > 0 } ?: 60
            val minPortion = food.minServingGrams.takeIf { it > 0 } ?: (baseServing / 2).coerceAtLeast(10)
            val maxPortion = food.maxServingGrams.takeIf { it > 0 } ?: (baseServing * 3)

            val days = when (corridor) {
                CORRIDOR_GRAOS -> 5
                CORRIDOR_PROTEINAS -> 6
                else -> 4
            }

            val weeklyGrams = if (kcalPer100 > 0.0) {
                val neededGrams = ((perFoodKcalTarget / kcalPer100) * 100.0).toInt()
                val portion = (neededGrams / days).coerceIn(minPortion, maxPortion)
                portion * days
            } else {
                val scale = (dailyCalorieTarget / 2000.0).coerceIn(0.7, 2.5)
                val portion = (baseServing * scale).toInt().coerceIn(minPortion, maxPortion)
                portion * days
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
        destConsumptions: MutableList<PlannedConsumption>,
        limitations: MutableList<String>
    ) {
        if (candidates.isEmpty()) return

        val frutasPool = candidates.filter { groceryNameKey(it.category.orEmpty()).contains("fruta") }
        val folhasPool = candidates.filter {
            val c = groceryNameKey(it.category.orEmpty())
            c.contains("verdura") || c.contains("folhoso") || c.contains("hortalica") || c.contains("crucifera")
        }
        val legumesPool = candidates.filter { groceryNameKey(it.category.orEmpty()).contains("legume") }

        val frutas = if (hasHighGlucose) {
            frutasPool.filter { it.functionalTags.contains("baixo_ig") || it.functionalTags.contains("fibra_soluvel") }.take(2)
        } else {
            frutasPool.take(3)
        }.ifEmpty { frutasPool.take(2) }

        val folhas = if (hasHighBp) {
            folhasPool.filter { it.functionalTags.contains("nitrato_natural") || it.functionalTags.contains("magnesio") || it.functionalTags.contains("potassio") }.take(2)
        } else {
            folhasPool.take(2)
        }.ifEmpty { folhasPool.take(2) }

        val legumes = legumesPool.take(2)

        val selectedProduce = (frutas + folhas + legumes).distinctBy { it.remoteId }
        val perFoodKcal = if (selectedProduce.isNotEmpty()) targetKcal / selectedProduce.size else 0.0

        selectedProduce.forEach { food ->
            val kcalPer100 = food.kcal ?: 0.0
            val baseServing = food.servingSizeGrams.takeIf { it > 0 } ?: 100
            val minPortion = food.minServingGrams.takeIf { it > 0 } ?: (baseServing / 2).coerceAtLeast(20)
            val maxPortion = food.maxServingGrams.takeIf { it > 0 } ?: (baseServing * 3)
            val days = 6

            val weeklyGrams = if (kcalPer100 > 0.0 && perFoodKcal > 0.0) {
                val neededGrams = ((perFoodKcal / kcalPer100) * 100.0).toInt()
                val portion = (neededGrams / days).coerceIn(minPortion, maxPortion)
                portion * days
            } else {
                val scale = (dailyCalorieTarget / 2000.0).coerceIn(0.7, 2.5)
                val portion = (baseServing * scale).toInt().coerceIn(minPortion, maxPortion)
                portion * days
            }

            destConsumptions.add(PlannedConsumption(food, weeklyGrams, CORRIDOR_HORTIFRUTI))
        }
    }

    private fun calibrateTotalEnergy(
        targetWeeklyKcal: Double,
        plannedConsumptions: MutableList<PlannedConsumption>,
        limitations: MutableList<String>
    ) {
        val currentKcal = plannedConsumptions.sumOf { it.plannedCalories }
        val deficit = targetWeeklyKcal - currentKcal

        if (deficit > 500.0 && plannedConsumptions.isNotEmpty()) {
            // Ajusta proporcionalmente alimentos com calorias positivas respeitando os limites máximos de porção
            val eligibleForBoost = plannedConsumptions.filter { (it.food.kcal ?: 0.0) > 0.0 }
            if (eligibleForBoost.isNotEmpty()) {
                val boostShare = deficit / eligibleForBoost.size
                for (i in plannedConsumptions.indices) {
                    val pc = plannedConsumptions[i]
                    val foodKcal = pc.food.kcal ?: 0.0
                    if (foodKcal > 0.0) {
                        val additionalGrams = ((boostShare / foodKcal) * 100.0).toInt()
                        val maxAllowedGrams = (pc.food.maxServingGrams.takeIf { it > 0 } ?: 500) * 7
                        val newGrams = (pc.weeklyGrams + additionalGrams).coerceAtMost(maxAllowedGrams)
                        plannedConsumptions[i] = pc.copy(weeklyGrams = newGrams)
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
}
