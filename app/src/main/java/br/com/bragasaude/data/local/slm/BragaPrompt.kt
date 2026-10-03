package br.com.bragasaude.data.local.slm

/** ChatML do GGUF Qwen2; tamanho conservador, com validação final pelo tokenizer nativo. */
object BragaPrompt {
    const val SYSTEM = "Você é o Braga, assistente pessoal e companheiro diário de saúde. " +
        "Suas respostas são calmas, respeitosas, curtas e sem emojis. " +
        "Fale de igual para igual, sem infantilizar o usuário."
    fun build(history: List<Pair<String, String>>, context: String? = null): String {
        val valid = history.filter { it.first == "user" || it.first == "assistant" }
        require(valid.isNotEmpty() && valid.last().first == "user") { "Digite uma mensagem para conversar." }
        require(valid.last().second.length <= 1000) { "Use até 1.000 caracteres por mensagem no Braga local." }
        val selected = mutableListOf<Pair<String, String>>()
        var remaining = (1200 - (context?.length ?: 0)).coerceAtLeast(1000)
        for (message in valid.takeLast(8).asReversed()) {
            if (message.second.length > remaining) break
            selected.add(0, message)
            remaining -= message.second.length
        }
        while (selected.firstOrNull()?.first == "assistant") selected.removeAt(0)
        fun safe(text: String) = text.replace("<|", "< |")
        return buildString {
            append("<|im_start|>system\n$SYSTEM<|im_end|>\n")
            selected.forEachIndexed { index, (role, text) ->
                append("<|im_start|>$role\n")
                if (index == selected.lastIndex && context != null) {
                    append("[Contexto do Kotlin: ${safe(context)}]\n")
                    append("[Usuário: ${safe(text)}]")
                } else append(safe(text))
                append("<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }
    }
}
