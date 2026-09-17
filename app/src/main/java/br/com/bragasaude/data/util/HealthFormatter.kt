package br.com.bragasaude.data.util

import java.text.NumberFormat
import java.util.Locale

object HealthFormatter {
    private val ptBrLocale = Locale("pt", "BR")
    
    /**
     * Converte uma string de entrada (que pode conter vírgula) para Double de forma segura.
     */
    fun parseDouble(value: String?): Double? {
        if (value.isNullOrBlank()) return null
        return try {
            value.replace(",", ".").toDoubleOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converte uma string de entrada para Float de forma segura.
     */
    fun parseFloat(value: String?): Float? {
        if (value.isNullOrBlank()) return null
        return try {
            value.replace(",", ".").toFloatOrNull()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Formata um valor numérico para exibição amigável (ex: 70,5).
     */
    fun formatNumber(value: Number?): String {
        if (value == null) return ""
        return NumberFormat.getNumberInstance(ptBrLocale).format(value)
    }

    /**
     * Máscara para digitação de data no formato brasileiro DD/MM/AAAA.
     */
    fun formatDateInput(input: String): String {
        val digits = input.filter { it.isDigit() }.take(8)
        return when {
            digits.isEmpty() -> ""
            digits.length <= 2 -> digits
            digits.length <= 4 -> "${digits.take(2)}/${digits.substring(2)}"
            else -> "${digits.take(2)}/${digits.substring(2, 4)}/${digits.substring(4)}"
        }
    }

    /**
     * Valida se uma string é uma data DD/MM/AAAA válida.
     */
    fun isValidDate(dateStr: String?): Boolean {
        if (dateStr.isNullOrBlank()) return true // opcional
        val digits = dateStr.filter { it.isDigit() }
        if (digits.length != 8) return false
        val day = digits.substring(0, 2).toIntOrNull() ?: return false
        val month = digits.substring(2, 4).toIntOrNull() ?: return false
        val year = digits.substring(4, 8).toIntOrNull() ?: return false
        val currentYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
        if (day !in 1..31 || month !in 1..12 || year !in 1900..currentYear) return false
        return true
    }

    /**
     * Máscara para telefone brasileiro: (XX) XXXXX-XXXX ou (XX) XXXX-XXXX
     */
    fun formatPhoneInput(input: String): String {
        val digits = input.filter { it.isDigit() }.take(11)
        return when {
            digits.isEmpty() -> ""
            digits.length <= 2 -> if (digits.length == 2) "($digits) " else "($digits"
            digits.length <= 6 -> "(${digits.take(2)}) ${digits.substring(2)}"
            digits.length <= 10 -> "(${digits.take(2)}) ${digits.substring(2, 6)}-${digits.substring(6)}"
            else -> "(${digits.take(2)}) ${digits.substring(2, 7)}-${digits.substring(7)}"
        }
    }

    /**
     * Valida se o telefone possui DDD e número completo (10 ou 11 dígitos).
     */
    fun isValidPhone(phone: String?): Boolean {
        if (phone.isNullOrBlank()) return true // opcional
        val digits = phone.filter { it.isDigit() }
        return digits.length in 10..11
    }

    /**
     * Converte data ISO (AAAA-MM-DD) ou qualquer formato para exibição DD/MM/AAAA.
     */
    fun formatDisplayDate(dateStr: String?): String {
        if (dateStr.isNullOrBlank()) return ""
        val clean = dateStr.trim()
        if (clean.contains("/")) return clean
        if (clean.contains("-")) {
            val parts = clean.split("-")
            if (parts.size == 3) {
                return "%02d/%02d/%04d".format(
                    parts[2].toIntOrNull() ?: 1,
                    parts[1].toIntOrNull() ?: 1,
                    parts[0].toIntOrNull() ?: 1990
                )
            }
        }
        // Fallback para representações de objeto serializadas como "LocalDate(year=1996, month=8, day=2)" ou "localDate: year 1996..."
        val yearMatch = Regex("""year\s*[:=]?\s*(\d{4})""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toIntOrNull()
        val monthMatch = Regex("""month\s*[:=]?\s*(\d{1,2})""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toIntOrNull()
        val dayMatch = Regex("""day\s*[:=]?\s*(\d{1,2})""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toIntOrNull()
        if (yearMatch != null) {
            return "%02d/%02d/%04d".format(dayMatch ?: 1, monthMatch ?: 1, yearMatch)
        }
        return clean
    }

    /**
     * Converte data DD/MM/AAAA para ISO AAAA-MM-DD.
     */
    fun toIsoDateString(dateStr: String?): String? {
        if (dateStr.isNullOrBlank()) return null
        val clean = dateStr.trim()
        if (clean.contains("/")) {
            val parts = clean.split("/")
            if (parts.size == 3) {
                val day = parts[0].toIntOrNull() ?: return null
                val month = parts[1].toIntOrNull() ?: return null
                val year = parts[2].toIntOrNull() ?: return null
                return "%04d-%02d-%02d".format(year, month, day)
            }
        }
        val yearMatch = Regex("""year\s*[:=]?\s*(\d{4})""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toIntOrNull()
        val monthMatch = Regex("""month\s*[:=]?\s*(\d{1,2})""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toIntOrNull()
        val dayMatch = Regex("""day\s*[:=]?\s*(\d{1,2})""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toIntOrNull()
        if (yearMatch != null) {
            return "%04d-%02d-%02d".format(yearMatch, monthMatch ?: 1, dayMatch ?: 1)
        }
        return clean
    }

    /**
     * Converte string de data (DD/MM/AAAA ou AAAA-MM-DD) para java.time.LocalDate nativo.
     * Substitui a dependência legada do com.google.firebase.dataconnect.LocalDate.
     */
    fun parseLocalDate(str: String?): java.time.LocalDate? {
        if (str.isNullOrBlank()) return null
        val clean = str.trim()
        return try {
            if (clean.contains("/")) {
                val parts = clean.split("/")
                if (parts.size == 3) {
                    java.time.LocalDate.of(parts[2].toInt(), parts[1].toInt(), parts[0].toInt())
                } else null
            } else if (clean.contains("-")) {
                val parts = clean.split("-")
                if (parts.size == 3) {
                    java.time.LocalDate.of(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
                } else null
            } else {
                val yearMatch = Regex("""year\s*[:=]?\s*(\d{4})""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toIntOrNull()
                val monthMatch = Regex("""month\s*[:=]?\s*(\d{1,2})""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toIntOrNull()
                val dayMatch = Regex("""day\s*[:=]?\s*(\d{1,2})""", RegexOption.IGNORE_CASE).find(clean)?.groupValues?.get(1)?.toIntOrNull()
                if (yearMatch != null) {
                    java.time.LocalDate.of(yearMatch, monthMatch ?: 1, dayMatch ?: 1)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Máscara para digitação de horário (HH:mm) sem saltos bruscos de cursor.
     */
    fun formatTimeInput(input: String): String {
        val digits = input.filter { it.isDigit() }.take(4)
        return when {
            digits.isEmpty() -> ""
            digits.length <= 2 -> digits
            else -> "${digits.take(2)}:${digits.substring(2)}"
        }
    }

    /**
     * Normaliza entrada de horário garantindo limites válidos (00:00 a 23:59).
     */
    fun normalizeTime(input: String, defaultTime: String = "22:00"): String {
        val clean = input.trim()
        if (clean.isBlank()) return defaultTime
        val digits = clean.filter { it.isDigit() }
        return when (digits.length) {
            1 -> "0$digits:00"
            2 -> {
                val h = (digits.toIntOrNull() ?: 0).coerceIn(0, 23)
                "%02d:00".format(h)
            }
            3 -> {
                val h = (digits.substring(0, 1).toIntOrNull() ?: 0).coerceIn(0, 23)
                val m = (digits.substring(1).toIntOrNull() ?: 0).coerceIn(0, 59)
                "%02d:%02d".format(h, m)
            }
            4 -> {
                val h = (digits.substring(0, 2).toIntOrNull() ?: 0).coerceIn(0, 23)
                val m = (digits.substring(2, 4).toIntOrNull() ?: 0).coerceIn(0, 59)
                "%02d:%02d".format(h, m)
            }
            else -> if (clean.matches(Regex("^\\d{1,2}:\\d{2}$"))) {
                val parts = clean.split(":")
                val h = (parts[0].toIntOrNull() ?: 0).coerceIn(0, 23)
                val m = (parts[1].toIntOrNull() ?: 0).coerceIn(0, 59)
                "%02d:%02d".format(h, m)
            } else defaultTime
        }
    }
}

