package dev.heyduk.relay.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.heyduk.relay.data.repository.ChatRepository
import dev.heyduk.relay.domain.model.ChatMessage
import dev.heyduk.relay.voice.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the per-session chat screen.
 * Collects messages reactively from ChatRepository and handles sending.
 */
class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val ttsManager: TtsManager,
    private val kuerzel: String
) : ViewModel() {

    data class ChatUiState(
        val messages: List<ChatMessage> = emptyList(),
        val kuerzel: String = "",
        val isSending: Boolean = false,
        val errorMessage: String? = null,
        val sendingCallbackIds: Set<Long> = emptySet(),
        val ttsPlayingMessageId: Long? = null
    )

    private data class LocalState(
        val isSending: Boolean = false,
        val errorMessage: String? = null
    )

    private val _localState = MutableStateFlow(LocalState())
    private val _sendingCallbacks = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.messagesForSession(kuerzel),
        _localState,
        _sendingCallbacks,
        ttsManager.speakingMessageId
    ) { messages, local, sendingIds, ttsId ->
        ChatUiState(
            messages = messages,
            kuerzel = kuerzel,
            isSending = local.isSending,
            errorMessage = local.errorMessage,
            sendingCallbackIds = sendingIds,
            ttsPlayingMessageId = ttsId
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(kuerzel = kuerzel)
    )

    /** Send a text message to the current session. */
    fun sendMessage(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            _localState.update { it.copy(isSending = true, errorMessage = null) }
            try {
                chatRepository.sendMessage(kuerzel, text)
            } catch (e: Exception) {
                _localState.update { it.copy(errorMessage = "Send failed: ${e.message}") }
            } finally {
                _localState.update { it.copy(isSending = false) }
            }
        }
    }

    /** Answer a permission callback (Allow/Deny). */
    fun answerCallback(messageId: Long, response: String) {
        viewModelScope.launch {
            _sendingCallbacks.update { it + messageId }
            try {
                chatRepository.answerCallback(messageId, kuerzel, response)
            } catch (e: Exception) {
                _localState.update { it.copy(errorMessage = "Callback failed: ${e.message}") }
            } finally {
                _sendingCallbacks.update { it - messageId }
            }
        }
    }

    /** Answer a question by sending the callback and the option text as a message. */
    fun answerQuestion(messageId: Long, option: String) {
        viewModelScope.launch {
            _sendingCallbacks.update { it + messageId }
            try {
                chatRepository.answerCallback(messageId, kuerzel, option)
                chatRepository.sendMessage(kuerzel, option)
            } catch (e: Exception) {
                _localState.update { it.copy(errorMessage = "Answer failed: ${e.message}") }
            } finally {
                _sendingCallbacks.update { it - messageId }
            }
        }
    }

    /** Clear error message after snackbar dismissal. */
    fun clearError() {
        _localState.update { it.copy(errorMessage = null) }
    }

    /** Play TTS for a message. */
    fun playTts(messageId: Long, text: String) {
        ttsManager.speak(messageId, text)
    }

    /** Stop TTS playback. */
    fun stopTts() {
        ttsManager.stop()
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}
