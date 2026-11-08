package br.com.bragasaude.ui.family

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.bragasaude.data.local.ConsultationEntity
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ConsultationSummaryItem(
    consultation: ConsultationEntity,
    reminderStatus: ConsultationReminderStatus,
    onCancel: () -> Unit
) {
    val date = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
        .format(consultation.scheduledDate)
    Surface(shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(consultation.title, style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(date, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onCancel) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Cancelar Consulta",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                }
            }
            val badgeColor = if (reminderStatus.active) Color(0xFF6A1B9A) else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(shape = RoundedCornerShape(6.dp), color = badgeColor.copy(alpha = 0.1f),
                modifier = Modifier.semantics(mergeDescendants = true) {
                    contentDescription = reminderStatus.accessibilityLabel
                }) {
                if (reminderStatus.active) {
                    FlowRow(Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (reminderStatus.scheduledTags.size > 1) "Avisos:" else "Aviso:",
                            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium, color = badgeColor)
                        Text(reminderStatus.timingLabel, style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium, color = badgeColor, softWrap = false, maxLines = 1)
                    }
                } else {
                    Text(reminderStatus.label, style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium, color = badgeColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
            }
        }
    }
}
