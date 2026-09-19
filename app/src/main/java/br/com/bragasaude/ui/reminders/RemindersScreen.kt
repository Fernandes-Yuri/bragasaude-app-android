package br.com.bragasaude.ui.reminders

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.data.remote.model.RemoteMedication
import java.util.*
import br.com.bragasaude.domain.MedicationSchedule

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val medicationItems by viewModel.medications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMedication by remember { mutableStateOf<RemoteMedication?>(null) }
    val saveError by viewModel.saveError.collectAsState()

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
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, cal.timeInMillis + 15 * 60 * 1000) // 15 min de duração
            }
            
            // Regra de recorrência diária para facilitar para o usuário
            putExtra("rrule", "FREQ=DAILY;INTERVAL=1")
            
            // Tenta forçar a exibição da agenda para salvar
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Painel de Medicamentos", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingMedication = null; showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Novo Medicamento")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Registre conforme a prescrição. O botão disponível não orienta antecipar a dose. XP: até 1 hora antes ou depois do horário.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            
            Spacer(Modifier.height(16.dp))

            if (isLoading && medicationItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (medicationItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Text("Nenhum medicamento cadastrado.", color = Color.Gray)
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
                            onEdit = { editingMedication = med; showAddDialog = true },
                            onDelete = { viewModel.deleteMedication(med.id ?: "") },
                            onSyncCalendar = { addToCalendar(med) }
                        )
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddMedicationDialog(
            initial = editingMedication,
            isSaving = isLoading,
            error = saveError,
            onDismiss = { if (!isLoading) showAddDialog = false },
            onConfirm = { name, dosage, dosageMg, pillQty, time ->
                viewModel.saveMedication(name, dosage, dosageMg, pillQty, time, editingMedication) { showAddDialog = false }
            }
        )
    }
}

@Composable
fun MedicationCard(
    med: RemoteMedication, 
    isTaken: Boolean,
    isAvailable: Boolean,
    onTake: () -> Unit,
    onEdit: () -> Unit = {},
    doses: List<MedicationDoseState> = emptyList(),
    onTakeDose: (String) -> Unit = {},
    onDelete: () -> Unit,
    onSyncCalendar: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isTaken) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f) 
                            else MaterialTheme.colorScheme.surface 
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isTaken) 0.dp else 1.dp),
        shape = MaterialTheme.shapes.medium
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
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }

                // Ações discretas
                Row {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Editar medicamento") }
                    if (!isTaken) {
                        IconButton(onClick = onSyncCalendar, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Agenda", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Remover", tint = Color.Gray.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            doses.forEach { dose ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(dose.time, style = MaterialTheme.typography.titleMedium)
                    when {
                        dose.taken -> Text("Registrada hoje", color = MaterialTheme.colorScheme.primary)
                        dose.available -> Button(onClick = { onTakeDose(dose.time) }) { Text("Registrar dose") }
                        else -> Text("Disponível perto do horário", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (doses.isEmpty()) Text("Edite o medicamento para definir um horário válido.")

        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remover Medicamento") },
            text = { Text("Deseja realmente remover o lembrete de ${med.name}?") },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Remover", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun AddMedicationDialog(
    initial: RemoteMedication? = null,
    isSaving: Boolean = false,
    error: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double?, Int?, String) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var dosage by remember(initial?.id) { mutableStateOf(initial?.dosage ?: "") }
    var dosageMg by remember(initial?.id) { mutableStateOf(initial?.dosageMg?.toString() ?: "") }
    var pillQty by remember(initial?.id) { mutableStateOf(initial?.pillQuantity?.toString() ?: "") }
    var time by remember(initial?.id) { mutableStateOf(initial?.scheduleTimes ?: initial?.scheduleTime ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Novo Medicamento" else "Editar Medicamento") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nome do Remédio") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dosageMg,
                        onValueChange = { dosageMg = it },
                        label = { Text("Dose (mg)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = pillQty,
                        onValueChange = { pillQty = it },
                        label = { Text("Qtd (Comp)") },
                        modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                OutlinedTextField(
                    value = time,
                    onValueChange = { time = it },
                    label = { Text("Horários prescritos (HH:mm)") },
                    placeholder = { Text("Ex: 08:00, 14:00, 20:00") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Text(
                    "Informe o horário prescrito. Você pode editar este lembrete depois.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, dosage, dosageMg.toDoubleOrNull(), pillQty.toIntOrNull(), time)
                },
                enabled = !isSaving && name.isNotBlank() && MedicationSchedule.parse(time) != null
            ) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) { Text("Cancelar") }
        }
    )
}
