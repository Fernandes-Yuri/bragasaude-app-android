package br.com.bragasaude.ui.family

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.local.FamilyMessageEntity
import br.com.bragasaude.domain.FamilyConversationPdfExporter
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary
import java.text.SimpleDateFormat
import java.util.*

/**
 * Tela de Chat Familiar Bidirecional.
 * 
 * Permite que paciente e cuidador troquem mensagens em tempo real,
 * com suporte a notificações e ícones representativos (coração, remédio, água, etc).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyChatScreen(
    onBack: () -> Unit,
    onNavigateToConnect: () -> Unit,
    viewModel: FamilyViewModel = hiltViewModel()
) {
    val currentUserId by remember { derivedStateOf { viewModel.currentUserId } }
    val messages by viewModel.familyMessages.collectAsState()
    val binding by viewModel.activeBindingsForCurrentUser.collectAsState()
    val context = LocalContext.current
    val exportScope = androidx.compose.runtime.rememberCoroutineScope()

    var messageText by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    // D47: mensagem aguardando confirmação de exclusão (toque longo)
    var messagePendingDelete by remember { mutableStateOf<FamilyMessageEntity?>(null) }
    
    // Marcar mensagens como lidas quando os vínculos estiverem carregados
    LaunchedEffect(binding) {
        val patientId = binding.firstOrNull()?.patientUserId
        if (!patientId.isNullOrBlank()) {
            viewModel.markAllMessagesAsRead(patientId)
        }
    }
    
    val listState = rememberLazyListState()

    // Scroll para as mensagens mais recentes (índice 0 com reverseLayout)
    LaunchedEffect(Unit) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = BragaTextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                title = {
                    val activeBinding = binding.firstOrNull()
                    val titleText = when {
                        activeBinding == null -> "Conversa com a Família"
                        activeBinding.caregiverUserId == currentUserId -> "Paciente / Familiar"
                        activeBinding.caregiverName.isNotBlank() -> activeBinding.caregiverName
                        else -> "Familiar"
                    }
                    val subtitleText = when {
                        activeBinding == null -> "Ponte Familiar Braga Saúde"
                        activeBinding.caregiverUserId == currentUserId -> "Acompanhado por você"
                        activeBinding.caregiverRelation.isNotBlank() -> activeBinding.caregiverRelation
                        else -> "Acompanhante"
                    }
                    Column {
                        Text(
                            text = titleText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subtitleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    // D47: exportar a conversa do ciclo vigente (PDF compartilhável)
                    IconButton(
                        onClick = {
                            FamilyConversationPdfExporter.exportAndShare(context, messages, exportScope)
                        },
                        enabled = messages.isNotEmpty()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar conversa em PDF")
                    }
                    IconButton(onClick = onNavigateToConnect) {
                        Icon(Icons.Default.Groups, contentDescription = "Gerenciar Círculo Familiar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // D47: aviso permanente de retenção — as mensagens vivem por 24 horas
            FamilyRetentionNotice(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Lista de mensagens
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp)
            ) {
                items(
                    messages.filter { it.messageText.isNotBlank() || it.iconType != "CUSTOM" },
                    key = { it.id }
                ) { message ->
                    val isFromMe = message.senderUserId == currentUserId
                    ChatBubble(
                        message = message,
                        isFromMe = isFromMe,
                        onLongClick = if (isFromMe) ({ messagePendingDelete = message }) else null,
                        modifier = Modifier.fillMaxWidth(0.85f)
                    )
                }
                
                if (messages.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(300.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Forum,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp).padding(bottom = 16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                )
                                Text(
                                    "Nenhuma mensagem ainda",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Envie um bilhete de carinho ou receba atualizações da sua família",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
            
            // Chips de resposta rápida
            HorizontalQuickReplyChips(
                onSendQuickReply = { text ->
                    messageText = text
                }
            )
            
            // Campo de entrada
            MessageInputField(
                messageText = messageText,
                onMessageChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank() && !isSending) {
                        isSending = true
                        val bindingId = binding.firstOrNull()?.id
                        if (bindingId != null) {
                            viewModel.sendMessageToFamily(bindingId, messageText, iconType = "CUSTOM") {
                                messageText = ""
                                isSending = false
                            }
                        }
                    }
                },
                isSending = isSending
            )
        }
    }

    // D47: confirmação clara antes de apagar (padrão geriátrico)
    messagePendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { messagePendingDelete = null },
            title = {
                Text("Apagar mensagem?", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "A mensagem será apagada para você e para a sua família. " +
                        "Essa ação não pode ser desfeita.",
                    fontSize = 15.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteFamilyMessage(target.id)
                        messagePendingDelete = null
                    }
                ) {
                    Text(
                        "Apagar",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { messagePendingDelete = null }) {
                    Text("Cancelar", fontSize = 16.sp)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatBubble(
    message: FamilyMessageEntity,
    isFromMe: Boolean,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom
    ) {
        // Avatar
        if (!isFromMe) {
            Surface(
                shape = CircleShape,
                color = BragaMint,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        message.senderName.firstOrNull()?.uppercase()?.toString() ?: "?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaEmerald
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
        }
        
        Column(horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start) {
            // Balão da mensagem (toque longo na própria mensagem oferece exclusão — D47)
            val bubbleShape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isFromMe) 4.dp else 16.dp,
                bottomEnd = if (isFromMe) 16.dp else 4.dp
            )
            Surface(
                modifier = if (onLongClick != null) {
                    Modifier
                        .clip(bubbleShape)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = onLongClick,
                            onLongClickLabel = "Apagar mensagem"
                        )
                } else {
                    Modifier
                },
                shape = bubbleShape,
                color = if (isFromMe) BragaEmerald.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    // Ícone tipo de mensagem
                    if (message.iconType != "CUSTOM" && message.iconType.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                getFamilyMessageIcon(message.iconType),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = BragaEmerald
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    
                    // Texto da mensagem
                    if (message.messageText.isNotBlank()) {
                        Text(
                            message.messageText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 15.sp,
                            color = if (isFromMe) Color.Black else BragaTextPrimary
                        )
                    }
                    
                    Spacer(Modifier.height(4.dp))
                    
                    // Carimbo de hora
                    Text(
                        formatMessageDate(message.sentAt),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = if (isFromMe) BragaTextSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Indicador de lida
            if (isFromMe && message.sentAt > 0) {
                Spacer(Modifier.height(2.dp))
                Icon(
                    Icons.Default.Check,
                    contentDescription = if (message.isRead) "Lido" else "Enviado",
                    modifier = Modifier.size(14.dp),
                    tint = if (message.isRead) BragaEmerald else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HorizontalQuickReplyChips(
    onSendQuickReply: (String) -> Unit
) {
    val quickReplies = listOf(
        "Já tomei meu remédio",
        "Estou bem, obrigado",
        "Vou aferir a pressão",
        "Obrigado!",
        "Preciso beber água",
        "Vamos caminhar?"
    )
    
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(quickReplies) { reply ->
            AssistChip(
                onClick = { onSendQuickReply(reply) },
                label = {
                    Text(reply, maxLines = 1, fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(
                        when {
                            reply.contains("remédio", ignoreCase = true) -> Icons.Default.Medication
                            reply.contains("bem", ignoreCase = true) -> Icons.Default.Favorite
                            reply.contains("pressão", ignoreCase = true) -> Icons.Default.DirectionsRun
                            reply.contains("obrigado", ignoreCase = true) -> Icons.Default.SentimentSatisfied
                            reply.contains("água", ignoreCase = true) -> Icons.Default.WaterDrop
                            reply.contains("caminh", ignoreCase = true) -> Icons.Default.DirectionsWalk
                            else -> Icons.Default.ChatBubbleOutline
                        },
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                },
                modifier = Modifier.height(36.dp)
            )
        }
    }
}

@Composable
private fun MessageInputField(
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    isSending: Boolean
) {
    Surface(
        tonalElevation = 2.dp,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageChange,
                placeholder = {
                    Text("Digite uma mensagem...", style = MaterialTheme.typography.bodyMedium)
                },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp, max = 120.dp),
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BragaEmerald,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                maxLines = 5,
                enabled = !isSending
            )
            
            Spacer(Modifier.width(12.dp))
            
            Button(
                onClick = onSend,
                enabled = messageText.isNotBlank() && !isSending,
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald)
            ) {
                if (isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Enviar",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

private fun getFamilyMessageIcon(iconType: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (iconType.uppercase()) {
        "WATER" -> Icons.Default.WaterDrop
        "MED", "MEDICATION" -> Icons.Default.Medication
        "LOVE", "FAVORITE" -> Icons.Default.Favorite
        "WALK", "STEPS" -> Icons.Default.DirectionsWalk
        "STAR" -> Icons.Default.Star
        else -> Icons.Default.ChatBubbleOutline
    }
}
