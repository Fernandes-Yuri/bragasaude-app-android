package br.com.bragasaude.domain.nutrition.core

import java.text.Normalizer
import java.util.Locale

/**
 * Nível canônico de atividade física com Fator de Atividade Física (FAF) padronizado.
 * Suporta interoperabilidade com enums legados e textos descritivos em pt-BR/EN.
 */
enum class CanonicalActivityLevel(
    val factor: Float,
    val displayName: String
) {
    SEDENTARY(1.20f, "Sedentário"),
    LIGHTLY_ACTIVE(1.375f, "Levemente ativo"),
    MODERATELY_ACTIVE(1.55f, "Moderadamente ativo"),
    VERY_ACTIVE(1.725f, "Muito ativo"),
    EXTREME_ATHLETE(1.90f, "Extremamente ativo");

    companion object {
        fun from(raw: String?): CanonicalActivityLevel {
            if (raw.isNullOrBlank()) return SEDENTARY
            val normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replace("\\p{M}".toRegex(), "")
                .uppercase(Locale.ROOT)
                .trim()

            // 1. Verificação por nome exato de Enum (EN / legado)
            when (normalized) {
                "SEDENTARY" -> return SEDENTARY
                "LIGHTLY_ACTIVE", "LIGHT", "LEVE" -> return LIGHTLY_ACTIVE
                "MODERATELY_ACTIVE", "MODERATE", "MODERADO" -> return MODERATELY_ACTIVE
                "VERY_ACTIVE", "VERY", "MUITO_ATIVO" -> return VERY_ACTIVE
                "EXTREME_ATHLETE", "EXTREME", "ATLETA" -> return EXTREME_ATHLETE
            }

            // 2. Verificação semântica por palavras-chave
            val lower = normalized.lowercase(Locale.ROOT)
            return when {
                lower.contains("extrem") || lower.contains("atleta") -> EXTREME_ATHLETE
                lower.contains("muito") || lower.contains("intenso") || lower.contains("pesado") -> VERY_ACTIVE
                lower.contains("modera") || lower.contains("regular") -> MODERATELY_ACTIVE
                lower.contains("leve") || lower.contains("caminhada") -> LIGHTLY_ACTIVE
                lower.contains("sedent") || lower.contains("parado") -> SEDENTARY
                else -> SEDENTARY
            }
        }
    }
}

/**
 * Objetivo dietético canônico com ajuste calórico e coeficiente proteico (g/kg).
 */
enum class CanonicalDietaryGoal(
    val calorieAdjustment: Float,
    val displayName: String,
    val targetProteinPerKg: Float
) {
    LOSE_WEIGHT(-400f, "Emagrecimento", 2.0f),
    MAINTAIN(0f, "Manutenção", 1.6f),
    GAIN_MUSCLE(350f, "Ganho de Massa", 1.8f);

    companion object {
        fun from(
            explicitGoalStr: String?,
            weight: Double? = null,
            weightGoal: Double? = null
        ): CanonicalDietaryGoal {
            val raw = explicitGoalStr.orEmpty()
            if (raw.isNotBlank()) {
                val normalized = Normalizer.normalize(raw, Normalizer.Form.NFD)
                    .replace("\\p{M}".toRegex(), "")
                    .uppercase(Locale.ROOT)
                    .trim()

                // Nomes de Enums conhecidos
                when (normalized) {
                    "LOSE_WEIGHT", "WEIGHT_LOSS" -> return LOSE_WEIGHT
                    "MAINTAIN", "MAINTENANCE" -> return MAINTAIN
                    "GAIN_MUSCLE", "HYPERTROPHY" -> return GAIN_MUSCLE
                }

                val lower = normalized.lowercase(Locale.ROOT)
                when {
                    lower.contains("emagrec") || lower.contains("perda") || lower.contains("deficit") || lower.contains("secar") -> return LOSE_WEIGHT
                    lower.contains("hipertrofia") || lower.contains("ganho") || lower.contains("massa") || lower.contains("crescer") -> return GAIN_MUSCLE
                    lower.contains("manut") || lower.contains("equilibr") || lower.contains("estavel") -> return MAINTAIN
                }
            }

            // Fallback por comparação de peso-meta
            if (weight != null && weightGoal != null && weight > 0 && weightGoal > 0) {
                val diff = weightGoal - weight
                if (diff <= -1.0) return LOSE_WEIGHT
                if (diff >= 1.0) return GAIN_MUSCLE
            }

            return MAINTAIN
        }
    }
}

