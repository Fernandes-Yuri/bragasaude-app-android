package br.com.bragasaude.data.local.slm

/** ChatML do GGUF Qwen2; tamanho conservador, com validação final pelo tokenizer nativo. */
object BragaPrompt {
    const val SYSTEM = "Você é o Braga, assistente de autocuidado do aplicativo Braga Saúde. " +
        "Responda em português brasileiro, com frases curtas, diretas e carinhosas, sem emojis. " +
        "Ajude com acolhimento, hidratação e organização de medicação. Nunca diagnostique, prescreva " +
        "ou altere doses. Para sintomas de emergência, oriente procurar socorro e ligar SAMU 192. " +
        "Não afirme que salvou registros: o usuário confirma na tela."
    fun build(history: List<Pair<String, String>>): String {
        val valid = history.filter { it.first == "user" || it.first == "assistant" }
        require(valid.isNotEmpty() && valid.last().first == "user") { "Digite uma mensagem para conversar." }
        require(valid.last().second.length <= 1800) { "Use até 1.800 caracteres por mensagem no Braga local." }
        val selected = mutableListOf<Pair<String, String>>()
        var remaining = 2600
        for (message in valid.takeLast(8).asReversed()) {
            if (message.second.length > remaining) break
            selected.add(0, message)
            remaining -= message.second.length
        }
        while (selected.firstOrNull()?.first == "assistant") selected.removeAt(0)
        fun safe(text: String) = text.replace("<|", "< |")
        return buildString {
            append("<|im_start|>system\n$SYSTEM<|im_end|>\n")
            selected.forEach { (role, text) -> append("<|im_start|>$role\n${safe(text)}<|im_end|>\n") }
            append("<|im_start|>assistant\n")
        }
    }
}
