package br.com.bragasaude.util

import java.util.Locale

/**
 * Utilitário de normalização fonética para síntese de voz (TTS) em Português do Brasil.
 *
 * Ajusta grafias de nomes próprios com raízes anglo-saxãs, germânicas ou convenções
 * cartorárias brasileiras para garantir que os motores neurais (ex: Faber, Cadu)
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
     * Converte números inteiros (0 a 999.999) para texto por extenso em Português do Brasil.
     */
    fun numberToWords(n: Long): String {
        if (n < 0) return "menos " + numberToWords(-n)
        if (n == 0L) return "zero"
        if (n == 100L) return "cem"

        val units = arrayOf(
            "zero", "um", "dois", "três", "quatro", "cinco", "seis", "sete", "oito", "nove",
            "dez", "onze", "doze", "treze", "quatorze", "quinze", "dezesseis", "dezessete", "dezoito", "dezenove"
        )
        val tens = arrayOf(
            "", "", "vinte", "trinta", "quarenta", "cinquenta", "sessenta", "setenta", "oitenta", "noventa"
        )
        val hundreds = arrayOf(
            "", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos", "seiscentos", "setecentos", "oitocentos", "novecentos"
        )

        if (n < 20) return units[n.toInt()]
        if (n < 100) {
            val ten = (n / 10).toInt()
            val rem = (n % 10).toInt()
            return if (rem == 0) tens[ten] else "${tens[ten]} e ${units[rem]}"
        }
        if (n < 1000) {
            val hundred = (n / 100).toInt()
            val rem = n % 100
            return if (rem == 0L) hundreds[hundred] else "${hundreds[hundred]} e ${numberToWords(rem)}"
        }
        if (n < 1_000_000) {
            val thousands = n / 1000
            val rem = n % 1000
            val thousandStr = if (thousands == 1L) "mil" else "${numberToWords(thousands)} mil"
            if (rem == 0L) return thousandStr
            val sep = if (rem < 100 || rem % 100 == 0L) " e " else ", "
            return "$thousandStr$sep${numberToWords(rem)}"
        }
        return n.toString()
    }

    /**
     * Normaliza datas, horários e números para pronúncia natural em sintetizadores TTS.
     * Ex: "30/09/2026" -> "no dia trinta, do nove de dois mil e vinte e seis"
     */
    fun normalizeDatesAndNumbersForSpeech(text: String): String {
        if (text.isBlank()) return ""
        var result = text

        // 1. Datas completas com ano: "30/09/2026", "em 30/09/2026", "05-10-2025"
        val dateWithYearRegex = Regex("""(?i)(?:(?:em|no dia)\s+)?\b([0-3]?[0-9])[/-](0?[1-9]|1[0-2])[/-](\d{4})\b""")
        result = dateWithYearRegex.replace(result) { match ->
            val dayInt = match.groupValues[1].toLongOrNull() ?: 1L
            val monthInt = match.groupValues[2].toLongOrNull() ?: 1L
            val yearInt = match.groupValues[3].toLongOrNull() ?: 2026L

            val dayWords = if (dayInt == 1L) "primeiro" else numberToWords(dayInt)
            val monthWords = numberToWords(monthInt)
            val yearWords = numberToWords(yearInt)

            "no dia $dayWords, do $monthWords de $yearWords"
        }

        // 2. Datas sem ano: "30/09", "em 30/09"
        val dateWithoutYearRegex = Regex("""(?i)(?:(?:em|no dia)\s+)?\b([0-3]?[0-9])[/-](0?[1-9]|1[0-2])\b""")
        result = dateWithoutYearRegex.replace(result) { match ->
            val dayInt = match.groupValues[1].toLongOrNull() ?: 1L
            val monthInt = match.groupValues[2].toLongOrNull() ?: 1L

            if (dayInt in 1..31 && monthInt in 1..12) {
                val dayWords = if (dayInt == 1L) "primeiro" else numberToWords(dayInt)
                val monthWords = numberToWords(monthInt)
                "no dia $dayWords, do $monthWords"
            } else {
                match.value
            }
        }

        // 3. Horários: "às 14:30", "14:30", "às 08:00"
        val timeRegex = Regex("""(?i)(?:(?:às|as)\s+)?\b([0-1]?[0-9]|2[0-3]):([0-5][0-9])\b""")
        result = timeRegex.replace(result) { match ->
            val hourInt = match.groupValues[1].toLongOrNull() ?: 0L
            val minInt = match.groupValues[2].toLongOrNull() ?: 0L

            when {
                hourInt == 0L && minInt == 0L -> "à meia-noite"
                hourInt == 12L && minInt == 0L -> "ao meio-dia"
                hourInt == 1L && minInt == 0L -> "à uma hora"
                hourInt == 1L -> "à uma e ${numberToWords(minInt)}"
                minInt == 0L -> "às ${numberToWords(hourInt)} horas"
                else -> "às ${numberToWords(hourInt)} e ${numberToWords(minInt)}"
            }
        }

        // 4. Porcentagem: "25%" -> "vinte e cinco por cento"
        val percentRegex = Regex("""\b(\d+)\s*%""")
        result = percentRegex.replace(result) { match ->
            val num = match.groupValues[1].toLongOrNull() ?: 0L
            "${numberToWords(num)} por cento"
        }

        // 5. Pressão arterial "120 por 80" ou "12 por 8"
        val pressureRegex = Regex("""\b(\d{1,3})\s+(?:por|x)\s+(\d{1,3})\b""")
        result = pressureRegex.replace(result) { match ->
            val sys = match.groupValues[1].toLongOrNull() ?: 0L
            val dia = match.groupValues[2].toLongOrNull() ?: 0L
            "${numberToWords(sys)} por ${numberToWords(dia)}"
        }

        // 6. Números inteiros isolados restantes para síntese fonética sem dígitos
        val isolatedNumberRegex = Regex("""\b\d+\b""")
        result = isolatedNumberRegex.replace(result) { match ->
            val num = match.value.toLongOrNull()
            if (num != null) numberToWords(num) else match.value
        }

        return result
    }

    /**
     * Limpa e sanitiza o texto para síntese de voz (TTS).
     * Remove rúbricas, markdown, emojis, repetições e converte datas/números para texto por extenso.
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

        // Converte datas, horários e números para texto por extenso natural para áudio
        clean = normalizeDatesAndNumbersForSpeech(clean)

        // Normalizar espaços e pontuação
        clean = clean.replace(Regex("""\s+"""), " ")
        clean = clean.replace(Regex("""\s+([.,!?:;])"""), "$1")
        return clean.trim()
    }
}

