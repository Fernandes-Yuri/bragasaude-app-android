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

/**
 * Leitura tolerante a divergência de contrato entre servidores (Welisa <-> Yuri):
 * o Go braga-chat serializa `patientId`/`senderRole` em camelCase enquanto o
 * gateway Python usa `patient_id`/`sender_role` em snake_case. Tenta cada
 * variante na ordem informada e devolve "" quando nenhuma existir.
 */
fun JSONObject.safeEither(vararg keys: String): String {
    for (key in keys) {
        if (!isNull(key)) {
            val value = safeString(key)
            if (value.isNotEmpty()) return value
        }
    }
    return ""
}

/** Variante nullable de [safeEither] (devolve null quando todas as variantes estão ausentes/vazias). */
fun JSONObject.safeNullableEither(vararg keys: String): String? = safeEither(*keys).ifBlank { null }

/** Leitura booleana tolerante a caminhada de nomes (ex.: `is_read` / `isRead`). */
fun JSONObject.safeEitherBoolean(default: Boolean, vararg keys: String): Boolean {
    for (key in keys) {
        if (!isNull(key)) return optBoolean(key, default)
    }
    return default
}
