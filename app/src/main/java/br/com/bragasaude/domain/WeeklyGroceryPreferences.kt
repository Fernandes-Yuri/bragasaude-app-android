package br.com.bragasaude.domain

/**
 * Faixa de orçamento para o planejamento semanal de compras.
 */
enum class GroceryBudgetTier(val label: String, val description: String) {
    ECONOMIC(
        label = "Econômica (Custo-Benefício)",
        description = "Alimentos essenciais de alto rendimento e densidade calórica acessível. Cesta enxuta de 12 a 15 itens."
    ),
    BALANCED(
        label = "Variada / Livre",
        description = "Maior diversidade de ingredientes e alternativas da feira com rotação aberta."
    )
}

/**
 * Preferências de fontes de proteína da semana.
 */
enum class GroceryProteinPreference(val label: String, val categoryKeywords: List<String>) {
    EGGS("Ovos", listOf("ovo", "ovos")),
    POULTRY("Frango e Aves", listOf("frango", "peito de frango", "sobrecoxa", "ave")),
    BEEF("Carnes bovinas", listOf("carne", "patinho", "alcatra", "bovino", "moída")),
    FISH("Peixes e Pescados", listOf("peixe", "tilápia", "sardinha", "atum", "salmão")),
    PLANT_BASED("Proteínas vegetais", listOf("grão-de-bico", "lentilha", "soja", "tofu"))
}

/**
 * Configurações de preferências do usuário para a geração da lista semanal.
 */
data class WeeklyGroceryPreferences(
    val budgetTier: GroceryBudgetTier = GroceryBudgetTier.BALANCED,
    val selectedProteins: Set<GroceryProteinPreference> = emptySet(),
    val hasPantryStaples: Boolean = false
) {
    val isEconomic: Boolean get() = budgetTier == GroceryBudgetTier.ECONOMIC

    /**
     * Valida e garante ao menos uma proteína selecionada quando no modo restrito.
     */
    fun normalized(): WeeklyGroceryPreferences {
        val proteins = if (selectedProteins.isEmpty() && isEconomic) {
            setOf(GroceryProteinPreference.EGGS)
        } else {
            selectedProteins
        }
        return copy(selectedProteins = proteins)
    }

    companion object {
        val RECOMMENDED = WeeklyGroceryPreferences(
            budgetTier = GroceryBudgetTier.ECONOMIC,
            selectedProteins = setOf(
                GroceryProteinPreference.EGGS,
                GroceryProteinPreference.POULTRY
            ),
            hasPantryStaples = true
        )
    }
}
