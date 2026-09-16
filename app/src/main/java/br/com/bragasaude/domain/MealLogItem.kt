package br.com.bragasaude.domain

import java.util.UUID

/**
 * Representação de um alimento consumido e registrado pelo usuário.
 */
data class MealLogItem(
    val id: String = UUID.randomUUID().toString(),
    val mealType: String, // "Café da Manhã", "Almoço", "Lanche", "Jantar", "Café da Tarde", "Ceia"
    val foodName: String,
    val portionGrams: Int,
    val kcal: Double
)
