package br.com.bragasaude.ui.reminders

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.remote.model.RemoteMedication
import br.com.bragasaude.domain.MedicationSchedule
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val medicationItems by viewModel.medications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    fun addToCalendar(med: RemoteMedication) {
        if (MedicationSchedule.times(med.scheduleTimes, med.scheduleTime).size > 1) {
            android.widget.Toast.makeText(context, "Os horários já têm lembretes no app. Para a agenda externa, crie um evento por horário.", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, "Tomar Remédio: ${med.name}")
            val description = buildString {
                append("Medicamento: ${med.name}\n")
                if (med.dosageMg != null) append("Dose: ${med.dosageMg}mg\n")
                if (med.pillQuantity != null) append("Quantidade: ${med.pillQuantity} comp.\n")
                append("\nRegistrado via Braga Saúde")
            }
            putExtra(CalendarContract.Events.DESCRIPTION, description)
            putExtra(CalendarContract.Events.EVENT_LOCATION, "Braga Saúde App")
            putExtra(CalendarContract.Events.ALL_DAY, false)
            val timeParts = med.scheduleTime?.split(":")
            if (timeParts?.size == 2) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, timeParts[0].toInt())
                    set(Calendar.MINUTE, timeParts[1].toInt())
                    set(Calendar.SECOND, 0)
                }
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, cal.timeInMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 15 * 60 * 1000)
            }
            putExtra("rrule", "FREQ=DAILY;INTERVAL=1")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Rotina de Medicamentos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Shield, contentDescription = null)
                    Text(
                        "Confirme a tomada das doses prescritas. O registro ajuda a manter sua rotina pontual e consistente.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            if (isLoading && medicationItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (medicationItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Medication, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Nenhum remédio cadastrado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "Para cadastrar novos remédios ou alterar horários, use o ícone 'Remédios' no menu superior da tela inicial.",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(medicationItems, key = { it.first.id ?: it.first.name }) { (med, taken, available, doses) ->
                        MedicationCard(
                            med = med,
                            isTaken = taken,
                            isAvailable = available,
                            onTake = {},
                            doses = doses,
                            onTakeDose = { time -> viewModel.takeMedication(med.id ?: "", time) },
                            onSyncCalendar = { addToCalendar(med) }
                        )
                    }
                    item {
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(
                                    "Para cadastrar novos remédios ou editar dosagens e horários, use a tela 'Remédios' no menu superior.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
fun MedicationCard(
    med: RemoteMedication, 
    isTaken: Boolean,
    isAvailable: Boolean,
    onTake: () -> Unit,
    doses: List<MedicationDoseState> = emptyList(),
    onTakeDose: (String) -> Unit = {},
    onSyncCalendar: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTaken) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) 
                            else MaterialTheme.colorScheme.surface 
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTaken) 0.dp else 1.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Ícone
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isTaken) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) 
                            else MaterialTheme.colorScheme.surfaceVariant,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isTaken) Icons.Default.CheckCircle else Icons.Default.MedicalServices,
                        contentDescription = null,
                        tint = if (isTaken) MaterialTheme.colorScheme.primary else Color.Gray,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(Modifier.width(16.dp))
                
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        med.name, 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.Bold,
                        color = if (isTaken) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                    val detail = buildString {
                        if (med.dosageMg != null) append("${med.dosageMg}mg • ")
                        if (med.pillQuantity != null) append("${med.pillQuantity} comp. • ")
                        append(med.scheduleTimes ?: med.scheduleTime ?: "--")
                    }
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (!isTaken) {
                    IconButton(onClick = onSyncCalendar, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Agenda", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            doses.forEach { dose ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(dose.time, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    when {
                        dose.taken -> Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primaryContainer) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text("Dose registrada", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        dose.available -> Button(
                            onClick = { onTakeDose(dose.time) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Registrar dose")
                        }
                        else -> OutlinedButton(
                            onClick = { onTakeDose(dose.time) },
                            modifier = Modifier.heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Registrar agora (${dose.time})")
                        }
                    }
                }
            }
            if (doses.isEmpty()) Text("Horário prescrito não configurado.")
        }
    }
}
