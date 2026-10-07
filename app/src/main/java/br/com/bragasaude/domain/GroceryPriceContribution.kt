package br.com.bragasaude.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

const val GROCERY_PRICE_NOTICE = "Preços estimados com base em referências nacionais. O valor pago pode variar conforme região, loja e data. Use como referência para planejar a semana."

data class GroceryPriceContribution(
    val amountPaid: BigDecimal,
    val quantity: BigDecimal,
    val unit: String,
    val state: String?,
    val purchaseDate: LocalDate
) {
    companion object {
        fun parse(amount: String, quantity: String, unit: String, state: String, date: String,
                  today: LocalDate = LocalDate.now()): GroceryPriceContribution? {
            fun decimal(text: String): BigDecimal? = text.trim().replace(',', '.').toBigDecimalOrNull()
            val paid = decimal(amount) ?: return null
            val qty = decimal(quantity) ?: return null
            if (paid <= BigDecimal.ZERO || paid > BigDecimal("100000") || paid.stripTrailingZeros().scale() > 2) return null
            if (qty < BigDecimal("0.001") || qty > BigDecimal("100000") || qty.stripTrailingZeros().scale() > 3) return null
            if (unit !in setOf("g", "kg", "ml", "L", "un")) return null
            if (unit == "un" && qty.stripTrailingZeros().scale() > 0) return null
            val uf = state.trim().uppercase(java.util.Locale.ROOT).ifBlank { null }
            val states = "AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO".split(" ")
            if (uf != null && uf !in states) return null
            val day = try { LocalDate.parse(date.trim(), DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)) }
                catch (_: Exception) { return null }
            if (day > today || day < today.minusDays(90)) return null
            val baseQty = if (unit in setOf("g", "ml")) qty.divide(BigDecimal("1000")) else qty
            val price = paid.divide(baseQty, 8, java.math.RoundingMode.HALF_UP)
            if (price < BigDecimal("0.01") || price > BigDecimal("100000")) return null
            return GroceryPriceContribution(paid, qty, unit, uf, day)
        }
    }
}

data class CommunityGroceryPrice(val foodName: String, val unit: String, val average: Double, val contributors: Int)

data class GroceryContributionState(val submitting: Boolean = false, val success: Boolean = false, val message: String? = null)
