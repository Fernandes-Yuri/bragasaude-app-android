package br.com.bragasaude.ui.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.LeagueCycleEntity
import br.com.bragasaude.data.local.LeagueMembershipEntity
import br.com.bragasaude.data.local.ProfileEntity
import br.com.bragasaude.data.remote.repository.LeagueRepository
import br.com.bragasaude.data.remote.repository.ProfileRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.map
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LeagueViewModel @Inject constructor(
    private val leagueRepository: LeagueRepository,
    private val profileRepository: ProfileRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val guestId = "00000000-0000-0000-0000-000000000000"
    val currentUserId: String
        get() = auth.currentUser?.uid ?: guestId

    val profile: StateFlow<ProfileEntity?> = profileRepository.getProfile(currentUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val currentLevel: Flow<Int> = profile.map { it?.currentLevel ?: 1 }.distinctUntilChanged()

    val activeCycle: StateFlow<LeagueCycleEntity?> = currentLevel.flatMapLatest { level ->
        leagueRepository.getActiveCycle(level)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val myMembership: StateFlow<LeagueMembershipEntity?> = activeCycle.flatMapLatest { cycle ->
        if (cycle != null) {
            leagueRepository.getMyMembership(currentUserId, cycle.id)
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val ranking: StateFlow<List<LeagueMembershipEntity>> = activeCycle.flatMapLatest { cycle ->
        if (cycle != null) {
            leagueRepository.getRanking(cycle.id)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Fase 3 — mensagem de encerramento de ciclo (promoção/manutenção/rebaixamento). */
    val cycleOutcomeMessage: SharedFlow<String> = leagueRepository.cycleOutcomeMessage

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            val level = profile.value?.currentLevel ?: 1
            leagueRepository.fetchAndSyncLeagueData(currentUserId, level)
            _isRefreshing.value = false
        }
    }
}
