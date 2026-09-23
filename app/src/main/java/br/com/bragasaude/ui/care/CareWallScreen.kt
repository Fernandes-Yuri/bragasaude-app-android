package br.com.bragasaude.ui.care

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.local.CareAuditEntity
import br.com.bragasaude.ui.theme.*
import br.com.bragasaude.util.BragaTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Mural de Cuidado Compartilhado — Activity Feed (Care OS — D62).
 *
 * Linha do tempo de quem cuidou do paciente:
 *   "Maria (Filha) registrou Losartana às 08:02"
 *   "Seu Antônio registrou pressão (12/8) às 14:30"
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CareWallScreen(
    onBack: () -> Unit,
    viewModel: CareOsViewModel = hiltViewModel()
) {
    val ui by viewModel.ui.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshWall() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mural de Cuidado", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshWall() }, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Atualizar mural")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BragaEmerald,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        containerColor = BragaBackground
    ) { padding ->
        if (!ui.isAuthenticated) {
            CareAuthenticationRequired(Modifier.padding(padding).padding(16.dp))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp)
            ) {
                item { CarePatientSelector(ui, viewModel::selectPatient) }
                ui.dailyBulletin?.let { bulletin ->
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = BragaMintSurface)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Boletim do dia", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text("Remédios: ${bulletin.medicationsTaken}/${bulletin.medicationsExpected}")
                                Text("Pressão mais recente: ${bulletin.latestBloodPressure ?: "sem registro"}")
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = BragaEmerald)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Hidratação: ${bulletin.hydrationMl} ml")
                                }
                            }
                        }
                    }
                }
                if (ui.correlations.isNotEmpty()) {
                    item { Text("Correlações preventivas", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    items(ui.correlations, key = { "${it.eventAt}|${it.eventType}" }) { correlation ->
                        Card(colors = CardDefaults.cardColors(containerColor = BragaEmergencyLight)) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text(correlation.observation, fontWeight = FontWeight.SemiBold)
                                correlation.evidence.forEach { Text("• $it", fontSize = 14.sp) }
                                Text("Associação temporal; não é diagnóstico.", fontSize = 12.sp, color = BragaTextSecondary)
                            }
                        }
                    }
                    ui.correlationsDisclaimer?.let { disclaimer ->
                        item { Text(disclaimer, fontSize = 12.sp, color = BragaTextSecondary) }
                    }
                }
                if (ui.wall.isEmpty()) item { EmptyWall(Modifier.heightIn(min = 240.dp)) }
                items(ui.wall, key = { it.id }) { entry ->
                    CareWallItem(entry)
                }
            }
        }
    }
}

@Composable
private fun EmptyWall(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Filled.VolunteerActivism,
                contentDescription = null,
                tint = BragaEmerald,
                modifier = Modifier.size(56.dp)
            )
            Text(
                "Ainda não há registros de cuidado",
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = BragaTextPrimary
            )
            Text(
                "Quando alguém registrar um remédio ou uma medida sua, " +
                    "a ação aparece aqui para toda a família acompanhar.",
                fontSize = 15.sp,
                color = BragaTextSecondary
            )
        }
    }
}

@Composable
private fun CareWallItem(entry: CareAuditEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = BragaCardSurface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Avatar do ator com ícone da ação
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(BragaMint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    actionIcon(entry.actionType),
                    contentDescription = null,
                    tint = BragaEmeraldDark,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    describe(entry),
                    fontSize = 16.sp,
                    color = BragaTextPrimary,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    formatTime(entry.occurredAt),
                    fontSize = 13.sp,
                    color = BragaTextSecondary
                )
            }
        }
    }
}

/** Ícone Material Symbols por tipo de ação (sem emojis, sempre). */
private fun actionIcon(actionType: String) = when {
    actionType.contains("MEDICATION", ignoreCase = true) -> Icons.Filled.Medication
    actionType.contains("VITAL", ignoreCase = true) ||
        actionType.contains("GLUCOSE", ignoreCase = true) ||
        actionType.contains("PRESSURE", ignoreCase = true) -> Icons.Filled.MonitorHeart
    actionType.contains("SYMPTOM", ignoreCase = true) ||
        actionType.contains("CHECKIN", ignoreCase = true) -> Icons.AutoMirrored.Filled.Comment
    else -> Icons.Filled.VolunteerActivism
}

/** Texto humano da linha: "Maria registrou Losartana às 08:02". */
private fun describe(entry: CareAuditEntity): String {
    val text = try {
        org.json.JSONObject(entry.detailsJson).optString("text").takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }
    val verb = when {
        entry.actionType.contains("TAKEN", ignoreCase = true) -> "registrou a dose de"
        entry.actionType.contains("REGISTERED", ignoreCase = true) -> "cadastrou"
        entry.actionType.contains("RESTOCK", ignoreCase = true) -> "reabasteceu"
        entry.actionType.contains("CHECKIN", ignoreCase = true) -> "fez o check-in"
        else -> "registrou"
    }
    val actor = entry.actorName.ifBlank { "Cuidador" }
    return if (text != null) "$actor $verb • $text" else "$actor $verb"
}

private fun formatTime(date: Date): String {
    val fmt = SimpleDateFormat("dd/MM 'às' HH:mm", Locale("pt", "BR"))
    fmt.timeZone = java.util.TimeZone.getTimeZone(BragaTime.ZONE)
    return fmt.format(date)
}
