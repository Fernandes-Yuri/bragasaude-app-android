package br.com.bragasaude.domain

import br.com.bragasaude.data.local.FamilyMessageEntity

object FamilyMessagePresentation {
    fun preview(message: FamilyMessageEntity): String = when {
        message.awaitingDecryption || message.messageText.isBlank() -> "Mensagem indisponível neste aparelho. Abra a conversa para conferir."
        else -> "“${message.messageText}”"
    }

    fun status(unread: Int, messages: List<FamilyMessageEntity>): String {
        val unavailable = messages.count { it.awaitingDecryption || it.messageText.isBlank() }
        return listOfNotNull(
            if (unread > 0) "$unread não lida${if (unread > 1) "s" else ""}" else "Nenhuma mensagem legível pendente",
            unavailable.takeIf { it > 0 }?.let { "$it ${if (it > 1) "indisponíveis" else "indisponível"}" }
        ).joinToString(" • ")
    }
}
