package br.com.bragasaude.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.SocialPostEntity
import br.com.bragasaude.data.remote.repository.SocialFeedRepository
import br.com.bragasaude.data.remote.service.TelemetryService
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import br.com.bragasaude.util.BragaConstants

@HiltViewModel
class SocialFeedViewModel @Inject constructor(
    private val socialFeedRepository: SocialFeedRepository,
    private val telemetryService: TelemetryService,
    private val auth: FirebaseAuth
) : ViewModel() {

    sealed interface PublishState {
        data object Idle : PublishState
        data object Loading : PublishState
        data object Success : PublishState
        data class Error(val message: String) : PublishState
    }

    private val guestId = BragaConstants.GUEST_UID
    val currentUserId: String
        get() = auth.currentUser?.uid ?: guestId

    val posts: StateFlow<List<SocialPostEntity>> = socialFeedRepository.getGlobalFeed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(30000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    private val _publishState = MutableStateFlow<PublishState>(PublishState.Idle)
    val publishState: StateFlow<PublishState> = _publishState.asStateFlow()
    private val _feedError = MutableStateFlow<String?>(null)
    val feedError: StateFlow<String?> = _feedError.asStateFlow()

    init {
        refresh()
        telemetryService.logEvent(currentUserId, "SOCIAL_FEED", "OPEN")
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _feedError.value = null
            try {
                telemetryService.logEvent(currentUserId, "SOCIAL_FEED", "REFRESH")
                socialFeedRepository.fetchGlobalFeed(currentUserId = currentUserId)
            } catch (e: Exception) {
                android.util.Log.w("SocialFeedVM", "Refresh failed: ${e.message}")
                _feedError.value = e.message ?: "Não foi possível atualizar o mural."
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun reactionsForPost(postId: String) = socialFeedRepository.reactionsForPost(postId)

    private val reacting = mutableSetOf<String>()
    fun react(post: SocialPostEntity, reactionType: String) {
        if (!reacting.add(post.id)) return
        val userId = currentUserId
        viewModelScope.launch {
            try {
                val synced = socialFeedRepository.reactToPost(post.id, userId, reactionType)
                android.util.Log.i("SocialFeedVM", "reaction postId=${post.id} userId=$userId synced=$synced")
            }
            finally { reacting.remove(post.id) }
        }
    }

    /**
     * Criar nova publicação de conquista/meta.
     */
    fun createAchievementPost(
        title: String,
        description: String,
        postType: String,
        visibility: String = "PUBLIC",
        milestoneId: String? = null
    ) {
        if (_publishState.value == PublishState.Loading) return
        val userId = currentUserId
        viewModelScope.launch {
            _publishState.value = PublishState.Loading
            try {
                socialFeedRepository.createPost(
                    userId = userId,
                    userName = null,
                    postType = postType,
                    title = title,
                    description = description,
                    visibility = visibility,
                    relatedMilestoneId = milestoneId
                )
                telemetryService.logSocialFeed(userId, "CREATE", title, postType)
                _publishState.value = PublishState.Success
            } catch (e: Exception) {
                _publishState.value = PublishState.Error(
                    e.message ?: "Não foi possível publicar agora. Tente novamente."
                )
            }
        }
    }

    fun clearPublishState() {
        _publishState.value = PublishState.Idle
    }

    fun clearFeedError() {
        _feedError.value = null
    }
}
