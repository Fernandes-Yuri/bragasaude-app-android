package br.com.bragasaude.data.remote.b2b

import br.com.bragasaude.domain.b2b.AppContextMode
import br.com.bragasaude.domain.b2b.B2BContextDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Guardiao em memoria do contexto ativo da sessao (Uso Pessoal vs Programa Parceiro).
 *
 * Regras de seguranca (B2B-04):
 * - Inicia sempre em [AppContextMode.PERSONAL].
 * - Ausencia de contexto B2B nao emite nenhum header extra no [AuthInterceptor],
 *   preservando 100% os contratos do app publicado.
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
