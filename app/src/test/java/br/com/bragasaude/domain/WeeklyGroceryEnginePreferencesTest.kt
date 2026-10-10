package br.com.bragasaude.domain

import br.com.bragasaude.data.local.FoodEntity
import org.junit.Assert.*
import org.junit.Test

class WeeklyGroceryEnginePreferencesTest {

    private fun createTestCatalog(): Pair<List<FoodEntity>, GroceryIngredientCatalog> {
        val ingredients = listOf(
            GroceryIngredient("arroz-agulhinha", "Arroz Agulhinha Tipo 1", "kg", 1000, 1000, listOf("Arroz Branco"), listOf("food_arroz"), 6.0),
            GroceryIngredient("feijao-preto", "Feijão Preto", "kg", 1000, 1000, listOf("Feijão"), listOf("food_feijao"), 8.0),
            GroceryIngredient("peito-frango", "Peito de Frango", "kg", 100, 500, listOf("Frango Grelhado"), listOf("food_frango"), 22.0),
            GroceryIngredient("tilapia-file", "Filé de Tilápia", "kg", 100, 500, listOf("Peixe Grelhado"), listOf("food_peixe"), 35.0),
            GroceryIngredient("ovo-caipira", "Ovo Caipira", "un", 12, 12, listOf("Ovo Cozido"), listOf("food_ovo"), 14.0, gramsPerUnit = 50.0),
            GroceryIngredient("leite-desnatado", "Leite Desnatado", "L", 1000, 1000, listOf("Leite"), listOf("food_leite"), 5.5, densityGPerMl = 1.03),
            GroceryIngredient("aveia-flocos", "Aveia em Flocos", "kg", 100, 200, listOf("Aveia"), listOf("food_aveia"), 7.0),
            GroceryIngredient("mandioca-aipim", "Mandioca / Aipim", "kg", 100, 500, listOf("Mandioca Cozida"), listOf("food_mandioca"), 6.0),
            GroceryIngredient("banana-prata", "Banana Prata", "kg", 100, 500, listOf("Banana"), listOf("food_banana"), 7.0),
            GroceryIngredient("maca-fuji", "Maçã Fuji", "kg", 100, 500, listOf("Maçã"), listOf("food_maca"), 9.0),
            GroceryIngredient("couve-manteiga", "Couve Manteiga", "kg", 50, 150, listOf("Couve Refogada"), listOf("food_couve"), 3.5),
            GroceryIngredient("abobrinha-verde", "Abobrinha Verde", "kg", 100, 300, listOf("Abobrinha Refogada"), listOf("food_abobrinha"), 5.0),
            GroceryIngredient("cenoura-fresca", "Cenoura Fresca", "kg", 100, 300, listOf("Cenoura"), listOf("food_cenoura"), 6.0),
            GroceryIngredient("azeite-oliva", "Azeite de Oliva Extravirgem", "L", 500, 500, listOf("Azeite"), listOf("food_azeite"), 38.0, densityGPerMl = 0.92),
            GroceryIngredient("camarao-fresco", "Camarão Fresco", "kg", 100, 400, listOf("Camarão Cozido"), listOf("food_camarao"), 65.0)
        )

        val foods = listOf(
            FoodEntity("food_arroz", "Arroz Branco Cozido", "Grãos & Cereais", kcal = 130.0, carbsG = 28.0, proteinG = 2.5, fatG = 0.3, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 300),
            FoodEntity("food_feijao", "Feijão Preto Cozido", "Leguminosas & Grãos", kcal = 76.0, carbsG = 14.0, proteinG = 4.5, fatG = 0.5, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 250),
            FoodEntity("food_frango", "Peito de Frango Grelhado", "Carnes & Aves", kcal = 165.0, carbsG = 0.0, proteinG = 31.0, fatG = 3.6, servingSizeGrams = 120, minServingGrams = 60, maxServingGrams = 300),
            FoodEntity("food_peixe", "Filé de Tilápia Grelhado", "Peixes & Frutos do Mar", kcal = 128.0, carbsG = 0.0, proteinG = 26.0, fatG = 2.6, servingSizeGrams = 130, minServingGrams = 60, maxServingGrams = 300),
            FoodEntity("food_camarao", "Camarão Cozido", "Peixes & Frutos do Mar", kcal = 99.0, carbsG = 0.2, proteinG = 24.0, fatG = 0.3, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 250),
            FoodEntity("food_ovo", "Ovo Cozido", "Proteínas & Ovos", kcal = 145.0, carbsG = 0.8, proteinG = 13.0, fatG = 9.5, servingSizeGrams = 50, minServingGrams = 50, maxServingGrams = 200, servingUnit = "1 unidade (50g)"),
            FoodEntity("food_leite", "Leite Desnatado", "Laticínios", kcal = 35.0, carbsG = 5.0, proteinG = 3.4, fatG = 0.2, servingSizeGrams = 200, minServingGrams = 100, maxServingGrams = 400, servingUnit = "1 copo (200ml)"),
            FoodEntity("food_aveia", "Aveia em Flocos", "Cereais & Fibras", kcal = 394.0, carbsG = 66.0, proteinG = 14.0, fatG = 8.5, servingSizeGrams = 40, minServingGrams = 20, maxServingGrams = 100),
            FoodEntity("food_mandioca", "Mandioca Cozida", "Raízes & Tubérculos", kcal = 125.0, carbsG = 30.0, proteinG = 0.6, fatG = 0.3, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 300),
            FoodEntity("food_banana", "Banana Prata", "Frutas Frescas", kcal = 98.0, carbsG = 26.0, proteinG = 1.3, fatG = 0.1, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 200),
            FoodEntity("food_maca", "Maçã Fuji com Casca", "Frutas Frescas", kcal = 56.0, carbsG = 14.8, proteinG = 0.3, fatG = 0.2, servingSizeGrams = 120, minServingGrams = 60, maxServingGrams = 240),
            FoodEntity("food_couve", "Couve Manteiga Refogada", "Verduras", kcal = 27.0, carbsG = 4.4, proteinG = 2.9, fatG = 0.5, servingSizeGrams = 50, minServingGrams = 20, maxServingGrams = 150),
            FoodEntity("food_abobrinha", "Abobrinha Verde Refogada", "Legumes", kcal = 19.0, carbsG = 3.0, proteinG = 1.1, fatG = 0.2, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 250),
            FoodEntity("food_cenoura", "Cenoura Cozida", "Legumes", kcal = 35.0, carbsG = 8.0, proteinG = 0.8, fatG = 0.2, servingSizeGrams = 100, minServingGrams = 50, maxServingGrams = 200),
            FoodEntity("food_azeite", "Azeite de Oliva Extravirgem", "Gorduras & Azeites", kcal = 884.0, carbsG = 0.0, proteinG = 0.0, fatG = 100.0, servingSizeGrams = 15, minServingGrams = 5, maxServingGrams = 40, servingUnit = "1 colher de sopa (15ml)")
        )

        val components = mapOf(
            "food_arroz" to listOf("arroz-agulhinha"),
            "food_feijao" to listOf("feijao-preto"),
            "food_frango" to listOf("peito-frango"),
            "food_peixe" to listOf("tilapia-file"),
            "food_camarao" to listOf("camarao-fresco"),
            "food_ovo" to listOf("ovo-caipira"),
            "food_leite" to listOf("leite-desnatado"),
            "food_aveia" to listOf("aveia-flocos"),
            "food_mandioca" to listOf("mandioca-aipim"),
            "food_banana" to listOf("banana-prata"),
            "food_maca" to listOf("maca-fuji"),
            "food_couve" to listOf("couve-manteiga"),
            "food_abobrinha" to listOf("abobrinha-verde"),
            "food_cenoura" to listOf("cenoura-fresca"),
            "food_azeite" to listOf("azeite-oliva")
        )

        val purchaseFactors = mapOf(
            "food_arroz" to mapOf("arroz-agulhinha" to 0.4),
            "food_feijao" to mapOf("feijao-preto" to 0.33),
            "food_frango" to mapOf("peito-frango" to 1.2),
            "food_peixe" to mapOf("tilapia-file" to 1.15),
            "food_camarao" to mapOf("camarao-fresco" to 1.3),
            "food_ovo" to mapOf("ovo-caipira" to 1.0),
            "food_leite" to mapOf("leite-desnatado" to 1.0),
            "food_aveia" to mapOf("aveia-flocos" to 1.0),
            "food_mandioca" to mapOf("mandioca-aipim" to 1.1),
            "food_banana" to mapOf("banana-prata" to 1.0),
            "food_maca" to mapOf("maca-fuji" to 1.0),
            "food_couve" to mapOf("couve-manteiga" to 1.2),
            "food_abobrinha" to mapOf("abobrinha-verde" to 1.1),
            "food_cenoura" to mapOf("cenoura-fresca" to 1.0),
            "food_azeite" to mapOf("azeite-oliva" to 1.0)
        )

        val catalog = GroceryIngredientCatalog(
            ingredients = ingredients,
            components = components,
            purchaseFactors = purchaseFactors,
            requiredGroups = listOf(listOf("food_arroz"), listOf("food_feijao")),
            version = 1
        )

        return foods to catalog
    }

