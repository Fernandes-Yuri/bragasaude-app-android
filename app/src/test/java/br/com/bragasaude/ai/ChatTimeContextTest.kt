package br.com.bragasaude.ai

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class ChatTimeContextTest {
    @Test fun `todos os limites selecionam o turno correto`() {
        listOf(
            "00:00" to "Dificuldade para dormir", "04:59" to "Dificuldade para dormir",
            "05:00" to "Café da manhã saudável", "10:59" to "Café da manhã saudável",
            "11:00" to "O que preparar para o almoço?", "14:59" to "O que preparar para o almoço?",
            "15:00" to "Opção de lanche saudável", "18:29" to "Opção de lanche saudável",
            "18:30" to "O que jantar leve?", "23:59" to "O que jantar leve?"
        ).forEach { (time, expected) ->
            assertEquals(time, expected, ChatTimeContext.suggestions(LocalTime.parse(time)).first())
        }
    }

    @Test fun `madrugada oferece tres sugestoes e outros turnos quatro`() {
        assertEquals(3, ChatTimeContext.suggestions(LocalTime.MIDNIGHT).size)
        listOf(5, 11, 15, 19).forEach { assertEquals(4, ChatTimeContext.suggestions(LocalTime.of(it, 0)).size) }
    }
}
