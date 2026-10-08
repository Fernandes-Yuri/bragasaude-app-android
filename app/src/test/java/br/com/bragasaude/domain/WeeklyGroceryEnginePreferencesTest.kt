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

        val catalog = GroceryIngredientCatalog(
            ingredients = ingredients,
            preparations = listOf(
                GroceryPreparation("Arroz Branco", listOf(GroceryIngredientPortion("arroz-agulhinha", 0.4))),
                GroceryPreparation("Feijão", listOf(GroceryIngredientPortion("feijao-preto", 0.33))),
                GroceryPreparation("Frango Grelhado", listOf(GroceryIngredientPortion("peito-frango", 1.2))),
                GroceryPreparation("Peixe Grelhado", listOf(GroceryIngredientPortion("tilapia-file", 1.15))),
                GroceryPreparation("Camarão Cozido", listOf(GroceryIngredientPortion("camarao-fresco", 1.3))),
                GroceryPreparation("Ovo Cozido", listOf(GroceryIngredientPortion("ovo-caipira", 1.0))),
                GroceryPreparation("Leite", listOf(GroceryIngredientPortion("leite-desnatado", 1.0))),
                GroceryPreparation("Aveia", listOf(GroceryIngredientPortion("aveia-flocos", 1.0))),
                GroceryPreparation("Mandioca Cozida", listOf(GroceryIngredientPortion("mandioca-aipim", 1.1))),
                GroceryPreparation("Banana", listOf(GroceryIngredientPortion("banana-prata", 1.0))),
                GroceryPreparation("Maçã", listOf(GroceryIngredientPortion("maca-fuji", 1.0))),
                GroceryPreparation("Couve Refogada", listOf(GroceryIngredientPortion("couve-manteiga", 1.2))),
                GroceryPreparation("Abobrinha Refogada", listOf(GroceryIngredientPortion("abobrinha-verde", 1.1))),
                GroceryPreparation("Cenoura", listOf(GroceryIngredientPortion("cenoura-fresca", 1.0))),
                GroceryPreparation("Azeite", listOf(GroceryIngredientPortion("azeite-oliva", 1.0)))
            ),
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
            exams = emptyList(),
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
            exams = emptyList(),
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
}
