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

object WeeklyGroceryEngine {

    const val CORRIDOR_HORTIFRUTI = "Hortifruti e Feira"
    const val CORRIDOR_GRAOS = "Cereais, Sementes e Graos"
    const val CORRIDOR_PROTEINAS = "Proteinas, Ovos e Laticinios"
    const val CORRIDOR_MERCEARIA = "Mercearia, Temperos e Chas"

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
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val weekStartDate = dateFormat.format(Date())

        val available = catalog.filter { food ->
            if (ingredientCatalog.forFood(food.remoteId, food.name).isEmpty()) return@filter false
            val clean = food.name.trim().lowercase()
            val notDisliked = !dislikedFoodNames.any { it.trim().lowercase() == clean }
            val allergySafe = NutritionSuggestionEngine.isSafeFromAllergies(
                food,
                profile?.foodAllergies ?: emptyList(),
                profile?.customFoodRestrictions
            )
            notDisliked && allergySafe &&
                (profile?.hasDiabetes != true || food.isDiabetesSafe) &&
                (profile?.hasHypertension != true || food.isHypertensionSafe) &&
                (profile?.hasThyroidIssue != true || food.isThyroidSafe)
        }

        // Determina marcadores clinicos
        val glucose = exams.firstOrNull { it.itemKey == "glucose" }?.valueNumeric
            ?: vitals.firstOrNull { it.glucoseLevel != null }?.glucoseLevel?.toDouble()
        val hasHighGlucose = (glucose != null && glucose > 100.0) || profile?.hasDiabetes == true

        val cholesterol = exams.firstOrNull { it.itemKey == "total_cholesterol" }?.valueNumeric
        val hasHighCholesterol = cholesterol != null && cholesterol > 190.0

        val sys = vitals.firstOrNull { it.systolicPressure != null }?.systolicPressure ?: 0
        val hasHighBp = sys > 130 || profile?.hasHypertension == true

        val selectedItems = mutableListOf<GroceryListItemEntity>()
        // Os grupos básicos e a ordem de preferência são fornecidos pelo gateway.
        ingredientCatalog.requiredGroups.forEach { group ->
            val selected = group.firstNotNullOfOrNull { id -> available.firstOrNull { it.remoteId == id } }
            if (selected != null) selectedItems.addAll(createGroceryItems(
                userId, weekStartDate, selected, CORRIDOR_GRAOS, ingredientCatalog, targetCalories))
        }


        // 1. CORREDOR HORTIFRUTI (6 a 8 itens)
        val frutasPool = available.filter { it.category?.contains("Frutas", ignoreCase = true) == true }.shuffled()
        val frutasEscolhidas = if (hasHighGlucose) {
            frutasPool.filter { it.functionalTags.contains("baixo_ig") || it.functionalTags.contains("fibra_soluvel") }.take(2)
        } else {
            frutasPool.take(2)
        }.ifEmpty { frutasPool.take(2) }

        val folhasPool = available.filter { it.category?.contains("Verduras", ignoreCase = true) == true }.shuffled()
        val folhasEscolhidas = if (hasHighBp) {
            folhasPool.filter { it.functionalTags.contains("nitrato_natural") || it.functionalTags.contains("magnesio") || it.functionalTags.contains("potassio") }.take(2)
        } else {
            folhasPool.take(2)
        }.ifEmpty { folhasPool.take(2) }

        val legumesPool = available.filter { it.category?.contains("Legumes", ignoreCase = true) == true || it.category?.contains("Raizes", ignoreCase = true) == true }.shuffled()
        val legumesEscolhidos = legumesPool.take(2)

        val citrusPool = available.filter { it.name.contains("Lima", ignoreCase = true) || it.name.contains("Alho", ignoreCase = true) }
        val temperoFresco = citrusPool.take(1)

        val hortifrutiFoods = frutasEscolhidas + folhasEscolhidas + legumesEscolhidos + temperoFresco

        hortifrutiFoods.forEach { food ->
            selectedItems.addAll(createGroceryItems(userId, weekStartDate, food, CORRIDOR_HORTIFRUTI, ingredientCatalog, targetCalories))
        }

        // 2. CORREDOR CEREAIS & GRAOS (3 a 4 itens)
        val aveiaOrCereal = available.filter { it.name.contains("Aveia", ignoreCase = true) || it.name.contains("Quinoa", ignoreCase = true) }.shuffled().take(1)
        val sementes = available.filter { it.category?.contains("Sementes", ignoreCase = true) == true || it.category?.contains("Oleaginosas", ignoreCase = true) == true }.shuffled().take(1)
        val leguminosa = available.filter { it.category?.contains("Leguminosas", ignoreCase = true) == true }.shuffled().take(1)
        val graoOuRaiz = available.filter { it.category?.contains("Graos", ignoreCase = true) == true || it.category?.contains("Tuberculos", ignoreCase = true) == true }.shuffled().take(1)

