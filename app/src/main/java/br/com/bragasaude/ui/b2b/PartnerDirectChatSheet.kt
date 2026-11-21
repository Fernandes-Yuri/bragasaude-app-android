package br.com.bragasaude.ui.b2b

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.b2b.B2BOnboardingValidator
import br.com.bragasaude.domain.b2b.B2BThemeEngine
import br.com.bragasaude.domain.b2b.DirectChatMessageDto
import br.com.bragasaude.domain.b2b.DirectChatThreadPageDto
import br.com.bragasaude.domain.b2b.DirectMessageSenderRole
import br.com.bragasaude.domain.b2b.ThreadHandlingMode
import br.com.bragasaude.ui.components.BragaBottomSheet
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ptBrLocale: Locale = Locale.forLanguageTag("pt-BR")

private val directMsgClockFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", ptBrLocale)

private val directChatDayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM/yyyy", ptBrLocale)

private fun formatDirectMsgClock(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(directMsgClockFormatter)
}.getOrElse {
    // Fallback humano (dd/MM/yyyy às HH:mm) em vez de despejar o ISO cru na bolha.
    B2BTextFormatters.formatIsoDateTime(iso).orEmpty()
}

/** Itens da linha do tempo do chat: separador de dia ou mensagem. */
internal sealed interface DirectChatTimelineItem {
    data class DaySeparator(val label: String) : DirectChatTimelineItem
    data class Message(val message: DirectChatMessageDto) : DirectChatTimelineItem
}

internal fun directChatDayLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Hoje"
    today.minusDays(1) -> "Ontem"
    else -> date.format(directChatDayFormatter)
}

internal fun directChatDateOrNull(
    raw: String?,
    zone: ZoneId = ZoneId.systemDefault()
): LocalDate? {
    val clean = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { Instant.parse(clean).atZone(zone).toLocalDate() }
        .getOrElse {
            runCatching { OffsetDateTime.parse(clean).atZoneSameInstant(zone).toLocalDate() }
                .getOrElse {
                    runCatching { LocalDateTime.parse(clean).toLocalDate() }
                        .getOrElse { runCatching { LocalDate.parse(clean) }.getOrNull() }
                }
        }
}

/**
 * Agrupa mensagens (já em ordem cronológica) em separadores "Hoje", "Ontem" ou data.
 * Mensagens sem timestamp legível ficam sob um separador próprio, sem desaparecer.
 */
internal fun buildDirectChatTimeline(
    messages: List<DirectChatMessageDto>,
    today: LocalDate = LocalDate.now(ZoneId.systemDefault()),
    zone: ZoneId = ZoneId.systemDefault()
): List<DirectChatTimelineItem> {
    val items = mutableListOf<DirectChatTimelineItem>()
    var lastDate: LocalDate? = null
    var undatedHeaderEmitted = false
    messages.forEach { message ->
        val date = directChatDateOrNull(message.createdAt, zone)
        if (date == null) {
            if (!undatedHeaderEmitted) {
                items += DirectChatTimelineItem.DaySeparator("Mensagens sem data")
                undatedHeaderEmitted = true
                lastDate = null
            }
        } else if (date != lastDate) {
            items += DirectChatTimelineItem.DaySeparator(directChatDayLabel(date, today))
            lastDate = date
            undatedHeaderEmitted = false
        }
        items += DirectChatTimelineItem.Message(message)
    }
    return items
}

