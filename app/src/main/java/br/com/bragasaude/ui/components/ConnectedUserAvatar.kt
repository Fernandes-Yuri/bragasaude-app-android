package br.com.bragasaude.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage

/** Foto publicada do usuário: mesma identidade visual no mural e nos vínculos. */
@Composable
fun ConnectedUserAvatar(
    userId: String?,
    name: String?,
    size: Dp = 48.dp,
    photoUrl: String? = null,
    avatarIdentifier: String? = null,
    viewModel: ConnectedUserAvatarViewModel = hiltViewModel()
) {
    val session by viewModel.session.collectAsState()
    val identity = session.avatars[userId]
    val lifecycleOwner = LocalLifecycleOwner.current
    val previousDescriptor = remember(userId, session.userId) { mutableStateOf(photoUrl to avatarIdentifier) }
    LaunchedEffect(userId, session.userId, photoUrl, avatarIdentifier) {
        val changed = previousDescriptor.value != (photoUrl to avatarIdentifier)
        previousDescriptor.value = photoUrl to avatarIdentifier
        if (!userId.isNullOrBlank()) viewModel.load(userId, force = changed)
    }
    DisposableEffect(lifecycleOwner, userId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START && !userId.isNullOrBlank()) viewModel.load(userId, force = true)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SharedProfileAvatar(name,
        photoUrl = if (identity != null) identity.photoUrl else photoUrl,
        avatarIdentifier = if (identity != null) identity.avatarIdentifier else avatarIdentifier,
        size = size)
}

@Composable
internal fun SharedProfileAvatar(name: String?, photoUrl: String?, avatarIdentifier: String?, size: Dp) {
    var failed by remember(photoUrl) { mutableStateOf(false) }
    val icon = when (avatarIdentifier) {
        "Person" -> Icons.Default.Person
        "Favorite" -> Icons.Default.Favorite
        "Accessibility" -> Icons.Default.AccessibilityNew
        "Star" -> Icons.Default.Star
        else -> null
    }
    Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center) {
        when {
            icon != null -> Icon(icon, contentDescription = "Avatar de ${name.orEmpty()}",
                tint = MaterialTheme.colorScheme.onSecondaryContainer)
            !photoUrl.isNullOrBlank() && !failed -> AsyncImage(
                model = photoUrl, contentDescription = "Foto de ${name.orEmpty()}",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                onError = { failed = true })
            else -> Text(name?.trim()?.firstOrNull()?.uppercase() ?: "F",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.titleMedium)
        }
    }
}
