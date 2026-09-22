package br.com.bragasaude.ui.family

import br.com.bragasaude.data.local.FamilyBindingEntity

fun familyChatGroups(bindings: List<FamilyBindingEntity>, userId: String): List<String> =
    bindings.filter { it.status == "ACTIVE" && it.patientUserId.isNotBlank() &&
        (it.patientUserId == userId || it.caregiverUserId == userId) }
        .map { it.patientUserId }.distinct().sorted()

fun resolveFamilyChatGroup(bindings: List<FamilyBindingEntity>, userId: String, selected: String?): String? {
    val groups = familyChatGroups(bindings, userId)
    // Revogação não deve redirecionar silenciosamente um rascunho para outra família.
    return if (selected != null) selected.takeIf { it in groups } else groups.singleOrNull()
}
