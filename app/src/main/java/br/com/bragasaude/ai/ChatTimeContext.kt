package br.com.bragasaude.ai

import java.time.LocalTime

object ChatTimeContext {
    fun suggestions(time: LocalTime = LocalTime.now()): List<String> = when {
        time < LocalTime.of(5, 0) -> listOf("Dificuldade para dormir", "Como está minha pressão?", "Preciso de ajuda")
        time < LocalTime.of(11, 0) -> listOf("Ideias para um café da manhã saudável", "Como está minha pressão?", "Como foi minha glicemia em jejum?", "Meta de água de hoje")
        time < LocalTime.of(15, 0) -> listOf("Ideias para um almoço saudável", "Como está minha pressão?", "Meta de água de hoje", "Resumo do meu dia")
        time < LocalTime.of(18, 30) -> listOf("Opção de lanche saudável", "Como está minha hidratação?", "Caminhada leve hoje?", "Resumo do meu dia")
        else -> listOf("Ideias para um jantar leve", "Como melhorar meu sono?", "Como está minha hidratação?", "Resumo do meu dia")
    }
}
