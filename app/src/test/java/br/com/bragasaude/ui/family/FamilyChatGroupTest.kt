package br.com.bragasaude.ui.family

import br.com.bragasaude.data.local.FamilyBindingEntity
import org.junit.Assert.*
import org.junit.Test

class FamilyChatGroupTest {
    private fun binding(patient: String, caregiver: String, status: String = "ACTIVE") =
        FamilyBindingEntity("$patient-$caregiver", patient, caregiver, "Nome", "Familiar", "ABCDEFGH", status)

    @Test fun twoCaregiversShareOnePatientGroup() {
        val bindings = listOf(binding("p", "a"), binding("p", "b"))
        for (user in listOf("p", "a", "b")) {
            assertEquals("p", resolveFamilyChatGroup(bindings, user, null))
        }
    }

    @Test fun multiplePatientsRequireSelectionRegardlessOfOrdering() {
        val bindings = listOf(binding("p", "a"), binding("q", "a"))
        assertNull(resolveFamilyChatGroup(bindings, "a", null))
        assertEquals("q", resolveFamilyChatGroup(bindings.reversed(), "a", "q"))
    }

    @Test fun revokedAndUnrelatedBindingsCannotSelectAGroup() {
        val bindings = listOf(binding("p", "a", "REVOKED"), binding("q", "b"), binding("r", "a"))
        assertNull(resolveFamilyChatGroup(bindings, "a", "p"))
        assertEquals(listOf("r"), familyChatGroups(bindings, "a"))
    }

    @Test fun hybridUserMustChooseBetweenOwnAndWatchedFamily() {
        assertNull(resolveFamilyChatGroup(listOf(binding("a", "b"), binding("p", "a")), "a", null))
    }
}
