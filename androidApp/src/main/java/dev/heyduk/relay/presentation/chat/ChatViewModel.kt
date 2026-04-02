package dev.heyduk.relay.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.heyduk.relay.data.repository.ChatRepository
import dev.heyduk.relay.domain.model.ChatMessage
import dev.heyduk.relay.voice.AudioRecorder
import dev.heyduk.relay.voice.TtsManager
import dev.heyduk.relay.voice.WhisperManager
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
    private val whisperManager: WhisperManager,
    private val audioRecorder: AudioRecorder,
    private val kuerzel: String
) : ViewModel() {

    data class ChatUiState(
        val messages: List<ChatMessage> = emptyList(),
        val kuerzel: String = "",
        val isSending: Boolean = false,
        val errorMessage: String? = null,
        val sendingCallbackIds: Set<Long> = emptySet(),
        val ttsPlayingMessageId: Long? = null,
        val isRecording: Boolean = false,
        val isTranscribing: Boolean = false,
        val transcriptPreview: String? = null
    )

    private data class LocalState(
        val isSending: Boolean = false,
        val errorMessage: String? = null,
        val isRecording: Boolean = false,
        val isTranscribing: Boolean = false,
        val transcriptPreview: String? = null
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
            ttsPlayingMessageId = ttsId,
            isRecording = local.isRecording,
            isTranscribing = local.isTranscribing,
            transcriptPreview = local.transcriptPreview
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

    // --- Voice recording ---

    /** Initialize whisper on first use. */
    private var whisperInitialized = false

    fun startRecording() {
        viewModelScope.launch {
            // Initialize whisper if needed (first use)
            if (!whisperInitialized) {
                try {
                    whisperManager.initialize()
                    whisperInitialized = true
                } catch (e: Exception) {
                    _localState.update { it.copy(errorMessage = "Whisper init failed: ${e.message}") }
                    return@launch
                }
            }

            _localState.update { it.copy(isRecording = true) }
            try {
                audioRecorder.startRecording()
            } catch (e: Exception) {
                _localState.update {
                    it.copy(isRecording = false, errorMessage = "Recording failed: ${e.message}")
                }
            }
        }
    }

    fun stopRecording() {
        audioRecorder.stopRecording()
        _localState.update { it.copy(isRecording = false, isTranscribing = true) }

        viewModelScope.launch {
            try {
                val wavFile = audioRecorder.getOutputFile()
                    ?: throw IllegalStateException("No recording file available")
                val transcript = whisperManager.transcribe(wavFile)
                if (transcript.isBlank()) {
                    _localState.update {
                        it.copy(isTranscribing = false, errorMessage = "Could not transcribe audio")
                    }
                } else {
                    _localState.update {
                        it.copy(isTranscribing = false, transcriptPreview = transcript)
                    }
                }
                // Clean up temp file
                wavFile.delete()
            } catch (e: Exception) {
                _localState.update {
                    it.copy(isTranscribing = false, errorMessage = "Transcription failed: ${e.message}")
                }
            }
        }
    }

    fun sendTranscript(text: String) {
        _localState.update { it.copy(transcriptPreview = null) }
        sendMessage(text)  // reuses existing send flow
    }

    fun cancelTranscript() {
        _localState.update { it.copy(transcriptPreview = null) }
    }

    override fun onCleared() {
        super.onCleared()
        ttsManager.stop()
    }
}
