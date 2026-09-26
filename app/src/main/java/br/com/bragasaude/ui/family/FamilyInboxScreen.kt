package br.com.bragasaude.ui.family

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.ui.theme.BragaBackground
import br.com.bragasaude.ui.theme.BragaCardBorder
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Tela de Inbox / Lista de Conversas da Família.
 *
 * Resolve o isolamento de conversas para o usuário híbrido (ex: cuida da mãe e é acompanhado pela irmã).
 * Cada contato ou círculo possui sua janela de conversa isolada e seu perfil visualizável.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyInboxScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onNavigateToConnect: () -> Unit,
    onOpenCaregiverDashboard: ((String) -> Unit)? = null,
    viewModel: FamilyViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsState()
    var selectedProfileConversation by remember { mutableStateOf<FamilyConversationUi?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = BragaBackground,
        topBar = {
            EmeraldHeaderBanner(
                title = "Mensagens da Família",
                subtitle = "Círculo de cuidado e acompanhamento",
                onBack = onBack,
                trailingContent = {
                    IconButton(onClick = onNavigateToConnect) {
                        Icon(
                            imageVector = Icons.Default.PersonAdd,
                            contentDescription = "Conectar familiar",
                            tint = Color.White
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Faixa informativa da retenção de 24 horas (D47)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = BragaMint.copy(alpha = 0.45f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = BragaEmerald
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Mensagens protegidas por retenção de 24h",
                        style = MaterialTheme.typography.labelSmall,
                        color = BragaEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            if (conversations.isEmpty()) {
                // Estado vazio quando o usuário ainda não possui vínculos ativos
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BragaMint,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = BragaEmerald
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "Nenhuma conversa ativa",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = BragaTextPrimary
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Conecte seus familiares ou cuidadores para trocar mensagens seguras com retenção de 24 horas.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BragaTextSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 20.sp
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onNavigateToConnect,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                Icons.Default.PersonAdd,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Conectar familiar",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(conversations, key = { it.patientId }) { conversation ->
                        FamilyConversationCard(
                            conversation = conversation,
                            onClick = { onOpenConversation(conversation.patientId) },
                            onProfileClick = { selectedProfileConversation = conversation }
                        )
                    }
                }
            }
        }
    }

    selectedProfileConversation?.let { conversation ->
        FamilyMemberProfileBottomSheet(
            conversation = conversation,
            onDismissRequest = { selectedProfileConversation = null },
            onNavigateToConnect = onNavigateToConnect,
            onOpenCaregiverDashboard = onOpenCaregiverDashboard
        )
    }
}

@Composable
private fun FamilyConversationCard(
    conversation: FamilyConversationUi,
    onClick: () -> Unit,
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
        border = BorderStroke(1.dp, BragaCardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar com inicial e clique para perfil
            Surface(
                shape = CircleShape,
                color = BragaMint,
                modifier = Modifier
                    .size(50.dp)
                    .clickable(onClick = onProfileClick)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = conversation.avatarLetter,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaEmerald
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Conteúdo textual
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = conversation.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    val timeLabel = formatConversationTime(conversation.lastMessageTimeMs)
                    if (timeLabel.isNotBlank()) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (conversation.unreadCount > 0) BragaEmerald else BragaTextSecondary,
                            fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }

                Spacer(Modifier.height(3.dp))

                // Tag de papel / relação
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (conversation.isCaredByMe) BragaEmerald.copy(alpha = 0.1f) else BragaMint.copy(alpha = 0.6f)
                ) {
                    Text(
                        text = if (conversation.isCaredByMe) "Você acompanha" else "Te acompanha",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.sp,
                        color = BragaEmerald,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(Modifier.height(5.dp))

                // Prévia da última mensagem e contador de não lidas
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val previewText = if (conversation.lastMessageText != null) {
                        val sender = conversation.lastMessageSender
                        if (sender != null) "$sender: ${conversation.lastMessageText}" else conversation.lastMessageText
                    } else {
                        "Toque para enviar um bilhete de carinho"
                    }

                    Text(
                        text = previewText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (conversation.unreadCount > 0) BragaTextPrimary else BragaTextSecondary,
                        fontWeight = if (conversation.unreadCount > 0) FontWeight.SemiBold else FontWeight.Normal,
                        fontStyle = if (conversation.lastMessageText == null) FontStyle.Italic else FontStyle.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (conversation.unreadCount > 0) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = CircleShape,
                            color = BragaEmerald,
                            modifier = Modifier.size(20.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // Botão sutil para abrir o perfil do familiar
            IconButton(
                onClick = onProfileClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Ver perfil do familiar",
                    tint = BragaEmerald.copy(alpha = 0.65f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun formatConversationTime(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0L) return ""
    val date = Date(timestamp)
    val now = Calendar.getInstance()
    val msgCal = Calendar.getInstance().apply { time = date }

    return if (now.get(Calendar.DAY_OF_YEAR) == msgCal.get(Calendar.DAY_OF_YEAR) &&
        now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
    ) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } else if (now.get(Calendar.DAY_OF_YEAR) - msgCal.get(Calendar.DAY_OF_YEAR) == 1 &&
        now.get(Calendar.YEAR) == msgCal.get(Calendar.YEAR)
    ) {
        "Ontem"
    } else {
        SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)
    }
}
