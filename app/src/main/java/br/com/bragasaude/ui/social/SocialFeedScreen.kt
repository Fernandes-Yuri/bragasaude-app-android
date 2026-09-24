package br.com.bragasaude.ui.social

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diversity3
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.local.PostReactionEntity
import br.com.bragasaude.data.local.SocialPostEntity
import br.com.bragasaude.ui.theme.BragaCardSurface
import br.com.bragasaude.ui.theme.BragaEmerald
import br.com.bragasaude.ui.theme.BragaEmeraldDark
import br.com.bragasaude.ui.theme.BragaMint
import br.com.bragasaude.ui.theme.BragaMintBorder
import br.com.bragasaude.ui.theme.BragaMintSurface
import br.com.bragasaude.ui.theme.BragaTextPrimary
import br.com.bragasaude.ui.theme.BragaTextSecondary
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
    val publishState by viewModel.publishState.collectAsState()
    val feedError by viewModel.feedError.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchQuery by remember { mutableStateOf("") }
    var showCreateSheet by remember { mutableStateOf(false) }

    LaunchedEffect(publishState) {
        when (val state = publishState) {
            SocialFeedViewModel.PublishState.Success -> {
                showCreateSheet = false
                snackbarHostState.showSnackbar("Conquista publicada com sucesso!")
                viewModel.clearPublishState()
            }
            is SocialFeedViewModel.PublishState.Error -> {
                snackbarHostState.showSnackbar(state.message)
                viewModel.clearPublishState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(feedError) {
        feedError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFeedError()
        }
    }

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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mural da Comunidade",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = BragaTextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Voltar",
                            tint = BragaTextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showCreateSheet = true },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Publicar Conquista",
                            tint = BragaEmeraldDark
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Barra de busca acessível
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Buscar por nome ou conquista...",
                        fontSize = 14.sp,
                        color = BragaTextSecondary
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = BragaEmeraldDark
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(
                            onClick = { searchQuery = "" },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpar busca",
                                tint = BragaTextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BragaEmerald,
                    unfocusedBorderColor = BragaMintBorder
                ),
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
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        if (searchQuery.isBlank()) {
                            CommunityWelcomeBanner(onPublishClick = { showCreateSheet = true })
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Nenhuma publicação encontrada." else "O mural está calmo hoje.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = BragaTextPrimary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Tente buscar por outro termo ou nome." else "Conquistas e metas dos seus colegas de saúde aparecerão aqui para inspirar o seu dia!",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontSize = 16.sp,
                                    color = BragaTextSecondary,
                                    textAlign = TextAlign.Center
                                )
                            }
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
                        if (searchQuery.isBlank()) {
                            item(key = "welcome_banner") {
                                CommunityWelcomeBanner(onPublishClick = { showCreateSheet = true })
                            }
                        }

                        items(filteredPosts, key = { it.id }) { post ->
                            val reactions by remember(post.id) { viewModel.reactionsForPost(post.id) }.collectAsState(initial = emptyList())
                            val selectedReaction = reactions.firstOrNull { it.userId == viewModel.currentUserId }?.reactionType
                                ?: if (post.hasUserReacted) "apoio" else null
                            SocialPostCard(
                                post = post,
                                reactions = reactions,
                                selectedReaction = selectedReaction,
                                onReact = { reactionType ->
                                    viewModel.toggleReaction(post, reactionType, selectedReaction)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateAchievementBottomSheet(
            templates = viewModel.achievementTemplates,
            publishState = publishState,
            onDismiss = {
                if (publishState != SocialFeedViewModel.PublishState.Loading) {
                    showCreateSheet = false
                    viewModel.clearPublishState()
                }
            },
            onPublish = { title, description, postType, isPublic ->
                viewModel.createAchievementPost(
                    title = title,
                    description = description,
                    postType = postType,
                    visibility = if (isPublic) "PUBLIC" else "FAMILY"
                )
            }
        )
    }
}

/**
 * Banner acolhedor de destaque no topo da lista.
 * Canto inferior direito 100% livre para a Orbe de IA.
 */
@Composable
private fun CommunityWelcomeBanner(
    onPublishClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = BragaCardSurface
        ),
        border = BorderStroke(1.dp, BragaMintBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BragaMintSurface,
                            BragaMint.copy(alpha = 0.45f),
                            BragaCardSurface
                        )
                    )
                )
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(BragaMint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = BragaEmeraldDark,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Compartilhe uma vitória hoje com a comunidade",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = BragaTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Celebre pequenas conquistas, inspire outros colegas e fortaleça sua rotina.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 14.sp,
                        color = BragaTextSecondary
                    )
                }
            }

            Button(
                onClick = onPublishClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BragaEmerald,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Publicar Conquista",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Card contemporâneo do feed com cantos de 24.dp, borda sutil BragaMintBorder
 * e barra de 3 reações expressivas com contadores e feedback tátil.
 */
@Composable
private fun SocialPostCard(
    post: SocialPostEntity,
    reactions: List<PostReactionEntity>,
    selectedReaction: String?,
    onReact: (String) -> Unit
) {
    val (typeIcon, typeColor, typeLabel) = getPostTypeBadge(post.postType)
    val haptics = LocalHapticFeedback.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = BragaCardSurface
        ),
        border = BorderStroke(1.dp, BragaMintBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header do Post: Avatar/Nome + Selo da Conquista
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BragaMint),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (post.userName ?: "C").take(1).uppercase(Locale.ROOT),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = BragaEmeraldDark
                        )
                    }

                    Column {
                        Text(
                            text = post.userName ?: "Colega de Saúde",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = BragaTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Nível ${post.userLevel} • ${formatRelativeTime(post.createdAt)}",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 14.sp,
                            color = BragaTextSecondary
                        )
                    }
                }

                // Selo ilustrado da conquista
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = typeColor.copy(alpha = 0.14f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = typeLabel,
                            tint = typeColor,
                            modifier = Modifier.size(18.dp)
                        )
                        if (typeLabel.isNotBlank()) {
                            Text(
                                text = typeLabel,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = typeColor
                            )
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = BragaTextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!post.description.isNullOrBlank()) {
                    Text(
                        text = post.description,
                        style = MaterialTheme.typography.bodyLarge,
                        fontSize = 16.sp,
                        color = BragaTextSecondary,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            HorizontalDivider(
                color = BragaMintBorder.copy(alpha = 0.5f)
            )

            // Barra de 3 Reações Expressivas: Cuidado, Palmas e Força
            val reactionOptions = listOf(
                Triple("apoio", "Cuidado", Icons.Default.VolunteerActivism),
                Triple("palmas", "Palmas", Icons.Default.ThumbUp),
                Triple("forca", "Força", Icons.Default.FitnessCenter)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                reactionOptions.forEach { (type, label, icon) ->
                    val isSelected = selectedReaction == type
                    val count = reactions.count { it.reactionType == type }
                    val reactionScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.05f else 1f,
                        label = "ReactionScale"
                    )

                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onReact(type)
                        },
                        label = {
                            Text(
                                text = if (count > 0) "$label $count" else label,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .scale(reactionScale),
                        shape = RoundedCornerShape(50),
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = BragaMintSurface,
                            labelColor = BragaTextPrimary,
                            iconColor = BragaTextSecondary,
                            selectedContainerColor = BragaMint,
                            selectedLabelColor = BragaEmeraldDark,
                            selectedLeadingIconColor = BragaEmeraldDark
                        ),
                        border = BorderStroke(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = if (isSelected) BragaEmerald else BragaMintBorder
                        )
                    )
                }
            }

            val totalReactions = maxOf(post.reactionCount, reactions.size)
            if (totalReactions > 0) {
                Text(
                    text = if (totalReactions == 1) "1 apoio da comunidade" else "$totalReactions apoios da comunidade",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = BragaTextSecondary
                )
            }
        }
    }
}

