package br.com.bragasaude.ui.chat

internal data class ChatScrollMessage(val id: String, val user: Boolean)

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
    val lastItemOffset: Int = 0,
    val messages: List<ChatScrollMessage> = emptyList(),
    val returnRequest: Int = 0
)

internal data class ChatScrollNavigation(
    val shouldFollow: Boolean = true,
    val showReturnToLatest: Boolean = false,
    val unreadMessages: Int = 0
)

/** Crescimento da lista não é gesto. Só leitura manual suspende o acompanhamento. */
internal class ChatScrollPolicy {
    private var first: String? = null
    private var user: String? = null
    private var history = true
    private var following = true
    private var returnRequest = 0
    private var knownMessages = emptySet<String>()
    private var previousMessages = emptyList<ChatScrollMessage>()
    private val unreadMessages = mutableSetOf<String>()
    private var unreadPartial = false
    private var partialAwaitingCompletion = false

    fun follow(snapshot: ChatScrollSnapshot): Boolean = update(snapshot).shouldFollow

    fun update(snapshot: ChatScrollSnapshot): ChatScrollNavigation {
        val reset = first != snapshot.firstMessage || (history && !snapshot.history) ||
            user != snapshot.latestUser || returnRequest != snapshot.returnRequest
        if (reset) {
            following = true
            unreadMessages.clear()
            unreadPartial = false
            partialAwaitingCompletion = false
        }
        first = snapshot.firstMessage
        user = snapshot.latestUser
        history = snapshot.history
        returnRequest = snapshot.returnRequest
        if (snapshot.dragging) following = snapshot.atBottom
        else if (!snapshot.scrolling && snapshot.atBottom) following = true

        val messagesChanged = previousMessages !== snapshot.messages
        val currentIds = if (messagesChanged) snapshot.messages.map { it.id }.toSet() else knownMessages
        val incoming = if (reset || !messagesChanged) emptyList() else
            snapshot.messages.filter { !it.user && it.id !in knownMessages }
        unreadMessages.retainAll(currentIds)
        incoming.forEachIndexed { index, message ->
            // A resposta parcial e sua mensagem final são a mesma chegada.
            val replacesPartial = index == 0 && partialAwaitingCompletion
            if (!following && (!replacesPartial || unreadPartial)) unreadMessages.add(message.id)
            if (replacesPartial) {
                unreadPartial = false
                partialAwaitingCompletion = false
            }
        }
        val hasPartial = snapshot.streaming && snapshot.partialLength > 0
        if (hasPartial && !partialAwaitingCompletion && incoming.isEmpty()) {
            partialAwaitingCompletion = true
            unreadPartial = !following
        }
        if (!snapshot.streaming) {
            partialAwaitingCompletion = false
            unreadPartial = false
        }
        if (following || snapshot.history) {
            unreadMessages.clear()
            unreadPartial = false
        }
        knownMessages = currentIds
        previousMessages = snapshot.messages
        return ChatScrollNavigation(
            shouldFollow = !snapshot.history && !snapshot.dragging && following,
            showReturnToLatest = !snapshot.history && !snapshot.atBottom && !following,
            unreadMessages = unreadMessages.size + if (unreadPartial) 1 else 0
        )
    }
}
