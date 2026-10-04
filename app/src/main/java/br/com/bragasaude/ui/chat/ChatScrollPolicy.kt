package br.com.bragasaude.ui.chat

internal data class ChatScrollSnapshot(
    val firstMessage: String?,
    val latestUser: String?,
    val messageCount: Int,
    val partialLength: Int = 0,
    val streaming: Boolean = false,
    val history: Boolean = false,
    val dragging: Boolean = false,
    val atBottom: Boolean = true,
    val scrolling: Boolean = false,
    val itemCount: Int = messageCount + 2,
    val viewportEnd: Int = 0,
    val lastItemOffset: Int = 0
)

/** Crescimento da lista não é gesto. Só leitura manual suspende o acompanhamento. */
internal class ChatScrollPolicy {
    private var first: String? = null
    private var user: String? = null
    private var history = true
    private var following = true

    fun follow(snapshot: ChatScrollSnapshot): Boolean {
        if (first != snapshot.firstMessage || (history && !snapshot.history) || user != snapshot.latestUser) {
            following = true
        }
        first = snapshot.firstMessage
        user = snapshot.latestUser
        history = snapshot.history
        if (snapshot.dragging) following = snapshot.atBottom
        else if (!snapshot.scrolling && snapshot.atBottom) following = true
        return !snapshot.history && !snapshot.dragging && following
    }
}
