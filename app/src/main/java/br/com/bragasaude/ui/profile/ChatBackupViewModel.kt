package br.com.bragasaude.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.backup.ChatBackupRepository
import br.com.bragasaude.domain.backup.ChatBackupCodec
import br.com.bragasaude.domain.backup.ChatBackupStats
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ChatBackupViewModel @Inject constructor(
    private val backupRepository: ChatBackupRepository
) : ViewModel() {

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(backupRepository.isAutoBackupEnabled())
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _lastBackupAtMs = MutableStateFlow(backupRepository.getLastBackupTimestampMs())
    val lastBackupAtMs: StateFlow<Long?> = _lastBackupAtMs.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun consumeMessages() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    fun setStatus(message: String) {
        _errorMessage.value = null
        _statusMessage.value = message
    }

    fun setError(message: String) {
        _statusMessage.value = null
        _errorMessage.value = message
    }

    fun validatePassphraseForExport(passphrase: String): Boolean {
        val err = ChatBackupCodec.validatePassphrase(passphrase)
        if (err != null) {
            setError(err)
            return false
        }
        consumeMessages()
        return true
    }

    suspend fun prepareEncryptedBackupBytes(passphrase: String): ByteArray? {
        if (!validatePassphraseForExport(passphrase)) return null
        _isWorking.value = true
        val res = backupRepository.exportEncryptedBytes(passphrase)
        _isWorking.value = false
        return res.fold(
            onSuccess = { (bytes, stats) ->
                setStatus(formatBackupSummary("Backup gerado com sucesso", stats))
                bytes
            },
            onFailure = { err ->
                setError(err.message ?: "Não foi possível gerar o backup criptografado.")
                null
            }
        )
    }

    fun restoreFromFileBytes(rawBytes: ByteArray, passphrase: String) {
        if (passphrase.isBlank()) {
            setError("Informe a senha de recuperação para restaurar.")
            return
        }
        viewModelScope.launch {
            _isWorking.value = true
            val res = backupRepository.restoreFromEncryptedBytes(rawBytes, passphrase)
            _isWorking.value = false
            res.fold(
                onSuccess = { stats ->
                    setStatus(formatBackupSummary("Conversas restauradas com sucesso", stats))
                },
                onFailure = { err ->
                    setError(err.message ?: "Falha ao restaurar o arquivo de backup.")
                }
            )
        }
    }

    fun backupToGoogleDrive(accessToken: String, passphrase: String) {
        if (!validatePassphraseForExport(passphrase)) return
        viewModelScope.launch {
            _isWorking.value = true
            val res = backupRepository.backupToGoogleDrive(accessToken, passphrase)
            _isWorking.value = false
            res.fold(
                onSuccess = { stats ->
                    _lastBackupAtMs.value = backupRepository.getLastBackupTimestampMs() ?: stats.timestampMillis
                    setStatus(formatBackupSummary("Backup salvo no Google Drive", stats))
                },
                onFailure = { err ->
                    setError(err.message ?: "Não foi possível salvar no Google Drive agora.")
                }
            )
        }
    }

    fun restoreFromGoogleDrive(accessToken: String, passphrase: String) {
        if (passphrase.isBlank()) {
            setError("Informe a senha de recuperação usada no backup.")
            return
        }
        viewModelScope.launch {
            _isWorking.value = true
            val res = backupRepository.restoreFromGoogleDrive(accessToken, passphrase)
            _isWorking.value = false
            res.fold(
                onSuccess = { stats ->
                    setStatus(formatBackupSummary("Backup restaurado do Google Drive", stats))
                },
                onFailure = { err ->
                    setError(err.message ?: "Não foi possível restaurar do Google Drive agora.")
                }
            )
        }
    }

    fun toggleAutoBackup(enabled: Boolean, passphrase: String) {
        try {
            backupRepository.setAutoBackupConfig(enabled, passphrase)
            _autoBackupEnabled.value = enabled
            setStatus(
                if (enabled) {
                    "Backup automático semanal ativado (Wi-Fi e carregando)."
                } else {
                    "Backup automático semanal desativado."
                }
            )
        } catch (err: IllegalArgumentException) {
            setError(err.message ?: "Defina uma senha válida com 8 ou mais caracteres.")
        }
    }

    private fun formatBackupSummary(prefix: String, stats: ChatBackupStats): String =
        "$prefix: ${stats.familyCount} msgs da família, ${stats.orbConversationCount} conversas IA e ${stats.b2bMessageCount} msgs de programas."
}
