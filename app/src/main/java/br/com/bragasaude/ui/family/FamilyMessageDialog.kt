package br.com.bragasaude.ui.family

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val QUICK_REMINDERS = listOf(
    QuickReminder("Lembre de beber água", Icons.Default.WaterDrop, "WATER"),
    QuickReminder("Hora do remédio", Icons.Default.Medication, "MED"),
    QuickReminder("Fez sua caminhada hoje?", Icons.AutoMirrored.Filled.DirectionsWalk, "WALK"),
    QuickReminder("Passando para desejar um ótimo dia!", Icons.Default.Favorite, "LOVE"),
    QuickReminder("Te amo!", Icons.Default.Star, "LOVE")
)

data class QuickReminder(val text: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val key: String)

/**
 * Diálogo de comunicação direta do cuidador.
 * Permite mensagens personalizadas livremente digitadas e lembretes rápidos predefinidos.
 * Sem emojis infantis — ícones vetoriais Material apenas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyMessageDialog(
    patientName: String,
    caregiverName: String,
    onDismiss: () -> Unit,
    onSendFreeMessage: (text: String, iconType: String) -> Unit,
    onSendQuickReminder: (text: String, iconType: String) -> Unit,
    isSending: Boolean = false
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var freeMessageText by remember { mutableStateOf("") }
    val maxChars = 200

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text("Recados para $patientName", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Forum,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Canal direto com $patientName",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        text = {
            Column {
                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    indicator = { _ -> },
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Livre", fontSize = 14.sp, fontWeight = if (selectedTab == 0) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Lembretes", fontSize = 14.sp, fontWeight = if (selectedTab == 1) FontWeight.SemiBold else FontWeight.Normal)
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (selectedTab == 0) {
                    // Mensagem Livre
                    OutlinedTextField(
                        value = freeMessageText,
                        onValueChange = { if (it.length <= maxChars) freeMessageText = it },
                        label = { Text("Escreva um recado para $patientName") },
                        placeholder = { Text("Ex: Vi que bateu a meta de água hoje! Muito orgulho!") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 160.dp),
                        singleLine = false,
                        maxLines = 5,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        trailingIcon = {
                            Text(
                                "${freeMessageText.length}/$maxChars",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (freeMessageText.length > maxChars * 0.9) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    // Lembretes Rápidos
                    Text(
                        "Toque em um lembrete rápido para enviar:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QUICK_REMINDERS.forEach { reminder ->
                            ReminderChip(
                                text = reminder.text,
                                icon = reminder.icon,
                                onClick = {
                                    onSendQuickReminder(reminder.text, reminder.key)
                                    onDismiss()
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (freeMessageText.isNotBlank() && selectedTab == 0) {
                        val iconType = when {
                            freeMessageText.contains("agua", ignoreCase = true) || freeMessageText.contains("água", ignoreCase = true) -> "WATER"
                            freeMessageText.contains("remedio", ignoreCase = true) || freeMessageText.contains("remédio", ignoreCase = true) -> "MED"
                            freeMessageText.contains("caminhada", ignoreCase = true) || freeMessageText.contains("caminhar", ignoreCase = true) -> "WALK"
                            else -> "LOVE"
                        }
                        onSendFreeMessage(freeMessageText.trim(), iconType)
                        freeMessageText = ""
                        onDismiss()
                    }
                },
                enabled = (freeMessageText.isNotBlank() && selectedTab == 0) && !isSending,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                if (isSending) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                }
                Text("Enviar recado", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ReminderChip(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
