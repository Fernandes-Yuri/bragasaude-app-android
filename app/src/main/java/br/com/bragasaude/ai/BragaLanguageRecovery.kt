package br.com.bragasaude.ai

import java.text.Normalizer
import java.util.Locale

/** Vocabulário fechado para reconhecimento. Nunca reescreve a entrada dos executores. */
internal object BragaLanguageRecovery {
    private val aliases = mapOf(
        "intendi" to "entendi", "intendí" to "entendi", "comprendi" to "compreendi",
        "presao" to "pressao", "preçao" to "pressao",
        "glicimia" to "glicemia", "glicemía" to "glicemia", "glicosemia" to "glicemia",
        "jejun" to "jejum", "remedo" to "remedio", "medicmento" to "medicamento",
        "relatario" to "relatorio", "historco" to "historico", "hidrataçao" to "hidratacao",
        "ezame" to "exame", "ezames" to "exames", "obrigdo" to "obrigado",
        "socoro" to "socorro", "respiraçao" to "respiracao", "naum" to "nao"
    ).mapKeys { fold(it.key) }
    private val words = Regex("""[\p{L}]+""")
    private fun fold(text: String) = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")

    fun recognize(text: String): String {
        val folded = fold(text)
        val recovered = words.replace(folded) { aliases[it.value] ?: it.value }
        return recovered
            .replace(Regex("""\b(?:taxa de acucar|acucar no sangue)\b"""), "glicemia")
            .replace(Regex("""\bcomo (?:eu )?faco (?:pra|para)\b"""), "como")
            .replace(Regex("""\bcomo (?:eu )?faco (?:o |a )?(?=registro|cadastro)"""), "como ")
            .replace(Regex("\\s+"), " ").trim()
    }

    /** Ambiguidades reconhecidas não podem chegar à consulta contextual ou ao parser de ação. */
    fun clarification(text: String): String? {
        val input = recognize(text)
        if (Regex("""\bprecissao\b""").containsMatchIn(input))
            return "Você quis dizer pressão? Repita o nome da medida que deseja consultar."
        if (Regex("""\banti ontem\b""").containsMatchIn(input))
            return "Você quis dizer ontem ou anteontem? Confirme o período que deseja consultar."
        val measurement = Regex("""\b(pressao|glicemia|glicose|agua|ml|mililitros?|litros?|copos?)\b""").containsMatchIn(input)
        val numeric = "(?:\\d+(?:[.,]\\d+)?|um|uma|dois|duas|tres|quatro|cinco|seis|sete|oito|nove|dez|onze|doze|treze|quatorze|quinze|vinte|trinta|quarenta|cinquenta|cem|cento|duzentos|quinhentos)"
        if (measurement && Regex("""\b$numeric\s+(?:ou|quer dizer)\s+$numeric\b""").containsMatchIn(input))
            return "Qual é o valor correto? Repita a medida e a unidade antes de preparar o registro."
        if (measurement && Regex("""\b(?:ml|mililitros?|litros?|litro|l)\s+ou\s+(?:$numeric\s+)?(?:ml|mililitros?|litros?|litro|l)\b""").containsMatchIn(input))
            return "Confirme a unidade da quantidade: mililitros ou litros?"
        if (Regex("""\bpressao\b""").containsMatchIn(input) &&
            Regex("""\b(?:1\d{2}|2\d{2})\s+(?:por|/)\s+[1-9]\b|\b1[0-9]\s+(?:por|/)\s+[5-9][0-9]\b""").containsMatchIn(input))
            return "Confirme a escala dos dois valores da pressão. Repita, por exemplo, 12 por 8 ou 120 por 80."
        if (Regex("""\b(tomei|tomar|remedio|medicamento|comprimido)\b""").containsMatchIn(input) &&
            Regex("""\b(?:ou|acho que|nao sei qual)\b""").containsMatchIn(input))
            return "Confirme o nome exato do medicamento na sua lista ou na embalagem. Não vou escolher nem corrigir o nome por aproximação."
        if (Regex("""\b(bebi|anota|anote|registra|registre)\b""").containsMatchIn(input) &&
            Regex("""\bagua\b""").containsMatchIn(input) && Regex("""\b$numeric\b""").containsMatchIn(input) &&
            !Regex("""\b(ml|mililitros?|litros?|litro|l|copos?|garrafas?|canecas?)\b""").containsMatchIn(input))
            return "Qual é a unidade dessa quantidade de água: mililitros, litros ou copos?"
        if (Regex("""\b(?:naum|num|n)\s+(?:tomei|bebi|anota|anote|registra|registre|entendi|intendi|tenho|sinto)\b""").containsMatchIn(fold(text)))
            return "Não ficou claro se você está negando essa informação. Repita a frase com ou sem a palavra não."
        return null
    }
}
