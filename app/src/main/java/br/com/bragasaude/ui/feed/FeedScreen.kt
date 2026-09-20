package br.com.bragasaude.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.local.SocialPostEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FeedScreen(
    viewModel: FeedViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (uiState.posts.isEmpty()) {
            EmptyFeedMessage(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.posts) { post ->
                    SocialPostCard(
                        post = post,
                        onReact = { viewModel.onReact(post.id, post.hasUserReacted) }
                    )
                }

                if (uiState.hasMore) {
                    item {
                        LoadMoreButton(
                            isLoading = uiState.isPaginating,
                            onClick = { viewModel.loadFeed(reset = false) }
                        )
                    }
                }
                
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

@Composable
fun SocialPostCard(post: SocialPostEntity, onReact: () -> Unit) {
    val (icon, color) = when (post.postType) {
        "milestone" -> Icons.Default.EmojiEvents to MaterialTheme.colorScheme.primary
        "streak" -> Icons.Default.LocalFireDepartment to Color(0xFFF59E0B) // Amber
        "level_up" -> Icons.AutoMirrored.Filled.TrendingUp to Color(0xFF10B981) // Green
        "goal_hit" -> Icons.Default.CheckCircle to Color(0xFF3B82F6) // Blue
        "family" -> Icons.Default.Favorite to Color(0xFFEC4899) // Rede Social Intergeracional
        else -> Icons.Default.Stars to MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = post.userName ?: "Usuário",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatRelativeTime(post.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Gray
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Selo visual da Rede Social Intergeracional (D11):
            // "👨‍👧 Publicação em Família • [Nome do Filho] & [Nome do Idoso]"
            if (post.isFamilyPost) {
                FamilyPrideBadge(
                    caregiverName = post.caregiverName,
                    patientName = post.patientName
                )
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = post.description ?: post.title,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onReact,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (post.hasUserReacted) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (post.hasUserReacted) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(
                    imageVector = if (post.hasUserReacted) Icons.Default.ThumbUp else Icons.Default.ThumbUpOffAlt,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (post.hasUserReacted) "Apoiado" else "Apoiar",
                    fontWeight = FontWeight.Bold
                )
                if (post.reactionCount > 0) {
                    Text(" • ${post.reactionCount}", modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

@Composable
fun EmptyFeedMessage(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Groups,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.LightGray
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "O feed está tranquilo...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Continue usando o Braga Saúde para ver suas conquistas aparecerem aqui e inspirar outros usuários!",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LoadMoreButton(isLoading: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.heightIn(min = 48.dp).fillMaxWidth(0.6f)
            ) {
                Text("Ver mais", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatRelativeTime(date: Date): String {
    val diff = System.currentTimeMillis() - date.time
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        days > 0 -> "há $days dias"
        hours > 0 -> "há $hours horas"
        minutes > 0 -> "há $minutes min"
        else -> "agora mesmo"
    }
}

/**
 * Selo visual das Publicações de Orgulho Familiar (Rede Social Intergeracional).
 * Formato: Publicação em Família • [Nome do Filho/Cuidador] & [Nome do Idoso]
 */
@Composable
fun FamilyPrideBadge(caregiverName: String?, patientName: String?) {
    val label = buildString {
        append("Publicação em Família")
        if (!caregiverName.isNullOrBlank() && !patientName.isNullOrBlank()) {
            append(" • $caregiverName & $patientName")
        }
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFFFDF2F8), // Pink 50
        border = BorderStroke(1.dp, Color(0xFFEC4899).copy(alpha = 0.4f))
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFFDB2777), // Pink 600
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
