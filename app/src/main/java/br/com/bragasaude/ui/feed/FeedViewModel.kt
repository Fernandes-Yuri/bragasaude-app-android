package br.com.bragasaude.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.SocialPostEntity
import br.com.bragasaude.data.remote.repository.SocialFeedRepository
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import br.com.bragasaude.util.BragaConstants

data class FeedUiState(
    val posts: List<SocialPostEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isPaginating: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = true
)

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: SocialFeedRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val guestId = BragaConstants.GUEST_UID
    val currentUserId: String
        get() = auth.currentUser?.uid ?: guestId

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var currentPage = 1
    private val pageSize = 20

    init {
        viewModelScope.launch {
            repository.getGlobalFeed()
                .catch { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
                .collect { posts ->
                    _uiState.update { state ->
                        state.copy(
                            posts = posts,
                            isLoading = false,
                            isPaginating = false
                        )
                    }
                }
        }
        loadFeed(reset = true)
    }

    fun loadFeed(reset: Boolean = false) {
        if (reset) {
            currentPage = 1
            _uiState.update { it.copy(isLoading = it.posts.isEmpty(), hasMore = true, error = null) }
        } else {
            if (_uiState.value.isPaginating || !_uiState.value.hasMore) return
            _uiState.update { it.copy(isPaginating = true) }
        }

        viewModelScope.launch {
            try {
                val offset = if (reset) 0 else (currentPage - 1) * pageSize
                val count = repository.fetchGlobalFeed(limit = pageSize, offset = offset, currentUserId = currentUserId)
                if (count < pageSize) {
                    _uiState.update { it.copy(hasMore = false, isPaginating = false) }
                }
                currentPage++
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isPaginating = false, error = e.message) }
            }
        }
    }

    fun onReact(postId: String, isReacted: Boolean) {
        viewModelScope.launch {
            if (isReacted) {
                repository.removeReaction(postId, currentUserId)
            } else {
                repository.reactToPost(postId, currentUserId)
            }
        }
    }
}
