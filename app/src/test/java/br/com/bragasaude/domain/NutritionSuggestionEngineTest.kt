package br.com.bragasaude.domain

import br.com.bragasaude.data.local.FoodEntity
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.data.remote.model.RemoteProfile
import org.junit.Assert.*
import org.junit.Test
import java.util.Date

class NutritionSuggestionEngineTest {

    private val sampleFoods = listOf(
        FoodEntity(
            remoteId = "food-1",
            name = "Aveia em Flocos",
            category = "Cereais",
            functionalTags = listOf("fibra_soluvel", "beta_glucana", "baixo_ig"),
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),
        FoodEntity(
            remoteId = "food-2",
            name = "Maçã com Casca",
            category = "Frutas",
            functionalTags = listOf("fibra_soluvel", "antioxidante_glicemia"),
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),
        FoodEntity(
            remoteId = "food-3",
            name = "Semente de Chia",
            category = "Sementes",
            functionalTags = listOf("fibra_soluvel", "omega3"),
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),
        FoodEntity(
            remoteId = "food-4",
            name = "Linhaça Dourada",
            category = "Sementes",
            functionalTags = listOf("fibra_soluvel", "omega3"),
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),
        FoodEntity(
            remoteId = "food-5",
            name = "Canela em Pó",
            category = "Especiarias",
            functionalTags = listOf("antioxidante_glicemia", "baixo_ig"),
            isDiabetesSafe = true,
            isHypertensionSafe = true
        ),
        FoodEntity(
            remoteId = "food-6",
            name = "Castanha-do-Pará",
            category = "Oleaginosas",
            functionalTags = listOf("gordura_boa", "antioxidante_glicemia"),
            isDiabetesSafe = true,
            isHypertensionSafe = true
        )
    )

}
