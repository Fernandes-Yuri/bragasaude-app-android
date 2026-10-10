package br.com.bragasaude.domain

/**
 * Faixa de orçamento para o planejamento semanal de compras.
 */
enum class GroceryBudgetTier(
    val title: String,
    val subtitle: String,
    val description: String,
    val maxBasketSize: Int
) {
    ULTRA_ECONOMIC(
        title = "Ultraeconômica",
        subtitle = "Cesta Essencial (8 a 10 itens)",
        description = "Máximo rendimento por real gasto. Concentra a nutrição na base brasileira (arroz, feijão, ovos, aveia, frango acessível/PTS e banana). Menor custo semanal.",
        maxBasketSize = 10
    ),
    ECONOMIC(
        title = "Econômica",
        subtitle = "Custo-Benefício (12 a 15 itens)",
        description = "Alimentos essenciais de alto rendimento com boa variedade, sem cortes nobres ou ingredientes caros.",
        maxBasketSize = 15
    ),
    MODERATE(
        title = "Média",
        subtitle = "Equilibrada (16 a 20 itens)",
        description = "Maior diversidade de ingredientes, incluindo carnes magras, peixes e frutas variadas da estação.",
        maxBasketSize = 20
    ),
    FREE(
        title = "Custo Livre",
        subtitle = "Variada / Aberta (20+ itens)",
        description = "Catálogo completo liberado sem restrições de preço, com rotação de frutos do mar, cortes nobres e castanhas.",
        maxBasketSize = 35
    );

    val label: String get() = "$title ($subtitle)"

    companion object {
        // Alias de compatibilidade retroativa para código legado
        val BALANCED: GroceryBudgetTier get() = MODERATE
    }
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
    val budgetTier: GroceryBudgetTier = GroceryBudgetTier.MODERATE,
    val selectedProteins: Set<GroceryProteinPreference> = emptySet(),
    val hasPantryStaples: Boolean = false,
    val maxWeeklyBudgetReais: Double? = null
) {
    val isUltraEconomic: Boolean get() = budgetTier == GroceryBudgetTier.ULTRA_ECONOMIC
    val isEconomic: Boolean get() = budgetTier == GroceryBudgetTier.ECONOMIC || budgetTier == GroceryBudgetTier.ULTRA_ECONOMIC

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
            budgetTier = GroceryBudgetTier.ULTRA_ECONOMIC,
            selectedProteins = setOf(
                GroceryProteinPreference.EGGS,
                GroceryProteinPreference.POULTRY
            ),
            hasPantryStaples = true
        )
    }
}
