package br.com.bragasaude.data.remote.b2b

import br.com.bragasaude.domain.b2b.AppAccountMode
import br.com.bragasaude.domain.b2b.AppContextMode
import br.com.bragasaude.domain.b2b.B2BContextDescriptor
import br.com.bragasaude.domain.b2b.B2BContextsEnvelope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArraySet
import javax.inject.Inject
import javax.inject.Singleton

/** Server-authorized contexts. Session memory is cleared before publishing a new shell. */
@Singleton
class ActiveContextStore @Inject constructor() {
    private val _activeContext = MutableStateFlow(B2BContextDescriptor.personal())
    val activeContext: StateFlow<B2BContextDescriptor> = _activeContext.asStateFlow()
    private val _availableContexts = MutableStateFlow(listOf(B2BContextDescriptor.personal()))
    val availableContexts: StateFlow<List<B2BContextDescriptor>> = _availableContexts.asStateFlow()
    private val _accountMode = MutableStateFlow(AppAccountMode.PERSONAL)
    val accountMode: StateFlow<AppAccountMode> = _accountMode.asStateFlow()
    private val _isBusinessSession = MutableStateFlow(false)
    val isBusinessSession: StateFlow<Boolean> = _isBusinessSession.asStateFlow()
    private val _sessionGeneration = MutableStateFlow(0L)
    val sessionGeneration: StateFlow<Long> = _sessionGeneration.asStateFlow()
    private val resetListeners = CopyOnWriteArraySet<() -> Unit>()

    fun addSessionResetListener(listener: () -> Unit): () -> Unit {
        resetListeners.add(listener)
        return { resetListeners.remove(listener) }
    }

    private fun clearSessionMemory() {
        resetListeners.forEach { it() }
        _sessionGeneration.value += 1
    }

    private fun permitted(context: B2BContextDescriptor, mode: AppAccountMode): Boolean = when (context.parsedMode) {
        AppContextMode.PARTNER_OWNER -> mode != AppAccountMode.PERSONAL
        AppContextMode.PARTNER_PARTICIPANT -> mode != AppAccountMode.BUSINESS_ONLY
        AppContextMode.PERSONAL -> mode != AppAccountMode.BUSINESS_ONLY
    }

    private fun publish(userId: String, mode: AppAccountMode, business: Boolean, contexts: List<B2BContextDescriptor>) {
        val uid = userId.trim()
        val normalized = listOf(B2BContextDescriptor.personal(uid)) + contexts.filter {
            it.userId == uid && it.isPartnerContext && permitted(it, mode)
        }.distinctBy { it.cacheKey() }
        val previous = _activeContext.value
        val next = if (business) normalized.firstOrNull { it.parsedMode == AppContextMode.PARTNER_OWNER }
            else normalized.firstOrNull { it.cacheKey() == previous.cacheKey() && it.parsedMode != AppContextMode.PARTNER_OWNER }
        val resolved = next ?: B2BContextDescriptor.personal(uid)
        if (previous.userId != uid || _isBusinessSession.value != business ||
            (_accountMode.value != mode) ||
            (previous.isPartnerContext && normalized.none { it.cacheKey() == previous.cacheKey() })) {
            clearSessionMemory()
        }
        _accountMode.value = mode
        _availableContexts.value = normalized
        _activeContext.value = resolved
        _isBusinessSession.value = business
    }

    fun updateAvailableContexts(userId: String, contexts: List<B2BContextDescriptor>) {
        publish(userId, _accountMode.value, _isBusinessSession.value, contexts)
    }

    fun updateFromEnvelope(userId: String, envelope: B2BContextsEnvelope) {
        val mode = AppAccountMode.fromWire(envelope.accountMode)
        val business = when (mode) {
            AppAccountMode.BUSINESS_ONLY -> true
            AppAccountMode.PERSONAL -> false
            AppAccountMode.DUAL -> _activeContext.value.userId == userId.trim() && _isBusinessSession.value
        }
        publish(userId, mode, business, envelope.contexts)
    }

    fun updateAccountMode(mode: AppAccountMode, enterBusinessSession: Boolean? = null) {
        val business = when (mode) {
            AppAccountMode.BUSINESS_ONLY -> true
            AppAccountMode.PERSONAL -> false
            AppAccountMode.DUAL -> enterBusinessSession ?: _isBusinessSession.value
        }
        publish(_activeContext.value.userId, mode, business, _availableContexts.value)
    }

    fun setBusinessSessionActive(active: Boolean): Boolean {
        val mode = _accountMode.value
        if (active && mode == AppAccountMode.PERSONAL || !active && mode == AppAccountMode.BUSINESS_ONLY) return false
        publish(_activeContext.value.userId, mode, active, _availableContexts.value)
        return true
    }

    fun selectContext(userId: String, target: B2BContextDescriptor): Boolean {
        val uid = userId.trim()
        if (uid.isEmpty() || target.userId != uid || !permitted(target, _accountMode.value)) return false
        // Selecting a context never implicitly switches DUAL into the business shell.
        if (target.parsedMode == AppContextMode.PARTNER_OWNER && !_isBusinessSession.value) return false
        if (target.parsedMode != AppContextMode.PARTNER_OWNER && _isBusinessSession.value) return false
        val authorized = _availableContexts.value.firstOrNull { it.userId == uid && it.cacheKey() == target.cacheKey() }
            ?: return false
        _activeContext.value = authorized
        return true
    }

    fun resetToPersonal(userId: String = "") {
        clearSessionMemory()
        _accountMode.value = AppAccountMode.PERSONAL
        _isBusinessSession.value = false
        _activeContext.value = B2BContextDescriptor.personal(userId.trim())
        _availableContexts.value = listOf(_activeContext.value)
    }

    fun currentHeaderProgramId(): String? = _activeContext.value.takeIf {
        it.isPartnerContext && permitted(it, _accountMode.value) &&
            (it.parsedMode != AppContextMode.PARTNER_OWNER || _isBusinessSession.value)
    }?.programId?.trim()

    fun currentHeaderMode(): String? = if (currentHeaderProgramId() != null) _activeContext.value.parsedMode.name else null

    companion object {
        const val HEADER_PROGRAM_ID = "X-Braga-Program-Id"
        const val HEADER_CONTEXT_MODE = "X-Braga-Context-Mode"
    }
}
