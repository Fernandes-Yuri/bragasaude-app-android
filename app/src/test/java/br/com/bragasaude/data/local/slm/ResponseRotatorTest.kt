package br.com.bragasaude.data.local.slm

import org.junit.Assert.*
import org.junit.Test

class ResponseRotatorTest {
    @Test fun eachSeverityHasFiveVariantsWithoutRepeatingAcrossCycles() {
        val rotator = ResponseRotator()
        TriageSeverity.values().filter { it != TriageSeverity.NENHUMA }.forEach { severity ->
            val first = (1..5).map { rotator.next(severity) }
            assertEquals(5, first.toSet().size)
            var last = first.last()
            repeat(200) {
                val next = rotator.next(severity)
                assertNotEquals(last, next)
                if (severity == TriageSeverity.EMERGENCIA) assertTrue(next.contains("192"))
                last = next
            }
        }
    }
}
