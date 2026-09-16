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
        dislikedFoodNames: Set<String> = emptySet()
    ): List<GroceryListItemEntity> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val weekStartDate = dateFormat.format(Date())

        val available = catalog.filter { food ->
            val clean = food.name.trim().lowercase()
            val notDisliked = !dislikedFoodNames.any { it.trim().lowercase() == clean }
            val allergySafe = NutritionSuggestionEngine.isSafeFromAllergies(
                food,
                profile?.foodAllergies ?: emptyList(),
                profile?.customFoodRestrictions
            )
            notDisliked && allergySafe
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
            selectedItems.add(createGroceryItem(userId, weekStartDate, food, CORRIDOR_HORTIFRUTI))
        }

        // 2. CORREDOR CEREAIS & GRAOS (3 a 4 itens)
        val aveiaOrCereal = available.filter { it.name.contains("Aveia", ignoreCase = true) || it.name.contains("Quinoa", ignoreCase = true) }.shuffled().take(1)
        val sementes = available.filter { it.category?.contains("Sementes", ignoreCase = true) == true || it.category?.contains("Oleaginosas", ignoreCase = true) == true }.shuffled().take(1)
        val leguminosa = available.filter { it.category?.contains("Leguminosas", ignoreCase = true) == true }.shuffled().take(1)
        val graoOuRaiz = available.filter { it.category?.contains("Graos", ignoreCase = true) == true || it.category?.contains("Tuberculos", ignoreCase = true) == true }.shuffled().take(1)

        val graosFoods = (aveiaOrCereal + sementes + leguminosa + graoOuRaiz).distinctBy { it.remoteId }
        graosFoods.forEach { food ->
            selectedItems.add(createGroceryItem(userId, weekStartDate, food, CORRIDOR_GRAOS))
        }

        // 3. CORREDOR PROTEINAS, OVOS & LATICINIOS (3 a 4 itens)
        val ovos = available.filter { it.name.contains("Ovo", ignoreCase = true) }.take(1)
        val carnesPeixes = available.filter { it.category?.contains("Peixes", ignoreCase = true) == true || it.category?.contains("Carnes", ignoreCase = true) == true }.shuffled().take(2)
        val laticinios = available.filter { it.category?.contains("Laticinios", ignoreCase = true) == true || it.category?.contains("Queijo", ignoreCase = true) == true }.shuffled().take(1)

        val proteinasFoods = (ovos + carnesPeixes + laticinios).distinctBy { it.remoteId }
        proteinasFoods.forEach { food ->
            selectedItems.add(createGroceryItem(userId, weekStartDate, food, CORRIDOR_PROTEINAS))
        }

        // 4. CORREDOR MERCEARIA, TEMPEROS & CHAS (2 a 3 itens)
        val azeite = available.filter { it.name.contains("Azeite", ignoreCase = true) || it.category?.contains("Gorduras", ignoreCase = true) == true }.take(1)
        val especiaria = available.filter { it.category?.contains("Especiarias", ignoreCase = true) == true || it.category?.contains("Temperos", ignoreCase = true) == true }.shuffled().take(1)
        val chas = available.filter { it.category?.contains("Chas", ignoreCase = true) == true }.shuffled().take(1)

        val merceariaFoods = (azeite + especiaria + chas).distinctBy { it.remoteId }
        merceariaFoods.forEach { food ->
            selectedItems.add(createGroceryItem(userId, weekStartDate, food, CORRIDOR_MERCEARIA))
        }

        return selectedItems
    }

    private fun createGroceryItem(
        userId: String,
        weekStartDate: String,
        food: FoodEntity,
        corridor: String
    ): GroceryListItemEntity {
        val dailyGrams = if (food.servingSizeGrams > 0) food.servingSizeGrams else 50
        val daysPerWeek = when (corridor) {
            CORRIDOR_HORTIFRUTI -> 6
            CORRIDOR_GRAOS -> 5
            CORRIDOR_PROTEINAS -> 6
            else -> 4
        }
        val weeklyBaseGrams = dailyGrams * daysPerWeek
        // Aplica Margem de Seguranca de +20%
        val purchaseGrams = (weeklyBaseGrams * 1.20).toInt().coerceAtLeast(50)

        val (unitText, price) = calculatePackagingAndPrice(food, purchaseGrams)

        return GroceryListItemEntity(
            remoteId = UUID.randomUUID().toString(),
            userId = userId,
            weekStartDate = weekStartDate,
            foodId = food.remoteId,
            foodName = food.name,
            category = corridor,
            suggestedServingWeekGrams = weeklyBaseGrams,
            purchaseWeightGrams = purchaseGrams,
            purchaseUnitText = unitText,
            estimatedPriceBrl = price,
            isCheckedInPantry = false
        )
    }

    private fun calculatePackagingAndPrice(food: FoodEntity, grams: Int): Pair<String, Double> {
        val name = food.name.lowercase()

        return when {
            name.contains("ovo") -> Pair("1 duzia (12 unidades)", 13.50)
            name.contains("banana") -> Pair("1 palma / penca (~850g a 1kg)", 7.20)
            name.contains("maca") || name.contains("pera") -> Pair("5 a 6 unidades (~700g)", 8.50)
            name.contains("mamao") -> Pair("1 unidade media (~600g)", 6.00)
            name.contains("melao") || name.contains("melancia") -> Pair("1 unidade / fatia grande (~1,5kg)", 9.00)
            name.contains("couve") || name.contains("alface") || name.contains("rucula") || name.contains("agriao") -> Pair("2 maos frescos", 6.50)
            name.contains("brocolis") || name.contains("couve-flor") -> Pair("1 mao grande (~500g)", 7.80)
            name.contains("beterraba") || name.contains("cenoura") -> Pair("3 unidades medias (~450g)", 4.80)
            name.contains("tomate") -> Pair("4 a 5 unidades (~500g)", 5.50)
            name.contains("limao") -> Pair("4 unidades (~300g)", 3.50)
            name.contains("alho") -> Pair("1 cabeca media (~50g)", 2.50)
            name.contains("aveia") -> Pair("1 pacote (200g a 250g)", 5.90)
            name.contains("chia") || name.contains("linhaca") || name.contains("psyllium") -> Pair("1 pacote (150g)", 7.50)
            name.contains("castanha") || name.contains("nozes") || name.contains("amendoa") -> Pair("100g a granel (~10 un)", 11.00)
            name.contains("feijao") || name.contains("arroz") -> Pair("1 pacote (1kg)", 8.50)
            name.contains("lentilha") || name.contains("grao-de-bico") -> Pair("1 pacote (500g)", 9.20)
            name.contains("frango") || name.contains("patinho") -> Pair("Bandeja de 500g a 600g", 16.50)
            name.contains("sardinha") || name.contains("tilapia") || name.contains("pescada") -> Pair("Bandeja de 500g de files", 18.90)
            name.contains("ricota") || name.contains("minas") || name.contains("iogurte") -> Pair("1 embalagem (250g a 500g)", 8.90)
            name.contains("azeite") -> Pair("1 garrafa (500ml)", 36.00)
            name.contains("canela") || name.contains("curcuma") || name.contains("gengibre") || name.contains("paprica") -> Pair("1 pacote / frasco (50g)", 4.50)
            name.contains("cha") -> Pair("1 caixa (10 a 15 saches) ou 50g erva", 6.20)
            grams >= 1000 -> Pair("${String.format(Locale.getDefault(), "%.1f", grams / 1000.0)}kg", 12.00)
            else -> Pair("${grams}g (~porcao semanal)", 6.00)
        }
    }
}