private data class PendingDirectSend(val body: String, val knownMessageIds: Set<String>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerDirectChatBottomSheet(
    page: DirectChatThreadPageDto,
    isOwnerView: Boolean,
    counterpartTitle: String,
    isLoadingMore: Boolean,
    isSending: Boolean,
    isUpdatingHandoff: Boolean = false,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softSurface: Color,
    onLoadOlder: () -> Unit,
    onSendMessage: (String) -> Unit,
    onHandoffModeChange: (String) -> Unit = {},
    onRequestHuman: () -> Unit = {},
    sendErrorMessage: String? = null,
    onRetrySend: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var draft by rememberSaveable(page.programId, page.thread?.patientUserId) {
        mutableStateOf("")
    }
    var pendingSend by remember { mutableStateOf<PendingDirectSend?>(null) }
    val orderedMessages = remember(page.messages) {
        page.messages.asReversed()
    }
    val today = remember { LocalDate.now(ZoneId.systemDefault()) }
    val timeline = remember(orderedMessages, today) {
        buildDirectChatTimeline(orderedMessages, today)
    }
    val sendBlockedOffHours = !isOwnerView &&
        page.isOffHoursNow &&
        !page.directChatPolicy.allowOffHoursQueue
    val maxMessageChars = B2BOnboardingValidator.MAX_DIRECT_MESSAGE_CHARS
    val charCount = draft.length

    // Mantém o rascunho enquanto o envio não é confirmado na lista: em falha
    // (snackbar do scaffold) o texto continua no campo para uma nova tentativa.
    LaunchedEffect(isSending, page.messages) {
        val pending = pendingSend ?: return@LaunchedEffect
        val confirmed = page.messages.any { message ->
            message.messageId !in pending.knownMessageIds && message.body.trim() == pending.body
        }
        if (!isSending && confirmed) {
            draft = ""
            pendingSend = null
        }
    }

    BragaBottomSheet(onDismissRequest = { if (!isSending) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SupportAgent,
                    contentDescription = "Canal direto do programa",
                    tint = brandAccent
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = counterpartTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Atendimento: ${page.directChatPolicy.serviceHoursLabel} · ${page.directChatPolicy.responseSlaLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            val darkNotice = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = if (darkNotice) Color(0xFF472600) else Color(0xFFFFF3E0),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
            ) {
                Text(
                    text = B2BThemeEngine.DIRECT_CHAT_NON_URGENCY_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (darkNotice) Color(0xFFFFDBAC) else Color(0xFF713900),
                    modifier = Modifier.padding(10.dp)
                )
            }

            val currentMode = page.thread?.parsedHandlingMode ?: ThreadHandlingMode.AI_ACTIVE
            if (page.aiReceptionistEnabled) {
                if (page.aiPaused) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = "Recepcionista IA pausada pela moderação Braga. O canal humano segue normalmente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                } else if (isOwnerView) {
                    val isHuman = currentMode == ThreadHandlingMode.HUMAN_ACTIVE
                    val isWaiting = currentMode == ThreadHandlingMode.WAITING_HUMAN
                    val nextMode = if (isHuman) "AI_ACTIVE" else "HUMAN_ACTIVE"
                    val modeHeadline = when (currentMode) {
                        ThreadHandlingMode.HUMAN_ACTIVE -> "Você assumiu este chat"
                        ThreadHandlingMode.WAITING_HUMAN -> "Participante pediu atendente humano"
                        ThreadHandlingMode.AI_ACTIVE -> "Recepcionista IA ativa (${page.aiReceptionistPolicy.personaName})"
                    }
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (isWaiting) brandPrimary.copy(alpha = 0.14f) else softSurface,
                        border = BorderStroke(1.dp, brandAccent.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = modeHeadline,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = brandAccent
                                )
                                page.thread?.handoffNote?.takeIf { it.isNotBlank() }?.let { note ->
                                    Text(
                                        text = note,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = { onHandoffModeChange(nextMode) },
                                enabled = !isUpdatingHandoff,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                if (isUpdatingHandoff) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = brandAccent
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = if (isUpdatingHandoff) {
                                        "Atualizando..."
                                    } else if (isHuman) {
                                        "Devolver para IA"
                                    } else {
                                        "Assumir atendimento"
                                    }
                                )
                            }
                        }
                    }
                } else {
                    when (currentMode) {
                        ThreadHandlingMode.AI_ACTIVE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Atendimento inicial: Assistente Virtual · ${page.aiReceptionistPolicy.personaName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = brandAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = onRequestHuman,
                                    enabled = !isUpdatingHandoff,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text(if (isUpdatingHandoff) "Enviando..." else "Pedir atendimento humano")
                                }
                            }
                        }
                        ThreadHandlingMode.WAITING_HUMAN -> {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = brandPrimary.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = "Solicitação de atendimento humano enviada. A equipe responderá nesta conversa.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = brandAccent,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                        ThreadHandlingMode.HUMAN_ACTIVE -> Unit
                    }
                }
            }

            if (!isOwnerView && page.isOffHoursNow) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                ) {
                    Text(
                        text = if (page.directChatPolicy.allowOffHoursQueue) {
                            "Fora do horário de atendimento agora — sua mensagem entra na fila aguardando retorno no próximo expediente."
                        } else {
                            "Canal fora do horário de atendimento configurado (${page.directChatPolicy.serviceHoursLabel}). Envio pausado até a reabertura."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (page.hasMore) {
                OutlinedButton(
                    onClick = onLoadOlder,
                    enabled = !isLoadingMore,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isLoadingMore) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = brandAccent
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isLoadingMore) "Carregando anteriores..." else "Carregar mensagens anteriores")
                }
            }

            if (orderedMessages.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = softSurface
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Conversa ainda não iniciada",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Envie a primeira mensagem para orientar o participante sobre a rotina. Este canal é assíncrono.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    timeline.forEach { item ->
                        when (item) {
                            is DirectChatTimelineItem.DaySeparator -> DirectChatDaySeparator(item.label)
                            is DirectChatTimelineItem.Message -> DirectChatMessageRow(
                                message = item.message,
                                isOwnerView = isOwnerView,
                                counterpartTitle = counterpartTitle,
                                programName = page.programName,
                                personaName = page.aiReceptionistPolicy.personaName,
                                brandPrimary = brandPrimary,
                                brandOnPrimary = brandOnPrimary,
                                brandAccent = brandAccent,
                                softSurface = softSurface
                            )
                        }
                    }
                }
            }

            // Rodapé único: "salvas apenas neste aparelho" + "apenas texto" (evita avisos empilhados).
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = B2BThemeEngine.LOCAL_CHAT_DEVICE_ONLY_NOTICE,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = B2BThemeEngine.DIRECT_CHAT_ZERO_ATTACHMENT_HINT,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = draft,
                onValueChange = { if (it.length <= maxMessageChars) draft = it },
                label = { Text("Escreva sua mensagem") },
                supportingText = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("$charCount de $maxMessageChars caracteres")
                        if (charCount >= maxMessageChars) {
                            Text(
                                text = "Limite atingido",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                enabled = !isSending && !sendBlockedOffHours,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            if (!sendErrorMessage.isNullOrBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Assertive },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = sendErrorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (onRetrySend != null) {
                            TextButton(
                                onClick = onRetrySend,
                                enabled = !isSending && draft.isNotBlank(),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Tentar novamente", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (isSending) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = brandAccent
                    )
                    Text(
                        text = "Enviando sua mensagem…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSending,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Fechar")
                }
                Button(
                    onClick = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotEmpty()) {
                            pendingSend = PendingDirectSend(
                                body = trimmed,
                                knownMessageIds = page.messages.map { it.messageId }.toSet()
                            )
                            onSendMessage(trimmed)
                        }
                    },
                    enabled = draft.isNotBlank() && !isSending && !sendBlockedOffHours,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
                    if (isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = brandOnPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isSending) "Enviando..." else "Enviar", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun DirectChatDaySeparator(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }
}

