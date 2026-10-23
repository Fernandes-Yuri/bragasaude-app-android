package br.com.bragasaude.data.avatar

import java.text.Normalizer
import java.util.Locale

data class AvatarVisualFindings(val faceCount: Int, val recognizedText: String)

/** Pré-checagem de formato/pessoa/documentos. Não classifica nudez ou violência. */
object AvatarPhotoRules {
    fun rejection(width: Int, height: Int, findings: AvatarVisualFindings): String? {
        if (minOf(width, height) < 64) return "A foto está muito pequena. Escolha uma imagem com mais resolução."
        val text = Normalizer.normalize(findings.recognizedText, Normalizer.Form.NFD)
            .replace("\\p{M}+".toRegex(), "").replace("\\s+".toRegex(), " ").uppercase(Locale.ROOT)
        val documentLabels = listOf("REGISTRO GERAL", "REPUBLICA FEDERATIVA", "CARTEIRA DE IDENTIDADE",
            "CARTEIRA NACIONAL DE HABILITACAO", "DOCUMENTO DE IDENTIDADE")
        val hasDocument = text.contains("CARTEIRA NACIONAL DE HABILITACAO") ||
            documentLabels.count { text.contains(it) } >= 2 ||
            (Regex("\\bCPF\\b").containsMatchIn(text) && Regex("(?<!\\d)\\d{3}[.\\s]?\\d{3}[.\\s]?\\d{3}[-\\s]?\\d{2}(?!\\d)")
                .findAll(text).any { validCpf(it.value.filter(Char::isDigit)) })
        if (hasDocument) return "Esta imagem parece um documento com dados pessoais. Escolha uma foto de perfil sem documentos."
        if (findings.faceCount < 1) return "Não encontrei um rosto nítido. Escolha outra foto ou use um dos avatares do perfil."
        return null
    }

    private fun validCpf(value: String): Boolean {
        if (value.length != 11 || value.toSet().size == 1) return false
        fun digit(length: Int): Int {
            val sum = (0 until length).sumOf { value[it].digitToInt() * (length + 1 - it) }
            return ((sum * 10) % 11).let { if (it == 10) 0 else it }
        }
        return digit(9) == value[9].digitToInt() && digit(10) == value[10].digitToInt()
    }
}
