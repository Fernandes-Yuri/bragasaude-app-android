package br.com.bragasaude.domain

/** Contexto somente da sessão atual. Não grava dados no disco nem cria preferências. */
class VoiceConversationMemory {
    private var owner: String? = null
    private val turns = ArrayDeque<Pair<String, String>>()
    var lastResponse: String = ""
        private set

    fun selectUser(userId: String) {
        if (owner != userId) { clear(); owner = userId }
    }
    fun clear() { turns.clear(); lastResponse = ""; owner = null }
    fun recordUser(text: String) = record("user", text)
    fun recordAssistant(text: String) { lastResponse = text; record("assistant", text) }
    fun snapshot(): List<Pair<String, String>> = turns.toList()
    private fun record(role: String, text: String) {
        if (text.isBlank()) return
        turns.addLast(role to text.take(4000))
        while (turns.size > 20) turns.removeFirst()
    }
}
