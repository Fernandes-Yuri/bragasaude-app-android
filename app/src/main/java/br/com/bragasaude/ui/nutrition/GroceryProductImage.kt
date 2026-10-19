package br.com.bragasaude.ui.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

@Composable
fun GroceryProductImage(url: String?, name: String, modifier: Modifier = Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        if (url.isNullOrBlank() || failed) {
            Text("Sem foto", style = MaterialTheme.typography.bodySmall)
        } else {
            AsyncImage(
                model = url,
                contentDescription = name,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Fit,
                onError = { failed = true }
            )
        }
    }
}
