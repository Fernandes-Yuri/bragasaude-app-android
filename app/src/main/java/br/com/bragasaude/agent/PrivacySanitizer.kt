package br.com.bragasaude.agent

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sanitizador Ativo de Privacidade on-device.
 *
 * Remove ou ofusca informações pessoais sensíveis (PII) como CPFs, RGs,
 * números de Cartão Nacional de Saúde (SUS), telefones, CEPs, endereços de carimbos
 * e e-mails antes de qualquer saída de ferramenta ser enviada ao modelo de IA.
 *
 * Preserva estritamente dados clínicos essenciais: dosagens, posologias,
 * referências laboratoriais, valores pressóricos e códigos CID-10.
 */
@Singleton
class PrivacySanitizer @Inject constructor() {

    companion object {
        const val REDACTED_CPF = "[CPF_OFUSCADO]"
        const val REDACTED_SUS = "[SUS_OFUSCADO]"
        const val REDACTED_RG = "[RG_OFUSCADO]"
        const val REDACTED_PHONE = "[TELEFONE_OFUSCADO]"
        const val REDACTED_CEP = "[CEP_OFUSCADO]"
        const val REDACTED_ADDRESS = "[ENDERECO_OFUSCADO]"
        const val REDACTED_EMAIL = "[EMAIL_OFUSCADO]"

        // 1. CPF: formatado (000.000.000-00) ou rotulado (CPF: 00000000000)
        private val CPF_FORMATTED_REGEX = Regex("""\b\d{3}\.\d{3}\.\d{3}-\d{2}\b""")
        private val CPF_LABELED_REGEX = Regex("""(?i)(\bcpf[:\s#]*)\d{11}\b""")

        // 2. SUS / CNS: 15 dígitos iniciando com 1, 2, 7, 8 ou 9, ou rotulado com "SUS" / "CNS"
        private val SUS_NUMBER_REGEX = Regex("""\b[12789]\d{2}[\s.]\d{4}[\s.]\d{4}[\s.]\d{4}\b""")
        private val SUS_LABELED_REGEX = Regex(
            """(?i)(\b(?:sus|cns|cart[aã]o\s+sus|cart[aã]o\s+nacional\s+de\s+sa[uú]de)[:\s#]*)(\d{15}|\d{3}[\s.]\d{4}[\s.]\d{4}[\s.]\d{4})\b"""
        )

        // 3. RG: formatado (00.000.000-0 ou 0.000.000-X) ou rotulado (RG: 12345678)
        private val RG_FORMATTED_REGEX = Regex("""\b\d{1,2}\.\d{3}\.\d{3}-[0-9A-Za-z]\b""")
        private val RG_LABELED_REGEX = Regex(
            """(?i)(\b(?:rg|identidade|c[eé]dula\s+de\s+identidade)[:\s#]*)([0-9A-Za-z.\-]{6,14})\b"""
        )

        // 4. E-mail
        private val EMAIL_REGEX = Regex("""\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b""")

        // 5. Telefone brasileiro (com DDD ou rotulado)
        private val PHONE_LABELED_REGEX = Regex(
            """(?i)(\b(?:tel|telefone|cel|celular|whatsapp|fone|contato)[:\s#]*)(?:\+?55[\s.-]*)?(?:\(?[1-9]{2}\)?[\s.-]*)?(?:9[\s.-]?[0-9]{4}|[2-8][0-9]{3})[\s.-]?[0-9]{4}\b"""
        )
        private val PHONE_DDD_PARENS_REGEX = Regex(
            """\b(?:\+?55[\s.-]*)?\([1-9]{2}\)[\s.-]?(?:9[\s.-]?[0-9]{4}|[2-8][0-9]{3})[\s.-]?[0-9]{4}\b"""
        )
        private val PHONE_DDD_SPACE_REGEX = Regex(
            """\b(?:\+?55[\s.-]*)?[1-9]{2}[\s.-]+(?:9[\s.-]?[0-9]{4}|[2-8][0-9]{3})[\s.-]?[0-9]{4}\b"""
        )

        // 6. CEP: 00000-000 ou rotulado CEP
        private val CEP_REGEX = Regex("""(?i)\b(?:cep[:\s#]*)?\b\d{5}-\d{3}\b""")

        // 7. Endereço: rotulado ou padrão de logradouro com número
        private val ADDRESS_LABELED_REGEX = Regex("""(?i)(\b(?:endereço|endereco)[:\s#]*)[^\n\r]+""")
        private val ADDRESS_STAMP_REGEX = Regex(
            """(?i)\b(?:rua|r\.|av\.|avenida|alameda|al\.|travessa|trav\.|rodovia|rod\.|praça|praca)\s+[A-Za-zÀ-ÖØ-öø-ÿ0-9\s.,'-]+?(?:,\s*(?:n[º°o]?\s*)?\d+[A-Za-z0-9\s/'-]*)"""
        )
    }

    /**
     * Sanitiza a string de entrada, substituindo dados pessoais por identificadores ofuscados.
     */
    fun sanitize(text: String?): String {
        if (text.isNullOrBlank()) return text.orEmpty()

        var sanitized = text

        // 1. CPF
        sanitized = sanitized.replace(CPF_FORMATTED_REGEX, REDACTED_CPF)
        sanitized = sanitized.replace(CPF_LABELED_REGEX) { match ->
            "${match.groupValues[1]}$REDACTED_CPF"
        }

        // 2. SUS / CNS
        sanitized = sanitized.replace(SUS_NUMBER_REGEX, REDACTED_SUS)
        sanitized = sanitized.replace(SUS_LABELED_REGEX) { match ->
            "${match.groupValues[1]}$REDACTED_SUS"
        }

        // 3. RG
        sanitized = sanitized.replace(RG_FORMATTED_REGEX, REDACTED_RG)
        sanitized = sanitized.replace(RG_LABELED_REGEX) { match ->
            "${match.groupValues[1]}$REDACTED_RG"
        }

        // 4. E-mail
        sanitized = sanitized.replace(EMAIL_REGEX, REDACTED_EMAIL)

        // 5. Telefones
        sanitized = sanitized.replace(PHONE_LABELED_REGEX) { match ->
            "${match.groupValues[1]}$REDACTED_PHONE"
        }
        sanitized = sanitized.replace(PHONE_DDD_PARENS_REGEX, REDACTED_PHONE)
        sanitized = sanitized.replace(PHONE_DDD_SPACE_REGEX, REDACTED_PHONE)

        // 6. CEP
        sanitized = sanitized.replace(CEP_REGEX, REDACTED_CEP)

        // 7. Endereços
        sanitized = sanitized.replace(ADDRESS_LABELED_REGEX) { match ->
            "${match.groupValues[1]}$REDACTED_ADDRESS"
        }
        sanitized = sanitized.replace(ADDRESS_STAMP_REGEX, REDACTED_ADDRESS)

        return sanitized
    }
}
