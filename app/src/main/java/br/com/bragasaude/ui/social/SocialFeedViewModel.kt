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

@HiltViewModel
class SocialFeedViewModel @Inject constructor(
    private val socialFeedRepository: SocialFeedRepository,
    private val telemetryService: TelemetryService,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val guestId = "00000000-0000-0000-0000-000000000000"
    val currentUserId: String
        get() = auth.currentUser?.uid ?: guestId

    val posts: StateFlow<List<SocialPostEntity>> = socialFeedRepository.getGlobalFeed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(30000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refresh()
        telemetryService.logEvent(currentUserId, "SOCIAL_FEED", "OPEN")
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                telemetryService.logEvent(currentUserId, "SOCIAL_FEED", "REFRESH")
                socialFeedRepository.fetchGlobalFeed(currentUserId = currentUserId)
            } catch (e: Exception) {
                android.util.Log.w("SocialFeedVM", "Refresh failed: ${e.message}")
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    /** Estado otimista: reações aparecem instantaneamente na UI. */
    private val _optimisticReactions = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    fun toggleReaction(post: SocialPostEntity) {
        val isReacting = !post.hasUserReacted
        // Atualiza a UI imediatamente (optimistic)
        _optimisticReactions.value = _optimisticReactions.value.toMutableMap().apply {
            set(post.id, isReacting)
        }
        val action = if (isReacting) "LIKE" else "UNLIKE"
        telemetryService.logSocialFeed(currentUserId, action, post.id, post.postType)
        viewModelScope.launch {
            try {
                if (isReacting) {
                    socialFeedRepository.reactToPost(post.id, currentUserId)
                } else {
                    socialFeedRepository.removeReaction(post.id, currentUserId)
                }
            } catch (e: Exception) {
                // Reverte em caso de erro (pessimistic rollback)
                _optimisticReactions.value = _optimisticReactions.value.toMutableMap().apply {
                    set(post.id, !isReacting)
                }
            }
        }
    }

    /** Retorna o estado otimista da reação para um post. null = use o estado do post. */
    fun getOptimisticReaction(postId: String): Boolean? = _optimisticReactions.value[postId]
    
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
        telemetryService.logSocialFeed(currentUserId, "CREATE", title, postType)
        viewModelScope.launch {
            socialFeedRepository.createPost(
                userId = currentUserId,
                userName = currentUserId,
                postType = postType,
                title = title,
                description = description,
                visibility = visibility,
                relatedMilestoneId = milestoneId
            )
            refresh()
        }
    }
}
