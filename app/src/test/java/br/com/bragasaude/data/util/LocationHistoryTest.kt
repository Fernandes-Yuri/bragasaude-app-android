package br.com.bragasaude.data.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationHistoryTest {
    @Test
    fun `successive GPS updates preserve separate readable coordinates`() {
        var raw = ""
        repeat(15) { index ->
            raw = appendLocationHistory(raw, -23.0, -46.0 + index * 0.001, 200)
        }
        val coordinates = raw.split(';').map { entry -> entry.split('|').map { it.toDouble() } }
        assertEquals(15, coordinates.size)
        coordinates.forEach { assertEquals(2, it.size) }
        assertEquals(-46.0, coordinates.first()[1], 0.000001)
        assertEquals(-45.986, coordinates.last()[1], 0.000001)
    }

    @Test
    fun `repeated coordinate does not consume history capacity`() {
        val first = appendLocationHistory("", -23.0, -46.0, 200)
        assertEquals(first, appendLocationHistory(first, -23.0, -46.0, 200))
    }

    @Test
    fun `history retains the newest coordinates at capacity`() {
        var raw = ""
        repeat(201) { raw = appendLocationHistory(raw, 0.0, it.toDouble(), 200) }
        assertEquals(200, raw.split(';').size)
        assertEquals("0.0|1.0", raw.split(';').first())
        assertEquals("0.0|200.0", raw.split(';').last())
    }
}
