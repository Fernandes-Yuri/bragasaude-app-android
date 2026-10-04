package br.com.bragasaude.ai

/** Retém palavras parciais e entrega frases completas ao sintetizador on-device. */
internal class BragaSpeechBuffer {
    private val pending = StringBuilder()
    fun append(delta: String): List<String> {
        pending.append(delta)
        val sentences = mutableListOf<String>()
        while (true) {
            val end = Regex("[.!?](?=\\s)").find(pending)?.range?.last ?: break
            val sentence = pending.substring(0, end + 1).trim()
            pending.delete(0, end + 1)
            if (sentence.isNotBlank()) sentences.add(sentence)
        }
        return sentences
    }
    fun finish(): String = pending.toString().trim().also { pending.clear() }
}
