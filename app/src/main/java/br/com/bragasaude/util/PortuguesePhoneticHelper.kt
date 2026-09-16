package br.com.bragasaude.util

import java.util.Locale

/**
 * Utilitário de normalização fonética para síntese de voz (TTS) em Português do Brasil.
 *
 * Ajusta grafias de nomes próprios com raízes anglo-saxãs, germânicas ou convenções
 * cartorárias brasileiras para garantir que os motores neurais (ex: Faber, Cadu, Edresson) garantam...
 * pronunciem com a cadência, estresse tônico e sonoridade nativos do Brasil.
 */
object PortuguesePhoneticHelper {

    /**
     * Extrai apenas o primeiro nome do usuário para uso afetivo e conversacional.
     * Ex: "Welisa Ferreira da Silva" -> "Welisa"
     */
    fun extractFirstName(fullName: String?): String {
        if (fullName.isNullOrBlank()) return ""
        return fullName.trim().split(Regex("\\s+")).firstOrNull()?.replace(Regex("[^a-zA-ZáàâãéèêíïóôõöúçñÁÀÂÃÉÈÊÍÏÓÔÕÖÚÇÑ]"), "") ?: ""
    }

    /**
     * Converte o primeiro nome para uma grafia foneticamente amigável para motores de TTS.
     *
     * Regras fonéticas brasileiras:
     * 1. W inicial:
     *    - 'W' + 'el'/'es'/'ell'/'ill' -> som de U com acentuação tônica (Welisa -> Uélisa, Wesley -> Uéslei, William -> Uíliam)
     *    - 'W' + 'ag'/'alt' -> som de V tradicional (Wagner -> Vágner, Walter -> Válter)
     * 2. Y inicial:
     *    - 'Y' -> I com acento tônico onde necessário (Yuri -> Iúri, Yasmin -> Iasmin, Ygor -> Ígor)
     * 3. Dígrafos estrangeiros:
     *    - 'Th' -> 'T' (Thiago -> Tiago, Thais -> Taís)
     *    - 'Ph' -> 'F' (Raphael -> Rafael, Sophia -> Sofia)
     *    - Consoantes duplicadas no final (Kelly -> Kéli, Jenny -> Jéni)
     */
    fun toTtsFriendlyName(name: String?): String {
        val raw = extractFirstName(name)
        if (raw.isBlank()) return ""

        val lower = raw.lowercase(Locale("pt", "BR"))

        // Mapeamentos específicos diretos de altíssima frequência no Brasil
        val directMap = mapOf(
            "welisa" to "Uélisa",
            "welissa" to "Uélisa",
            "wesley" to "Uéslei",
            "weslei" to "Uéslei",
            "weslley" to "Uéslei",
            "william" to "Uíliam",
            "wiliam" to "Uíliam",
            "williams" to "Uíliams",
            "wellington" to "Uélinton",
            "welinton" to "Uélinton",
            "wallace" to "Uólas",
            "walace" to "Uólas",
            "washington" to "Uóshington",
            "wagner" to "Vágner",
            "walter" to "Válter",
            "waldir" to "Valdir",
            "waldemar" to "Valdemar",
            "yuri" to "Iúri",
            "iuri" to "Iúri",
            "ygor" to "Ígor",
            "igor" to "Ígor",
            "yasmin" to "Iasmin",
            "yasmim" to "Iasmim",
            "yolanda" to "Iolanda",
            "yara" to "Iara",
            "thiago" to "Tiago",
            "tiago" to "Tiago",
            "thais" to "Taís",
            "thaís" to "Taís",
            "matheus" to "Mateus",
            "mateus" to "Mateus",
            "raphael" to "Rafael",
            "rafael" to "Rafael",
            "sophia" to "Sofia",
            "sofia" to "Sofia",
            "kelly" to "Kéli",
            "kely" to "Kéli",
            "gabrielly" to "Gabriéli",
            "gabriely" to "Gabriéli",
            "isabelle" to "Isabele",
            "isabelly" to "Isabéli"
        )

        directMap[lower]?.let { return it }

        // Regras genéricas para nomes com prefixo 'W' ou 'Y'
        var normalized = raw
        if (normalized.startsWith("W", ignoreCase = true)) {
            val rest = normalized.substring(1)
            normalized = when {
                rest.startsWith("e", ignoreCase = true) -> "Ué" + rest.substring(1)
                rest.startsWith("i", ignoreCase = true) -> "Uí" + rest.substring(1)
                rest.startsWith("a", ignoreCase = true) -> "Ua" + rest.substring(1)
                rest.startsWith("o", ignoreCase = true) -> "Uo" + rest.substring(1)
                else -> "U" + rest
            }
        } else if (normalized.startsWith("Y", ignoreCase = true)) {
            val rest = normalized.substring(1)
            normalized = "I" + rest
        }

        // Substituição de dígrafos 'Th' -> 'T', 'Ph' -> 'F'
        normalized = normalized
            .replace(Regex("(?i)th"), "t")
            .replace(Regex("(?i)ph"), "f")
            .replace(Regex("(?i)lly$"), "li")
            .replace(Regex("(?i)ly$"), "li")
            .replace(Regex("(?i)y$"), "i")

        return normalized
    }

    /**
     * Limpa e sanitiza o texto para síntese de voz (TTS).
     * Remove:
     * - Rúbricas de interpretação / stage directions: *sorrindo*, (pausa), [risos]
     * - Emojis e símbolos
     * - Markdown: **, *, _, `, #, ~
     * - Onomatopeias com repetição de letras que causam delírio de soletração: "hummm", "ahhh", "kkk"
     * - Letras repetidas 3+ vezes consecutivas (ex: "oiiii" -> "oi")
     */
    fun cleanTextForTts(rawText: String): String {
        if (rawText.isBlank()) return ""
        var clean = rawText
        // Remove rúbricas/stage directions como *sorrindo*, (pausa), [suspiro]
        clean = clean.replace(Regex("""\*.*?\*"""), " ")
        clean = clean.replace(Regex("""\(.*?\)"""), " ")
        clean = clean.replace(Regex("""\[.*?\]"""), " ")
        // Remove markdown
        clean = clean.replace(Regex("""[#*_`~|>]"""), "")
        // Remove URLs
        clean = clean.replace(Regex("""https?://\S+|www\.\S+"""), "")
        // Remove emojis e símbolos pictográficos Unicode
        clean = clean.replace(Regex("""[\p{So}\p{Sk}\p{Cs}\p{Cn}]"""), "")
        // Remove onomatopeias e repetições que o TTS soletra como letras soltas
        clean = clean.replace(Regex("""(?i)\bhu[m]+\b"""), "")
        clean = clean.replace(Regex("""(?i)\bh+[m]+\b"""), "")
        clean = clean.replace(Regex("""(?i)\bah+[h]+\b"""), "")
        clean = clean.replace(Regex("""(?i)\b(k{2,}|rs{2,}|haha+)\b"""), "")
        // Reduz repetições excessivas de caracteres (ex: "oiiii" -> "oi")
        clean = clean.replace(Regex("""(.)\1{2,}"""), "$1")
        // Normalizar espaços e pontuação
        clean = clean.replace(Regex("""\s+"""), " ")
        clean = clean.replace(Regex("""\s+([.,!?:;])"""), "$1")
        return clean.trim()
    }
}

