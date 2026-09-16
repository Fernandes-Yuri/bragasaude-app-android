package br.com.bragasaude.data.util

object LgpdSanitizer {

    // Regex para CPF com ou sem pontuação (ex: 123.456.789-00 ou 12345678900)
    private val CPF_REGEX = Regex("""\b\d{3}\.?\d{3}\.?\d{3}-?\d{2}\b""")

    // Regex para números de telefone/WhatsApp brasileiros (ex: (11) 98765-4321, 11987654321, +55 11 98765-4321)
    private val PHONE_REGEX = Regex("""(?:\+?55\s?)?(?:\(?0?[1-9]{2}\)?\s?)?(?:9\s?)?\d{4}[-\s]?\d{4}\b""")

    /**
     * Mascara automaticamente dados pessoais sensíveis (CPF, telefone) para conformidade com a LGPD
     * antes de persistir em logs ou bancos de feedbacks gerais.
     */
    fun sanitize(text: String): String {
        if (text.isBlank()) return text

        var sanitized = text

        // Mascara CPF -> ***.***.***-**
        sanitized = CPF_REGEX.replace(sanitized) { "***.***.***-**" }

        // Mascara Telefone -> (XX) 9****-****
        sanitized = PHONE_REGEX.replace(sanitized) { matchResult ->
            val value = matchResult.value.trim()
            if (value.length >= 8) {
                "(XX) 9****-****"
            } else {
                value
            }
        }

        return sanitized
    }
}
