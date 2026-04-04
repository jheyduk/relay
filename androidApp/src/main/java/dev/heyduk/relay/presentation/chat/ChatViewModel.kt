package dev.heyduk.relay.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.heyduk.relay.data.repository.ChatRepository
import dev.heyduk.relay.domain.model.ChatMessage
import dev.heyduk.relay.domain.model.RelayMessageType
import dev.heyduk.relay.voice.AudioRecorder
import dev.heyduk.relay.voice.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
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
    private val audioRecorder: AudioRecorder,
    private val kuerzel: String
) : ViewModel() {

    data class PendingAttachment(val filename: String, val base64Data: String)

    data class ChatUiState(
        val messages: List<ChatMessage> = emptyList(),
        val kuerzel: String = "",
        val isSending: Boolean = false,
        val errorMessage: String? = null,
        val sendingCallbackIds: Set<Long> = emptySet(),
        val ttsPlayingMessageId: Long? = null,
        val isRecording: Boolean = false,
        val isTranscribing: Boolean = false,
        val transcriptPreview: String? = null,
        val pendingAttachment: PendingAttachment? = null
    )

    private data class LocalState(
        val isSending: Boolean = false,
        val errorMessage: String? = null,
        val isRecording: Boolean = false,
        val isTranscribing: Boolean = false,
        val transcriptPreview: String? = null,
        val pendingAttachment: PendingAttachment? = null
    )

    private val _localState = MutableStateFlow(LocalState())
    private val _sendingCallbacks = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.messagesForSession(kuerzel),
        _localState,
        _sendingCallbacks,
        ttsManager.speakingMessageId
    ) { messages, local, sendingIds, ttsId ->
        // Filter out STATUS messages — they're for the session list, not the conversation
        val filtered = messages.filter { it.type != RelayMessageType.STATUS }
        ChatUiState(
            messages = filtered,
            kuerzel = kuerzel,
            isSending = local.isSending,
            errorMessage = local.errorMessage,
            sendingCallbackIds = sendingIds,
            ttsPlayingMessageId = ttsId,
            isRecording = local.isRecording,
            isTranscribing = local.isTranscribing,
            transcriptPreview = local.transcriptPreview,
            pendingAttachment = local.pendingAttachment
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ChatUiState(kuerzel = kuerzel)
    )

    init {
        // Observe incoming transcript messages from the server
        viewModelScope.launch {
            chatRepository.transcripts
                .filter { it.session == kuerzel }
                .collect { transcript ->
                    _localState.update {
                        it.copy(
                            isTranscribing = false,
                            transcriptPreview = transcript.message
                        )
                    }
                }
        }
    }

    /** Send a text message (and pending attachment if any) to the current session. */
    fun sendMessage(text: String) {
        val attachment = _localState.value.pendingAttachment
        if (text.isBlank() && attachment == null) return
        viewModelScope.launch {
            _localState.update { it.copy(isSending = true, errorMessage = null, pendingAttachment = null) }
            try {
                // Send attachment first if staged
                if (attachment != null) {
                    // Insert a local chat bubble showing the attachment
                    val attachLabel = "\uD83D\uDCCE ${attachment.filename}"
                    val displayText = if (text.isNotBlank()) "$attachLabel\n$text" else attachLabel
                    chatRepository.insertLocalMessage(kuerzel, displayText)
                    // Send attachment to server (saves file, types path into terminal)
                    chatRepository.sendAttachment(kuerzel, attachment.filename, attachment.base64Data)
                    // Wait for server to dispatch the file path + Enter before sending text
                    if (text.isNotBlank()) kotlinx.coroutines.delay(1500)
                }
                // Send text via terminal
                if (text.isNotBlank() && attachment != null) {
                    chatRepository.sendCommand(kuerzel, text)
                } else if (text.isNotBlank()) {
                    chatRepository.sendMessage(kuerzel, text)
                }
            } catch (e: Exception) {
                _localState.update { it.copy(errorMessage = "Send failed: ${e.message}") }
            } finally {
                _localState.update { it.copy(isSending = false) }
            }
        }
    }

    /** Show a transient message (snackbar). */
    fun showMessage(text: String) {
        _localState.update { it.copy(errorMessage = text) }
    }

    /** Stage a file for sending with the next message. */
    fun stageAttachment(filename: String, base64Data: String) {
        _localState.update { it.copy(pendingAttachment = PendingAttachment(filename, base64Data)) }
    }

    /** Remove the staged attachment. */
    fun clearAttachment() {
        _localState.update { it.copy(pendingAttachment = null) }
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

    /** Answer a question with a structured payload (single/multi/text). */
    fun answerQuestion(messageId: Long, type: String, selections: List<Int>, text: String?, optionCount: Int) {
        viewModelScope.launch {
            _sendingCallbacks.update { it + messageId }
            try {
                chatRepository.answerQuestion(messageId, kuerzel, type, selections, text, optionCount)
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

    fun startRecording() {
        viewModelScope.launch {
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
                val audioBytes = wavFile.readBytes()
                // Send audio to server for transcription
                chatRepository.sendAudio(kuerzel, audioBytes)
                // Clean up temp file
                wavFile.delete()
                // Transcript will arrive asynchronously via WebSocket (handled in init block)
            } catch (e: Exception) {
                _localState.update {
                    it.copy(isTranscribing = false, errorMessage = "Send audio failed: ${e.message}")
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
