package br.com.bragasaude.data.remote.b2b

import br.com.bragasaude.domain.b2b.AppAccountMode
import br.com.bragasaude.domain.b2b.AppContextMode
import br.com.bragasaude.domain.b2b.B2BContextDescriptor
import br.com.bragasaude.domain.b2b.B2BContextsEnvelope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guardiao em memoria do contexto ativo da sessao (Uso Pessoal vs Programa Parceiro)
 * e do modo da conta B2B-30 (`PERSONAL`, `BUSINESS_ONLY`, `DUAL`).
 *
 * Regras de seguranca (B2B-04 / B2B-30):
 * - Contas `PERSONAL` iniciam sempre em [AppContextMode.PERSONAL] sem emitir headers extras.
 * - Contas `BUSINESS_ONLY` iniciam automaticamente na Sessao Clinica (`isBusinessSession = true`)
 *   e priorizam o contexto [AppContextMode.PARTNER_OWNER] sem exibir a Home pessoal.
 * - Contas `DUAL` permitem alternancia explicita entre Sessao Clinica e Sessao Pessoal.
 * - Um contexto B2B so pode ser ativado se pertencer ao `userId` autenticado atual
 *   e possuir `programId` valido.
 */
@Singleton
class ActiveContextStore @Inject constructor() {

    private val _activeContext = MutableStateFlow(B2BContextDescriptor.personal())
    val activeContext: StateFlow<B2BContextDescriptor> = _activeContext.asStateFlow()

    private val _availableContexts = MutableStateFlow<List<B2BContextDescriptor>>(
        listOf(B2BContextDescriptor.personal())
    )
    val availableContexts: StateFlow<List<B2BContextDescriptor>> = _availableContexts.asStateFlow()

    private val _accountMode = MutableStateFlow(AppAccountMode.PERSONAL)
    val accountMode: StateFlow<AppAccountMode> = _accountMode.asStateFlow()

    private val _isBusinessSession = MutableStateFlow(false)
    val isBusinessSession: StateFlow<Boolean> = _isBusinessSession.asStateFlow()

    fun updateAvailableContexts(userId: String, contexts: List<B2BContextDescriptor>) {
        val cleanUserId = userId.trim()
        val normalized = buildList {
            add(B2BContextDescriptor.personal(cleanUserId))
            contexts
                .filter { it.userId == cleanUserId && it.isPartnerContext }
                .distinctBy { it.cacheKey() }
                .forEach { add(it) }
        }
        _availableContexts.value = normalized

        val current = _activeContext.value
        val refreshed = normalized.firstOrNull { it.cacheKey() == current.cacheKey() }
        if (current.userId != cleanUserId || refreshed == null) {
            _activeContext.value = B2BContextDescriptor.personal(cleanUserId)
        } else {
            _activeContext.value = refreshed
        }
    }

    fun updateFromEnvelope(userId: String, envelope: B2BContextsEnvelope) {
        val cleanUserId = userId.trim()
        val mode = AppAccountMode.fromWire(envelope.accountMode)
        _accountMode.value = mode
        updateAvailableContexts(cleanUserId, envelope.contexts)

        when (mode) {
            AppAccountMode.BUSINESS_ONLY -> {
                _isBusinessSession.value = true
                val ownerCtx = _availableContexts.value.firstOrNull {
                    it.parsedMode == AppContextMode.PARTNER_OWNER && it.userId == cleanUserId
                }
                if (ownerCtx != null) {
                    _activeContext.value = ownerCtx
                }
            }
            AppAccountMode.PERSONAL -> {
                _isBusinessSession.value = false
                if (_activeContext.value.parsedMode == AppContextMode.PARTNER_OWNER) {
                    _activeContext.value = B2BContextDescriptor.personal(cleanUserId)
                }
            }
            AppAccountMode.DUAL -> {
                if (envelope.defaultMode.equals(AppContextMode.PARTNER_OWNER.name, ignoreCase = true)) {
                    _isBusinessSession.value = true
                }
                if (_isBusinessSession.value) {
                    val ownerCtx = _availableContexts.value.firstOrNull {
                        it.parsedMode == AppContextMode.PARTNER_OWNER && it.userId == cleanUserId
                    }
                    if (ownerCtx != null && _activeContext.value.parsedMode != AppContextMode.PARTNER_OWNER) {
                        _activeContext.value = ownerCtx
                    }
                }
            }
        }
    }

