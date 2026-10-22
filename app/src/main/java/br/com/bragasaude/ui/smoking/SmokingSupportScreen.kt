package br.com.bragasaude.ui.smoking

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import br.com.bragasaude.data.local.smoking.*
import br.com.bragasaude.domain.smoking.*
import br.com.bragasaude.ui.components.BragaAlertDialog
import kotlinx.coroutines.delay
import java.time.LocalDate

@Composable
fun SmokingSupportScreen(onBack: () -> Unit, viewModel: SmokingSupportViewModel = hiltViewModel()) {
    val state by viewModel.snapshot.collectAsState()
    val today by viewModel.today.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) { viewModel.refreshDate(); delay(30_000) }
        }
    }
    // Drafts belong to this owner and disappear on account changes or screen exit.
    key(state.ownerId) {
        var page by remember { mutableStateOf("main") }
        var editingDay by remember { mutableStateOf<SmokingDailyEntry?>(null) }
        var confirmation by remember { mutableStateOf<String?>(null) }
        var dayToDelete by remember { mutableStateOf<SmokingDailyEntry?>(null) }
        val leavePage = { page = "main"; viewModel.clearMessage() }
        BackHandler(page != "main") { leavePage() }
        SmokingSupportLayout(onBack = { if (page == "main") onBack() else leavePage() }) {
            if (!state.loaded && state.ownerId != null) {
                item { CircularProgressIndicator(Modifier.padding(24.dp)) }
            } else when (page) {
                "plan", "editPlan" -> item {
                    SmokingPlanForm(state.plan, today, page == "editPlan", busy) { start, target, reason, triggers, stage ->
                        viewModel.savePlan(start, target, reason, triggers, stage, page == "editPlan", leavePage)
                    }
                }
                "day" -> item {
                    SmokingDayForm(editingDay, today, busy) { date, smoked, quantity ->
                        viewModel.saveDay(date, smoked, quantity) { page = "main" }
                    }
                }
                "pause" -> item {
                    SmokingPause(state.plan?.personalReason, busy, onClose = leavePage, onRecord = { intensity ->
                        viewModel.recordCraving(intensity, leavePage)
                    })
                }
                else -> {
                    if (state.enabled) {
                        val plan = state.plan!!
                        val start = LocalDate.parse(plan.startedOn)
                        val zeroDays = state.days.count { !it.smoked && it.localDate >= start.toString() && it.localDate <= today.toString() }
                        item {
                            SupportCard {
                                Text("Um dia de cada vez", style = MaterialTheme.typography.titleLarge)
                                Text("Dias registrados sem fumar: $zeroDays", style = MaterialTheme.typography.titleMedium)
                                Text("Contamos somente os dias em que você confirmou que não fumou. Dias sem registro são desconhecidos.")
                                if (state.days.none { it.localDate == today.toString() }) Text("Você ainda não registrou como foi hoje.")
                                Button(onClick = { editingDay = state.days.firstOrNull { it.localDate == today.toString() }; page = "day" }, modifier = Modifier.fillMaxWidth()) { Text("Registrar meu dia") }
                                OutlinedButton(onClick = { page = "pause" }, modifier = Modifier.fillMaxWidth()) { Text("Estou com vontade agora") }
                            }
                        }
                        item {
                            SupportCard {
                                Text("Meu plano", style = MaterialTheme.typography.titleMedium)
                                Text(if (plan.stage == PreparationStage.PREPARING) "Quero me preparar" else "Já comecei")
                                Text("Início do acompanhamento: ${start.format(smokingDateFormat)}")
                                plan.quitTargetOn?.let { Text("Data-meta: ${LocalDate.parse(it).format(smokingDateFormat)}") }
                                plan.personalReason?.let { Text("Meu motivo: $it") }
                                val selected = SmokingTrigger.entries.filter { it.name in plan.triggers.split(',') }
                                if (selected.isNotEmpty()) Text("Situações difíceis: ${selected.joinToString { triggerLabel(it) }}")
                                TextButton(onClick = { page = "editPlan" }) { Text("Editar meu plano") }
                            }
                        }
                        item { Text("Meu diário", style = MaterialTheme.typography.titleLarge) }
                        if (state.days.isEmpty()) item { Text("Seu diário começa com o que você escolher registrar.") }
                        items(state.days, key = { it.localDate }) { day ->
                            SupportCard {
                                Text(LocalDate.parse(day.localDate).format(smokingDateFormat), style = MaterialTheme.typography.titleMedium)
                                Text(if (!day.smoked) "Não fumei" else day.cigaretteCount?.let { "Fumei — $it cigarro(s)" } ?: "Fumei — quantidade não informada")
                                Text("Fuso do registro: ${day.zoneId}", style = MaterialTheme.typography.bodySmall)
                                Row {
                                    TextButton(onClick = { editingDay = day; page = "day" }) { Text("Editar dia") }
                                    TextButton(onClick = { dayToDelete = day }) { Text("Remover dia") }
                                }
                            }
                        }
                    } else {
                        item {
                            SupportCard {
                                Text("Apoio para parar de fumar, no seu ritmo", style = MaterialTheme.typography.titleLarge)
                                Text("Organize seu plano pessoal, registre como foi seu dia e encontre uma pausa nos momentos de vontade. Você também pode usar este apoio se já parou de fumar.")
                                Text(SmokingSupportContent.limits)
                                Text(SmokingSupportContent.privacy)
                                if (state.ownerId == null) Text("Para ativar e guardar seu acompanhamento, entre em uma conta própria. O conteúdo de ajuda está disponível abaixo.")
                                else if (state.plan != null) {
                                    Text("Seu histórico foi mantido. Ao reativar, você continua o plano anterior.")
                                    Button(enabled = !busy, onClick = {
                                        viewModel.savePlan(state.plan!!.startedOn.let { LocalDate.parse(it).format(smokingDateFormat) }, "", "", emptySet(), PreparationStage.PREPARING, false, {})
                                    }, modifier = Modifier.fillMaxWidth()) { Text("Ativar meu apoio") }
                                } else Button(onClick = { page = "plan" }, modifier = Modifier.fillMaxWidth(), enabled = !busy) { Text("Preparar meu plano") }
                            }
                        }
                    }
                    item { SmokingHelp() }
                    if (state.ownerId != null && (state.settings != null || state.plan != null)) item {
                        SupportCard {
                            Text("Meu acompanhamento", style = MaterialTheme.typography.titleMedium)
                            Text("Desativar mantém seu diário e oculta o acesso da tela inicial. Apagar remove o plano e os registros deste apoio. Uma nova ativação continua disponível aqui.")
                            if (state.enabled) OutlinedButton(onClick = { confirmation = "disable" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Desativar apoio") }
                            OutlinedButton(onClick = { confirmation = "erase" }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("Apagar dados do apoio") }
                        }
                    }
                }
            }
            message?.let { text -> item { Text(text, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
        if (confirmation != null) BragaAlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (confirmation == "erase") "Apagar dados deste apoio?" else "Desativar apoio?") },
            text = { Text(if (confirmation == "erase") "O plano, o diário e os registros de vontade serão removidos deste celular. Os dados das outras áreas serão mantidos. Esta ação não pode ser desfeita." else "Seu histórico será mantido, e o cartão sairá da tela inicial. Você pode reativar nas Configurações, no Perfil ou em Planos.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { if (confirmation == "erase") viewModel.erase() else viewModel.disable(); confirmation = null }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancelar") } }
        )
        dayToDelete?.let { day -> BragaAlertDialog(
            onDismissRequest = { dayToDelete = null }, title = { Text("Remover este dia?") },
            text = { Text("O dia ${LocalDate.parse(day.localDate).format(smokingDateFormat)} ficará sem informação no diário.") },
            confirmButton = { TextButton(enabled = !busy, onClick = { viewModel.removeDay(LocalDate.parse(day.localDate)); dayToDelete = null }) { Text("Remover") } },
            dismissButton = { TextButton(onClick = { dayToDelete = null }) { Text("Cancelar") } }
        ) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmokingSupportLayout(onBack: () -> Unit, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Meu caminho sem cigarro", style = MaterialTheme.typography.titleLarge) }, navigationIcon = {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") }
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).imePadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
internal fun SupportCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
}

@Composable
internal fun SmokingHelp() {
    val uri = LocalUriHandler.current
    SupportCard {
        Text("Apoio e tratamento gratuito", style = MaterialTheme.typography.titleMedium)
        Text(SmokingSupportContent.treatment)
        Text(SmokingSupportContent.products)
        Text("Fonte: INCA — Tratamento do tabagismo. Conteúdo local v1, conferido em 22/10/2026.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { uri.openUri(SmokingSupportContent.sourceUrl) }) { Text("Consultar fonte do INCA (internet)") }
    }
}

internal fun triggerLabel(trigger: SmokingTrigger): String = when (trigger) {
    SmokingTrigger.STRESS -> "Estresse"
    SmokingTrigger.COFFEE -> "Café"
    SmokingTrigger.AFTER_MEALS -> "Após refeições"
    SmokingTrigger.SOCIAL -> "Situações sociais"
    SmokingTrigger.ALCOHOL -> "Bebida alcoólica"
}

@Composable
internal fun SmokingPlanForm(plan: SmokingSupportPlan?, today: LocalDate, editing: Boolean, busy: Boolean,
    onSave: (String, String, String, Set<SmokingTrigger>, PreparationStage) -> Unit) {
    var start by remember { mutableStateOf(plan?.startedOn?.let { LocalDate.parse(it).format(smokingDateFormat) } ?: today.format(smokingDateFormat)) }
    var target by remember { mutableStateOf(plan?.quitTargetOn?.let { LocalDate.parse(it).format(smokingDateFormat) }.orEmpty()) }
    var reason by remember { mutableStateOf(plan?.personalReason.orEmpty()) }
    var triggers by remember { mutableStateOf(SmokingTrigger.entries.filter { it.name in plan?.triggers.orEmpty().split(',') }.toSet()) }
    var stage by remember { mutableStateOf(plan?.stage ?: PreparationStage.PREPARING) }
    SupportCard {
        Text(if (editing) "Editar meu plano" else "Meu plano pessoal", style = MaterialTheme.typography.titleLarge)
        Text("Você escolhe como começar. Motivo, situações difíceis e data-meta são opcionais.")
        ChoiceRow("Quero me preparar", stage == PreparationStage.PREPARING) { stage = PreparationStage.PREPARING }
        ChoiceRow("Já comecei", stage == PreparationStage.STARTED) { stage = PreparationStage.STARTED }
        OutlinedTextField(value = start, onValueChange = { start = it.take(10) }, enabled = !editing && !busy, label = { Text("Início do acompanhamento") }, placeholder = { Text("DD/MM/AAAA") }, supportingText = { Text("Hoje ou uma data passada. Não é uma medida de abstinência.") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(value = target, onValueChange = { target = it.take(10) }, enabled = !busy, label = { Text("Data-meta para parar (opcional)") }, placeholder = { Text("DD/MM/AAAA") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Text("A data-meta é um planejamento. Ela não acrescenta dias sem fumar.", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(value = reason, onValueChange = { reason = it.take(300) }, enabled = !busy, label = { Text("Meu motivo (opcional)") }, supportingText = { Text("${reason.length}/300") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Text("Situações difíceis (opcional)", fontWeight = FontWeight.SemiBold)
        SmokingTrigger.entries.forEach { trigger ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = trigger in triggers, role = Role.Checkbox, onValueChange = { checked -> triggers = if (checked) triggers + trigger else triggers - trigger }), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = trigger in triggers, onCheckedChange = null)
                Text(triggerLabel(trigger), Modifier.padding(start = 8.dp))
            }
        }
        if (!editing) { Text(SmokingSupportContent.privacy); Text(SmokingSupportContent.limits) }
        Button(onClick = { onSave(start, target, reason, triggers, stage) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text(if (editing) "Salvar meu plano" else "Ativar meu apoio") }
    }
}

@Composable
internal fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 8.dp))
    }
}

@Composable
internal fun SmokingDayForm(entry: SmokingDailyEntry?, today: LocalDate, busy: Boolean, onSave: (String, Boolean, String) -> Unit) {
    var date by remember(entry) { mutableStateOf(entry?.localDate?.let { LocalDate.parse(it).format(smokingDateFormat) } ?: today.format(smokingDateFormat)) }
    var smoked by remember(entry) { mutableStateOf(entry?.smoked) }
    var quantity by remember(entry) { mutableStateOf(entry?.cigaretteCount?.takeIf { it > 0 }?.toString().orEmpty()) }
    SupportCard {
        Text("Como foi seu dia?", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(value = date, onValueChange = { date = it.take(10) }, enabled = !busy && entry == null, label = { Text("Data (DD/MM/AAAA)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        ChoiceRow("Não fumei", smoked == false) { smoked = false }
        ChoiceRow("Fumei", smoked == true) { smoked = true }
        if (smoked == true) OutlinedTextField(value = quantity, onValueChange = { quantity = it.take(10) }, enabled = !busy, label = { Text("Quantidade de cigarros (opcional)") }, supportingText = { Text("Deixe em branco se não souber a quantidade.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(SmokingSupportContent.products, style = MaterialTheme.typography.bodySmall)
        Button(onClick = { smoked?.let { onSave(date, it, quantity) } }, enabled = smoked != null && !busy, modifier = Modifier.fillMaxWidth()) { Text("Salvar meu dia") }
    }
}

@Composable
private fun SmokingPause(reason: String?, busy: Boolean, onClose: () -> Unit, onRecord: (CravingIntensity) -> Unit) {
    var deadline by remember { mutableStateOf<Long?>(null) }
    var remaining by remember { mutableIntStateOf(120) }
    var intensity by remember { mutableStateOf<CravingIntensity?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(deadline, lifecycle) {
        val end = deadline ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                remaining = ((end - SystemClock.elapsedRealtime()).coerceAtLeast(0) + 999).div(1000).toInt()
                if (remaining == 0) break
                delay(250)
            }
        }
    }
    SupportCard {
        Text("Uma pausa para você", style = MaterialTheme.typography.titleLarge)
        reason?.let { Text("Meu motivo: $it") }
        Text(SmokingSupportContent.pause)
        if (deadline != null) Text(if (remaining == 0) "O tempo da pausa terminou. Você decide seu próximo passo." else "Pausa: ${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.titleMedium)
        if (deadline == null) OutlinedButton(onClick = { deadline = SystemClock.elapsedRealtime() + 120_000 }, modifier = Modifier.fillMaxWidth()) { Text("Começar pausa de 2 minutos") }
        Text("Se quiser, registre como está a vontade agora. Esse registro não é um diagnóstico nem um dia sem fumar.")
        CravingIntensity.entries.forEach { value ->
            val label = when (value) { CravingIntensity.LOW -> "Baixa"; CravingIntensity.MEDIUM -> "Média"; CravingIntensity.HIGH -> "Alta" }
            ChoiceRow(label, intensity == value) { intensity = value }
        }
        Button(onClick = { intensity?.let(onRecord) }, enabled = intensity != null && !busy, modifier = Modifier.fillMaxWidth()) { Text("Registrar vontade e encerrar") }
        TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Pular e encerrar") }
    }
}
