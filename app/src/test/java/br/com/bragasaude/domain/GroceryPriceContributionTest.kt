package br.com.bragasaude.domain

import java.math.BigDecimal
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class GroceryPriceContributionTest {
    private val today = LocalDate.of(2026, 10, 6)
    private fun parse(amount: String = "8,00", quantity: String = "500", unit: String = "g", state: String = "", date: String = "06/10/2026") =
        GroceryPriceContribution.parse(amount, quantity, unit, state, date, today)

    @Test fun acceptsOptionalRegionAndActualPurchaseAmountAndQuantity() {
        val value = parse(state = "sp")!!
        assertEquals(0, value.amountPaid.compareTo(BigDecimal("8")))
        assertEquals(0, value.quantity.compareTo(BigDecimal("500")))
        assertEquals("SP", value.state)
        assertEquals(today, value.purchaseDate)
        assertNull(parse()!!.state)
        assertNotNull(parse(amount = "12", quantity = "12", unit = "un"))
        assertNotNull(parse(amount = "8", quantity = "0,5", unit = "kg"))
    }

    @Test fun refusesInvalidPricesQuantitiesUnitsRegionsAndDates() {
        for (price in listOf("", "0", "-1", "NaN", "Infinity", "1,001", "100001")) assertNull(parse(amount = price))
        for (quantity in listOf("", "0", "-1", "0,0001", "Infinity", "100001")) assertNull(parse(quantity = quantity))
        assertNull(parse(quantity = "1,5", unit = "un"))
        assertNull(parse(unit = "pacote"))
        assertNull(parse(state = "ZZ"))
        assertNull(parse(date = "31/02/2026"))
        assertNull(parse(date = "07/10/2026"))
        assertNull(parse(date = "01/01/2026"))
        assertNull(parse(amount = "10000", quantity = "1", unit = "g"))
    }
}
