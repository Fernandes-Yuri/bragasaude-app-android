package br.com.bragasaude.ui.milestones

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.model.RemoteMilestone
import br.com.bragasaude.data.remote.repository.MilestonesRepository
import br.com.bragasaude.data.remote.repository.ProfileRepository
import br.com.bragasaude.data.util.toRemote
import dagger.hilt.android.lifecycle.HiltViewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MilestonesViewModel @Inject constructor(
    private val milestonesRepository: MilestonesRepository,
    private val profileRepository: ProfileRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _milestones = MutableStateFlow<List<RemoteMilestone>>(emptyList())
    val milestones = _milestones.asStateFlow()

    private val _points = MutableStateFlow(0)
    val points = _points.asStateFlow()

    init {
        val userId = auth.currentUser?.uid ?: "00000000-0000-0000-0000-000000000000"
        
        viewModelScope.launch {
            milestonesRepository.getMilestones(userId).collectLatest { entities ->
                _milestones.value = entities.map { it.toRemote() }
            }
        }

        viewModelScope.launch {
            profileRepository.getProfile(userId).collectLatest { profile ->
                _points.value = profile?.pointsDiscipline ?: 0
            }
        }
    }

    fun loadData() {
        // Agora via Flow no init
    }
}
