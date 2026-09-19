package br.com.bragasaude.ui.social

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
                        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
                    ) {
                        items(filteredPosts, key = { it.id }) { post ->
                            val isReacted = viewModel.getOptimisticReaction(post.id) ?: post.hasUserReacted
                            SocialPostCard(
                                post = post,
                                isReacted = isReacted,
                                onToggleReaction = { viewModel.toggleReaction(post) }
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
            "Pressão Arterial Monitorada!"
        )

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Text(
                    "Publicar Conquista",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Escolha a conquista para comemorar:",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    achievementOptions.forEach { opt ->
                        FilterChip(
                            selected = selectedAchievement == opt,
                            onClick = { selectedAchievement = opt },
                            label = { Text(opt, fontSize = 13.sp) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = descriptionText,
                        onValueChange = { descriptionText = it },
                        label = { Text("Mensagem (opcional)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (isPublic) "🌐 Visível para Todos" else "👨‍👩‍👧 Círculo Familiar",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Switch(
                            checked = isPublic,
                            onCheckedChange = { isPublic = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createAchievementPost(
                            title = selectedAchievement,
                            description = descriptionText,
                            postType = "MILESTONE",
                            visibility = if (isPublic) "PUBLIC" else "FAMILY"
                        )
                        showCreateDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Publicar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun SocialPostCard(
    post: SocialPostEntity,
    isReacted: Boolean,
    onToggleReaction: () -> Unit
) {
    val (typeIcon, typeColor, typeLabel) = getPostTypeBadge(post.postType)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
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
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            // Botão de Apoio / Reação (Target de Toque >= 48dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(
                    onClick = onToggleReaction,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (isReacted) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        if (isReacted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Apoiar conquista",
                        tint = if (isReacted) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isReacted) "Apoiado!" else "Dar Apoio",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }

                if (post.reactionCount > 0) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${post.reactionCount}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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
