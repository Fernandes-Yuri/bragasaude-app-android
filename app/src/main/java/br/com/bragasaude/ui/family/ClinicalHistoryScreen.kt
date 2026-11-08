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
import br.com.bragasaude.data.remote.sync.userIds
import br.com.bragasaude.ui.components.EmeraldHeaderBanner
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClinicalHistoryViewModel @Inject constructor(private val dao: VitalSignDao,
    private val family: FamilyDao, private val auth: FirebaseAuth) : ViewModel() {
    private val selection = MutableStateFlow("" to "PRESSURE")
    private val limit = MutableStateFlow(20)
    val records = combine(auth.userIds(), selection, limit) { owner, target, count -> Triple(owner, target, count) }
        .flatMapLatest { (owner, target, count) ->
            val (patient, metric) = target
            if (patient.isBlank()) flowOf(emptyList())
            else if (owner == patient) dao.metricWindow(patient, metric, count + 1)
            else family.getActiveBindingsForCaregiver(owner).flatMapLatest { bindings ->
                if (bindings.any { it.patientUserId == patient }) dao.metricWindow(patient, metric, count + 1)
                else flowOf(emptyList())
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun select(patient: String, metric: String) { selection.value = patient to metric; limit.value = 20 }
    fun more() { limit.value += 20 }
    fun visible(rows: List<VitalSignEntity>) = rows.take(limit.value)
    fun hasMore(rows: List<VitalSignEntity>) = rows.size > limit.value
}

@Composable
fun ClinicalHistoryScreen(patient: String, metric: String, onBack: () -> Unit,
                          viewModel: ClinicalHistoryViewModel = hiltViewModel()) {
    val rows by viewModel.records.collectAsState()
    LaunchedEffect(patient, metric) { viewModel.select(patient, metric) }
    Scaffold(topBar = { EmeraldHeaderBanner(title = if (metric == "GLUCOSE") "Glicemia" else "Pressão",
        subtitle = "Histórico do familiar", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rows.isEmpty()) item { Text("Nenhum registro disponível.") }
            items(viewModel.visible(rows), key = { it.localId }) { row ->
                if (metric == "GLUCOSE") GlucoseRecordRow(row) else BPRecordRow(row)
            }
            if (viewModel.hasMore(rows)) item { TextButton(onClick = viewModel::more) { Text("Carregar mais") } }
        }
    }
}