/**
 * ModalBottomSheet moderno para criação de novas postagens de conquistas.
 * Inclui seleção de template com chips, mensagem personalizada, visibilidade e live preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateAchievementBottomSheet(
    templates: List<AchievementTemplate>,
    publishState: SocialFeedViewModel.PublishState,
    onDismiss: () -> Unit,
    onPublish: (title: String, description: String, postType: String, isPublic: Boolean) -> Unit
) {
    var selectedTemplate by remember { mutableStateOf(templates.first()) }
    var descriptionText by remember { mutableStateOf(templates.first().defaultDescription) }
    var isPublic by remember { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = {
            if (publishState != SocialFeedViewModel.PublishState.Loading) {
                onDismiss()
            }
        },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = BragaCardSurface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Compartilhar Conquista",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = BragaTextPrimary
                )
                Text(
                    text = "Selecione o tipo de conquista para celebrar com a comunidade.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = 14.sp,
                    color = BragaTextSecondary
                )
            }

            // Chips com as 4 categorias harmonizadas
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Tipo de conquista",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = BragaTextPrimary
                )

                templates.chunked(2).forEach { rowTemplates ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTemplates.forEach { template ->
                            val isSelected = selectedTemplate.id == template.id
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (descriptionText.isBlank() || descriptionText == selectedTemplate.defaultDescription) {
                                        descriptionText = template.defaultDescription
                                    }
                                    selectedTemplate = template
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = achievementIcon(template.category),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = template.category,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    containerColor = BragaMintSurface,
                                    labelColor = BragaTextPrimary,
                                    iconColor = BragaTextSecondary,
                                    selectedContainerColor = BragaMint,
                                    selectedLabelColor = BragaEmeraldDark,
                                    selectedLeadingIconColor = BragaEmeraldDark
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) BragaEmerald else BragaMintBorder
                                )
                            )
                        }
                    }
                }
            }

            // Campo para mensagem personalizada opcional
            OutlinedTextField(
                value = descriptionText,
                onValueChange = { descriptionText = it.take(500) },
                label = { Text("Mensagem da conquista", fontSize = 14.sp) },
                placeholder = { Text("Descreva como foi alcançar essa meta...", fontSize = 14.sp) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = BragaEmerald,
                    focusedLabelColor = BragaEmeraldDark,
                    cursorColor = BragaEmerald
                )
            )

            // Seletor de Visibilidade
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = BragaMintSurface,
                border = BorderStroke(1.dp, BragaMintBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isPublic) Icons.Default.Groups else Icons.Default.Diversity3,
                            contentDescription = null,
                            tint = BragaEmeraldDark,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = if (isPublic) "Público: Comunidade" else "Privado: Apenas Família",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BragaTextPrimary
                            )
                            Text(
                                text = if (isPublic) "Visível para todos os colegas" else "Visível para cuidadores e familiares",
                                fontSize = 14.sp,
                                color = BragaTextSecondary
                            )
                        }
                    }
                    Switch(
                        checked = isPublic,
                        onCheckedChange = { isPublic = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = BragaEmerald
                        )
                    )
                }
            }

            // Card de Live Preview
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Pré-visualização ao vivo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = BragaTextSecondary
                )

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = BragaMintSurface
                    ),
                    border = BorderStroke(1.dp, BragaMintBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(BragaMint),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "V",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = BragaEmeraldDark
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Você",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = BragaTextPrimary
                                    )
                                    Text(
                                        text = "Nível 1 • Agora mesmo",
                                        fontSize = 14.sp,
                                        color = BragaTextSecondary
                                    )
                                }
                            }

                            val (badgeIcon, badgeColor, badgeLabel) = getPostTypeBadge(selectedTemplate.postType)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = badgeColor.copy(alpha = 0.14f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = badgeIcon,
                                        contentDescription = badgeLabel,
                                        tint = badgeColor,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = badgeLabel,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = badgeColor
                                    )
                                }
                            }
                        }

                        Text(
                            text = selectedTemplate.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = BragaTextPrimary
                        )

                        Text(
                            text = descriptionText.ifBlank { selectedTemplate.defaultDescription },
                            fontSize = 16.sp,
                            color = BragaTextSecondary
                        )
                    }
                }
            }

            // Botão [ Publicar Conquista ]
            Button(
                onClick = {
                    onPublish(
                        selectedTemplate.title,
                        descriptionText.ifBlank { selectedTemplate.defaultDescription },
                        selectedTemplate.postType,
                        isPublic
                    )
                },
                enabled = publishState != SocialFeedViewModel.PublishState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BragaEmerald,
                    contentColor = Color.White
                )
            ) {
                if (publishState == SocialFeedViewModel.PublishState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Publicando...",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "Publicar Conquista",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private data class PostBadgeInfo(
    val icon: ImageVector,
    val color: Color,
    val label: String
)

private fun getPostTypeBadge(postType: String): PostBadgeInfo {
    return when (postType.lowercase(Locale.ROOT)) {
        "milestone" -> PostBadgeInfo(Icons.Default.EmojiEvents, Color(0xFFE5A800), "Conquista")
        "streak" -> PostBadgeInfo(Icons.Default.LocalFireDepartment, Color(0xFFE65100), "Sequência")
        "level_up" -> PostBadgeInfo(Icons.Default.Star, Success, "Subiu de Nível")
        "goal_hit" -> PostBadgeInfo(Icons.Default.TrendingUp, Color(0xFF1976D2), "Meta Batida")
        "weekly_recap" -> PostBadgeInfo(Icons.Default.MilitaryTech, Color(0xFF7B1FA2), "Semanal")
        else -> PostBadgeInfo(Icons.Default.Star, BragaEmerald, "Conquista")
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
            imageVector = Icons.Default.Diversity3,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = BragaTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun achievementIcon(categoryOrTitle: String): ImageVector = when {
    categoryOrTitle.contains("Passos", ignoreCase = true) -> Icons.AutoMirrored.Filled.DirectionsWalk
    categoryOrTitle.contains("Hidratação", ignoreCase = true) || categoryOrTitle.contains("Agua", ignoreCase = true) -> Icons.Default.WaterDrop
    categoryOrTitle.contains("Medicamentos", ignoreCase = true) || categoryOrTitle.contains("Remédios", ignoreCase = true) -> Icons.Default.Medication
    categoryOrTitle.contains("Constância", ignoreCase = true) || categoryOrTitle.contains("Disciplina", ignoreCase = true) -> Icons.Default.LocalFireDepartment
    categoryOrTitle.contains("Pressão", ignoreCase = true) -> Icons.Default.Favorite
    else -> Icons.Default.EmojiEvents
}
