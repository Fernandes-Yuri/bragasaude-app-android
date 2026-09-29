package br.com.bragasaude.domain

fun communityDisplayName(fullName: String?, nickname: String? = null): String {
    nickname?.trim()?.takeIf { it.isNotEmpty() && it != "null" }?.let { return it }
    val parts = fullName?.trim()?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
    if (parts.isEmpty()) return "Colega de Saúde"
    return if (parts.size == 1) parts.first() else "${parts.first()} ${parts[1].take(1)}."
}
