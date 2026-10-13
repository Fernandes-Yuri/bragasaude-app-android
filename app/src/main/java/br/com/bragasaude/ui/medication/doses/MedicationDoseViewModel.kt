package br.com.bragasaude.ui.medication.doses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.DoseIntentEntity
import br.com.bragasaude.data.medicationdoses.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class DoseScreenState(val groups:List<DoseGroup> = emptyList(),val intents:List<DoseIntentEntity> = emptyList(),
    val reserved:Set<String> = emptySet(),val busy:Boolean=false,val message:String?=null,
    val schedule:JSONObject?=null,val scheduleMedication:String?=null,val patient:String="")
@HiltViewModel
class MedicationDoseViewModel @Inject constructor(private val repository:MedicationDoseRepository):ViewModel() {
    private val _state=MutableStateFlow(DoseScreenState())
    val state:StateFlow<DoseScreenState> = _state.asStateFlow()
    private var watch:Job?=null
    fun open(patient:String) {
        if(patient==_state.value.patient) return
        watch?.cancel(); _state.value=DoseScreenState(patient=patient)
        watch=viewModelScope.launch {
            try {
                combine(repository.groups(patient),repository.intents(patient)) { groups,intents -> groups to intents }
                    .collect { (rows,intents) ->
                        val groups=rows.map { DoseGroup.parse(JSONObject(it.payload)) }
                        _state.update { it.copy(groups=groups,intents=intents,reserved=repository.reservations(patient)) }
                    }
            } catch(e:Exception) { _state.update { it.copy(message=e.message) } }
        }
        refresh()
    }
    fun refresh()=run {
        repository.refresh(_state.value.patient)
        repository.enqueue()
        "Programação atualizada."
    }
    fun submit(group:DoseGroup,selected:Set<String>)=run { repository.submit(group,selected) }
    fun cancel(id:String)=run { repository.cancel(id);"Cancelamento solicitado." }
    fun edit(medication:String)=run {
        val response=repository.schedule(_state.value.patient,medication)
        _state.update { it.copy(schedule=response,scheduleMedication=medication) }
        "Confira a quantidade em cada horário."
    }
    fun discardDraft()=run {
        val medication=requireNotNull(_state.value.scheduleMedication)
        repository.discardDraft(medication)
        val schedule=repository.schedule(_state.value.patient,medication)
        _state.update { it.copy(schedule=schedule) }
        "Programação remota recuperada."
    }
    fun closeEditor() { _state.update { it.copy(schedule=null,scheduleMedication=null) } }
    fun save(slots:JSONArray)=run {
        val s=_state.value
        val response=repository.saveSchedule(s.patient,requireNotNull(s.scheduleMedication),requireNotNull(s.schedule).getInt("schedule_revision"),slots)
        closeEditor();response
    }
    private fun run(action:suspend ()->String) {
        if(_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy=true,message=null) }
            try { val message=action();_state.update { it.copy(message=message) } }
            catch(e:kotlinx.coroutines.CancellationException) { throw e }
            catch(e:Exception) { _state.update { it.copy(message=e.message ?: "Não foi possível concluir.") } }
            finally { _state.update { it.copy(busy=false) } }
        }
    }
}
