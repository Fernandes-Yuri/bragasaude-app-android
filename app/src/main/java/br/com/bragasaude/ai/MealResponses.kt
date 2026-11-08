package br.com.bragasaude.ai

/** A variação fica dentro da refeição solicitada; horário não troca o assunto. */
internal object MealResponses {
    fun candidates(text: String): List<String> {
        val normalized = java.text.Normalizer.normalize(text.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return when {
            "lanche" in normalized -> listOf("Para um lanche, que tal uma fruta ou uma torrada com ovo? Considere suas restrições alimentares.",
                "Uma ideia de lanche: iogurte natural com fruta, se esses alimentos fizerem parte da sua alimentação.")
            "cafe da manha" in normalized -> listOf("Para o café da manhã, uma ideia é pão com ovo e uma fruta, conforme suas restrições.",
                "Uma opção de café da manhã é aveia com fruta. Prefere algo quente ou frio?")
            "almoco" in normalized -> listOf("Para o almoço, uma ideia é arroz, feijão e legumes, com uma proteína que você costuma consumir.",
                "Uma opção de almoço é uma combinação de verduras, arroz e feijão. Há algum alimento que você precisa evitar?")
            "jantar" in normalized -> listOf("Para o jantar, uma ideia é omelete com legumes, conforme suas restrições.",
                "Uma opção de jantar é sopa de legumes com uma proteína que faça parte da sua alimentação.")
            else -> listOf("Você quer ideias para o café da manhã, almoço, jantar ou um lanche?")
        }
    }
    fun respond(text: String): String = candidates(text).random()
}
