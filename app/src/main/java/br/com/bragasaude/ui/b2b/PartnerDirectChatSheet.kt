package br.com.bragasaude.ui.b2b

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.b2b.B2BOnboardingValidator
import br.com.bragasaude.domain.b2b.B2BThemeEngine
import br.com.bragasaude.domain.b2b.DirectChatThreadPageDto
import br.com.bragasaude.domain.b2b.DirectMessageSenderRole
import br.com.bragasaude.domain.b2b.ThreadHandlingMode
import br.com.bragasaude.ui.components.BragaBottomSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val directMsgTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale("pt", "BR"))

private fun formatDirectMsgTime(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(directMsgTimeFormatter)
}.getOrElse {
    // Fallback humano (dd/MM/yyyy às HH:mm) em vez de despejar o ISO cru na bolha.
    B2BTextFormatters.formatIsoDateTime(iso).orEmpty()
}

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
    onDismiss: () -> Unit
) {
    var draft by rememberSaveable(page.programId, page.thread?.patientUserId) {
        mutableStateOf("")
    }
    val orderedMessages = remember(page.messages) {
        page.messages.asReversed()
    }
    val sendBlockedOffHours = !isOwnerView &&
        page.isOffHoursNow &&
        !page.directChatPolicy.allowOffHoursQueue

    BragaBottomSheet(onDismissRequest = { if (!isSending) onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.SupportAgent, contentDescription = null, tint = brandAccent)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = counterpartTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
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
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (isUpdatingHandoff) {
                                        "..."
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
                                    shape = RoundedCornerShape(10.dp)
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isLoadingMore) "Carregando anteriores..." else "Carregar mensagens anteriores")
                }
            }

            if (orderedMessages.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = softSurface
                ) {
                    Text(
                        text = "Nenhuma mensagem enviada neste canal ainda. Use o campo abaixo para iniciar a conversa de rotina.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    orderedMessages.forEach { msg ->
                        val role = msg.parsedRole
                        val isOwnMessage = if (isOwnerView) {
                            role == DirectMessageSenderRole.PARTNER
                        } else {
                            role == DirectMessageSenderRole.PATIENT
                        }
                        val align = if (isOwnMessage) Alignment.End else Alignment.Start
                        val bubbleColor = if (isOwnMessage) {
                            brandPrimary.copy(alpha = 0.14f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                        }
                        val senderLabel = when (role) {
                            DirectMessageSenderRole.PARTNER -> page.programName.ifBlank { role.labelPtBr }
                            DirectMessageSenderRole.PATIENT -> if (isOwnerView) counterpartTitle else "Você"
                            DirectMessageSenderRole.AI_RECEPTIONIST -> "Assistente Virtual · ${page.aiReceptionistPolicy.personaName}"
                        }
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = align
                        ) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(0.88f),
                                shape = RoundedCornerShape(12.dp),
                                color = bubbleColor,
                                border = if (isOwnMessage) {
                                    BorderStroke(1.dp, brandPrimary.copy(alpha = 0.35f))
                                } else {
                                    null
                                }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
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
                                            text = formatDirectMsgTime(msg.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                    Text(
                                        text = msg.body,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
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
                onValueChange = { if (it.length <= B2BOnboardingValidator.MAX_DIRECT_MESSAGE_CHARS) draft = it },
                label = { Text("Escreva sua mensagem (até 1000 caracteres)") },
                enabled = !isSending && !sendBlockedOffHours,
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !isSending,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Fechar")
                }
                Button(
                    onClick = {
                        val trimmed = draft.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendMessage(trimmed)
                            draft = ""
                        }
                    },
                    enabled = draft.isNotBlank() && !isSending && !sendBlockedOffHours,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandPrimary,
                        contentColor = brandOnPrimary
                    )
                ) {
                    Text(if (isSending) "Enviando..." else "Enviar", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
