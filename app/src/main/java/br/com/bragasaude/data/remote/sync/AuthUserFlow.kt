package br.com.bragasaude.data.remote.sync

import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** A observação acompanha login, logout e troca de conta durante a vida do ViewModel. */
fun FirebaseAuth.userIds(): Flow<String> = callbackFlow {
    trySend(currentUser?.uid ?: BragaConstants.GUEST_UID)
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser?.uid ?: BragaConstants.GUEST_UID)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}.distinctUntilChanged()
