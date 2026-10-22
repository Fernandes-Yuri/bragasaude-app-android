package br.com.bragasaude.ui.components

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.api.BragaApiClient
import br.com.bragasaude.data.remote.api.ProfileAvatarIdentity
import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AvatarSession(val userId: String?, val avatars: Map<String, ProfileAvatarIdentity> = emptyMap())

@HiltViewModel
class ConnectedUserAvatarViewModel @Inject constructor(
    private val api: BragaApiClient,
    private val auth: FirebaseAuth
) : ViewModel() {
    private val _session = MutableStateFlow(AvatarSession(auth.currentUser?.uid))
    val session = _session.asStateFlow()
    private val jobs = mutableMapOf<String, Job>()
    private val loadedAt = mutableMapOf<String, Long>()
    private val listener = FirebaseAuth.AuthStateListener {
        val uid = auth.currentUser?.uid
        if (_session.value.userId != uid) {
            jobs.values.forEach { it.cancel() }
            jobs.clear()
            loadedAt.clear()
            _session.value = AvatarSession(uid)
        }
    }
    init { auth.addAuthStateListener(listener) }

    fun load(userId: String, force: Boolean = false) {
        val uid = auth.currentUser?.uid ?: return
        if (uid == BragaConstants.GUEST_UID || userId.isBlank() || jobs[userId]?.isActive == true) return
        val now = System.nanoTime()
        if (!force && now - (loadedAt[userId] ?: Long.MIN_VALUE / 2) < 30_000_000_000L) return
        jobs[userId] = viewModelScope.launch {
            try {
                val avatar = api.getProfileAvatar(userId)
                if (auth.currentUser?.uid == uid && _session.value.userId == uid) {
                    _session.value = _session.value.copy(avatars = _session.value.avatars + (userId to avatar))
                    loadedAt[userId] = System.nanoTime()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Mantém a identidade já carregada durante indisponibilidade temporária.
            }
        }
    }

    override fun onCleared() {
        auth.removeAuthStateListener(listener)
        super.onCleared()
    }
}
