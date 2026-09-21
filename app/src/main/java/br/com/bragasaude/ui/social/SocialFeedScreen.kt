package br.com.bragasaude.ui.social

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.local.SocialPostEntity
import br.com.bragasaude.ui.theme.Success
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialFeedScreen(
    onBack: () -> Unit,
    viewModel: SocialFeedViewModel = hiltViewModel()
) {
    val posts by viewModel.posts.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val filteredPosts = remember(posts, searchQuery) {
        if (searchQuery.isBlank()) {
            posts
        } else {
            posts.filter { post ->
                post.userName?.contains(searchQuery, ignoreCase = true) == true ||
                post.title.contains(searchQuery, ignoreCase = true) ||
                post.description?.contains(searchQuery, ignoreCase = true) == true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Mural da Comunidade",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                icon = { Icon(Icons.Default.EmojiEvents, contentDescription = null) },
                text = { Text("Publicar Conquista", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Barra de busca
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar por nome ou conquista...", fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Buscar", tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Limpar")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize()
            ) {
                if (filteredPosts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                if (searchQuery.isNotBlank()) "Nenhuma publicação encontrada." else "O mural está calmo hoje.",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                if (searchQuery.isNotBlank()) "Tente buscar por outro termo ou nome." else "Conquistas e metas dos seus colegas de saúde aparecerão aqui para inspirar o seu dia!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp)
                    ) {
                        items(filteredPosts, key = { it.id }) { post ->
                            val reactions by remember(post.id) { viewModel.reactionsForPost(post.id) }.collectAsState(initial = emptyList())
                            val reaction = reactions.firstOrNull { it.userId == viewModel.currentUserId }?.reactionType
                                ?: if (post.hasUserReacted) "apoio" else null
                            SocialPostCard(
                                post = post,
                                selectedReaction = reaction,
                                onReact = { viewModel.react(post, it) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var selectedAchievement by remember { mutableStateOf("Meta de Passos Concluída!") }
        var descriptionText by remember { mutableStateOf("Hoje alcancei minha meta diária de passos!") }
        var isPublic by remember { mutableStateOf(true) }

        val achievementOptions = listOf(
            "Meta de Passos Concluída!",
            "Hidratação do Dia Completa!",
            "Consistência de Saúde!",
            "Pressão Arterial Monitorada!",
            "Rotina de Remédios em Dia!"
        )

        ModalBottomSheet(onDismissRequest = { showCreateDialog = false }) {
            Column(
                Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Compartilhar conquista", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                achievementOptions.chunked(2).forEach { options ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.forEach { option ->
                            FilterChip(
                                selected = selectedAchievement == option,
                                onClick = { selectedAchievement = option },
                                leadingIcon = { Icon(achievementIcon(option), contentDescription = null) },
                                label = { Text(option, style = MaterialTheme.typography.labelLarge) },
                                modifier = Modifier.weight(1f).heightIn(min = 64.dp)
                            )
                        }
                    }
                }
                OutlinedTextField(value = descriptionText, onValueChange = { descriptionText = it.take(500) },
                    label = { Text("Sua mensagem") }, modifier = Modifier.fillMaxWidth(), maxLines = 4,
                    shape = RoundedCornerShape(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(if (isPublic) "Público: comunidade" else "Público: família", modifier = Modifier.weight(1f))
                    Switch(checked = isPublic, onCheckedChange = { isPublic = it })
                }
                Text("Prévia", style = MaterialTheme.typography.labelLarge)
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(achievementIcon(selectedAchievement), contentDescription = null)
                        Text(selectedAchievement, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        if (descriptionText.isNotBlank()) Text(descriptionText)
                    }
                }
                Button(onClick = {
                    viewModel.createAchievementPost(selectedAchievement, descriptionText, "MILESTONE", if (isPublic) "PUBLIC" else "FAMILY")
                    showCreateDialog = false
                }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(16.dp)) {
                    Text("Publicar conquista")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SocialPostCard(
    post: SocialPostEntity,
    selectedReaction: String?,
    onReact: (String) -> Unit
) {
    val (typeIcon, typeColor, typeLabel) = getPostTypeBadge(post.postType)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), MaterialTheme.colorScheme.surface)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header do Post: Avatar/Nome + Tipo de Conquista
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Avatar Inicial
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (post.userName ?: "C").take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Column {
                        Text(
                            text = post.userName ?: "Colega de Saúde",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Nível ${post.userLevel} • ${formatRelativeTime(post.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Tag do Tipo de Conquista
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = typeColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(
                            horizontal = if (typeLabel.isNotBlank()) 8.dp else 6.dp,
                            vertical = 4.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            typeIcon,
                            contentDescription = null,
                            tint = typeColor,
                            modifier = Modifier.size(16.dp)
                        )
                        if (typeLabel.isNotBlank()) {
                            // Removido conforme Tarefa 4: Manter apenas a estrela/ícone no Mural
                        }
                    }
                }
            }

            // Banner de Publicação em Família
            if (post.isFamilyPost) {
                FamilyPostBanner(
                    caregiverName = post.caregiverName,
                    patientName = post.patientName
                )
            }

            // Conteúdo do Post
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!post.description.isNullOrBlank()) {
                    Text(
                        text = post.description,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            val haptics = LocalHapticFeedback.current
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("apoio", "Cuidado", Icons.Default.VolunteerActivism),
                    Triple("palmas", "Palmas", Icons.Default.ThumbUp),
                    Triple("forca", "Força", Icons.Default.FitnessCenter)).forEach { (type, label, icon) ->
                    val selected = selectedReaction == type
                    val reactionScale by animateFloatAsState(if (selected) 1.05f else 1f, label = "Reação")
                    FilterChip(selected = selected, onClick = {
                        if (!selected) { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onReact(type) }
                    }, label = { Text(label) }, leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        modifier = Modifier.heightIn(min = 48.dp).scale(reactionScale), shape = RoundedCornerShape(50))
                }
            }
            if (post.reactionCount > 0) Text(post.reactionCount.toString() + " apoios", style = MaterialTheme.typography.labelLarge)

        }
    }
}

private data class PostBadgeInfo(
    val icon: ImageVector,
    val color: Color,
    val label: String
)

private fun getPostTypeBadge(postType: String): PostBadgeInfo {
    return when (postType) {
        "milestone" -> PostBadgeInfo(Icons.Default.EmojiEvents, Color(0xFFE5A800), "Conquista")
        "streak" -> PostBadgeInfo(Icons.Default.LocalFireDepartment, Color(0xFFE65100), "Sequência")
        "level_up" -> PostBadgeInfo(Icons.Default.Star, Success, "Subiu de Nível")
        "goal_hit" -> PostBadgeInfo(Icons.Default.TrendingUp, Color(0xFF1976D2), "Meta Batida")
        "weekly_recap" -> PostBadgeInfo(Icons.Default.MilitaryTech, Color(0xFF7B1FA2), "Semanal")
        else -> PostBadgeInfo(Icons.Default.Star, Color(0xFF1976D2), "")
    }
}

private fun formatRelativeTime(date: Date): String {
    val diff = System.currentTimeMillis() - date.time
    val minutes = diff / (60 * 1000)
    val hours = minutes / 60
    val days = hours / 24

    return when {
        minutes < 1 -> "Agora mesmo"
        minutes < 60 -> "Há ${minutes}m"
        hours < 24 -> "Há ${hours}h"
        days == 1L -> "Ontem"
        days < 7 -> "Há ${days}d"
        else -> SimpleDateFormat("dd/MM", Locale.getDefault()).format(date)
    }
}

/**
 * Banner acolhedor de publicações em família no Mural da Comunidade.
 * Formato: Diversidade3 Publicação em Família • [cuidador] & [paciente]
 */
@Composable
private fun FamilyPostBanner(caregiverName: String?, patientName: String?) {
    val label = buildString {
        append("Publicação em Família")
        if (!caregiverName.isNullOrBlank() && !patientName.isNullOrBlank()) {
            append(" • $caregiverName & $patientName")
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Default.Diversity3,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun achievementIcon(title: String): ImageVector = when {
    title.contains("Passos") -> androidx.compose.material.icons.Icons.AutoMirrored.Filled.DirectionsWalk
    title.contains("Hidratação") -> androidx.compose.material.icons.Icons.Default.WaterDrop
    title.contains("Remédios") -> androidx.compose.material.icons.Icons.Default.Medication
    title.contains("Pressão") -> androidx.compose.material.icons.Icons.Default.Favorite
    else -> androidx.compose.material.icons.Icons.Default.LocalFireDepartment
}
