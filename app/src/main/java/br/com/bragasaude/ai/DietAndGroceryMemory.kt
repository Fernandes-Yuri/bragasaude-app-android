package br.com.bragasaude.ai

import br.com.bragasaude.data.local.GroceryListDao
import br.com.bragasaude.data.local.GroceryListItemEntity
import br.com.bragasaude.data.local.MealRuleDao
import br.com.bragasaude.data.local.MealRuleEntity
import br.com.bragasaude.data.local.ProfileDao
import br.com.bragasaude.data.local.ProfileEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

/** Snapshot por turno, somente do usuário atual. Não mantém cache entre contas. */
class DietAndGroceryMemory @Inject constructor(
    private val groceries: GroceryListDao,
    private val meals: MealRuleDao,
    private val profiles: ProfileDao
) {
    suspend fun context(userId: String, question: String): String {
        if (!isFoodQuestion(question)) return ""
        return withContext(Dispatchers.IO) {
            describe(groceries.getGrocerySnapshot(userId), meals.getRules().first(), profiles.getProfileOneShot(userId))
        }
    }

    companion object {
        fun isFoodQuestion(question: String): Boolean {
            val normalized = Normalizer.normalize(question.lowercase(Locale.ROOT), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
            return Regex("\\b(comer|comida|almocar|almoco|jantar|lanche|cafe da manha|ingredientes?|despensa|alimentacao|refeicao|receita|preparar|dieta)\\b")
                .containsMatchIn(normalized)
        }

        private fun text(value: String): String = value.replace(Regex("[<>\\r\\n]"), " ").take(120)

        internal fun describe(items: List<GroceryListItemEntity>, rules: List<MealRuleEntity>, profile: ProfileEntity?): String {
            fun names(list: List<GroceryListItemEntity>): String = list.take(60).joinToString("; ") { text(it.foodName) }.ifBlank { "Nenhum item registrado." }
            val pantry = items.filter { it.isCheckedInPantry }
            val shopping = items.filterNot { it.isCheckedInPantry }
            val restrictions = if (profile == null) "Perfil clínico indisponível; restrições desconhecidas." else buildList {
                add("Diabetes informado: ${if (profile.hasDiabetes) "sim" else "não"}.")
                add("Hipertensão informada: ${if (profile.hasHypertension) "sim" else "não"}.")
                if (profile.hasRenalIssue) add("Condição renal informada.")
                if (profile.foodAllergies.isNotEmpty()) add("Alergias: ${profile.foodAllergies.joinToString("; ") { text(it) }}.")
                profile.customFoodRestrictions?.takeIf { it.isNotBlank() }?.let { add("Restrições: ${text(it)}.") }
            }.joinToString(" ")
            val configured = rules.take(12).joinToString("; ") { "${text(it.mealName)} (${text(it.suggestedTime.orEmpty())})" }.ifBlank { "Nenhuma refeição configurada." }
            return """
                Perfil clínico local: $restrictions
                Despensa marcada pelo usuário: ${names(pantry)}
                Lista de compras (ainda não disponível em casa): ${names(shopping)}
                Refeições configuradas: $configured
                Os registros podem estar desatualizados; não comprovam estoque ou quantidade atual.
            """.trimIndent().take(6000)
        }
    }
}
