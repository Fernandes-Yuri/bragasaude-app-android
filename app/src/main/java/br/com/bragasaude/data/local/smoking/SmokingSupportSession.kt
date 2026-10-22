package br.com.bragasaude.data.local.smoking

import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/** An in-memory gate closes before logout cleanup, preventing late writes. */
@Singleton
class SmokingSupportSession @Inject constructor(private val auth: FirebaseAuth) {
    private val blocked = MutableStateFlow<String?>(null)
    fun currentUserId(): String? = auth.currentUser?.takeUnless {
        it.isAnonymous || it.uid == BragaConstants.GUEST_UID || it.uid == blocked.value
    }?.uid

    val owners: Flow<String?> = combine(callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebase ->
            if (firebase.currentUser?.uid != blocked.value) blocked.value = null
            trySend(currentUserId())
        }
        auth.addAuthStateListener(listener)
        trySend(currentUserId())
        awaitClose { auth.removeAuthStateListener(listener) }
    }, blocked) { owner, _ -> owner?.takeIf { it == currentUserId() } }.distinctUntilChanged()

    fun closeForCleanup() { blocked.value = auth.currentUser?.uid }
}
