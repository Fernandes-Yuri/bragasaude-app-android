package br.com.bragasaude.domain

/**
 * Tipos formais de limitações encontradas no planejamento de compras semanais.
 * Diferencia explicitamente problemas de rendimento, densidade, peso por unidade,
 * proporções de receita, dados nutricionais e vínculos de catálogo.
 */
enum class GroceryLimitationType(val userTitle: String) {
    MISSING_YIELD("Rendimento de cocção estimado (1:1)"),
    MISSING_DENSITY("Densidade de volume não cadastrada"),
    MISSING_UNIT_WEIGHT("Peso por unidade não cadastrado"),
    MISSING_RECIPE_PROPORTIONS("Proporções de preparo pendentes"),
    MISSING_NUTRITIONAL_DATA("Dados calóricos ausentes no catálogo"),
    MISSING_INGREDIENT_MAPPING("Vínculo de ingrediente de compra não mapeado"),
    NO_ELIGIBLE_FOODS("Sem alimentos elegíveis para as restrições")
}

/**
 * Impacto técnico da limitação no cálculo da compra.
 */
enum class GroceryCalculationImpact {
    APPROXIMATED,
    NOT_CALCULABLE
}

/**
 * Limitação estruturada do dimensionamento.
 */
data class GroceryLimitation(
    val type: GroceryLimitationType,
    val affectedItems: List<String>,
    val impact: GroceryCalculationImpact,
    val userSummary: String
)

/**
 * Estado explícito de dimensionamento da compra.
 * Não confunde cobertura energética do consumo planejado com garantia de compra dimensionada.
 */
enum class PurchaseCalculationStatus(val label: String) {
    CALCULABLE("Compra dimensionada"),
    APPROXIMATED("Planejamento estimado"),
    INCOMPLETE("Compra com quantidades pendentes")
}

object GroceryLimitationFormatter {
    /**
     * Agrupa as limitações por tipo para exibição concisa na UI, sem repetição de textos.
     * Retorna pares de (Título simples do grupo, Lista de itens afetados).
     */
    fun groupForUi(limitations: List<GroceryLimitation>): List<Pair<String, List<String>>> {
        if (limitations.isEmpty()) return emptyList()
        return limitations.groupBy { it.type }.map { (type, list) ->
            val items = list.flatMap { it.affectedItems }.distinct()
            type.userTitle to items
        }
    }
}
