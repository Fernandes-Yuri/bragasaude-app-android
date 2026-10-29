package br.com.bragasaude.ui.medication.doses

import android.app.AlarmManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import br.com.bragasaude.ui.components.BragaAlertDialog
import br.com.bragasaude.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun MedicationDosePanel(
    patientId: String,
    canTake: Boolean,
    canEdit: Boolean,
    isCaregiver: Boolean = false,
    patientName: String = "",
    medications: List<Pair<String, String>> = emptyList(),
    viewModel: MedicationDoseViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            kotlinx.coroutines.delay(30_000)
        }
    }
    LaunchedEffect(patientId) { viewModel.open(patientId) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycle, patientId) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    val context = LocalContext.current
    if (!state.accessVerified || state.patient != patientId) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(state.accessMessage ?: "Verificando acesso aos horários...")
            TextButton(onClick = { viewModel.refresh() }, enabled = !state.busy) { Text("Tentar novamente") }
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!isCaregiver) {
            Text("Medicamentos por horário", style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { viewModel.refresh() }, enabled = !state.busy) { Text("Atualizar horários") }
            if (Build.VERSION.SDK_INT >= 31 && !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())
                Text("Permita alarmes exatos nas configurações para avisos no horário previsto.")
            if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled())
                Text("As notificações estão desativadas. Consulte os horários nesta tela.")
            var preAlert by remember {
                mutableStateOf(
                    context.getSharedPreferences("dose_preferences", android.content.Context.MODE_PRIVATE)
                        .getBoolean("pre_alert", false)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = preAlert, onCheckedChange = { value ->
                    preAlert = value
                    context.getSharedPreferences("dose_preferences", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("pre_alert", value).apply()
                    viewModel.reconcileAlarms()
                })
                Text("Receber também um pré-aviso de 15 minutos")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Horários e Doses de Hoje",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = BragaTextPrimary
                )
                TextButton(onClick = { viewModel.refresh() }, enabled = !state.busy) {
                    Text("Atualizar", color = BragaEmeraldDark, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = BragaEmeraldDark) }
        state.selectedDate?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Revisando o dia $it", color = BragaTextSecondary)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { viewModel.today() }) { Text("Voltar para hoje") }
            }
        }
        val todayGroups = state.groups.filter { it.date == (state.selectedDate ?: LocalDate.now(ZoneId.of(it.zone)).toString()) }
        if (todayGroups.isEmpty()) {
            Text(
                if (isCaregiver) "Nenhum horário programado para hoje. Cadastre os medicamentos ou confira a programação."
                else "Nenhum grupo disponível. Atualize a programação ou confira o cadastro.",
                color = BragaTextSecondary
            )
        }
        todayGroups.forEach { group ->
            val available = group.items.filter { it.eligible && it.occurrenceId !in state.reserved }
            var selected by remember(group.id, group.digest, available.map { it.occurrenceId }) {
                mutableStateOf(available.map { it.occurrenceId }.toSet())
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = BragaCardSurface),
                border = BorderStroke(1.dp, BragaCardBorder)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f).padding(end = 10.dp)) {
                            Text(
                                text = group.time,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = BragaEmeraldDark
                            )
                            if (isCaregiver && patientName.isNotBlank()) {
                                Text(
                                    text = "Horário de $patientName",
                                    fontSize = 13.sp,
                                    color = BragaTextSecondary
                                )
                            }
                        }

                        val allRecorded = group.items.isNotEmpty() && group.items.all { it.state == "recorded" }
                        if (allRecorded) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = BragaMintSurface,
                                border = BorderStroke(1.dp, BragaMintBorder)
                            ) {
                                Text(
                                    "Administrado",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BragaEmeraldDark,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.wrapContentWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        } else if (available.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFFEF3C7)
                            ) {
                                Text(
                                    "Pendente (${available.size})",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFB45309),
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.wrapContentWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        } else if (group.items.any { it.reason == "quantity_required" }) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFFF1F5F9)
                            ) {
                                Text(
                                    "Definir dose",
                                    fontSize = 12.sp,
                                    color = BragaTextSecondary,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.wrapContentWidth().padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    group.items.forEach { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = item.occurrenceId in selected,
                                enabled = canTake && item in available && !state.busy,
                                onCheckedChange = { checked ->
                                    selected = if (checked) selected + item.occurrenceId else selected - item.occurrenceId
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = "${item.name} - ${item.units?.let { "$it unidade(s)" } ?: "1 un. (a confirmar)"}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = BragaTextPrimary
                                )
                                item.instruction?.let {
                                    Text(
                                        br.com.bragasaude.ui.medication.medicationInstructionLabel(it),
                                        fontSize = 13.sp,
                                        color = BragaTextSecondary
                                    )
                                }
                                val detail = when {
                                    item.occurrenceId in state.reserved && item.state != "recorded" -> "Confirmação salva neste aparelho"
                                    item.state == "recorded" -> "Registrado como tomado"
                                    item.reason != null -> when (item.reason) {
                                        "quantity_required" -> "Defina a quantidade de comprimidos por dose"
                                        "insufficient_stock" -> "Estoque insuficiente para registrar"
                                        "unsupported_unit" -> "Esta apresentação exige confirmação individual"
                                        else -> "Cadastro precisa de revisão"
                                    }
                                    else -> "Pendente de confirmação"
                                }
                                Text(
                                    detail,
                                    fontSize = 13.sp,
                                    color = if (item.state == "recorded") BragaEmeraldDark else BragaTextSecondary
                                )
                                if (canEdit && item.reason == "quantity_required") {
                                    Button(
                                        onClick = { viewModel.edit(item.medicationId) },
                                        enabled = !state.busy,
                                        colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                                        modifier = Modifier.padding(top = 4.dp).heightIn(min = 36.dp),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Confirmar 1 un. por dose", fontSize = 13.sp, color = Color.White)
                                    }
                                } else if (canEdit && item.state != "recorded") {
                                    TextButton(onClick = { viewModel.edit(item.medicationId) }, enabled = !state.busy) {
                                        Text("Ajustar quantidade por horário", fontSize = 13.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (!isCaregiver) {
                        var quick by remember(group.id, group.digest) {
                            mutableStateOf(
                                context.getSharedPreferences("dose_preferences", android.content.Context.MODE_PRIVATE)
                                    .getString(br.com.bragasaude.data.medicationdoses.DoseProtocol.reviewKey(viewModel.actorId(), group), null) == br.com.bragasaude.data.medicationdoses.DoseProtocol.reviewDigest(group)
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = quick,
                                enabled = canTake && group.items.all { it.state == "recorded" || it.eligible },
                                onCheckedChange = { checked ->
                                    quick = checked
                                    context.getSharedPreferences("dose_preferences", android.content.Context.MODE_PRIVATE).edit()
                                        .putString(
                                            br.com.bragasaude.data.medicationdoses.DoseProtocol.reviewKey(viewModel.actorId(), group),
                                            if (checked) br.com.bragasaude.data.medicationdoses.DoseProtocol.reviewDigest(group) else null
                                        ).apply()
                                }
                            )
                            Text("Revisei este grupo; permitir confirmar diretamente na notificação.", fontSize = 13.sp)
                        }
                    }

                    if (available.isNotEmpty()) {
                        Button(
                            onClick = { viewModel.submit(group, selected) },
                            enabled = canTake && !state.busy && selected.isNotEmpty() && !group.instant.isAfter(Instant.ofEpochMilli(nowMillis)),
                            colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            val btnText = if (isCaregiver) {
                                if (selected.size == available.size && available.size == group.items.size) {
                                    if (group.items.size == 1) "Confirmar dose administrada" else "Confirmar todas as doses administradas"
                                } else {
                                    "Confirmar ${selected.size} dose(s) administrada(s)"
                                }
                            } else {
                                if (selected.size == available.size && available.size == group.items.size) "Tomei todos"
                                else "Confirmar ${selected.size} selecionados"
                            }
                            Text(btnText, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                        }
                    }

                    if (group.items.isNotEmpty() && group.items.all { it.state == "recorded" }) {
                        Text("Todas as doses deste horário estão confirmadas.", color = BragaEmeraldDark)
                    }

                    if (group.instant.isAfter(Instant.ofEpochMilli(nowMillis))) {
                        Text(
                            "A confirmação será liberada no horário desta dose (${group.time}).",
                            fontSize = 12.sp,
                            color = BragaTextSecondary
                        )
                    }
                }
            }
        }
        state.intents.take(12).forEach { intent ->
            Text(
                when (intent.state) {
                    "RESOLVED" -> intent.result?.let {
                        runCatching { br.com.bragasaude.data.medicationdoses.DoseProtocol.friendlySummary(JSONObject(it)) }.getOrNull()
                    } ?: "Confirmação processada. Confira as doses acima."
                    "REVIEW" -> "Esta confirmação precisa de revisão."
                    "ACCESS_LOST" -> "O acesso aos dados desta pessoa foi perdido."
                    "CANCELLED" -> "Proposta cancelada."
                    "CONFIRMING" -> "Pedido enviado; aguardando o resultado do servidor."
                    else -> "Confirmação salva; aguardando sincronização."
                },
                fontSize = 13.sp,
                color = BragaTextSecondary
            )
            if (intent.state == "REVIEW") TextButton(onClick = { viewModel.review(intent) }, enabled = !state.busy) { Text("Revisar esta confirmação") }
            if (intent.state in setOf("PENDING", "WAITING")) TextButton(onClick = { viewModel.cancel(intent.id) }) { Text("Cancelar proposta") }
        }
    }
    val schedule = state.schedule
    if (schedule != null) {
        val slots = schedule.getJSONArray("slots")
        var values by remember(schedule.toString()) {
            mutableStateOf((0 until slots.length()).associate {
                val slot = slots.getJSONObject(it)
                slot.getString("local_time") to (if (slot.isNull("dose_units")) "1" else slot.getInt("dose_units").toString())
            })
        }
        BragaAlertDialog(
            onDismissRequest = { viewModel.closeEditor() },
            title = { Text("Quantidade por horário", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Defina as unidades para cada tomada diária:", fontSize = 14.sp)
                    if (values.isEmpty()) Text("O horário está inválido. Corrija os horários no cadastro do medicamento e atualize esta tela.")
                    values.forEach { (time, value) ->
                        OutlinedTextField(
                            value = value,
                            onValueChange = { text -> values = values + (time to text) },
                            label = { Text("$time - comprimidos (1 a 20)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !state.busy && values.isNotEmpty() && values.values.all { it.toIntOrNull() in 1..20 },
                    colors = ButtonDefaults.buttonColors(containerColor = BragaEmerald),
                    onClick = {
                        viewModel.save(JSONArray(values.toSortedMap().map { (time, units) ->
                            JSONObject().put("local_time", time)
                                .put("dose_units", units.toInt())
                                .put("quantity_confirmed", true)
                        }))
                    }
                ) { Text("Confirmar e Salvar", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeEditor() }) { Text("Cancelar") }
            }
        )
    }
}
