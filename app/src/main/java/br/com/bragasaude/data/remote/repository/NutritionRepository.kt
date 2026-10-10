package br.com.bragasaude.data.remote.repository

import br.com.bragasaude.data.local.FoodDao
import br.com.bragasaude.data.local.FoodEntity
import br.com.bragasaude.data.remote.model.RemoteProfile
import br.com.bragasaude.domain.MealLogItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repositório de Inteligência Nutricional (Autocuidado).
 * Aplica os filtros de segurança alimentar e restrições baseadas no perfil clínico do usuário.
 * 
 * DIRETRIZES FUNDAMENTAIS:
 * 1. Sensibilidade a açúcares (Diabetes): oculta itens restritos (isDiabetesSafe == false).
 * 2. Bloqueio absoluto de medicação: NUNCA sugere remédios, fármacos ou substâncias sob prescrição.
 * 3. Finalidade de autocuidado com disclaimer claro de não-prescrição.
 */
@Singleton
class NutritionRepository @Inject constructor(
    private val foodDao: FoodDao,
    private val profileRepository: ProfileRepository
) {

    // Categorias estritamente proibidas no catálogo de alimentos (Regra de Medicação)
    private val prohibitedCategories = setOf(
        "medicamento",
        "farmaco",
        "fármaco",
        "droga",
        "suplemento_farmacologico",
        "prescricao"
    )

    /**
     * Retorna o catálogo de alimentos filtrado de acordo com as restrições de saúde do perfil.
     */
    fun getSafeFoodCatalog(profile: RemoteProfile?): Flow<List<FoodEntity>> {
        return foodDao.getCatalog().map { allFoods ->
            allFoods.filter { food ->
                // 1. Guarda de Medicação: Exclui qualquer item de categoria farmacológica
                val categoryLower = food.category?.lowercase() ?: ""
                val isNotMedication = !prohibitedCategories.any { categoryLower.contains(it) }

                // 2. Filtro de Diabetes / Sensibilidade a Açúcares: Oculta itens com restrição
                val isDiabetesSafe = if (profile?.hasDiabetes == true) {
                    food.isDiabetesSafe && food.status != "RESTRICTED"
                } else {
                    true
                }

                // 3. Filtro de Hipertensão: Oculta itens com alto teor de sódio se marcado
                val isHypertensionSafe = if (profile?.hasHypertension == true) {
                    food.isHypertensionSafe
                } else {
                    true
                }

                isNotMedication && isDiabetesSafe && isHypertensionSafe &&
                    br.com.bragasaude.domain.NutritionSuggestionEngine.isSafeFromAllergies(
                        food, profile?.foodAllergies.orEmpty(), profile?.customFoodRestrictions)
            }
        }
    }

    /**
     * Busca no catálogo com filtros de segurança aplicados.
     */
    fun searchSafeFoods(query: String, profile: RemoteProfile?): Flow<List<FoodEntity>> {
        return getSafeFoodCatalog(profile).map { list ->
            if (query.isBlank()) list
            else list.filter {
                it.name.contains(query, ignoreCase = true) ||
                (it.category?.contains(query, ignoreCase = true) == true)
            }
        }
    }

    /**
     * Retorna todo o catálogo de alimentos para busca e registro factual no diário,
     * excluindo apenas itens de categorias estritamente farmacológicas / medicamentos.
     * Permite que o usuário registre o que realmente consumiu (ex: pão, doce, pizza).
     */
    fun getFullFoodCatalog(): Flow<List<FoodEntity>> {
        return foodDao.getCatalog().map { allFoods ->
            allFoods.filter { food ->
                val categoryLower = food.category?.lowercase() ?: ""
                !prohibitedCategories.any { categoryLower.contains(it) }
            }
        }
    }

    /**
     * Avalia se um alimento possui alerta educativo/clínico para o perfil do usuário.
     * Não bloqueia o registro, servindo como orientação visual de autocuidado no diário.
     */
    fun evaluateFoodClinicalWarning(food: FoodEntity, profile: RemoteProfile?): String? {
        if (profile == null) return null

        // 1. Diabetes: alimento contraindicado ou com status RESTRICTED
        if (profile.hasDiabetes && (!food.isDiabetesSafe || food.status == "RESTRICTED")) {
            return "Atenção: alto índice glicêmico para diabetes"
        }

        // 2. Hipertensão: teor de sódio elevado
        if (profile.hasHypertension && !food.isHypertensionSafe) {
            return "Atenção: alto teor de sódio para hipertensão"
        }

        // 3. Alergias e restrições cadastradas
        val allergies = profile.foodAllergies.orEmpty()
        val customRestrictions = profile.customFoodRestrictions
        if (!br.com.bragasaude.domain.NutritionSuggestionEngine.isSafeFromAllergies(food, allergies, customRestrictions)) {
            return "Atenção: contém item registrado em suas alergias"
        }

        return null
    }

    // ==================== LOG DE REFEIÇÕES (AUTOCUIDADO) ====================

    private val _todayLoggedMeals = MutableStateFlow<List<MealLogItem>>(emptyList())
    val todayLoggedMeals: StateFlow<List<MealLogItem>> = _todayLoggedMeals.asStateFlow()

    fun logMeal(item: MealLogItem) {
        _todayLoggedMeals.value = _todayLoggedMeals.value + item
    }

    fun removeLoggedMeal(itemId: String) {
        _todayLoggedMeals.value = _todayLoggedMeals.value.filterNot { it.id == itemId }
    }

    /**
     * Atualiza a porção (em gramas) de um item já registrado, recalculando
     * proporcionalmente as calorias com base no valor de referência por grama.
     * kcal = (kcal_original / gramas_originais) * novas_gramas
     */
    fun updateLoggedMealPortion(itemId: String, newPortionGrams: Int) {
        _todayLoggedMeals.value = _todayLoggedMeals.value.map { item ->
            if (item.id == itemId) {
                val safePortion = newPortionGrams.coerceIn(1, 2000)
                val kcalPerGram = if (item.portionGrams > 0) item.kcal / item.portionGrams else 0.0
                item.copy(
                    portionGrams = safePortion,
                    kcal = kcalPerGram * safePortion
                )
            } else item
        }
    }
}
