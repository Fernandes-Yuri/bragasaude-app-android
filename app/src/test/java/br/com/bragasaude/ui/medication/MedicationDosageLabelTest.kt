package br.com.bragasaude.ui.medication

import org.junit.Assert.*
import org.junit.Test

class MedicationDosageLabelTest {
    @Test fun numericDoseUsesPortugueseDecimalAndSeparatesUnit() {
        assertEquals("50 mg", medicationDosageLabel(null, 50.0))
        assertEquals("12,5 mg", medicationDosageLabel(null, 12.5))
        assertEquals("0,025 mg", medicationDosageLabel(null, 0.025))
    }

    @Test fun registeredDoseKeepsItsUnitsAndTakesPrecedenceOverNumericFallback() {
        assertEquals("12,5 mg", medicationDosageLabel("12.5mg", 50.0))
        assertEquals("50 mg/5 ml", medicationDosageLabel("50mg/5ml", null))
        assertEquals("2 gotas", medicationDosageLabel(" 2 gotas ", 50.0))
        assertEquals("500 mcg", medicationDosageLabel("500mcg", null))
        assertEquals("1.000 mg", medicationDosageLabel("1.000mg", null))
        assertEquals("0,125 mg", medicationDosageLabel("0.125mg", null))
    }

    @Test fun missingOrInvalidNumericDoseNeverInventsAValue() {
        listOf(null, 0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertNull(medicationDosageLabel(" ", it))
        }
    }
}
