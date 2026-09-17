package br.com.bragasaude.ui.exams

import br.com.bragasaude.data.util.HealthFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamsPhase2Test {

    @Test
    fun `catalogo manual contem os 5 grupos normativos da especificacao`() {
        assertEquals(5, MANUAL_EXAM_CATALOG.size)

        val groupIds = MANUAL_EXAM_CATALOG.map { it.groupId }
        assertTrue(groupIds.contains("glycemia"))
        assertTrue(groupIds.contains("lipids"))
        assertTrue(groupIds.contains("renal_hepatic"))
        assertTrue(groupIds.contains("hemogram"))
        assertTrue(groupIds.contains("hormones_vitamins"))
    }

    @Test
    fun `grupo glicemia possui glicose e hemoglobina glicada com unidades corretas`() {
        val glycemia = MANUAL_EXAM_CATALOG.first { it.groupId == "glycemia" }
        assertEquals(2, glycemia.fields.size)

        val glucose = glycemia.fields.first { it.key == "glucose" }
        assertEquals("mg/dL", glucose.defaultUnit)

        val hba1c = glycemia.fields.first { it.key == "hba1c" }
        assertEquals("%", hba1c.defaultUnit)
    }

    @Test
    fun `grupo lipidico possui colesterol total hdl ldl e triglicerideos`() {
        val lipids = MANUAL_EXAM_CATALOG.first { it.groupId == "lipids" }
        assertEquals(4, lipids.fields.size)

        val keys = lipids.fields.map { it.key }
        assertTrue(keys.contains("total_cholesterol"))
        assertTrue(keys.contains("hdl"))
        assertTrue(keys.contains("ldl"))
        assertTrue(keys.contains("triglycerides"))
    }

    @Test
    fun `validacao de item editavel rejeita valor zero ou negativo ou invalido`() {
        fun isValid(value: String): Boolean {
            val num = HealthFormatter.parseDouble(value)
            return num != null && num > 0
        }

        assertFalse(isValid(""))
        assertFalse(isValid("abc"))
        assertFalse(isValid("0"))
        assertFalse(isValid("0.0"))
        assertFalse(isValid("-5.0"))

        assertTrue(isValid("98"))
        assertTrue(isValid("125,5"))
        assertTrue(isValid("0.5"))
    }

    @Test
    fun `contrato de conferencia humana so aprova se houver itens validos e nao deletados`() {
        val item1 = EditableExamItemState(
            itemKey = "glucose",
            itemName = "Glicose em Jejum",
            valueInput = "95",
            unit = "mg/dL",
            isNumericValid = true,
            isDeleted = false
        )
        val item2 = EditableExamItemState(
            itemKey = "ldl",
            itemName = "Colesterol LDL",
            valueInput = "110",
            unit = "mg/dL",
            isNumericValid = true,
            isDeleted = true // excluído pelo usuário
        )

        val activeItems = listOf(item1, item2).filter { !it.isDeleted }
        assertEquals(1, activeItems.size)
        assertTrue(activeItems.all { it.isNumericValid })

        val examTitle = "Exame de Rotina"
        val isConfirmEnabled = examTitle.isNotBlank() && activeItems.isNotEmpty() && activeItems.all { it.isNumericValid }
        assertTrue(isConfirmEnabled)
    }
}