@Composable
private fun DirectChatMessageRow(
    message: DirectChatMessageDto,
    isOwnerView: Boolean,
    counterpartTitle: String,
    programName: String,
    personaName: String,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softSurface: Color
) {
    val role = message.parsedRole
    val isAi = role == DirectMessageSenderRole.AI_RECEPTIONIST
    val isOwnMessage = if (isOwnerView) {
        role == DirectMessageSenderRole.PARTNER
    } else {
        role == DirectMessageSenderRole.PATIENT
    }
    val baseSenderLabel = when (role) {
        DirectMessageSenderRole.PARTNER -> programName.ifBlank { role.labelPtBr }
        DirectMessageSenderRole.PATIENT -> if (isOwnerView) counterpartTitle else "Você"
        DirectMessageSenderRole.AI_RECEPTIONIST -> "Assistente Virtual · $personaName"
    }
    val senderLabel = if (
        isOwnMessage &&
        !baseSenderLabel.startsWith("Você", ignoreCase = true)
    ) {
        "$baseSenderLabel · você"
    } else {
        baseSenderLabel
    }
    val avatarLabel = when (role) {
        DirectMessageSenderRole.PARTNER -> programName.ifBlank { baseSenderLabel }
        DirectMessageSenderRole.PATIENT -> if (isOwnerView) counterpartTitle else "Você"
        DirectMessageSenderRole.AI_RECEPTIONIST -> "IA"
    }
    val bubbleColor = when {
        isAi -> softSurface
        isOwnMessage -> brandPrimary.copy(alpha = 0.14f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val bubbleBorder = when {
        isAi -> BorderStroke(1.dp, brandAccent.copy(alpha = 0.35f))
        isOwnMessage -> BorderStroke(1.dp, brandPrimary.copy(alpha = 0.35f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (!isOwnMessage) {
            DirectChatAvatar(
                role = role,
                label = avatarLabel,
                brandPrimary = brandPrimary,
                brandOnPrimary = brandOnPrimary,
                brandAccent = brandAccent
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = if (isOwnMessage) Alignment.End else Alignment.Start
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .semantics(mergeDescendants = true) {},
                shape = RoundedCornerShape(12.dp),
                color = bubbleColor,
                border = bubbleBorder
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isAi) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = brandAccent.copy(alpha = 0.18f)
                            ) {
                                Text(
                                    text = "IA",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = brandAccent,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(
                            text = senderLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = brandAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatDirectMsgClock(message.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        if (isOwnMessage) {
            DirectChatAvatar(
                role = role,
                label = avatarLabel,
                brandPrimary = brandPrimary,
                brandOnPrimary = brandOnPrimary,
                brandAccent = brandAccent
            )
        }
    }
}

@Composable
private fun DirectChatAvatar(
    role: DirectMessageSenderRole,
    label: String,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color
) {
    val isAi = role == DirectMessageSenderRole.AI_RECEPTIONIST
    val container = when {
        isAi -> brandAccent.copy(alpha = 0.16f)
        role == DirectMessageSenderRole.PARTNER -> brandPrimary
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val content = when {
        isAi -> brandAccent
        role == DirectMessageSenderRole.PARTNER -> brandOnPrimary
        else -> brandAccent
    }
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(container),
        contentAlignment = Alignment.Center
    ) {
        if (isAi) {
            Icon(
                imageVector = Icons.Default.SupportAgent,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = content
            )
        } else {
            Text(
                text = label.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = content
            )
        }
    }
}
