package br.com.bragasaude.ai

import org.junit.Assert.*
import org.junit.Test

class MealResponsesTest {
    @Test fun everyVariationKeepsTheRequestedMeal() {
        for (meal in listOf("lanche", "café da manhã", "almoço", "jantar")) {
            val responses = MealResponses.candidates("Sugestões para $meal")
            assertTrue(responses.isNotEmpty())
            responses.forEach { assertTrue(it.lowercase().contains(meal)) }
        }
        MealResponses.candidates("ideias para um lanche da tarde").forEach {
            assertFalse(it.contains("almoço")); assertFalse(it.contains("jantar"))
        }
    }
    @Test fun explicitMealWinsWithoutConsultingTheClock() {
        assertEquals(MealResponses.candidates("café da manhã"), MealResponses.candidates("café da manhã às 15h"))
    }
    @Test fun vagueRequestAsksWhichMeal() {
        assertTrue(MealResponses.candidates("ideias para comer").single().endsWith("?"))
    }
}
