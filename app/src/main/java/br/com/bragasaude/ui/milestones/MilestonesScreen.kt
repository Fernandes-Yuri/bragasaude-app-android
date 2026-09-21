package br.com.bragasaude.ui.milestones

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun MilestonesScreen(
    viewModel: MilestonesViewModel = hiltViewModel()
) {
    val milestones by viewModel.milestones.collectAsState()
    val points by viewModel.points.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Text("Suas Conquistas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            
            // Points Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, br.com.bragasaude.ui.theme.BragaEmeraldDark))).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
                    Text("$points", color = Color.White, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text("Pontos de Disciplina", color = Color.White)
                }
            }
        }

        item {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Vitórias Recentes",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }

        if (milestones.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Suas próximas conquistas começam com a rotina de hoje.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(milestones) { milestone ->
                MilestoneItem(milestone)
            }
        }
    }
}

@Composable
fun MilestoneItem(milestone: br.com.bragasaude.data.remote.model.RemoteMilestone) {
    var showDetails by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val category = (milestone.badgeType.orEmpty() + " " + milestone.title).lowercase()
    val icon = when {
        listOf("water", "hydr", "água", "hidr").any { it in category } -> Icons.Default.WaterDrop
        listOf("step", "walk", "passo").any { it in category } -> Icons.AutoMirrored.Filled.DirectionsWalk
        listOf("med", "press").any { it in category } -> Icons.Default.Shield
        listOf("streak", "consist", "sequ").any { it in category } -> Icons.Default.LocalFireDepartment
        else -> Icons.Default.EmojiEvents
    }
    Card(
        onClick = { showDetails = true },
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(milestone.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                milestone.description?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                milestone.achievedAt?.let {
                    Text(it.take(10), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (showDetails) {
        AlertDialog(onDismissRequest = { showDetails = false },
            icon = { Icon(icon, contentDescription = null) },
            title = { Text(milestone.title) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                milestone.description?.let { Text(it) }
                milestone.achievedAt?.let { Text("Conquistado em " + it.take(10), style = MaterialTheme.typography.labelLarge) }
                Text("Cada dia de cuidado conta.")
            } },
            confirmButton = { TextButton(onClick = {
                val intent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, milestone.title + " — Braga Saúde") }
                context.startActivity(Intent.createChooser(intent, "Compartilhar conquista"))
            }) { Text("Compartilhar") } },
            dismissButton = { TextButton(onClick = { showDetails = false }) { Text("Fechar") } })
    }
}
