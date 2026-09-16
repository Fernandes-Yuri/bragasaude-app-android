package br.com.bragasaude.domain

import br.com.bragasaude.data.local.ExamItemEntity
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

    @Test
    fun generateSuggestions_produces_stable_and_deterministic_output() {
        val exams = listOf(
            ExamItemEntity(
                localId = 1,
                userId = "u1",
                examId = "e1",
                itemKey = "glucose",
                itemName = "Glicose",
                valueNumeric = 120.0,
                unit = "mg/dL",
                measuredAt = Date(),
                status = "confirmed"
            )
        )

        val run1 = NutritionSuggestionEngine.generateSuggestions(
            exams = exams,
            vitals = emptyList(),
            profile = RemoteProfile(id = "u1"),
            catalog = sampleFoods,
            selectedMealType = "Café da Manhã"
        )

        val run2 = NutritionSuggestionEngine.generateSuggestions(
            exams = exams,
            vitals = emptyList(),
            profile = RemoteProfile(id = "u1"),
            catalog = sampleFoods,
            selectedMealType = "Café da Manhã"
        )

        val run3 = NutritionSuggestionEngine.generateSuggestions(
            exams = exams,
            vitals = emptyList(),
            profile = RemoteProfile(id = "u1"),
            catalog = sampleFoods,
            selectedMealType = "Café da Manhã"
        )

        assertEquals(run1.size, run2.size)
        assertEquals(run1.size, run3.size)

        val group1 = run1.first { it.id == "glucose_balance" }
        val group2 = run2.first { it.id == "glucose_balance" }
        val group3 = run3.first { it.id == "glucose_balance" }

        val names1 = group1.options.map { it.name }
        val names2 = group2.options.map { it.name }
        val names3 = group3.options.map { it.name }

        assertEquals("As sugestões não devem oscilar ou trocar sozinhas em loop", names1, names2)
        assertEquals("As sugestões não devem oscilar ou trocar sozinhas em loop", names1, names3)
    }

    @Test
    fun dismissing_an_option_removes_only_that_option_without_chaotic_reordering() {
        val exams = listOf(
            ExamItemEntity(
                localId = 1,
                userId = "u1",
                examId = "e1",
                itemKey = "glucose",
                itemName = "Glicose",
                valueNumeric = 120.0,
                unit = "mg/dL",
                measuredAt = Date(),
                status = "confirmed"
            )
        )

        val initial = NutritionSuggestionEngine.generateSuggestions(
            exams = exams,
            vitals = emptyList(),
            profile = RemoteProfile(id = "u1"),
            catalog = sampleFoods,
            selectedMealType = "Café da Manhã"
        ).first { it.id == "glucose_balance" }

        val dismissedName = initial.options.first().name

        val afterDismiss = NutritionSuggestionEngine.generateSuggestions(
            exams = exams,
            vitals = emptyList(),
            profile = RemoteProfile(id = "u1"),
            catalog = sampleFoods,
            selectedMealType = "Café da Manhã",
            dislikedFoodNames = setOf(dismissedName)
        ).first { it.id == "glucose_balance" }

        assertFalse(afterDismiss.options.any { it.name == dismissedName })

        val remainingOriginal = initial.options.drop(1).map { it.name }
        for (item in remainingOriginal) {
            assertTrue("O item $item deveria permanecer na lista após a troca de outro", afterDismiss.options.any { it.name == item })
        }
    }
}
