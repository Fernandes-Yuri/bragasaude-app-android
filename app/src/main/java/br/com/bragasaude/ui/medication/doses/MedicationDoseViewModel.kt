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
    val selectedDate:String?=null,val schedule:JSONObject?=null,val scheduleMedication:String?=null,val patient:String="",
    val accessVerified:Boolean=false,val accessMessage:String?=null)
@HiltViewModel
class MedicationDoseViewModel @Inject constructor(private val repository:MedicationDoseRepository):ViewModel() {
    private val _state=MutableStateFlow(DoseScreenState())
    val state:StateFlow<DoseScreenState> = _state.asStateFlow()
    private var watch:Job?=null
    fun actorId()=runCatching { repository.actor() }.getOrDefault("")
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
            } catch(e:kotlinx.coroutines.CancellationException) { throw e
            } catch(e:Exception) { _state.update { it.copy(message=e.message) } }
        }
        refresh()
    }
    fun refresh()=run {
        val current=_state.value
        _state.update { it.copy(accessVerified=false,accessMessage=null) }
        try {
            repository.refresh(current.patient,if(current.selectedDate==null) 7 else 1,current.selectedDate)
            _state.update { if(it.patient==current.patient) it.copy(accessVerified=true,accessMessage=null) else it }
        } catch(e: DoseApiException) {
            _state.update { if(it.patient!=current.patient) it else it.copy(accessVerified=false,
                accessMessage=if(e.http==403 || e.http==401) "Não foi possível acessar os dados desta pessoa. Confira seu vínculo e sua conta." else "Não foi possível verificar o acesso. Confira a conexão e tente novamente.") }
            return@run "Não foi possível atualizar os horários."
        } catch(e: kotlinx.coroutines.CancellationException) { throw e
        } catch(e: Exception) {
            _state.update { if(it.patient!=current.patient) it else it.copy(accessVerified=false,accessMessage="Não foi possível verificar o acesso. Confira a conexão e tente novamente.") }
            return@run "Não foi possível atualizar os horários."
        }
        repository.enqueue()
        "Programação atualizada."
    }
    fun review(intent:DoseIntentEntity) {
        val group=DoseGroup.parse(JSONObject(intent.snapshot))
        _state.update { it.copy(selectedDate=group.date) }
        refresh()
    }
    fun today() { _state.update { it.copy(selectedDate=null) };refresh() }
    fun submit(group:DoseGroup,selected:Set<String>)=run {
        check(_state.value.accessVerified && _state.value.patient == group.patientId)
        repository.submit(group,selected)
    }
    fun reconcileAlarms()=run { repository.reschedule();"Preferência de avisos salva." }
    fun cancel(id:String)=run { repository.cancel(id) }
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
            catch(e:Exception) { _state.update { it.copy(message="Não foi possível concluir. Confira a conexão e tente novamente.") } }
            finally { _state.update { it.copy(busy=false) } }
        }
    }
}
