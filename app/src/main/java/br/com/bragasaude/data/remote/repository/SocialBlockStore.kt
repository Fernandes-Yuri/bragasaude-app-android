package br.com.bragasaude.data.remote.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Bloqueios locais isolados por conta, preservados ao reiniciar o aplicativo. */
@Singleton
class SocialBlockStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("social_blocks", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val revision = MutableStateFlow(0L)
    fun observe(userId: String) = revision.map { read(userId) }
    private fun read(userId: String): Set<String> = prefs.getStringSet(userId, emptySet())!!.toSet()
    suspend fun block(userId: String, authorId: String) = withContext(Dispatchers.IO) { mutex.withLock {
        require(userId != authorId && authorId.isNotBlank())
        check(prefs.edit().putStringSet(userId, read(userId) + authorId).commit()) {
            "Não foi possível salvar o bloqueio. Tente novamente."
        }
        revision.value += 1
    } }
    suspend fun clear(userId: String) = withContext(Dispatchers.IO) { mutex.withLock {
        check(prefs.edit().remove(userId).commit()) { "Não foi possível remover os bloqueios." }
        revision.value += 1
    } }
}