/**
 * Divisão balanceada de macronutrientes com conservação estrita da energia:
 * Kcal Total == (Carb * 4) + (Prot * 4) + (Fat * 9)
 */
data class BalancedMacroSplit(
    val proteinG: Float,
    val proteinKcal: Float,
    val fatG: Float,
    val fatKcal: Float,
    val carbsG: Float,
    val carbsKcal: Float,
    val targetKcal: Float
) {
    val totalCalculatedKcal: Float get() = proteinKcal + fatKcal + carbsKcal

    val isEnergyConserved: Boolean
        get() = kotlin.math.abs(totalCalculatedKcal - targetKcal) <= 2.0f
}

/**
 * Estado de segurança clínica tri-estado (Safe, Restricted com motivo, Unknown).
 * Não assume segurança permissiva se faltar evidência.
 */
sealed class ClinicalSafety {
    data object Safe : ClinicalSafety()
    data class Restricted(val reason: String) : ClinicalSafety()
    data object Unknown : ClinicalSafety()
}

/**
 * Famílias canônicas de alérgenos com dicionário ontológico de termos e sinônimos.
 */
enum class AllergenFamily(
    val displayName: String,
    val aliases: List<String>
) {
    MILK_LACTOSE(
        displayName = "Leite e Lactose",
        aliases = listOf("leite", "lactose", "queijo", "iogurte", "requeijao", "manteiga", "ricota", "caseina", "whey")
    ),
    GLUTEN(
        displayName = "Glúten",
        aliases = listOf("gluten", "trigo", "farinha de trigo", "centeio", "cevada", "malte", "paes", "pao")
    ),
    EGG(
        displayName = "Ovos",
        aliases = listOf("ovo", "ovos", "gema", "clara", "albumina", "omelete", "maionese")
    ),
    FISH(
        displayName = "Peixes",
        aliases = listOf("peixe", "peixes", "tilapia", "sardinha", "atum", "salmao", "pescada", "bacalhau")
    ),
    CRUSTACEAN(
        displayName = "Frutos do Mar e Crustáceos",
        aliases = listOf("camarao", "camaroes", "lagosta", "caranguejo", "siri", "frutos do mar", "marisco", "lula", "polvo")
    ),
    PEANUT(
        displayName = "Amendoim",
        aliases = listOf("amendoim", "pasta de amendoim", "pacoca", "xerem de amendoim")
    ),
    TREE_NUTS(
        displayName = "Castanhas e Nozes",
        aliases = listOf("castanha", "castanhas", "noz", "nozes", "amendoa", "amendoas", "avela", "avelas", "pistache", "macadamia")
    ),
    SOY(
        displayName = "Soja",
        aliases = listOf("soja", "tofu", "proteina de soja", "pts", "shoyu", "edamame")
    );

    companion object {
        private fun cleanText(text: String): String {
            return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace("\\p{M}".toRegex(), "")
                .lowercase(Locale.ROOT)
        }

        fun matchesFood(foodName: String, category: String?, tags: List<String>, allergen: AllergenFamily): Boolean {
            val cleanName = cleanText(foodName)
            val cleanCat = category?.let { cleanText(it) }.orEmpty()
            val cleanTags = tags.map { cleanText(it) }

            // Verifica tags declaradas (ex: "allergen:milk", "allergen:gluten")
            val allergenTagKey = when (allergen) {
                MILK_LACTOSE -> "milk"
                GLUTEN -> "gluten"
                EGG -> "egg"
                FISH -> "fish"
                CRUSTACEAN -> "crustacean"
                PEANUT -> "peanut"
                TREE_NUTS -> "tree_nuts"
                SOY -> "soy"
            }
            if (cleanTags.any { it == "allergen:$allergenTagKey" || it == "contem_$allergenTagKey" }) {
                return true
            }

            // Verifica aliases com correspondência de palavra inteira ou radical seguro
            return allergen.aliases.any { alias ->
                val cleanAlias = cleanText(alias)
                cleanName.contains(cleanAlias) || cleanCat.contains(cleanAlias) || cleanTags.any { it.contains(cleanAlias) }
            }
        }

        fun parseDeclaredAllergens(userQueryOrList: List<String>): Set<AllergenFamily> {
            val result = mutableSetOf<AllergenFamily>()
            val combined = userQueryOrList.joinToString(" ") { cleanText(it) }
            for (family in entries) {
                if (family.aliases.any { alias -> combined.contains(cleanText(alias)) }) {
                    result.add(family)
                }
            }
            return result
        }
    }
}