    @Test
    fun planWeeklyGrocery_withEconomicTier_generatesCompactBasketAndFiltersHighCost() {
        val (foods, catalog) = createTestCatalog()
        val prefs = WeeklyGroceryPreferences(
            budgetTier = GroceryBudgetTier.ECONOMIC,
            selectedProteins = setOf(GroceryProteinPreference.EGGS, GroceryProteinPreference.POULTRY),
            hasPantryStaples = true
        )

        val plan = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "test_user",
            vitals = emptyList(),
            profile = null,
            catalog = foods,
            ingredientCatalog = catalog,
            targetCalories = 1800.0,
            preferences = prefs
        )

        assertFalse("Plano não deve estar vazio", plan.items.isEmpty())
        assertTrue("Cesta econômica deve ser enxuta (<= 15 itens)", plan.items.size <= 15)

        val foodNames = plan.items.map { it.foodName.lowercase() }
        assertFalse("Não deve incluir camarão no modo econômico", foodNames.any { it.contains("camarão") })
        assertFalse("Não deve incluir azeite quando despensa básica estiver ativa", foodNames.any { it.contains("azeite") })
        assertTrue("Deve preservar arroz como âncora fixa", foodNames.any { it.contains("arroz") })
        assertTrue("Deve preservar feijão como âncora fixa", foodNames.any { it.contains("feijão") })
    }

    @Test
    fun planWeeklyGrocery_withSpecificProteins_onlySelectsSelectedCategories() {
        val (foods, catalog) = createTestCatalog()
        val prefs = WeeklyGroceryPreferences(
            budgetTier = GroceryBudgetTier.ECONOMIC,
            selectedProteins = setOf(GroceryProteinPreference.POULTRY),
            hasPantryStaples = true
        )

        val plan = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "test_user",
            vitals = emptyList(),
            profile = null,
            catalog = foods,
            ingredientCatalog = catalog,
            targetCalories = 1800.0,
            preferences = prefs
        )

        val foodNames = plan.items.map { it.foodName.lowercase() }
        assertFalse("Não deve conter peixe quando apenas aves selecionadas", foodNames.any { it.contains("tilápia") || it.contains("peixe") })
        assertTrue("Deve conter frango", foodNames.any { it.contains("frango") })
    }

    @Test
    fun planWeeklyGrocery_withUltraEconomicTier_generatesUltraCompactBasket() {
        val (foods, catalog) = createTestCatalog()
        val prefs = WeeklyGroceryPreferences(
            budgetTier = GroceryBudgetTier.ULTRA_ECONOMIC,
            selectedProteins = setOf(GroceryProteinPreference.EGGS),
            hasPantryStaples = true
        )

        val plan = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "test_user",
            vitals = emptyList(),
            profile = null,
            catalog = foods,
            ingredientCatalog = catalog,
            targetCalories = 1800.0,
            preferences = prefs
        )

        assertFalse("Plano não deve estar vazio", plan.items.isEmpty())
        assertTrue("Cesta ultraeconômica deve ter <= 10 itens, obteve ${plan.items.size}", plan.items.size <= 10)

        val foodNames = plan.items.map { it.foodName.lowercase() }
        assertTrue("Deve conter ovos", foodNames.any { it.contains("ovo") })
        assertTrue("Deve conter arroz", foodNames.any { it.contains("arroz") })
        assertTrue("Deve conter feijão", foodNames.any { it.contains("feijão") })
        assertFalse("Não deve incluir camarão no modo ultraeconômico", foodNames.any { it.contains("camarão") })
        assertFalse("Não deve incluir azeite no modo ultraeconômico com despensa", foodNames.any { it.contains("azeite") })
    }

    @Test
    fun planWeeklyGrocery_withUltraEconomicTierAndHighCalories_achievesHighEnergyCoverageWithCompactBasket() {
        val (foods, catalog) = createTestCatalog()
        val prefs = WeeklyGroceryPreferences(
            budgetTier = GroceryBudgetTier.ULTRA_ECONOMIC,
            selectedProteins = setOf(GroceryProteinPreference.EGGS, GroceryProteinPreference.POULTRY),
            hasPantryStaples = true
        )

        val targetDailyKcal = 3000.0
        val targetWeeklyKcal = targetDailyKcal * 7

        val plan = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "test_user",
            vitals = emptyList(),
            profile = null,
            catalog = foods,
            ingredientCatalog = catalog,
            targetCalories = targetDailyKcal,
            preferences = prefs
        )

        assertFalse("Plano não deve estar vazio", plan.items.isEmpty())
        assertTrue("Cesta ultraeconômica deve ter <= 10 itens, obteve ${plan.items.size}", plan.items.size <= 10)

        assertTrue("Cobertura energética deve ser >= 85% para meta alta sem passar fome, obteve ${plan.coveragePercent}%", plan.coveragePercent >= 85.0)

        val foodNames = plan.items.map { it.foodName.lowercase() }
        assertTrue("Deve conter arroz", foodNames.any { it.contains("arroz") })
        assertTrue("Deve conter feijão", foodNames.any { it.contains("feijão") })
    }

    @Test
    fun `budget limit in Reais is evaluated and signals when basket exceeds maxWeeklyBudgetReais`() {
        val (foods, catalog) = createTestCatalog()
        val tightBudget = 50.0 // R$ 50 para uma semana de 2000 kcal/dia
        val prefs = WeeklyGroceryPreferences(
            budgetTier = GroceryBudgetTier.ECONOMIC,
            maxWeeklyBudgetReais = tightBudget
        )

        val plan = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "test_user",
            vitals = emptyList(),
            profile = null,
            catalog = foods,
            ingredientCatalog = catalog,
            targetCalories = 2000.0,
            preferences = prefs
        )

        val totalCost = plan.items.sumOf { it.estimatedPriceBrl }
        if (totalCost > tightBudget) {
            assertTrue("Deve conter aviso de teto orçamentário excedido",
                plan.limitations.any { it.contains("excedeu o teto orçamentário") })
        }
    }

    @Test
    fun planWeeklyGroceryStrictlyEnforcesMaxBasketSizeAcrossAllTiers() {
        val (foods, catalog) = createTestCatalog()

        val tiers = listOf(
            GroceryBudgetTier.ULTRA_ECONOMIC,
            GroceryBudgetTier.ECONOMIC,
            GroceryBudgetTier.MODERATE
        )

        for (tier in tiers) {
            val prefs = WeeklyGroceryPreferences(budgetTier = tier)
            val plan = WeeklyGroceryEngine.planWeeklyGrocery(
                userId = "test_user_tier",
                vitals = emptyList(),
                profile = null,
                catalog = foods,
                ingredientCatalog = catalog,
                targetCalories = 2200.0,
                preferences = prefs
            )
            assertTrue("Plano para ${tier.name} deve ter <= ${tier.maxBasketSize} itens, obteve ${plan.items.size}",
                plan.items.size <= tier.maxBasketSize)
        }
    }

    @Test
    fun planWeeklyGrocery_acrossAllBudgetTiers_alwaysIncludesRiceBeansAndCarnivorousProtein() {
        val (foods, catalog) = createTestCatalog()

        val tiers = listOf(
            GroceryBudgetTier.ULTRA_ECONOMIC,
            GroceryBudgetTier.ECONOMIC,
            GroceryBudgetTier.MODERATE,
            GroceryBudgetTier.FREE
        )

        for (tier in tiers) {
            val prefs = WeeklyGroceryPreferences(budgetTier = tier)
            val plan = WeeklyGroceryEngine.planWeeklyGrocery(
                userId = "test_user_rice_beans_meat",
                vitals = emptyList(),
                profile = null,
                catalog = foods,
                ingredientCatalog = catalog,
                targetCalories = 2000.0,
                preferences = prefs
            )

            val itemNames = plan.items.map { it.foodName.lowercase() }
            assertTrue(
                "Tier $tier deve conter arroz na cesta semanal",
                itemNames.any { it.contains("arroz") }
            )
            assertTrue(
                "Tier $tier deve conter feijão na cesta semanal",
                itemNames.any { it.contains("feijão") }
            )
            val hasCarnivorous = plan.items.any { item ->
                val f = foods.firstOrNull { it.name == item.foodName }
                f != null && WeeklyGroceryEngine.isCarnivorousProtein(f)
            }
            assertTrue(
                "Tier $tier deve conter ao menos uma proteína carnívora na cesta semanal",
                hasCarnivorous
            )
        }
    }

    @Test
    fun planWeeklyGrocery_whenCatalogRequiredGroupsIsEmpty_stillGuaranteesRiceAndBeans() {
        val (foods, originalCatalog) = createTestCatalog()
        val catalogWithEmptyRequired = originalCatalog.copy(requiredGroups = emptyList())

        val plan = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "test_user_empty_required",
            vitals = emptyList(),
            profile = null,
            catalog = foods,
            ingredientCatalog = catalogWithEmptyRequired,
            targetCalories = 2000.0,
            preferences = WeeklyGroceryPreferences(budgetTier = GroceryBudgetTier.ECONOMIC)
        )

        val itemNames = plan.items.map { it.foodName.lowercase() }
        assertTrue("Deve conter arroz mesmo com requiredGroups vazio", itemNames.any { it.contains("arroz") })
        assertTrue("Deve conter feijão mesmo com requiredGroups vazio", itemNames.any { it.contains("feijão") })
    }

    @Test
    fun planWeeklyGrocery_whenUserSelectsOnlyEggs_guaranteesCarnivorousAsMainProteinAndEggAsComplementary() {
        val (foods, catalog) = createTestCatalog()
        val prefs = WeeklyGroceryPreferences(
            budgetTier = GroceryBudgetTier.ECONOMIC,
            selectedProteins = setOf(GroceryProteinPreference.EGGS)
        )

        val plan = WeeklyGroceryEngine.planWeeklyGrocery(
            userId = "test_user_eggs_only",
            vitals = emptyList(),
            profile = null,
            catalog = foods,
            ingredientCatalog = catalog,
            targetCalories = 2000.0,
            preferences = prefs
        )

        val itemNames = plan.items.map { it.foodName.lowercase() }
        val hasEgg = itemNames.any { it.contains("ovo") }
        val hasCarnivorous = plan.items.any { item ->
            val f = foods.firstOrNull { it.name == item.foodName }
            f != null && WeeklyGroceryEngine.isCarnivorousProtein(f)
        }

        assertTrue("Deve conter ovos como complementar", hasEgg)
        assertTrue("Deve conter proteína carnívora principal acompanhando o ovo", hasCarnivorous)
    }

    @Test
    fun isCarnivorousProteinAndIsEggProtein_categorizeCorrectly() {
        val frango = FoodEntity("f1", "Peito de Frango Grelhado", "Carnes & Aves")
        val peixe = FoodEntity("f2", "Filé de Tilápia Grelhado", "Peixes & Frutos do Mar")
        val carne = FoodEntity("f3", "Carne Moída Patinho", "Carnes & Aves")
        val ovo = FoodEntity("f4", "Ovo Cozido", "Proteínas & Ovos")
        val arroz = FoodEntity("f5", "Arroz Branco", "Grãos")

        assertTrue(WeeklyGroceryEngine.isCarnivorousProtein(frango))
        assertTrue(WeeklyGroceryEngine.isCarnivorousProtein(peixe))
        assertTrue(WeeklyGroceryEngine.isCarnivorousProtein(carne))
        assertFalse(WeeklyGroceryEngine.isCarnivorousProtein(ovo))
        assertFalse(WeeklyGroceryEngine.isCarnivorousProtein(arroz))

        assertTrue(WeeklyGroceryEngine.isEggProtein(ovo))
        assertFalse(WeeklyGroceryEngine.isEggProtein(frango))
        assertFalse(WeeklyGroceryEngine.isEggProtein(peixe))
        assertFalse(WeeklyGroceryEngine.isEggProtein(arroz))
    }
}
