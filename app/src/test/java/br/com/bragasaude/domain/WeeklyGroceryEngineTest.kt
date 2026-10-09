package br.com.bragasaude.domain

import br.com.bragasaude.data.local.FoodEntity
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.remote.model.RemoteProfile
import org.junit.Assert.*
import org.junit.Test

class WeeklyGroceryEngineTest {

    private fun createTestCatalog(): Pair<List<FoodEntity>, GroceryIngredientCatalog> {
        val ingredients = listOf(
            GroceryIngredient("arroz-agulhinha", "Arroz Agulhinha Tipo 1", "kg", 1000, 1000, listOf("Arroz Branco"), listOf("food_arroz"), 6.0),
            GroceryIngredient("feijao-preto", "Feijão Preto", "kg", 1000, 1000, listOf("Feijão"), listOf("food_feijao"), 8.0),
            GroceryIngredient("peito-frango", "Peito de Frango", "kg", 100, 500, listOf("Frango Grelhado"), listOf("food_frango"), 22.0),
            GroceryIngredient("tilapia-file", "Filé de Tilápia", "kg", 100, 500, listOf("Peixe Grelhado"), listOf("food_peixe"), 35.0),
            GroceryIngredient("ovo-caipira", "Ovo Caipira", "un", 12, 12, listOf("Ovo Cozido"), listOf("food_ovo"), 14.0, gramsPerUnit = 50.0),
            GroceryIngredient("leite-desnatado", "Leite Desnatado", "L", 1000, 1000, listOf("Leite"), listOf("food_leite"), 5.5, densityGPerMl = 1.03),
            GroceryIngredient("iogurte-natural", "Iogurte Natural", "kg", 100, 170, listOf("Iogurte"), listOf("food_iogurte"), 4.0),
            GroceryIngredient("aveia-flocos", "Aveia em Flocos", "kg", 100, 200, listOf("Aveia"), listOf("food_aveia"), 7.0),
            GroceryIngredient("farinha-milho", "Farinha de Milho Flocada", "kg", 500, 500, listOf("Cuscuz"), listOf("food_cuscuz"), 4.5),
            GroceryIngredient("mandioca-aipim", "Mandioca / Aipim", "kg", 100, 500, listOf("Mandioca Cozida"), listOf("food_mandioca"), 6.0),
            GroceryIngredient("banana-prata", "Banana Prata", "kg", 100, 500, listOf("Banana"), listOf("food_banana"), 7.0),
            GroceryIngredient("maca-fuji", "Maçã Fuji", "kg", 100, 500, listOf("Maçã"), listOf("food_maca"), 9.0),
            GroceryIngredient("couve-manteiga", "Couve Manteiga", "kg", 50, 150, listOf("Couve Refogada"), listOf("food_couve"), 3.5),
            GroceryIngredient("abobrinha-verde", "Abobrinha Verde", "kg", 100, 300, listOf("Abobrinha Refogada"), listOf("food_abobrinha"), 5.0),
            GroceryIngredient("azeite-oliva", "Azeite de Oliva Extravirgem", "L", 500, 500, listOf("Azeite"), listOf("food_azeite"), 38.0, densityGPerMl = 0.92),
            GroceryIngredient("amendoim-torrado", "Amendoim Torrado", "kg", 100, 200, listOf("Amendoim"), listOf("food_amendoim"), 8.0),
            GroceryIngredient("pts-soja", "Proteína Texturizada de Soja", "kg", 100, 400, listOf("PTS Refogada"), listOf("food_pts"), 12.0),
            GroceryIngredient("tofu-firme", "Tofu Firme", "kg", 100, 250, listOf("Tofu Grelhado"), listOf("food_tofu"), 15.0),
            GroceryIngredient("lentilha-seca", "Lentilha Seca", "kg", 500, 500, listOf("Lentilha Cozida"), listOf("food_lentilha"), 10.0),
            GroceryIngredient("camarao-fresco", "Camarão Fresco", "kg", 100, 400, listOf("Camarão Cozido"), listOf("food_camarao"), 60.0)
        )

        val vegTags = listOf("diet:vegan", "diet:vegetarian")
        val lactoOvoTags = listOf("diet:vegetarian")

        val foods = listOf(
            FoodEntity("food_arroz", "Arroz Branco Cozido", "Grãos & Cereais", kcal = 130.0, carbsG = 28.0, proteinG = 2.5, fatG = 0.3, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 300, functionalTags = vegTags),
            FoodEntity("food_feijao", "Feijão Preto Cozido", "Leguminosas & Grãos", kcal = 76.0, carbsG = 14.0, proteinG = 4.5, fatG = 0.5, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 250, functionalTags = vegTags),
            FoodEntity("food_frango", "Peito de Frango Grelhado", "Carnes & Aves", kcal = 165.0, carbsG = 0.0, proteinG = 31.0, fatG = 3.6, servingSizeGrams = 120, minServingGrams = 60, maxServingGrams = 300),
            FoodEntity("food_peixe", "Filé de Tilápia Grelhado", "Peixes & Frutos do Mar", kcal = 128.0, carbsG = 0.0, proteinG = 26.0, fatG = 2.6, servingSizeGrams = 130, minServingGrams = 60, maxServingGrams = 300),
            FoodEntity("food_ovo", "Ovo Cozido", "Proteínas & Ovos", kcal = 145.0, carbsG = 0.8, proteinG = 13.0, fatG = 9.5, servingSizeGrams = 50, minServingGrams = 50, maxServingGrams = 200, servingUnit = "1 unidade (50g)", functionalTags = lactoOvoTags),
            FoodEntity("food_leite", "Leite Desnatado", "Laticínios", kcal = 35.0, carbsG = 5.0, proteinG = 3.4, fatG = 0.2, servingSizeGrams = 200, minServingGrams = 100, maxServingGrams = 400, servingUnit = "1 copo (200ml)", functionalTags = lactoOvoTags),
            FoodEntity("food_iogurte", "Iogurte Natural Desnatado", "Laticínios Fermentados", kcal = 45.0, carbsG = 6.0, proteinG = 4.0, fatG = 0.5, servingSizeGrams = 170, minServingGrams = 100, maxServingGrams = 340, functionalTags = lactoOvoTags),
            FoodEntity("food_aveia", "Aveia em Flocos", "Cereais & Fibras", kcal = 394.0, carbsG = 66.0, proteinG = 14.0, fatG = 8.5, servingSizeGrams = 40, minServingGrams = 20, maxServingGrams = 100, functionalTags = vegTags),
            FoodEntity("food_cuscuz", "Cuscuz de Milho Cozido", "Cereais Tradicionais", kcal = 112.0, carbsG = 25.0, proteinG = 2.2, fatG = 0.7, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 250, functionalTags = vegTags),
            FoodEntity("food_mandioca", "Mandioca Cozida", "Raízes & Tubérculos", kcal = 125.0, carbsG = 30.0, proteinG = 0.6, fatG = 0.3, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 300, functionalTags = vegTags),
            FoodEntity("food_banana", "Banana Prata", "Frutas Frescas", kcal = 98.0, carbsG = 26.0, proteinG = 1.3, fatG = 0.1, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 200, functionalTags = vegTags),
            FoodEntity("food_maca", "Maçã Fuji com Casca", "Frutas Frescas", kcal = 56.0, carbsG = 14.8, proteinG = 0.3, fatG = 0.2, servingSizeGrams = 120, minServingGrams = 60, maxServingGrams = 240, functionalTags = vegTags),
            FoodEntity("food_couve", "Couve Manteiga Refogada", "Verduras", kcal = 27.0, carbsG = 4.4, proteinG = 2.9, fatG = 0.5, servingSizeGrams = 50, minServingGrams = 20, maxServingGrams = 150, functionalTags = vegTags),
            FoodEntity("food_abobrinha", "Abobrinha Verde Refogada", "Legumes", kcal = 19.0, carbsG = 3.0, proteinG = 1.1, fatG = 0.2, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 250, functionalTags = vegTags),
            FoodEntity("food_azeite", "Azeite de Oliva Extravirgem", "Gorduras & Azeites", kcal = 884.0, carbsG = 0.0, proteinG = 0.0, fatG = 100.0, servingSizeGrams = 15, minServingGrams = 5, maxServingGrams = 40, servingUnit = "1 colher de sopa (15ml)", functionalTags = vegTags),
            FoodEntity("food_amendoim", "Amendoim Torrado sem Sal", "Oleaginosas", kcal = 567.0, carbsG = 16.0, proteinG = 26.0, fatG = 49.0, servingSizeGrams = 20, minServingGrams = 10, maxServingGrams = 60, functionalTags = vegTags),
            FoodEntity("food_pts", "PTS Refogada", "Proteínas Vegetais", kcal = 120.0, carbsG = 9.0, proteinG = 20.0, fatG = 1.2, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 250, functionalTags = listOf("diet:vegan", "diet:vegetarian", "proteina_vegetal")),
            FoodEntity("food_tofu", "Tofu Firme Grelhado", "Proteínas Vegetais", kcal = 85.0, carbsG = 2.0, proteinG = 10.0, fatG = 4.8, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 250, functionalTags = listOf("diet:vegan", "diet:vegetarian", "proteina_vegetal")),
            FoodEntity("food_lentilha", "Lentilha Cozida", "Leguminosas & Grãos", kcal = 116.0, carbsG = 20.0, proteinG = 9.0, fatG = 0.4, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 250, functionalTags = listOf("diet:vegan", "diet:vegetarian", "proteina_vegetal")),
            FoodEntity("food_camarao", "Camarão Cozido", "Peixes & Frutos do Mar", kcal = 99.0, carbsG = 0.2, proteinG = 24.0, fatG = 0.3, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 250)
        )

        val components = mapOf(
            "food_arroz" to listOf("arroz-agulhinha"),
            "food_feijao" to listOf("feijao-preto"),
            "food_frango" to listOf("peito-frango"),
            "food_peixe" to listOf("tilapia-file"),
            "food_ovo" to listOf("ovo-caipira"),
            "food_leite" to listOf("leite-desnatado"),
            "food_iogurte" to listOf("iogurte-natural"),
            "food_aveia" to listOf("aveia-flocos"),
            "food_cuscuz" to listOf("farinha-milho"),
            "food_mandioca" to listOf("mandioca-aipim"),
            "food_banana" to listOf("banana-prata"),
            "food_maca" to listOf("maca-fuji"),
            "food_couve" to listOf("couve-manteiga"),
            "food_abobrinha" to listOf("abobrinha-verde"),
            "food_azeite" to listOf("azeite-oliva"),
            "food_amendoim" to listOf("amendoim-torrado"),
            "food_pts" to listOf("pts-soja"),
            "food_tofu" to listOf("tofu-firme"),
            "food_lentilha" to listOf("lentilha-seca"),
            "food_camarao" to listOf("camarao-fresco")
        )

        val purchaseFactors = mapOf(
            "food_arroz" to mapOf("arroz-agulhinha" to 0.4), // 100g cozido precisa de 40g cru
            "food_feijao" to mapOf("feijao-preto" to 0.45),
            "food_frango" to mapOf("peito-frango" to 1.25),
            "food_peixe" to mapOf("tilapia-file" to 1.2),
            "food_ovo" to mapOf("ovo-caipira" to 1.0),
            "food_leite" to mapOf("leite-desnatado" to 1.0),
            "food_iogurte" to mapOf("iogurte-natural" to 1.0),
            "food_aveia" to mapOf("aveia-flocos" to 1.0),
            "food_cuscuz" to mapOf("farinha-milho" to 0.6),
            "food_mandioca" to mapOf("mandioca-aipim" to 1.1),
            "food_banana" to mapOf("banana-prata" to 1.0),
            "food_maca" to mapOf("maca-fuji" to 1.0),
            "food_couve" to mapOf("couve-manteiga" to 1.2),
            "food_abobrinha" to mapOf("abobrinha-verde" to 1.1),
            "food_azeite" to mapOf("azeite-oliva" to 1.0),
            "food_amendoim" to mapOf("amendoim-torrado" to 1.0),
            "food_pts" to mapOf("pts-soja" to 0.35),
            "food_tofu" to mapOf("tofu-firme" to 1.0),
            "food_lentilha" to mapOf("lentilha-seca" to 0.45),
            "food_camarao" to mapOf("camarao-fresco" to 1.3)
        )

        val requiredGroups = listOf(
            listOf("food_arroz"),
            listOf("food_feijao")
        )

        val catalog = GroceryIngredientCatalog(
            ingredients = ingredients,
            components = components,
            requiredGroups = requiredGroups,
            purchaseFactors = purchaseFactors,
            version = 2
        )

        return Pair(foods, catalog)
    }

