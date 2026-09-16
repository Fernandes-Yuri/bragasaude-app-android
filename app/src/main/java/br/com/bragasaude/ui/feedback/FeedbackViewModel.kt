package br.com.bragasaude.ui.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.bragasaude.data.remote.repository.FeedbackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedbackViewModel @Inject constructor(
    private val repository: FeedbackRepository
) : ViewModel() {

    private val _category = MutableStateFlow("sugestao")
    val category = _category.asStateFlow()

    private val _title = MutableStateFlow("")
    val title = _title.asStateFlow()

    private val _message = MutableStateFlow("")
    val message = _message.asStateFlow()

    private val _screenshotBase64 = MutableStateFlow<String?>(null)
    val screenshotBase64 = _screenshotBase64.asStateFlow()

    private val _isVoiceTranscribed = MutableStateFlow(false)
    val isVoiceTranscribed = _isVoiceTranscribed.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _isSuccess = MutableStateFlow(false)
    val isSuccess = _isSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage = _errorMessage.asStateFlow()

    fun setCategory(newCategory: String) {
        _category.value = newCategory
    }

    fun setTitle(newTitle: String) {
        _title.value = newTitle
    }

    fun setMessage(newMessage: String) {
        _message.value = newMessage
    }

    fun setScreenshot(base64: String?) {
        _screenshotBase64.value = base64
    }

    fun onVoiceTranscriptionReceived(text: String) {
        if (text.isBlank()) return
        val current = _message.value.trim()
        _message.value = if (current.isEmpty()) text else "$current $text"
        _isVoiceTranscribed.value = true
    }

    fun clearVoiceNotice() {
        _isVoiceTranscribed.value = false
    }

    fun submitFeedback(onSuccess: () -> Unit) {
        val msg = _message.value.trim()
        if (msg.isEmpty()) {
            _errorMessage.value = "Por favor, digite ou fale sua mensagem antes de enviar."
            return
        }

        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val inputMethod = if (_isVoiceTranscribed.value) "voice_edited" else "text"
                val result = repository.sendFeedback(
                    category = _category.value,
                    message = msg,
                    title = _title.value.ifBlank { null },
                    inputMethod = inputMethod,
                    screenshotBase64 = _screenshotBase64.value
                )
                if (result.isSuccess) {
                    _isSuccess.value = true
                    _message.value = ""
                    _title.value = ""
                    _screenshotBase64.value = null
                    _isVoiceTranscribed.value = false
                    onSuccess()
                } else {
                    _errorMessage.value = "Não foi possível enviar agora. Tente novamente em instantes."
                }
            } catch (e: Exception) {
                _errorMessage.value = "Erro ao enviar: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun dismissSuccess() {
        _isSuccess.value = false
    }
}
