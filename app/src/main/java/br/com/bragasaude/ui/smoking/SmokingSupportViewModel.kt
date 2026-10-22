package br.com.bragasaude.ui.smoking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.BragaDatabase
import br.com.bragasaude.data.local.smoking.*
import br.com.bragasaude.di.SmokingClock
import br.com.bragasaude.di.SmokingZone
import br.com.bragasaude.domain.smoking.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle
import javax.inject.Inject
import javax.inject.Provider

internal val smokingDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT)
internal fun parseSmokingDate(value: String): LocalDate = try { LocalDate.parse(value.trim(), smokingDateFormat) }
    catch (_: Exception) { throw IllegalArgumentException("Informe uma data válida no formato DD/MM/AAAA.") }

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SmokingSupportViewModel @Inject constructor(
    private val repository: SmokingSupportRepository,
    session: SmokingSupportSession,
    database: BragaDatabase,
    @SmokingClock private val clock: Clock,
    @SmokingZone private val zones: Provider<ZoneId>
) : ViewModel() {
    val snapshot = repository.snapshot.stateIn(viewModelScope, SharingStarted.Eagerly, SmokingSupportSnapshot())
    private val ownerProfile = session.owners.flatMapLatest { owner ->
        if (owner == null) flowOf(null) else database.profileDao().getProfile(owner)
    }
    val offerEligible = combine(snapshot, ownerProfile) { state, profile ->
        state.loaded && state.ownerId != null && profile?.userId == state.ownerId && profile.isSmoker &&
            (profile.userRole != "CAREGIVER" || profile.caregiverMode == "HYBRID") &&
            (state.settings?.offerDecision ?: OfferDecision.UNSEEN) == OfferDecision.UNSEEN
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val automaticOffer = combine(repository.offerRequest, snapshot, offerEligible) { request, state, eligible ->
        request != null && request == state.ownerId && eligible
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _today = MutableStateFlow(currentDate())
    val today = _today.asStateFlow()
    init {
        viewModelScope.launch {
            session.owners.collect {
                _message.value = null
            }
        }
        viewModelScope.launch { session.owners.drop(1).collect { repository.consumeOfferRequest() } }
    }
    private fun currentDate(): LocalDate = LocalDate.now(clock.withZone(zones.get()))
    fun refreshDate() { _today.value = currentDate() }
    fun clearMessage() { _message.value = null }
    private fun perform(success: String? = null, onDone: () -> Unit = {}, work: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _message.value = null
        viewModelScope.launch {
            try { work(); if (success != null) _message.value = success; onDone() }
            catch (e: CancellationException) { throw e }
            catch (e: IllegalArgumentException) { _message.value = e.message ?: "Confira os campos e tente novamente." }
            catch (_: Exception) { _message.value = "Não foi possível concluir. Tente novamente." }
            finally { _busy.value = false }
        }
    }
    fun answerOffer(onDone: () -> Unit = {}) = perform(onDone = {
        repository.consumeOfferRequest(); onDone()
    }) { repository.declineOffer() }
    fun savePlan(start: String, target: String, reason: String, triggers: Set<SmokingTrigger>, stage: PreparationStage,
                 editing: Boolean, onDone: () -> Unit) = perform(onDone = onDone) {
        val targetDate = target.trim().takeIf { it.isNotEmpty() }?.let(::parseSmokingDate)
        if (editing) repository.updatePlan(targetDate, reason, triggers, stage)
        else repository.activate(parseSmokingDate(start), targetDate, reason, triggers, stage)
    }
    fun saveDay(date: String, smoked: Boolean, quantity: String, onDone: () -> Unit) = perform(onDone = onDone) {
        val count = if (!smoked) 0 else quantity.trim().takeIf { it.isNotEmpty() }?.let {
            it.toIntOrNull()?.takeIf { number -> number > 0 } ?: throw IllegalArgumentException("Informe uma quantidade inteira positiva ou deixe em branco.")
        }
        repository.saveDay(parseSmokingDate(date), smoked, count)
        _message.value = if (smoked) "Seu registro foi salvo. Você pode continuar no seu ritmo." else "Seu dia foi registrado."
    }
    fun removeDay(date: LocalDate) = perform("Registro removido.") { repository.deleteDay(date) }
    fun disable() = perform("Apoio desativado. Seu histórico foi mantido.") { repository.disable() }
    fun erase() = perform("Os dados do apoio foram apagados.") { repository.erase() }
    fun recordCraving(intensity: CravingIntensity, onDone: () -> Unit) = perform("Registro salvo.", onDone) { repository.recordCraving(intensity) }
}
