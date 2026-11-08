package br.com.bragasaude.ui.family

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.*
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import br.com.bragasaude.data.remote.sync.userIds
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConsultationsViewModel @Inject constructor(
    private val repository: FamilyBridgeRepository,
    private val dao: FamilyDao,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val target = MutableStateFlow("")
    private val focus = MutableStateFlow("")
    private val clock = MutableStateFlow(System.currentTimeMillis())
    val upcoming = MutableStateFlow(true)
    private val limit = MutableStateFlow(20)
    val error = MutableStateFlow<String?>(null)
    val refreshing = MutableStateFlow(false)
    private val room = combine(auth.userIds(), target) { owner, patient -> owner to patient.ifBlank { owner } }
        .flatMapLatest { (owner, patient) ->
            if (owner == BragaConstants.GUEST_UID) flowOf("")
            else if (patient == owner) flowOf(owner)
            else dao.getActiveBindingsForCaregiver(owner).map { bindings ->
                patient.takeIf { p -> bindings.any { it.patientUserId == p } } ?: ""
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val records = combine(room, upcoming, limit, clock) { patient, future, count, now ->
        arrayOf(patient, future, count, now)
    }.flatMapLatest { values ->
        val patient = values[0] as String
        if (patient.isBlank()) flowOf(emptyList()) else dao.getConsultationsWindow(patient,
            values[3] as Long, values[1] as Boolean, (values[2] as Int) + 1)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val next = combine(auth.userIds(), clock) { owner, now -> owner to now }.flatMapLatest { (owner, now) ->
        dao.getActiveConsultationsForUser(owner, now).map { it.take(3) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val focused = combine(room, focus) { patient, id -> patient to id }.flatMapLatest { (patient, id) ->
        if (patient.isBlank() || id.isBlank()) flowOf(null)
        else dao.observeConsultation(id).map { it?.takeIf { c -> c.userId == patient } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch { while (true) { delay(60_000); clock.value = System.currentTimeMillis() } }
        viewModelScope.launch { auth.userIds().collectLatest { if (it != BragaConstants.GUEST_UID) refresh() } }
    }
    fun select(patient: String, id: String) { target.value = patient; focus.value = id; limit.value = 20; refresh() }
    fun more() { limit.value += 20 }
    fun visible(records: List<ConsultationEntity>) = records.take(limit.value)
    fun hasMore(records: List<ConsultationEntity>) = records.size > limit.value
    fun refresh() {
        viewModelScope.launch {
            if (refreshing.value) return@launch
            refreshing.value = true
            try {
                val patient = target.value.ifBlank { auth.currentUser?.uid ?: return@launch }
                repository.flushConsultations()
                repository.syncConsultations(patient)
                error.value = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (_: Exception) { error.value = "Não foi possível atualizar. Os registros salvos continuam disponíveis." }
            finally { refreshing.value = false }
        }
    }
}

internal fun consultationState(c: ConsultationEntity): String = when {
    c.pendingSync -> "Aguardando sincronização"
    !c.remoteConfirmed -> "Salva neste aparelho"
    else -> mapOf("SUGGESTED" to "Aguardando acompanhante", "ACCEPTED" to "Agendada",
        "CANCELLED" to "Cancelada", "COMPLETED" to "Realizada", "REJECTED" to "Recusada")[c.status] ?: c.status
}

@Composable
private fun ConsultationItem(c: ConsultationEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.title, style = MaterialTheme.typography.titleMedium)
            Text(SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).format(c.scheduledDate))
            Text(consultationState(c), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun PersonalConsultationsCard(onOpen: () -> Unit, viewModel: ConsultationsViewModel = hiltViewModel()) {
    val next by viewModel.next.collectAsState()
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Consultas", style = MaterialTheme.typography.titleMedium)
            val first = next.firstOrNull()
            if (first == null) Text("Nenhuma consulta agendada.") else {
                Text(first.title)
                Text(SimpleDateFormat("dd/MM 'às' HH:mm", Locale.getDefault()).format(first.scheduledDate))
                Text(consultationState(first), style = MaterialTheme.typography.labelMedium)
            }
            Text("Ver agenda", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ConsultationsScreen(patientId: String, consultationId: String, onBack: () -> Unit,
                        viewModel: ConsultationsViewModel = hiltViewModel()) {
    val records by viewModel.records.collectAsState()
    val focused by viewModel.focused.collectAsState()
    val upcoming by viewModel.upcoming.collectAsState()
    val error by viewModel.error.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    LaunchedEffect(patientId, consultationId) { viewModel.select(patientId, consultationId) }
    Scaffold(topBar = { EmeraldHeaderBanner(title = "Consultas", subtitle = if (patientId.isBlank()) "Minha agenda" else "Agenda do familiar", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = upcoming, onClick = { viewModel.upcoming.value = true }, label = { Text("Próximas") })
                FilterChip(selected = !upcoming, onClick = { viewModel.upcoming.value = false }, label = { Text("Histórico") })
                TextButton(onClick = viewModel::refresh, enabled = !refreshing) { Text("Atualizar") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                focused?.let { item("focused") { ConsultationItem(it) } }
                if (records.isEmpty() && focused == null) item { Text(if (upcoming) "Nenhuma consulta agendada." else "Nenhuma consulta no histórico.") }
                items(viewModel.visible(records).filter { it.id != focused?.id }, key = { it.id }) { ConsultationItem(it) }
                if (viewModel.hasMore(records)) item { TextButton(onClick = viewModel::more) { Text("Carregar mais") } }
            }
        }
    }
}
