package br.com.bragasaude.ai

import java.time.LocalTime

object ChatTimeContext {
    fun suggestions(time: LocalTime = LocalTime.now()): List<String> = when {
        time < LocalTime.of(5, 0) -> listOf("Dificuldade para dormir", "Registrar pressão", "Preciso de ajuda")
        time < LocalTime.of(11, 0) -> listOf("Café da manhã saudável", "Remédios da manhã", "Como está minha pressão?", "Glicemia em jejum")
        time < LocalTime.of(15, 0) -> listOf("O que preparar para o almoço?", "Remédio pós-almoço", "Registrar pressão arterial", "Meta de água de hoje")
        time < LocalTime.of(18, 30) -> listOf("Opção de lanche saudável", "Como está minha hidratação?", "Remédios da tarde", "Caminhada / exercícios")
        else -> listOf("O que jantar leve?", "Remédios da noite", "Dicas para dormir bem", "Resumo do meu dia")
    }
}