    fun updateAccountMode(mode: AppAccountMode, enterBusinessSession: Boolean? = null) {
        val currentUid = _activeContext.value.userId.trim()
        _accountMode.value = mode
        when (mode) {
            AppAccountMode.BUSINESS_ONLY -> {
                _isBusinessSession.value = true
                val ownerCtx = _availableContexts.value.firstOrNull {
                    it.parsedMode == AppContextMode.PARTNER_OWNER &&
                        (currentUid.isEmpty() || it.userId == currentUid)
                }
                if (ownerCtx != null) {
                    _activeContext.value = ownerCtx
                }
            }
            AppAccountMode.PERSONAL -> {
                _isBusinessSession.value = false
                if (_activeContext.value.parsedMode == AppContextMode.PARTNER_OWNER) {
                    _activeContext.value = B2BContextDescriptor.personal(currentUid)
                }
            }
            AppAccountMode.DUAL -> {
                val targetBusiness = enterBusinessSession ?: _isBusinessSession.value
                _isBusinessSession.value = targetBusiness
                if (targetBusiness) {
                    val ownerCtx = _availableContexts.value.firstOrNull {
                        it.parsedMode == AppContextMode.PARTNER_OWNER &&
                            (currentUid.isEmpty() || it.userId == currentUid)
                    }
                    if (ownerCtx != null) {
                        _activeContext.value = ownerCtx
                    }
                } else if (_activeContext.value.parsedMode == AppContextMode.PARTNER_OWNER) {
                    _activeContext.value = B2BContextDescriptor.personal(currentUid)
                }
            }
        }
    }

    fun setBusinessSessionActive(active: Boolean): Boolean {
        val mode = _accountMode.value
        val cleanUid = _activeContext.value.userId.trim()
        return when {
            active && (mode == AppAccountMode.BUSINESS_ONLY || mode == AppAccountMode.DUAL) -> {
                _isBusinessSession.value = true
                val ownerCtx = _availableContexts.value.firstOrNull {
                    it.parsedMode == AppContextMode.PARTNER_OWNER &&
                        (cleanUid.isEmpty() || it.userId == cleanUid)
                }
                if (ownerCtx != null) {
                    _activeContext.value = ownerCtx
                }
                true
            }
            !active && mode != AppAccountMode.BUSINESS_ONLY -> {
                _isBusinessSession.value = false
                if (_activeContext.value.parsedMode == AppContextMode.PARTNER_OWNER) {
                    _activeContext.value = B2BContextDescriptor.personal(cleanUid)
                }
                true
            }
            else -> false
        }
    }

    /**
     * Ativa um contexto previamente autorizado para [userId].
     * Retorna `false` e mantem o modo pessoal se o contexto nao pertencer ao usuario
     * ou nao estiver na lista de contextos autorizados pelo servidor.
     */
    fun selectContext(userId: String, target: B2BContextDescriptor): Boolean {
        val cleanUserId = userId.trim()
        if (cleanUserId.isEmpty() || target.userId.trim() != cleanUserId) {
            return false
        }
        if (target.parsedMode == AppContextMode.PERSONAL) {
            _activeContext.value = B2BContextDescriptor.personal(cleanUserId)
            return true
        }
        if (!target.isPartnerContext) {
            return false
        }
        val authorized = _availableContexts.value.firstOrNull {
            it.userId == cleanUserId && it.cacheKey() == target.cacheKey()
        } ?: return false

        _activeContext.value = authorized
        return true
    }

    fun resetToPersonal(userId: String = "") {
        val cleanUserId = userId.trim()
        _accountMode.value = AppAccountMode.PERSONAL
        _isBusinessSession.value = false
        _activeContext.value = B2BContextDescriptor.personal(cleanUserId)
        _availableContexts.value = listOf(B2BContextDescriptor.personal(cleanUserId))
    }

    /**
     * Header `X-Braga-Program-Id` enviado ao gateway somente quando o contexto
     * ativo e um programa parceiro autorizado. No modo pessoal retorna `null`.
     */
    fun currentHeaderProgramId(): String? {
        val current = _activeContext.value
        return if (current.isPartnerContext) current.programId?.trim() else null
    }

    /**
     * Header `X-Braga-Context-Mode` enviado apenas em contexto B2B ativo.
     */
    fun currentHeaderMode(): String? {
        val current = _activeContext.value
        return if (current.isPartnerContext) current.parsedMode.name else null
    }

    companion object {
        const val HEADER_PROGRAM_ID = "X-Braga-Program-Id"
        const val HEADER_CONTEXT_MODE = "X-Braga-Context-Mode"
    }
}
