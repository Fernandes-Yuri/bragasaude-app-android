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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.domain.b2b.B2BOnboardingValidator
import br.com.bragasaude.domain.b2b.B2BThemeEngine
import br.com.bragasaude.domain.b2b.DirectChatThreadPageDto
import br.com.bragasaude.domain.b2b.DirectMessageSenderRole
import br.com.bragasaude.ui.components.BragaBottomSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val directMsgTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale("pt", "BR"))

private fun formatDirectMsgTime(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault()).format(directMsgTimeFormatter)
}.getOrElse { iso.take(16) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartnerDirectChatBottomSheet(
    page: DirectChatThreadPageDto,
    isOwnerView: Boolean,
    counterpartTitle: String,
    isLoadingMore: Boolean,
    isSending: Boolean,
    brandPrimary: Color,
    brandOnPrimary: Color,
    brandAccent: Color,
    softSurface: Color,
    onLoadOlder: () -> Unit,
    onSendMessage: (String) -> Unit,
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
                        text = "${page.directChatPolicy.serviceHoursLabel} · ${page.directChatPolicy.responseSlaLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f))
            ) {
                Text(
                    text = B2BThemeEngine.DIRECT_CHAT_NON_URGENCY_NOTICE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(10.dp)
                )
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
                            DirectMessageSenderRole.AI_RECEPTIONIST -> role.labelPtBr
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
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = senderLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = brandAccent
                                        )
                                        Text(
                                            text = formatDirectMsgTime(msg.createdAt),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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

            Text(
                text = B2BThemeEngine.DIRECT_CHAT_ZERO_ATTACHMENT_HINT,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
