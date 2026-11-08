package br.com.bragasaude.ui.family

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

private const val RESUME_SYNC_COOLDOWN_MS = 15_000L

data class HomeConsultationItem(
    val consultation: ConsultationEntity,
    val isPersonal: Boolean,
    val holderUserId: String,
    val holderLabel: String,
    val navigationPatientId: String
) {
    val headerLine: String
        get() = "$holderLabel · ${consultation.title}"
}

data class HomeConsultationsSummaryState(
    val items: List<HomeConsultationItem> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val watchedPatientIds: List<String> = emptyList()
)

internal fun resolveWatchedPatientLabel(
    profile: ProfileEntity?,
    binding: FamilyBindingEntity?,
    defaultLabel: String = "Familiar"
): String = profile?.fullName?.takeIf { it.isNotBlank() }
    ?: binding?.patientName?.takeIf { it.isNotBlank() }
    ?: defaultLabel

internal fun formatHomeConsultationDate(date: Date): String {
    val sdf = SimpleDateFormat("dd/MM 'às' HH'h'mm", Locale("pt", "BR"))
    return sdf.format(date)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ConsultationsViewModel @Inject constructor(
    private val repository: FamilyBridgeRepository,
    private val dao: FamilyDao,
    private val profileDao: ProfileDao,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val refreshLock = Mutex()
    private data class Query(val patient: String, val upcoming: Boolean, val limit: Int, val now: Long)
    private val target = MutableStateFlow("")
    private val focus = MutableStateFlow("")
    private val clock = MutableStateFlow(System.currentTimeMillis())
    private val initialHomeLoaded = MutableStateFlow(false)
    private var lastResumeSyncAtMs = 0L
    private var lastSyncedPatients: Set<String> = emptySet()
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
    val patientName = combine(auth.userIds(), room) { owner, patient -> owner to patient }
        .flatMapLatest { (owner, patient) ->
            if (patient.isBlank() || owner == BragaConstants.GUEST_UID) flowOf("familiar")
            else combine(
                profileDao.getProfile(patient).onStart { emit(null) },
                dao.getActiveBindingsForCaregiver(owner).onStart { emit(emptyList()) }
            ) { profile, bindings ->
                val binding = bindings.firstOrNull { it.patientUserId == patient }
                resolveWatchedPatientLabel(profile, binding, defaultLabel = "familiar")
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "familiar")
    val records = combine(room, upcoming, limit, clock) { patient, future, count, now ->
        Query(patient, future, count, now)
    }.flatMapLatest { values ->
        if (values.patient.isBlank()) flowOf(emptyList()) else dao.getConsultationsWindow(values.patient,
            values.now, values.upcoming, values.limit + 1)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val next = combine(auth.userIds(), clock) { owner, now -> owner to now }.flatMapLatest { (owner, now) ->
        dao.getActiveConsultationsForUser(owner, now).map { it.take(3) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val focused = combine(room, focus) { patient, id -> patient to id }.flatMapLatest { (patient, id) ->
        if (patient.isBlank() || id.isBlank()) flowOf(null)
        else dao.observeConsultation(id).map { it?.takeIf { c -> c.userId == patient } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val homeItemsFlow = combine(auth.userIds(), clock) { owner, now -> owner to now }
        .flatMapLatest { (owner, now) ->
            if (owner == BragaConstants.GUEST_UID) {
                flowOf(emptyList<HomeConsultationItem>() to emptyList<String>())
            } else {
                dao.getActiveBindingsForCaregiver(owner).onStart { emit(emptyList()) }
                    .flatMapLatest { bindings ->
                        val activeBindings = bindings
                            .filter { it.status == "ACTIVE" && it.patientUserId.isNotBlank() && it.patientUserId != owner }
                            .distinctBy { it.patientUserId }
                        val personalFlow = dao.getActiveConsultationsForUser(owner, now)
                            .onStart { emit(emptyList()) }
                            .map { list ->
                                list.filter {
                                    it.userId == owner &&
                                        it.status in setOf("SUGGESTED", "ACCEPTED") &&
                                        it.scheduledDate.time >= now
                                }.map { c ->
                                    HomeConsultationItem(
                                        consultation = c,
                                        isPersonal = true,
                                        holderUserId = owner,
                                        holderLabel = "Você",
                                        navigationPatientId = ""
                                    )
                                }
                            }
                        val patientFlows = activeBindings.map { binding ->
                            val patientId = binding.patientUserId
                            combine(
                                dao.getActiveConsultationsForUser(patientId, now).onStart { emit(emptyList()) },
                                profileDao.getProfile(patientId).onStart { emit(null) }
                            ) { consultations, profile ->
                                val label = resolveWatchedPatientLabel(profile, binding, defaultLabel = "Familiar")
                                consultations.filter {
                                    it.userId == patientId &&
                                        it.status in setOf("SUGGESTED", "ACCEPTED") &&
                                        it.scheduledDate.time >= now
                                }.map { c ->
                                    HomeConsultationItem(
                                        consultation = c,
                                        isPersonal = false,
                                        holderUserId = patientId,
                                        holderLabel = label,
                                        navigationPatientId = patientId
                                    )
                                }
                            }
                        }
                        val watchedIds = activeBindings.map { it.patientUserId }
                        if (patientFlows.isEmpty()) {
                            personalFlow.map { items ->
                                items.sortedWith(
                                    compareBy<HomeConsultationItem> { it.consultation.scheduledDate.time }
                                        .thenBy { it.consultation.id }
                                ).take(3) to watchedIds
                            }
                        } else {
                            combine(listOf(personalFlow) + patientFlows) { groups ->
                                val merged = groups.flatMap { it }
                                    .distinctBy { it.consultation.id }
                                    .sortedWith(
                                        compareBy<HomeConsultationItem> { it.consultation.scheduledDate.time }
                                            .thenBy { it.consultation.id }
                                    )
                                    .take(3)
                                merged to watchedIds
                            }
                        }
                    }
            }
        }

    val homeSummary: StateFlow<HomeConsultationsSummaryState> = combine(
        homeItemsFlow,
        refreshing,
        initialHomeLoaded,
        error
    ) { (items, watchedIds), isRefreshing, loaded, err ->
        HomeConsultationsSummaryState(
            items = items,
            loading = items.isEmpty() && (!loaded || isRefreshing),
            error = err,
            watchedPatientIds = watchedIds
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        HomeConsultationsSummaryState(loading = true)
    )

    init {
        viewModelScope.launch { while (true) { delay(60_000); clock.value = System.currentTimeMillis() } }
        viewModelScope.launch {
            auth.userIds().flatMapLatest { owner ->
                if (owner == BragaConstants.GUEST_UID) {
                    flowOf(owner to emptyList())
                } else {
                    dao.getActiveBindingsForCaregiver(owner).onStart { emit(emptyList()) }.map { bindings ->
                        owner to bindings
                            .filter { it.status == "ACTIVE" && it.patientUserId.isNotBlank() && it.patientUserId != owner }
                            .map { it.patientUserId }
                            .distinct()
                    }
                }
            }.collectLatest { (owner, watchedIds) ->
                if (owner == BragaConstants.GUEST_UID) {
                    lastSyncedPatients = emptySet()
                    initialHomeLoaded.value = true
                    return@collectLatest
                }
                val required = watchedIds.toSet() + owner
                lastSyncedPatients = lastSyncedPatients.intersect(required)
                if (!lastSyncedPatients.containsAll(required)) {
                    refresh()
                }
            }
        }
    }
    fun select(patient: String, id: String) { target.value = patient; focus.value = id; limit.value = 20; refresh() }
    fun more() { limit.value += 20 }
    fun visible(records: List<ConsultationEntity>) = records.take(limit.value)
    fun hasMore(records: List<ConsultationEntity>) = records.size > limit.value
    fun confirmLocal(id: String) {
        viewModelScope.launch {
            try { repository.confirmLocalConsultation(id); refresh() }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error.value = "A confirmação está salva e aguarda sincronização." }
        }
    }
    fun onResume(nowMs: Long = System.currentTimeMillis()) {
        val owner = auth.currentUser?.uid ?: return
        if (owner == BragaConstants.GUEST_UID) return
        if (nowMs - lastResumeSyncAtMs >= RESUME_SYNC_COOLDOWN_MS || error.value != null) {
            refresh()
        }
    }
    fun refresh() {
        viewModelScope.launch { refreshLock.withLock {
            val owner = auth.currentUser?.uid
            if (owner.isNullOrBlank() || owner == BragaConstants.GUEST_UID) {
                initialHomeLoaded.value = true
                return@withLock
            }
            refreshing.value = true
            try {
                val selectedPatient = target.value
                repository.flushConsultations()
                if (selectedPatient.isNotBlank()) {
                    repository.syncConsultations(selectedPatient)
                    if (selectedPatient != owner) {
                        runCatching { repository.syncPatientProfileIfMissing(selectedPatient) }
                    }
                } else {
                    runCatching { repository.syncBindingsForCaregiver(owner) }
                    var syncFailure: Exception? = null
                    try {
                        repository.syncConsultations(owner)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        syncFailure = e
                    }
                    val activeBindings = runCatching {
                        dao.getActiveBindingsForCaregiver(owner).first()
                    }.getOrDefault(emptyList())
                    val watchedIds = activeBindings
                        .filter { it.status == "ACTIVE" && it.patientUserId.isNotBlank() && it.patientUserId != owner }
                        .map { it.patientUserId }
                        .distinct()
                    for (patientId in watchedIds) {
                        try {
                            repository.syncConsultations(patientId)
                            runCatching { repository.syncPatientProfileIfMissing(patientId) }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (syncFailure == null) syncFailure = e
                        }
                    }
                    if (syncFailure != null) throw syncFailure
                    lastSyncedPatients = watchedIds.toSet() + owner
                    lastResumeSyncAtMs = System.currentTimeMillis()
                }
                error.value = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e
            } catch (_: Exception) { error.value = "Não foi possível atualizar. Os registros salvos continuam disponíveis." }
            finally {
                initialHomeLoaded.value = true
                refreshing.value = false
            }
        } }
    }
}

internal fun consultationState(c: ConsultationEntity): String = when {
    c.pendingSync -> "Aguardando sincronização"
    !c.remoteConfirmed -> "Salva neste aparelho"
    else -> mapOf("SUGGESTED" to "Aguardando acompanhante", "ACCEPTED" to "Agendada",
        "CANCELLED" to "Cancelada", "COMPLETED" to "Realizada", "REJECTED" to "Recusada")[c.status] ?: c.status
}

@Composable
private fun ConsultationItem(c: ConsultationEntity, onConfirmLocal: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(c.title, style = MaterialTheme.typography.titleMedium)
            Text(SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault()).format(c.scheduledDate))
            Text(consultationState(c), style = MaterialTheme.typography.labelMedium)
            if (!c.remoteConfirmed && !c.pendingSync && c.status in setOf("SUGGESTED", "ACCEPTED")) {
                Text("Confira os dados antes de incluir esta consulta na agenda compartilhada.")
                TextButton(onClick = { onConfirmLocal(c.id) }) { Text("Confirmar e sincronizar") }
            }
        }
    }
}

@Composable
fun PersonalConsultationsCard(
    onOpen: () -> Unit,
    onOpenConsultation: (patientId: String, consultationId: String) -> Unit = { _, _ -> onOpen() },
    viewModel: ConsultationsViewModel = hiltViewModel()
) {
    val summary by viewModel.homeSummary.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Próximas consultas", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onOpen) {
                    Text("Minha agenda", color = MaterialTheme.colorScheme.primary)
                }
            }
            when {
                summary.loading -> {
                    Text("Carregando compromissos…", style = MaterialTheme.typography.bodyMedium)
                }
                summary.items.isEmpty() -> {
                    if (summary.error == null) {
                        Text("Nenhuma consulta agendada.")
                    } else {
                        Text(
                            text = "Agenda indisponível. Toque para atualizar.",
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { viewModel.refresh() }
                        )
                    }
                }
                else -> {
                    summary.items.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onOpenConsultation(item.navigationPatientId, item.consultation.id)
                                }
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = item.headerLine,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${formatHomeConsultationDate(item.consultation.scheduledDate)} · ${consultationState(item.consultation)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                    summary.error?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable { viewModel.refresh() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConsultationsScreen(patientId: String, consultationId: String, onBack: () -> Unit,
                        viewModel: ConsultationsViewModel = hiltViewModel()) {
    val records by viewModel.records.collectAsState()
    val patientName by viewModel.patientName.collectAsState()
    val focused by viewModel.focused.collectAsState()
    val upcoming by viewModel.upcoming.collectAsState()
    val error by viewModel.error.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    LaunchedEffect(patientId, consultationId) { viewModel.select(patientId, consultationId) }
    Scaffold(topBar = { EmeraldHeaderBanner(title = "Consultas", subtitle = if (patientId.isBlank()) "Minha agenda" else "Agenda de $patientName", onBack = onBack) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = upcoming, onClick = { viewModel.upcoming.value = true }, label = { Text("Próximas") })
                FilterChip(selected = !upcoming, onClick = { viewModel.upcoming.value = false }, label = { Text("Histórico") })
                TextButton(onClick = viewModel::refresh, enabled = !refreshing) { Text("Atualizar") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (refreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
                focused?.let { item("focused") { ConsultationItem(it, viewModel::confirmLocal) } }
                if (records.isEmpty() && focused == null) item { Text(if (upcoming) "Nenhuma consulta agendada." else "Nenhuma consulta no histórico.") }
                items(viewModel.visible(records).filter { it.id != focused?.id }, key = { it.id }) { ConsultationItem(it, viewModel::confirmLocal) }
                if (viewModel.hasMore(records)) item { TextButton(onClick = viewModel::more) { Text("Carregar mais") } }
            }
        }
    }
}