    @Test
    fun plansWeeklyEnergyProportionallyFor1960_2800_3000_and_4000Kcal() {
        val (foods, catalog) = createTestCatalog()

        val result1960 = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "user1", vitals = emptyList(), profile = null,
            catalog = foods, ingredientCatalog = catalog, targetCalories = 1960.0
        )
        val result2800 = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "user1", vitals = emptyList(), profile = null,
            catalog = foods, ingredientCatalog = catalog, targetCalories = 2800.0
        )
        val result3000 = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "user1", vitals = emptyList(), profile = null,
            catalog = foods, ingredientCatalog = catalog, targetCalories = 3000.0
        )
        val result4000 = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "user1", vitals = emptyList(), profile = null,
            catalog = foods, ingredientCatalog = catalog, targetCalories = 4000.0
        )

        // 1. Meta de 1.960 kcal/dia -> 13.720 kcal semanais
        assertEquals(13720.0, result1960.targetWeeklyCalories, 1.0)
        assertTrue("Cobertura 1960 deve ser de pelo menos 90%", result1960.coveragePercent >= 90.0)

        // 2. Meta de 2.800 kcal/dia -> 19.600 kcal semanais
        assertEquals(19600.0, result2800.targetWeeklyCalories, 1.0)
        assertTrue("Cobertura 2800 deve ser de pelo menos 90%", result2800.coveragePercent >= 90.0)

        // 3. Meta de 3.000 kcal/dia -> 21.000 kcal semanais
        assertEquals(21000.0, result3000.targetWeeklyCalories, 1.0)
        assertTrue("Cobertura 3000 deve ser de pelo menos 90%", result3000.coveragePercent >= 90.0)

        // 4. Meta de 4.000 kcal/dia -> 28.000 kcal semanais
        assertEquals(28000.0, result4000.targetWeeklyCalories, 1.0)
        assertTrue("Cobertura 4000 deve ser de pelo menos 90%", result4000.coveragePercent >= 90.0)

        // As calorias planejadas devem escalar estritamente com a meta (sem estagnar no fator 1.6 antigo)
        assertTrue("Energia 2800 deve ser maior que 1960", result2800.plannedWeeklyCalories > result1960.plannedWeeklyCalories)
        assertTrue("Energia 3000 deve ser maior que 2800", result3000.plannedWeeklyCalories > result2800.plannedWeeklyCalories)
        assertTrue("Energia 4000 deve ser maior que 3000", result4000.plannedWeeklyCalories > result3000.plannedWeeklyCalories)
    }

    @Test
    fun consolidatesCanonicalIngredientsWithoutDuplicationsInSingleLine() {
        val (foods, catalog) = createTestCatalog()

        val result = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "user1", vitals = emptyList(), profile = null,
            catalog = foods, ingredientCatalog = catalog, targetCalories = 3000.0
        )

        // Nenhuma duplicação de linha por slug de ingrediente
        val slugs = result.items.map { it.foodId }
        assertEquals("Cada ingrediente deve ter exatamente uma única linha", slugs.distinct().size, slugs.size)

        // Arroz e feijão obrigatórios devem estar presentes
        assertTrue(slugs.contains("arroz-agulhinha"))
        assertTrue(slugs.contains("feijao-preto"))
    }

    @Test
    fun handlesKgLitersAndUnitsPackagingCorrectly() {
        val (foods, catalog) = createTestCatalog()

        val result = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "user1", vitals = emptyList(), profile = null,
            catalog = foods, ingredientCatalog = catalog, targetCalories = 2800.0
        )

        val arroz = result.items.first { it.foodId == "arroz-agulhinha" }
        assertTrue("Arroz deve ter peso em gramas múltiplo de 1000", arroz.purchaseWeightGrams % 1000 == 0)
        assertTrue(arroz.purchaseUnitText.endsWith("g"))

        val azeite = result.items.first { it.foodId == "azeite-oliva" }
        assertEquals(0, azeite.purchaseWeightGrams) // Para L o peso em gramas é 0 na entidade
        assertTrue("Azeite deve estar formatado em Litros", azeite.purchaseUnitText.endsWith("L"))

        val ovo = result.items.first { it.foodId == "ovo-caipira" }
        assertEquals(0, ovo.purchaseWeightGrams)
        assertTrue("Ovo deve estar formatado em unidades", ovo.purchaseUnitText.endsWith("un"))
        val ovosCount = ovo.purchaseUnitText.substringBefore(" ").toInt()
        assertEquals("Ovos devem ser comprados em múltiplos de 12", 0, ovosCount % 12)
    }

    @Test
    fun respectsVegetarianAndVeganProfilesRedistributingDemand() {
        val (foods, catalog) = createTestCatalog()

        val vegProfile = RemoteProfile(id = "veg", customFoodRestrictions = "vegetariano")
        val vegResult = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "veg", vitals = emptyList(), profile = vegProfile,
            catalog = foods, ingredientCatalog = catalog, targetCalories = 2800.0
        )

        // Não deve conter carne, peixe ou camarão
        val vegSlugs = vegResult.items.map { it.foodId }.toSet()
        assertFalse(vegSlugs.contains("peito-frango"))
        assertFalse(vegSlugs.contains("tilapia-file"))
        assertFalse(vegSlugs.contains("camarao-fresco"))

        // Deve conter proteínas vegetais e/ou ovos/laticínios
        assertTrue(vegSlugs.contains("pts-soja") || vegSlugs.contains("tofu-firme") || vegSlugs.contains("ovo-caipira"))
        assertTrue("Vegetariano atinge cobertura satisfatória", vegResult.coveragePercent >= 90.0)

        val veganProfile = RemoteProfile(id = "vegan", customFoodRestrictions = "vegano")
        val veganResult = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "vegan", vitals = emptyList(), profile = veganProfile,
            catalog = foods, ingredientCatalog = catalog, targetCalories = 2800.0
        )
        val veganSlugs = veganResult.items.map { it.foodId }.toSet()
        assertFalse(veganSlugs.contains("peito-frango"))
        assertFalse(veganSlugs.contains("tilapia-file"))
        assertFalse(veganSlugs.contains("ovo-caipira"))
        assertFalse(veganSlugs.contains("leite-desnatado"))
        assertFalse(veganSlugs.contains("iogurte-natural"))

        // O vegano recebe proteínas vegetais elegíveis
        assertTrue(veganSlugs.contains("pts-soja") || veganSlugs.contains("tofu-firme") || veganSlugs.contains("lentilha-seca"))
    }

    @Test
    fun strictlyBlocksAllergensWithoutRelaxingRulesToHitCalories() {
        val (foods, catalog) = createTestCatalog()

        val allergicProfile = RemoteProfile(
            id = "allergic",
            foodAllergies = listOf("Frutos do Mar", "Leite", "Peixe")
        )
        val result = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "allergic", vitals = emptyList(), profile = allergicProfile,
            catalog = foods, ingredientCatalog = catalog, targetCalories = 3000.0
        )

        val slugs = result.items.map { it.foodId }.toSet()
        assertFalse(slugs.contains("camarao-fresco"))
        assertFalse(slugs.contains("tilapia-file"))
        assertFalse(slugs.contains("leite-desnatado"))
        assertFalse(slugs.contains("iogurte-natural"))
    }

    @Test
    fun reportsDeficitHonestlyWhenCatalogIsExtremelyLimited() {
        // Catálogo com apenas 1 alimento com limite de porção
        val smallFood = FoodEntity("small_food", "Folha de Alface", "Verduras", kcal = 15.0, servingSizeGrams = 50, maxServingGrams = 100)
        val smallIng = GroceryIngredient("alface", "Alface", "kg", 50, 50, emptyList(), listOf("small_food"))
        val smallCatalog = GroceryIngredientCatalog(listOf(smallIng), mapOf("small_food" to listOf("alface")))

        val result = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "small", vitals = emptyList(), profile = null,
            catalog = listOf(smallFood), ingredientCatalog = smallCatalog, targetCalories = 3000.0
        )

        assertTrue("Cobertura deve ser bem menor que 90%", result.coveragePercent < 90.0)
        assertTrue("Mensagem deve explicitar déficit", result.statusMessage.contains("Déficit"))
    }

    @Test
    fun recognizesCuscuzAndNoodlesWithoutAccentVulnerability() {
        val (foods, catalog) = createTestCatalog()

        val cuscuzFood = foods.first { it.remoteId == "food_cuscuz" }
        val pillar = WeeklyGroceryEngine.classifyPillar(cuscuzFood)
        assertEquals(WeeklyGroceryEngine.CORRIDOR_GRAOS, pillar)
    }

    @Test
    fun sharedComponentsSummedBeforeApplyingSafetyMargin() {
        val ingA = GroceryIngredient("cebola", "Cebola", "kg", 100, 100, emptyList(), listOf("recipe1", "recipe2"))
        val recipe1 = FoodEntity("recipe1", "Prato 1", "Proteínas", kcal = 200.0, servingSizeGrams = 100)
        val recipe2 = FoodEntity("recipe2", "Prato 2", "Grãos", kcal = 200.0, servingSizeGrams = 100)

        val cat = GroceryIngredientCatalog(
            ingredients = listOf(ingA),
            components = mapOf("recipe1" to listOf("cebola"), "recipe2" to listOf("cebola")),
            purchaseFactors = mapOf("recipe1" to mapOf("cebola" to 0.5), "recipe2" to mapOf("cebola" to 0.5))
        )

        val result = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "u", vitals = emptyList(), profile = null,
            catalog = listOf(recipe1, recipe2), ingredientCatalog = cat, targetCalories = 2000.0
        )

        assertEquals("Deve existir apenas uma única linha para cebola", 1, result.items.size)
        assertEquals("cebola", result.items.single().foodId)
    }

    @Test
    fun varietySelectionAvoidsConcentrationOfSameVariant() {
        val ingAveia1 = GroceryIngredient("farelo-aveia", "Farelo de Aveia", "kg", 100, 200, emptyList(), listOf("food_av1"), canonicalGroupSlug = "aveia")
        val ingAveia2 = GroceryIngredient("aveia-finos", "Aveia em Flocos Finos", "kg", 100, 200, emptyList(), listOf("food_av2"), canonicalGroupSlug = "aveia")
        val ingQuinoa = GroceryIngredient("quinoa", "Quinoa em Grãos", "kg", 100, 200, emptyList(), listOf("food_qui"), canonicalGroupSlug = "quinoa")
        val ingMandioca = GroceryIngredient("mandioca", "Mandioca", "kg", 100, 200, emptyList(), listOf("food_man"), canonicalGroupSlug = "mandioca")

        val foodAv1 = FoodEntity("food_av1", "Farelo de Aveia", "Cereais", kcal = 300.0, servingSizeGrams = 30)
        val foodAv2 = FoodEntity("food_av2", "Aveia em Flocos Finos", "Cereais", kcal = 300.0, servingSizeGrams = 30)
        val foodQui = FoodEntity("food_qui", "Quinoa em Grãos", "Cereais", kcal = 300.0, servingSizeGrams = 30)
        val foodMan = FoodEntity("food_man", "Mandioca Cozida", "Raízes", kcal = 120.0, servingSizeGrams = 100)

        val cat = GroceryIngredientCatalog(
            ingredients = listOf(ingAveia1, ingAveia2, ingQuinoa, ingMandioca),
            components = mapOf(
                "food_av1" to listOf("farelo-aveia"),
                "food_av2" to listOf("aveia-finos"),
                "food_qui" to listOf("quinoa"),
                "food_man" to listOf("mandioca")
            )
        )

        val selected = WeeklyGroceryEngine.selectDiverseFoods(
            candidates = listOf(foodAv1, foodAv2, foodQui, foodMan),
            count = 3,
            ingredientCatalog = cat,
            selectedGroups = mutableSetOf()
        )

        val selectedIds = selected.map { it.remoteId }
        assertTrue("Deve selecionar food_av1", selectedIds.contains("food_av1"))
        assertTrue("Deve selecionar food_qui de grupo distinto", selectedIds.contains("food_qui"))
        assertTrue("Deve selecionar food_man de grupo distinto", selectedIds.contains("food_man"))
        assertFalse("Não deve selecionar duas variantes de aveia quando há opções de outros grupos", selectedIds.contains("food_av2"))
    }

    @Test
    fun separatesEnergyCoverageFromPurchaseCalculationStatus() {
        val ingSemDensidade = GroceryIngredient("bebida", "Bebida", "L", 1000, 1000, emptyList(), listOf("food_bebida"), densityGPerMl = null)
        val foodBebida = FoodEntity("food_bebida", "Bebida Láctea", "Laticínios", kcal = 100.0, servingSizeGrams = 200)
        val cat = GroceryIngredientCatalog(
            ingredients = listOf(ingSemDensidade),
            components = mapOf("food_bebida" to listOf("bebida")),
            purchaseFactors = mapOf("food_bebida" to mapOf("bebida" to 1.0))
        )

        val result = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "u", vitals = emptyList(), profile = null,
            catalog = listOf(foodBebida), ingredientCatalog = cat, targetCalories = 1000.0
        )

        assertEquals("Quando falta densidade, o status da compra deve ser INCOMPLETE",
            PurchaseCalculationStatus.INCOMPLETE, result.purchaseStatus)
    }

    @Test
    fun formatsLimitationsGroupedWithoutRepeatingFoodNames() {
        val limitations = listOf(
            GroceryLimitation(
                type = GroceryLimitationType.MISSING_YIELD,
                affectedItems = listOf("Feijão Carioca Cozido", "Arroz Integral Cozido"),
                impact = GroceryCalculationImpact.APPROXIMATED,
                userSummary = "Rendimento estimado em 1:1"
            ),
            GroceryLimitation(
                type = GroceryLimitationType.MISSING_RECIPE_PROPORTIONS,
                affectedItems = listOf("Chá de Cidreira com Limão"),
                impact = GroceryCalculationImpact.NOT_CALCULABLE,
                userSummary = "Proporções pendentes"
            )
        )

        val grouped = GroceryLimitationFormatter.groupForUi(limitations)
        assertEquals(2, grouped.size)
        assertEquals(GroceryLimitationType.MISSING_YIELD.userTitle, grouped[0].first)
        assertEquals(listOf("Feijão Carioca Cozido", "Arroz Integral Cozido"), grouped[0].second)
        assertEquals(GroceryLimitationType.MISSING_RECIPE_PROPORTIONS.userTitle, grouped[1].first)
        assertEquals(listOf("Chá de Cidreira com Limão"), grouped[1].second)
    }

    @Test
    fun preservesRiceAndBeansAnchorsAcrossRotationsForStandardProfiles() {
        val (foods, catalog) = createTestCatalog()

        // Semana 1: plano padrão
        val plan1 = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "user_rot", vitals = emptyList(), profile = null,
            catalog = foods, ingredientCatalog = catalog, targetCalories = 2500.0,
            shuffleSeed = 1001L, previousFoodIds = emptySet()
        )
        val items1 = plan1.items.map { it.foodId }.toSet()
        assertTrue("Arroz deve estar presente na semana 1", items1.contains("arroz-agulhinha"))
        assertTrue("Feijão deve estar presente na semana 1", items1.contains("feijao-preto"))

        // Semana 2: nova variedade informando os itens da semana 1
        val plan2 = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "user_rot", vitals = emptyList(), profile = null,
            catalog = foods, ingredientCatalog = catalog, targetCalories = 2500.0,
            shuffleSeed = 2002L, previousFoodIds = items1
        )
        val items2 = plan2.items.map { it.foodId }.toSet()
        assertTrue("Arroz DEVE ser preservado na semana 2 como âncora fixa", items2.contains("arroz-agulhinha"))
        assertTrue("Feijão DEVE ser preservado na semana 2 como âncora fixa", items2.contains("feijao-preto"))

        // Alimentos complementares devem apresentar rotatividade real
        val nonAnchor1 = items1 - setOf("arroz-agulhinha", "feijao-preto")
        val nonAnchor2 = items2 - setOf("arroz-agulhinha", "feijao-preto")
        val rotatedDifference = nonAnchor2 - nonAnchor1
        assertTrue("Deve haver alimentos novos rotacionados na semana 2", rotatedDifference.isNotEmpty())
    }

    @Test
    fun respectsRegisteredDietaryRestrictionsExcludingAnchorsIfRestricted() {
        val (foods, catalog) = createTestCatalog()

        // Perfil alérgico a feijão / leguminosas
        val restrictedProfile = RemoteProfile(
            id = "user_no_beans",
            foodAllergies = listOf("Feijão Preto", "Feijão")
        )

        val plan = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "user_no_beans", vitals = emptyList(), profile = restrictedProfile,
            catalog = foods, ingredientCatalog = catalog, targetCalories = 2500.0,
            shuffleSeed = 12345L, previousFoodIds = emptySet()
        )

        val items = plan.items.map { it.foodId }.toSet()
        assertFalse("Feijão NÃO deve entrar para usuário com restrição/alergia registrada", items.contains("feijao-preto"))
        assertTrue("Arroz permanece como âncora para quem não tem restrição a arroz", items.contains("arroz-agulhinha"))
        assertTrue("Meta energética atinge patamar satisfatório mesmo sem feijão", plan.coveragePercent >= 90.0)
    }

    @Test
    fun preventsAbnormalPantryQuantitiesInWeeklyPlan() {
        val (foods, catalog) = createTestCatalog()
        val plan = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "user_caps", vitals = emptyList(), profile = null,
            catalog = foods, ingredientCatalog = catalog, targetCalories = 3000.0,
            preferences = WeeklyGroceryPreferences(budgetTier = GroceryBudgetTier.ULTRA_ECONOMIC)
        )
        for (item in plan.items) {
            val name = item.foodName.lowercase()
            if (name.contains("couve") || name.contains("abobrinha")) {
                assertTrue("Hortifrúti e legumes não devem exceder 1800g de compra semanal: ${item.foodName} tem ${item.purchaseWeightGrams}g", item.purchaseWeightGrams <= 1800)
            }
            if (name.contains("mandioca")) {
                assertTrue("Tubérculos não devem exceder 2000g semanais: ${item.foodName} tem ${item.purchaseWeightGrams}g", item.purchaseWeightGrams <= 2000)
            }
        }
    }
}
