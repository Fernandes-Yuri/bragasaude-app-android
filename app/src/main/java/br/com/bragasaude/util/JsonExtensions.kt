package br.com.bragasaude.util

import org.json.JSONObject

/**
 * Extensões estritas para leitura segura de JSON (Blindagem Anti-"null" SaMD).
 * Impede que `optString` ou deserializadores retornem a string literal "null", "none" ou "undefined".
 */
fun JSONObject.safeString(key: String): String {
    if (isNull(key)) return ""
    val value = optString(key, "").trim()
    return if (value.equals("null", ignoreCase = true) ||
        value.equals("none", ignoreCase = true) ||
        value.equals("undefined", ignoreCase = true)
    ) "" else value
}

fun JSONObject.safeNullableString(key: String): String? {
    val s = safeString(key)
    return s.ifBlank { null }
}
