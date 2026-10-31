package br.com.bragasaude.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.local.FamilyMessageEntity
import br.com.bragasaude.data.local.security.FamilyE2eeManager
import br.com.bragasaude.data.remote.repository.FamilyBridgeRepository
import br.com.bragasaude.util.BragaConstants
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel dedicado à camada de Chat Familiar com Criptografia Ponta a Ponta (E2EE - SPEC-13).
 *
 * Responsável por coordenar:
 * - Cifragem local (AES-256-GCM com chave derivada via ECDH + HKDF-SHA256) antes do envio;
 * - Decifragem local ao receber mensagens do Blind Relay (`gateway-api`);
 * - Retrocompatibilidade com mensagens legadas em texto plano (`encryption_version = 0`),
 *   sinalizando o aviso de formato legado.
 */
@HiltViewModel
class FamilyChatViewModel @Inject constructor(
    private val familyRepository: FamilyBridgeRepository,
    private val e2eeManager: FamilyE2eeManager,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _uiEvents = MutableSharedFlow<FamilyUiEvent>()
    val uiEvents: SharedFlow<FamilyUiEvent> = _uiEvents.asSharedFlow()

    val currentUserId: String
        get() = auth.currentUser?.uid ?: BragaConstants.GUEST_UID

    /**
     * Prepara o envelope cifrado E2EE (`{"v":1,"iv":"...","ciphertext":"..."}`) para transmissão.
     */
    fun encryptOutgoingMessage(
        patientId: String,
        plaintext: String,
        caregiverIdHint: String? = null
    ): String {
        val cleanText = plaintext.trim()
        require(cleanText.isNotEmpty() && cleanText.length <= 2000) {
            "Escreva uma mensagem de até 2000 caracteres."
        }
        return e2eeManager.encryptForConversation(
            senderUserId = currentUserId,
            patientId = patientId,
            plaintext = cleanText,
            caregiverIdHint = caregiverIdHint
        )
    }

    /**
     * Decifra uma mensagem recebida ou preserva o conteúdo legado com aviso de formato legado.
     */
    fun decodeIncomingMessage(
        message: FamilyMessageEntity,
        caregiverIdHint: String? = null
    ): FamilyE2eeManager.DecodedFamilyMessage {
        return e2eeManager.decryptForConversation(
            currentUserId = currentUserId.ifBlank { message.patientUserId },
            patientId = message.patientUserId,
            rawPayload = message.messageText,
            senderUserId = message.senderUserId,
            caregiverIdHint = caregiverIdHint
        )
    }

    /**
     * Observa as mensagens ativas da conversa de [patientId] garantindo decifragem ponta a ponta.
     */
    fun observeDecryptedMessages(
        patientId: String,
        limit: Int = Int.MAX_VALUE
    ): Flow<List<FamilyMessageEntity>> {
        return familyRepository.getRecentMessagesForPatient(patientId, limit).map { list ->
            list.map { msg ->
                if (FamilyE2eeManager.isEncryptedEnvelope(msg.messageText)) {
                    runCatching {
                        val decoded = decodeIncomingMessage(msg)
                        msg.copy(messageText = decoded.text)
                    }.getOrDefault(msg)
                } else {
                    msg
                }
            }
        }
    }

    /**
     * Envia uma mensagem no chat familiar cifrando-a localmente antes da sincronização remota.
     */
    fun sendEncryptedMessage(
        patientId: String,
        senderName: String,
        messageText: String,
        iconType: String = "LOVE",
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        if (messageText.isBlank()) {
            onComplete?.invoke(false)
            return
        }
        viewModelScope.launch {
            try {
                val created = familyRepository.createFamilyMessage(
                    patientUserId = patientId,
                    senderName = senderName,
                    messageText = messageText,
                    iconType = iconType
                )
                onComplete?.invoke(true)
                _uiEvents.emit(
                    FamilyUiEvent.Notice(
                        if (created.pendingSync) {
                            "Mensagem cifrada salva no aparelho, aguardando sincronização."
                        } else {
                            "Mensagem protegida enviada à família."
                        }
                    )
                )
            } catch (e: Exception) {
                onComplete?.invoke(false)
                _uiEvents.emit(FamilyUiEvent.Error(e.message ?: "Não foi possível enviar a mensagem."))
            }
        }
    }
}
