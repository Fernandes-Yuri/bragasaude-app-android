package br.com.bragasaude.ui.family

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.local.FamilyMessageEntity
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaTextPrimary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Card de "Mensagens da Família" para a Home do idoso.
 *
 * Design sóbrio e profissional (nível Apple Health / Google Health):
 * - Fundo tonal surfaceVariant com borda sutil de 1dp
 * - Cabeçalho limpo com ícone vetorial Forum
 * - Badge de parentesco em pílula discreta
 * - Tipografia do recado em itálico sóbrio
 * - Carimbo de data legível
 * - Transição fluida com AnimatedVisibility
 * - Zero emojis
 */
@Composable
fun FamilyNotesCard(
    onSeeAll: () -> Unit = {},
    onOpenChat: () -> Unit = onSeeAll,
    onManageFamily: () -> Unit = onSeeAll,
    viewModel: PatientFamilyViewModel = hiltViewModel()
) {
    val messages by viewModel.familyMessages.collectAsState()
    val unreadCount by viewModel.unreadMessageCount.collectAsState()

    if (messages.isEmpty()) {
        EmptyFamilyNotesCard(
            onOpenChat = onOpenChat,
            onManageFamily = onManageFamily
        )
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val displayMessages = if (expanded) messages.take(5) else messages.take(2)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(20.dp)) {
            // Cabeçalho limpo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Forum,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Mensagens da Família",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 18.sp
                    )
                    Text(
                        if (unreadCount > 0) "$unreadCount não lida${if (unreadCount > 1) "s" else ""}" else "Todas lidas",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
                if (unreadCount > 0) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Text(unreadCount.toString(), fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Mensagens com AnimatedVisibility - toque abre a conversa no chat para responder
            displayMessages.forEachIndexed { index, message ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    FamilyMessageItem(
                        message = message,
                        onClick = {
                            viewModel.markMessageAsRead(message.id)
                            onOpenChat()
                        }
                    )
                }
                if (index < displayMessages.lastIndex) {
                    Spacer(Modifier.height(10.dp))
                }
            }

            Spacer(Modifier.height(14.dp))

            // Botão em destaque para interagir/responder
            Button(
                onClick = onOpenChat,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Forum,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Conversar com a Família / Responder",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            // Ações secundárias
            if (messages.size > displayMessages.size) {
                TextButton(
                    onClick = {
                        if (expanded) onOpenChat() else expanded = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (expanded) "Ver histórico completo no chat" else "Mostrar mais recados",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                }
            } else if (unreadCount > 0) {
                TextButton(
                    onClick = { viewModel.markAllMessagesAsRead() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Marcar todas como lidas",
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                }
            }
            
            Spacer(Modifier.height(4.dp))
            
            // Botão permanente para acessar código e círculos familiares
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onManageFamily() }
                        .padding(vertical = 12.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Diversity3,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Meu Código / Círculo Familiar",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyFamilyNotesCard(
    onOpenChat: () -> Unit = {},
    onManageFamily: () -> Unit = {}
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Diversity3,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Mensagens da Família",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "Conecte-se aos seus familiares para trocar recados e carinho.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onOpenChat,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Abrir Chat", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onManageFamily,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Diversity3, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Meu Código", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Item individual de mensagem familiar com design sóbrio.
 *
 * - Badge de parentesco em pílula discreta
 * - Tipografia do recado em itálico
 * - Carimbo de data legível
 * - Ícone vetorial por tipo de mensagem
 */
@Composable
private fun FamilyMessageItem(
    message: FamilyMessageEntity,
    onClick: () -> Unit = {}
) {
    val iconConfig = familyMessageIconConfig(message.iconType)
    val iconColor = iconConfig.color
    val roleBadge = inferSenderRole(message.senderName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (!message.isRead) MaterialTheme.colorScheme.surface
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
            )
            .then(
                if (!message.isRead) {
                    Modifier.border(
                        1.dp,
                        iconColor.copy(alpha = 0.35f),
                        RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        RoundedCornerShape(12.dp)
                    )
                }
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Ícone vetorial do tipo de mensagem
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = iconColor.copy(alpha = 0.1f),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = iconConfig.icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            // Nome + Badge de parentesco + Data
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    message.senderName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // Badge de parentesco em pílula discreta
                if (roleBadge != null) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = roleBadge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontSize = 11.sp
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Carimbo de data
                Text(
                    formatMessageDate(message.sentAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }

            // Indicador de não lida
            if (!message.isRead) {
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(6.dp)
                    ) {}
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Nova mensagem",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // Texto do recado em itálico sóbrio
            Text(
                text = "\u201C${message.messageText}\u201D",
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
        }
    }
}

/**
 * Configuração de ícone vetorial e cor por tipo de mensagem.
 * Zero emojis — apenas Material Icons com cores sóbrias.
 */
data class MessageIconConfig(
    val icon: ImageVector,
    val color: Color
)

fun familyMessageIconConfig(iconType: String): MessageIconConfig {
    return when (iconType) {
        "WATER" -> MessageIconConfig(
            icon = Icons.Default.WaterDrop,
            color = Color(0xFF0277BD) // Blue 700 — sóbrio
        )
        "MED" -> MessageIconConfig(
            icon = Icons.Default.Medication,
            color = Color(0xFFE65100) // Orange 900 — sóbrio
        )
        "LOVE" -> MessageIconConfig(
            icon = Icons.Default.Favorite,
            color = Color(0xFFAD1457) // Pink 800 — sóbrio
        )
        "WALK" -> MessageIconConfig(
            icon = Icons.Default.DirectionsWalk,
            color = Color(0xFF2E7D32) // Green 800 — sóbrio
        )
        else -> MessageIconConfig(
            icon = Icons.Default.Star,
            color = Color(0xFF6A1B9A) // Purple 800 — sóbrio
        )
    }
}

/**
 * Infere o papel/parentesco do remetente pelo nome.
 * Retorna null se não conseguir inferir (o badge fica oculto).
 */
private fun inferSenderRole(senderName: String): String? {
    val name = senderName.lowercase(Locale.ROOT)
    return when {
        name.contains("filha") -> "Filha"
        name.contains("filho") -> "Filho"
        name.contains("neta") -> "Neta"
        name.contains("neto") -> "Neto"
        name.contains("cuidadora") -> "Cuidadora"
        name.contains("cuidador") -> "Cuidador"
        name.contains("esposa") || name.contains("mulher") -> "Esposa"
        name.contains("esposo") || name.contains("marido") -> "Esposo"
        name.contains("irmã") -> "Irmã"
        name.contains("irmão") -> "Irmão"
        name.contains("sobrinha") -> "Sobrinha"
        name.contains("sobrinho") -> "Sobrinho"
        name.contains("médico") || name.contains("doutor") ||
            name.contains("dra") || name.contains("dr ") -> "Profissional"
        else -> null
    }
}

/** Data amigável e legível para idosos. Formato sóbrio. */
internal fun formatMessageDate(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    return when {
        diff < 60_000L -> "agora"
        diff < 3_600_000L -> "${diff / 60_000L} min"
        isToday(millis) -> {
            SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(millis))
        }
        isYesterday(millis) -> {
            "ontem"
        }
        else -> SimpleDateFormat("dd/MM", Locale("pt", "BR")).format(Date(millis))
    }
}

internal fun isToday(millis: Long): Boolean {
    val cal = Calendar.getInstance()
    val startOfDay = cal.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    return millis >= startOfDay
}

internal fun isYesterday(millis: Long): Boolean {
    val cal = Calendar.getInstance()
    val startOfToday = cal.apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val oneDay = 24 * 60 * 60 * 1000L
    return millis in (startOfToday - oneDay) until startOfToday
}

// ==================== TELA COMPLETA DE MENSAGENS ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyMessagesScreen(
    onBack: () -> Unit,
    onOpenChat: () -> Unit = {},
    viewModel: FamilyViewModel = hiltViewModel()
) {
    val messages by viewModel.familyMessages.collectAsState()
    val unread by viewModel.unreadMessageCount.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Mensagens da Família",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    if (unread > 0) {
                        TextButton(onClick = { viewModel.markAllMessagesAsRead() }) {
                            Text(
                                "Ler todas",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Button(
                        onClick = onOpenChat,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.Forum, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Responder / Conversar no Chat", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.ChatBubbleOutline,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(
                            "Nenhuma mensagem ainda",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Sua família enviará mensagens de carinho por aqui.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(8.dp))
                messages.forEachIndexed { index, message ->
                    FamilyMessageItem(
                        message = message,
                        onClick = {
                            viewModel.markMessageAsRead(message.id)
                            onOpenChat()
                        }
                    )
                    if (index < messages.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
