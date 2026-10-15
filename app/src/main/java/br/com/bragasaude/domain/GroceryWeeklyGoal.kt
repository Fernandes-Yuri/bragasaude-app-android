package br.com.bragasaude.domain

import br.com.bragasaude.data.local.GroceryListItemEntity

data class GroceryWeeklyGoal(val target: Double = 0.0, val accumulated: Double = 0.0, val unknownItems: Int = 0) {
    val remaining get() = (target - accumulated).coerceAtLeast(0.0)
    val fraction get() = if (target > 0) accumulated / target else 0.0
    val withinTarget get() = fraction in 0.9..1.1 && unknownItems == 0
    companion object {
        fun calculate(daily: Double, items: List<GroceryListItemEntity>) = GroceryWeeklyGoal(
            daily.takeIf { it.isFinite() && it > 0 }?.times(7) ?: 0.0,
            items.sumOf { if (it.kcalPerUnit.isFinite() && it.kcalPerUnit >= 0) it.quantity.coerceAtLeast(0) * it.kcalPerUnit else 0.0 },
            items.count { it.retailCardId == null }
        )
    }
}
