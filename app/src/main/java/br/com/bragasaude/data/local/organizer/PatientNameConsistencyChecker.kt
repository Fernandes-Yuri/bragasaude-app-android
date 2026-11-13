package br.com.bragasaude.data.local.organizer

import java.text.Normalizer

object PatientNameConsistencyChecker {
    private fun normalized(name: String): String = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .replace(Regex("[^a-z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun distinctNames(names: List<String>): List<String> = names.map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinctBy(::normalized)
        .sortedBy(::normalized)

    fun divergentNames(names: List<String>): List<String> = distinctNames(names)

    fun divergentDocumentNames(documents: List<OrganizerDocument>): List<String> =
        divergentNames(documents.flatMap { it.patientNames })
}
