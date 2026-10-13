package br.com.bragasaude.ui.medication.doses

import android.app.AlarmManager
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.components.BragaAlertDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun MedicationDosePanel(patientId:String,canTake:Boolean,canEdit:Boolean,medications:List<Pair<String,String>> = emptyList(),
    viewModel:MedicationDoseViewModel=hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(patientId) { viewModel.open(patientId) }
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle,patientId) {
        val observer=androidx.lifecycle.LifecycleEventObserver { _,event ->
            if(event==androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val context=LocalContext.current
    Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Medicamentos por horário",style=MaterialTheme.typography.titleLarge)
        TextButton(onClick={viewModel.refresh()},enabled=!state.busy) { Text("Atualizar horários") }
        if(Build.VERSION.SDK_INT>=31 && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
            Text("Permita alarmes exatos nas configurações para avisos no horário previsto.")
        if(!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
            Text("As notificações estão desativadas. Consulte os horários nesta tela.")
        state.message?.let { Text(it,style=MaterialTheme.typography.bodyLarge) }
        val todayGroups=state.groups.filter { it.date==LocalDate.now(ZoneId.of(it.zone)).toString() }
        if(todayGroups.isEmpty()) Text("Nenhum grupo disponível. Atualize a programação ou confira o cadastro.")
        todayGroups.forEach { group ->
            val available=group.items.filter { it.eligible && it.occurrenceId !in state.reserved }
            var selected by remember(group.id,group.digest,available.map { it.occurrenceId }) {
                mutableStateOf(available.map { it.occurrenceId }.toSet())
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("${group.patientName} - ${group.time}",fontSize=22.sp)
                    Text("${available.size} pendências elegíveis • ${group.date}")
                    group.items.forEach { item ->
                        Row(Modifier.fillMaxWidth()) {
                            Checkbox(checked=item.occurrenceId in selected,enabled=canTake && item in available && !state.busy,
                                onCheckedChange={ checked -> selected=if(checked) selected+item.occurrenceId else selected-item.occurrenceId })
                            Column(Modifier.weight(1f)) {
                                Text("${item.name} - ${item.units?.let { "$it unidade(s)" } ?: "quantidade a conferir"}",fontSize=18.sp)
                                item.instruction?.let { Text(it) }
                                val detail=when {
                                    item.occurrenceId in state.reserved && item.state!="recorded" -> "Confirmação salva neste aparelho"
                                    item.state=="recorded" -> "Já registrado"
                                    item.reason!=null -> when(item.reason) {
                                        "quantity_required" -> "Confira a quantidade por horário"
                                        "insufficient_stock" -> "Estoque insuficiente para registrar"
                                        "unsupported_unit" -> "Esta apresentação exige confirmação individual"
                                        else -> "Cadastro precisa de revisão"
                                    }
                                    else -> "Pendente de confirmação"
                                }
                                Text(detail)
                                if(canEdit) TextButton(onClick={viewModel.edit(item.medicationId)},enabled=!state.busy) { Text("Conferir quantidade") }
                            }
                        }
                    }
                    Button(onClick={viewModel.submit(group,selected)},enabled=canTake && !state.busy && selected.isNotEmpty() && !group.instant.isAfter(Instant.now()),
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Text(if(selected.size==available.size && available.size==group.items.size) "Tomei todos"
                            else "Confirmar ${selected.size} selecionados")
                    }
                    if(group.instant.isAfter(Instant.now())) Text("A confirmação será liberada no horário desta dose.")
                }
            }
        }
        state.intents.take(12).forEach { intent ->
            Text(when(intent.state) {
                "RESOLVED" -> intent.message ?: "Resultado recebido."
                "REVIEW" -> "Precisa de revisão: ${intent.message}"
                "ACCESS_LOST" -> "Acesso perdido: ${intent.message}"
                "CANCELLED" -> "Proposta cancelada."
                else -> "Confirmação salva; aguardando sincronização.${intent.message?.let { " $it" } ?: ""}"
            })
            if(intent.state in setOf("PENDING","WAITING")) TextButton(onClick={viewModel.cancel(intent.id)}) { Text("Cancelar proposta") }
        }
        if(canEdit && medications.isNotEmpty()) {
            Text("Revisar programação",style=MaterialTheme.typography.titleMedium)
            medications.forEach { (id,name) -> TextButton(onClick={viewModel.edit(id)},enabled=!state.busy) { Text(name) } }
        }
    }
    val schedule=state.schedule
    if(schedule!=null) {
        val slots=schedule.getJSONArray("slots")
        var values by remember(schedule.toString()) { mutableStateOf((0 until slots.length()).associate {
            val slot=slots.getJSONObject(it);slot.getString("local_time") to if(slot.isNull("dose_units")) "" else slot.getInt("dose_units").toString()
        }) }
        BragaAlertDialog(onDismissRequest={viewModel.closeEditor()},title={Text("Quantidade por horário")},
            text={Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Confira as unidades cadastradas para cada tomada. A concentração em mg não define a quantidade.")
                if(values.isEmpty()) Text("O horário está inválido. Corrija os horários no cadastro do medicamento e atualize esta tela.")
                TextButton(onClick={viewModel.discardDraft()},enabled=!state.busy) { Text("Descartar rascunho e atualizar") }
                values.forEach { (time,value) -> OutlinedTextField(value=value,onValueChange={text->values=values+(time to text)},
                    label={Text("$time - unidades (1 a 20)")},singleLine=true,modifier=Modifier.fillMaxWidth()) }
            }},confirmButton={TextButton(enabled=!state.busy && values.isNotEmpty() && values.values.all { it.toIntOrNull() in 1..20 },
                onClick={viewModel.save(JSONArray(values.toSortedMap().map { (time,units)->JSONObject().put("local_time",time)
                    .put("dose_units",units.toInt()).put("quantity_confirmed",true) }))}) { Text("Conferi e salvar") }},
            dismissButton={TextButton(onClick={viewModel.closeEditor()}) { Text("Cancelar") }})
    }
}
