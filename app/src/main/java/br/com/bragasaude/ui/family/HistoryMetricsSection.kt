package br.com.bragasaude.ui.family

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.WaterDrop
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
import br.com.bragasaude.data.local.VitalSignEntity
import br.com.bragasaude.domain.util.BloodPressureParser
import br.com.bragasaude.domain.util.GlucoseClassifier
import br.com.bragasaude.domain.model.BloodPressureCategory
import br.com.bragasaude.domain.model.GlucoseCategory
import br.com.bragasaude.domain.model.GlucoseContext
import java.text.SimpleDateFormat
import java.util.Locale

val BloodPressureCategory.displayColor: Color get() = Color(this.colorHex)
val GlucoseCategory.displayColor: Color get() = Color(this.colorHex)

/**
 * Seção de histórico de métricas para o painel do cuidador.
 * Exibe: Pressão Arterial, Glicemia (se diabetes), Hidratação e Passos.
 */
@Composable
fun HistoryMetricsSection(
    patientHasDiabetes: Boolean,
    patientHydrationTargetMl: Int,
    patientStepGoal: Int,
    bpRecords7d: List<VitalSignEntity>,
    bpRecords30d: List<VitalSignEntity>,
    glucoseRecords7d: List<VitalSignEntity>,
    glucoseRecords30d: List<VitalSignEntity> = emptyList(),
    hydrationDaysMet7d: Int,
    hydrationDaysTotal7d: Int,
    stepsDaysMet7d: Int,
    stepsDaysTotal7d: Int,
    hydrationDaysMet30d: Int,
    hydrationDaysTotal30d: Int,
    stepsDaysMet30d: Int,
    stepsDaysTotal30d: Int
) {
    var expanded by remember { mutableStateOf(true) }
    var selectedWindow by remember { mutableIntStateOf(7) }

    val bpRecords = if (selectedWindow == 7) bpRecords7d else bpRecords30d
    val glucoseRecords = if (selectedWindow == 7) glucoseRecords7d else glucoseRecords30d

    val currentHydrationDaysMet = if (selectedWindow == 7) hydrationDaysMet7d else hydrationDaysMet30d
    val currentHydrationDaysTotal = if (selectedWindow == 7) hydrationDaysTotal7d else hydrationDaysTotal30d
    val hydrationPercent = if (currentHydrationDaysTotal > 0) {
        (currentHydrationDaysMet.toFloat() / currentHydrationDaysTotal.toFloat() * 100).toInt().coerceIn(0, 100)
    } else 0

    val currentStepsDaysMet = if (selectedWindow == 7) stepsDaysMet7d else stepsDaysMet30d
    val currentStepsDaysTotal = if (selectedWindow == 7) stepsDaysTotal7d else stepsDaysTotal30d
    val stepsPercent = if (currentStepsDaysTotal > 0) {
        (currentStepsDaysMet.toFloat() / currentStepsDaysTotal.toFloat() * 100).toInt().coerceIn(0, 100)
    } else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Column {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Histórico de Medições",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Recolher" else "Expandir"
                        )
                    }
                }

                if (expanded) {
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedWindow == 7,
                            onClick = { selectedWindow = 7 },
                            label = { Text("7 dias", fontSize = 13.sp) }
                        )
                        FilterChip(
                            selected = selectedWindow == 30,
                            onClick = { selectedWindow = 30 },
                            label = { Text("30 dias", fontSize = 13.sp) }
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                }
            }

            if (expanded) {
                HorizontalDivider()

                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Pressão Arterial",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        if (bpRecords.isNotEmpty()) {
                            val latest = bpRecords.first()
                            val sys = latest.systolicPressure ?: 0
                            val dia = latest.diastolicPressure ?: 0
                            val classification = BloodPressureParser.classify(sys, dia)
                        Text(
                            classification.category.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    if (bpRecords.isEmpty()) {
                        Text(
                            "Nenhuma medição registrada neste período.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 24.dp)
                        )
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            bpRecords.take(10).forEach { record ->
                                BPRecordRow(record)
                            }
                        }
                    }
                }

                if (patientHasDiabetes && glucoseRecords.isNotEmpty()) {
                    HorizontalDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Bloodtype, contentDescription = null, tint = Color(0xFFFF6D00), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Glicemia",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            glucoseRecords.take(10).forEach { record ->
                                GlucoseRecordRow(record)
                            }
                        }
                    }
                }

                HorizontalDivider()
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        if (selectedWindow == 7) "Consistência Semanal (7 dias)" else "Consistência Mensal (30 dias)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    ConsistencyBar(
                        label = "Hidratação (meta: ${patientHydrationTargetMl}ml/dia)",
                        percentage = hydrationPercent,
                        daysMet = currentHydrationDaysMet,
                        totalDays = currentHydrationDaysTotal,
                        icon = Icons.Default.WaterDrop,
                        color = Color(0xFF29B6F6)
                    )

                    Spacer(Modifier.height(10.dp))

                    ConsistencyBar(
                        label = "Passos (meta: ${patientStepGoal} passos/dia)",
                        percentage = stepsPercent,
                        daysMet = currentStepsDaysMet,
                        totalDays = currentStepsDaysTotal,
                        icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                        color = Color(0xFF66BB6A)
                    )

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun BPRecordRow(record: VitalSignEntity) {
    val systolic = record.systolicPressure ?: 0
    val diastolic = record.diastolicPressure ?: 0
    val classification = BloodPressureParser.classify(systolic, diastolic)
    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(record.measuredAt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(classification.category.displayColor.copy(alpha = 0.08f))
            .border(1.dp, classification.category.displayColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            dateStr,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "$systolic/$diastolic mmHg",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = classification.category.displayColor,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        ChipLabel(classification.category.displayName, color = classification.category.displayColor)
    }
}

@Composable
private fun GlucoseRecordRow(record: VitalSignEntity) {
    val glucose = record.glucoseLevel ?: 0
    val classification = GlucoseClassifier.classify(glucose, GlucoseContext.FASTING)
    val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(record.measuredAt)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(classification.category.displayColor.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            dateStr,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(12.dp))
        Text(
            "$glucose mg/dL",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = classification.category.displayColor,
            modifier = Modifier.weight(1f, fill = false),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.weight(1f))
        ChipLabel(classification.category.displayName, color = classification.category.displayColor)
    }
}

@Composable
private fun ConsistencyBar(label: String, percentage: Int, daysMet: Int, totalDays: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f))
            Text("${percentage}%", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (percentage.toFloat() / 100f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text("$daysMet de $totalDays dias", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
    }
}

@Composable
private fun ChipLabel(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.heightIn(min = 24.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}

// ========================== HELPERS ==========================

@Composable
fun getBloodPressureCategoryColor(systolic: Int, diastolic: Int): Color {
    return BloodPressureParser.classify(systolic, diastolic).category.displayColor
}
