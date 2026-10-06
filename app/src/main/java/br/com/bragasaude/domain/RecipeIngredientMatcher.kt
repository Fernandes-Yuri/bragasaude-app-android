package br.com.bragasaude.domain

import java.text.Normalizer
import java.util.Locale

/** Correspondências explícitas com o catálogo; nunca aceita apenas um trecho do nome. */
internal object RecipeIngredientMatcher {
    private fun normalize(name: String): String =
        Normalizer.normalize(name, Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            .lowercase(Locale.ROOT)
            .trim()
            .replace(Regex("\\s+"), " ")

    private val aliases = mapOf(
        "Aveia em flocos" to setOf("Aveia em flocos", "Aveia em Flocos Finos", "Aveia em Flocos Grossos"),
        "Banana" to setOf("Banana", "Banana-Prata", "Banana-Maçã"),
        "Leite" to setOf("Leite", "Leite Desnatado Pasteurizado"),
        "Canela em pó" to setOf("Canela em pó", "Canela em Pó do Ceilão"),
        "Ovos" to setOf("Ovos", "Ovo", "Ovo Caipira Cozido", "Ovo Caipira Mexido no Azeite", "Ovo Caipira Poché"),
        "Tomate" to setOf("Tomate", "Tomate Italiano com Casca", "Tomate Cereja Doce"),
        "Azeite de oliva" to setOf("Azeite de oliva", "Azeite de Oliva Extravirgem"),
        "Orégano" to setOf("Orégano", "Orégano Seco Puro"),
        "Abacate" to setOf("Abacate", "Abacate Manteiga", "Abacate Hass (Avocado)"),
        "Limão" to setOf("Limão", "Suco de Limão Tahiti Fresco", "Suco de Limão Siciliano Fresco", "Limão Tahiti", "Limão Siciliano"),
        "Mamão" to setOf("Mamão", "Mamão Papaia", "Mamão Formosa"),
        "Ricota" to setOf("Ricota", "Queijo Ricota Fresca"),
        "Couve" to setOf("Couve", "Folhas de Couve Manteiga"),
        "Queijo minas frescal" to setOf("Queijo minas frescal", "Queijo Minas Frescal Light"),
        "Tilápia" to setOf("Tilápia", "Tilápia / Saint Peter Grelhada"),
        "Peito de frango" to setOf("Peito de frango", "Peito de Frango Grelhado em Tiras", "Frango Desfiado com Cheiro-Verde"),
        "Abóbora cabotiá" to setOf("Abóbora cabotiá", "Abóbora Cabotiá Cozida"),
        "Cúrcuma" to setOf("Cúrcuma", "Cúrcuma / Açafrão-da-Terra Puro"),
        "Feijão carioca" to setOf("Feijão carioca", "Feijão Carioca Cozido"),
        "Cenoura" to setOf("Cenoura", "Cenoura Crua Ralada", "Cenoura Cozida no Vapor"),
        "Louro" to setOf("Louro", "Louro Seco em Folhas"),
        "Sardinha" to setOf("Sardinha", "Sardinha Fresca Grelhada"),
        "Cebola" to setOf("Cebola", "Cebola Branca Picada", "Cebola Roxa Crua"),
        "Grão-de-bico" to setOf("Grão-de-bico", "Grão-de-Bico Cozido"),
        "Chuchu" to setOf("Chuchu", "Chuchu Cozido no Vapor"),
        "Abobrinha" to setOf("Abobrinha", "Abobrinha Italiana Grelhada"),
        "Arroz integral" to setOf("Arroz integral", "Arroz Integral Agulhinha", "Arroz Parboilizado Integral", "Arroz Vermelho Integral", "Arroz Negro Integral"),
        "Brócolis" to setOf("Brócolis", "Brócolis Ninja no Vapor", "Brócolis Ramoso Refogado"),
        "Alho" to setOf("Alho", "Alho Fresco Picado"),
        "Mandioquinha" to setOf("Mandioquinha", "Mandioquinha (Batata-Baroa)"),
        "Patinho moído" to setOf("Patinho moído", "Patinho Bovino Moído Magro"),
        "Quinoa" to setOf("Quinoa", "Quinoa em Grãos Cozida"),
        "Pepino" to setOf("Pepino", "Pepino Japonês Fatiado"),
        "Gengibre" to setOf("Gengibre", "Gengibre Fresco Ralado"),
        "Macarrão integral" to setOf("Macarrão integral", "Macarrão Integral Cozido"),
        "Manjericão" to setOf("Manjericão", "Manjericão Fresco")
    ).mapKeys { normalize(it.key) }.mapValues { (_, names) -> names.map(::normalize).toSet() }

    fun acceptedNames(ingredient: String): Set<String> =
        aliases[normalize(ingredient)] ?: setOf(normalize(ingredient))
}
