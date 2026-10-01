package br.com.bragasaude.domain

/** Quebra inclusive identificadores sem espaços, sem cortar pares Unicode. */
object PdfTextLayout {
    fun wrap(text: String, maxWidth: Float, measure: (String) -> Float): List<String> {
        require(maxWidth > 0)
        val lines = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            var line = ""
            paragraph.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (measure(candidate) <= maxWidth) {
                    line = candidate
                } else {
                    if (line.isNotEmpty()) lines.add(line)
                    line = ""
                    var offset = 0
                    while (offset < word.length) {
                        val end = offset + Character.charCount(word.codePointAt(offset))
                        val character = word.substring(offset, end)
                        if (line.isNotEmpty() && measure(line + character) > maxWidth) {
                            lines.add(line)
                            line = ""
                        }
                        line += character
                        offset = end
                    }
                }
            }
            lines.add(line)
        }
        return lines
    }
}