        val graosFoods = (aveiaOrCereal + sementes + leguminosa + graoOuRaiz).distinctBy { it.remoteId }
        graosFoods.forEach { food ->
            selectedItems.addAll(createGroceryItems(userId, weekStartDate, food, CORRIDOR_GRAOS, ingredientCatalog, targetCalories))
        }

        // 3. CORREDOR PROTEINAS, OVOS & LATICINIOS (3 a 4 itens)
        val ovos = available.filter { it.name.contains("Ovo", ignoreCase = true) }.take(1)
        val carnesPeixes = available.filter { it.category?.contains("Peixes", ignoreCase = true) == true || it.category?.contains("Carnes", ignoreCase = true) == true }.shuffled().take(2)
        val laticinios = available.filter { it.category?.contains("Laticinios", ignoreCase = true) == true || it.category?.contains("Queijo", ignoreCase = true) == true }.shuffled().take(1)

        val vegetais = available.filter { it.functionalTags.contains("proteina_vegetal") }.shuffled().take(1)
        val proteinasFoods = (ovos + carnesPeixes + laticinios + vegetais).distinctBy { it.remoteId }
        proteinasFoods.forEach { food ->
            selectedItems.addAll(createGroceryItems(userId, weekStartDate, food, CORRIDOR_PROTEINAS, ingredientCatalog, targetCalories))
        }

        // 4. CORREDOR MERCEARIA, TEMPEROS & CHAS (2 a 3 itens)
        val azeite = available.filter { it.name.contains("Azeite", ignoreCase = true) || it.category?.contains("Gorduras", ignoreCase = true) == true }.take(1)
        val especiaria = available.filter { it.category?.contains("Especiarias", ignoreCase = true) == true || it.category?.contains("Temperos", ignoreCase = true) == true }.shuffled().take(1)
        val chas = available.filter { it.category?.contains("Chas", ignoreCase = true) == true }.shuffled().take(1)

        val merceariaFoods = (azeite + especiaria + chas).distinctBy { it.remoteId }
        merceariaFoods.forEach { food ->
            selectedItems.addAll(createGroceryItems(userId, weekStartDate, food, CORRIDOR_MERCEARIA, ingredientCatalog, targetCalories))
        }

        return selectedItems.groupBy { it.foodId }.values.map { entries -> entries.maxBy { it.purchaseWeightGrams } }
    }

    private fun createGroceryItems(
        userId: String,
        weekStartDate: String,
        food: FoodEntity,
        corridor: String,
        ingredientCatalog: GroceryIngredientCatalog,
        targetCalories: Double = 1800.0
    ): List<GroceryListItemEntity> {
        val dailyGrams = food.servingSizeGrams.takeIf { it > 0 } ?: 50
        val days = when (corridor) { CORRIDOR_HORTIFRUTI -> 6; CORRIDOR_GRAOS -> 5; CORRIDOR_PROTEINAS -> 6; else -> 4 }
        val calorieFactor = (targetCalories / 1800.0).coerceIn(0.7, 1.6)
        val plannedGrams = (dailyGrams * days * calorieFactor * 1.20).toInt().coerceAtLeast(50)
        val scaledWeeklyServingGrams = (dailyGrams * days * calorieFactor).toInt()
        val ingredients = ingredientCatalog.forFood(food.remoteId, food.name)
        return ingredients.map { ingredient ->
            val factor = ingredientCatalog.purchaseFactors[food.remoteId]?.get(ingredient.slug) ?: 1.0
            val requested = if (ingredients.size == 1) kotlin.math.ceil(plannedGrams * factor).toInt() else ingredient.minimum
            val amount = GroceryPurchasePlanner.quantity(ingredient, requested)
            GroceryListItemEntity(
                remoteId = UUID.randomUUID().toString(),
                userId = userId,
                weekStartDate = weekStartDate,
                foodId = ingredient.slug,
                foodName = ingredient.name,
                category = corridor,
                suggestedServingWeekGrams = scaledWeeklyServingGrams,
                purchaseWeightGrams = if (ingredient.unit == "kg") amount else 0,
                purchaseUnitText = GroceryPurchasePlanner.text(ingredient, amount),
                estimatedPriceBrl = GroceryPurchasePlanner.cost(ingredient, amount)
            )
        }
    }
}
