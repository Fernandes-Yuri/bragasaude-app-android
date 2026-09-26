package br.com.bragasaude.ui.family

import br.com.bragasaude.data.local.FamilyBindingEntity

/**
 * Modelo de UI para a lista de conversas da família (Inbox).
 * 
 * Modela tanto conversas onde o usuário é cuidador (ex: Mãe),
 * quanto círculos onde o usuário é acompanhado por familiares (ex: Irmã/Filho).
 */
data class FamilyConversationUi(
    val patientId: String,
    val title: String,
    val subtitle: String,
    val relationLabel: String,
    val isCaredByMe: Boolean,
    val otherUserId: String? = null,
    val avatarLetter: String = "F",
    val lastMessageText: String? = null,
    val lastMessageSender: String? = null,
    val lastMessageTimeMs: Long? = null,
    val unreadCount: Int = 0,
    val activeBindings: List<FamilyBindingEntity> = emptyList()
)
