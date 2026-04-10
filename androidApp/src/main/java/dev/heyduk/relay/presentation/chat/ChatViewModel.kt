package dev.heyduk.relay.presentation.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.heyduk.relay.data.repository.ChatRepository
import dev.heyduk.relay.domain.model.ChatMessage
import dev.heyduk.relay.domain.model.RelayMessageType
import dev.heyduk.relay.domain.model.SessionStatus
import dev.heyduk.relay.voice.AudioRecorder
import dev.heyduk.relay.voice.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
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
        val sessionStatus: SessionStatus? = null,
        val isSending: Boolean = false,
        val errorMessage: String? = null,
        val sendingCallbackIds: Set<Long> = emptySet(),
        val ttsPlayingMessageId: Long? = null,
        val isRecording: Boolean = false,
        val isTranscribing: Boolean = false,
        val transcriptPreview: String? = null,
        val pendingAttachment: PendingAttachment? = null,
        val authPhase: AuthPhase? = null,
        val authUrl: String? = null,
        val isSendingAuthCode: Boolean = false,
        val authErrorMessage: String? = null
    )

    private data class LocalState(
        val isSending: Boolean = false,
        val errorMessage: String? = null,
        val isRecording: Boolean = false,
        val isTranscribing: Boolean = false,
        val transcriptPreview: String? = null,
        val pendingAttachment: PendingAttachment? = null,
        val authPhase: AuthPhase? = null,
        val authUrl: String? = null,
        val isSendingAuthCode: Boolean = false,
        val authErrorMessage: String? = null
    )

    private val _localState = MutableStateFlow(LocalState())
    private val _sendingCallbacks = MutableStateFlow<Set<Long>>(emptySet())
    private val _sessionStatus = MutableStateFlow<SessionStatus?>(null)

    init {
        // Observe WebSocket updates directly for real-time session status
        viewModelScope.launch {
            chatRepository.statusUpdates(kuerzel).collect { status ->
                _sessionStatus.value = status
                // Auth recovery confirmation: session going to WORKING means auth succeeded
                val currentAuth = _localState.value.authPhase
                if (currentAuth == AuthPhase.ENTER_CODE || currentAuth == AuthPhase.OPEN_URL) {
                    if (status == SessionStatus.WORKING) {
                        // Server confirmed auth succeeded — session is working again
                        _localState.update { it.copy(authPhase = AuthPhase.RECOVERED, isSendingAuthCode = false) }
                        kotlinx.coroutines.delay(5000)
                        _localState.update { it.copy(authPhase = null, authUrl = null) }
                    }
                }
            }
        }
    }

    val uiState: StateFlow<ChatUiState> = combine(
        chatRepository.messagesForSession(kuerzel),
        _localState,
        _sendingCallbacks,
        ttsManager.speakingMessageId,
        _sessionStatus
    ) { messages, local, sendingIds, ttsId, liveStatus ->
        // Use live status from WebSocket stream, fall back to DB
        val dbStatus = messages.lastOrNull { it.type == RelayMessageType.STATUS }?.status
        val currentStatus = liveStatus ?: dbStatus
        // Filter out STATUS messages — they're for the session list, not the conversation
        val filtered = messages.filter { it.type != RelayMessageType.STATUS }
        ChatUiState(
            messages = filtered,
            kuerzel = kuerzel,
            sessionStatus = currentStatus,
            isSending = local.isSending,
            errorMessage = local.errorMessage,
            sendingCallbackIds = sendingIds,
            ttsPlayingMessageId = ttsId,
            isRecording = local.isRecording,
            isTranscribing = local.isTranscribing,
            transcriptPreview = local.transcriptPreview,
            pendingAttachment = local.pendingAttachment,
            authPhase = local.authPhase,
            authUrl = local.authUrl,
            isSendingAuthCode = local.isSendingAuthCode,
            authErrorMessage = local.authErrorMessage
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

    init {
        // Auto-poll /last every 5 minutes (silent — no "No updates" snackbar)
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(5 * 60 * 1000L)
                fetchLast(silent = true)
            }
        }
    }

    init {
        // Observe auth-related relay updates for this session
        viewModelScope.launch {
            chatRepository.relayUpdates
                .filter { it.session == kuerzel }
                .collect { update ->
                    when (update.type) {
                        RelayMessageType.AUTH_REQUIRED -> {
                            // If we were waiting for confirmation (ENTER_CODE), this means the code was wrong
                            val wasWaiting = _localState.value.authPhase == AuthPhase.ENTER_CODE
                            _localState.update { it.copy(
                                authPhase = AuthPhase.AUTH_REQUIRED,
                                authErrorMessage = if (wasWaiting) "Authentication failed. Please try again." else null
                            ) }
                        }
                        RelayMessageType.AUTH_URL -> {
                            _localState.update { it.copy(authPhase = AuthPhase.OPEN_URL, authUrl = update.authUrl, authErrorMessage = null) }
                        }
                        RelayMessageType.AUTH_TIMEOUT -> {
                            _localState.update { it.copy(authPhase = AuthPhase.TIMED_OUT, authUrl = null, authErrorMessage = null) }
                        }
                        else -> { /* ignore */ }
                    }
                }
        }
    }

    /** Transition to ENTER_CODE phase after user opens the auth URL in browser. */
    fun openAuthUrl() {
        _localState.update { it.copy(authPhase = AuthPhase.ENTER_CODE) }
    }

    /** Send the authorization code to the server and handle recovery lifecycle. */
    fun sendAuthCode(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _localState.update { it.copy(isSendingAuthCode = true) }
            try {
                chatRepository.sendAuthCode(kuerzel, code)
                // Stay at ENTER_CODE — do NOT set RECOVERED optimistically.
                // The _sessionStatus observer will detect working -> triggers RECOVERED.
                _localState.update { it.copy(isSendingAuthCode = false) }
            } catch (e: Exception) {
                _localState.update { it.copy(isSendingAuthCode = false, errorMessage = "Auth code send failed: ${e.message}") }
            }
        }
    }

    /** Reset to AUTH_REQUIRED for retry -- server will re-detect and re-trigger. */
    fun retryAuth() {
        _localState.update { it.copy(authPhase = AuthPhase.AUTH_REQUIRED, authUrl = null) }
    }

    /** Send a text message (and pending attachment if any) to the current session. */
    fun sendMessage(text: String) {
        val attachment = _localState.value.pendingAttachment
        if (text.isBlank() && attachment == null) return
        // Optimistic: show working immediately
        _sessionStatus.value = SessionStatus.WORKING
        viewModelScope.launch {
            _localState.update { it.copy(isSending = true, errorMessage = null, pendingAttachment = null) }
            try {
                // Send attachment first if staged
                if (attachment != null) {
                    // Insert a local chat bubble showing the attachment
                    val attachLabel = "\uD83D\uDCCE ${attachment.filename}"
                    val displayText = if (text.isNotBlank()) "$attachLabel\n$text" else attachLabel
                    chatRepository.insertLocalMessage(kuerzel, displayText)
                    // Send attachment to server (saves file, types path into terminal + Enter)
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

    /** Fetch the last 2 messages from the session via server get_last action.
     *  @param silent If true, suppress "No updates" snackbar (used by auto-poll). */
    fun fetchLast(silent: Boolean = false) {
        viewModelScope.launch {
            try {
                chatRepository.sendGetLast(kuerzel, 2)
                val update = kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    chatRepository.relayUpdates
                        .filter { it.type == dev.heyduk.relay.domain.model.RelayMessageType.LAST_RESPONSE && it.session == kuerzel }
                        .first()
                }
                if (update != null && update.sessionCreatedSuccess == true) {
                    if (update.noChange) {
                        if (!silent) {
                            _localState.update { it.copy(errorMessage = "No updates") }
                        }
                    } else {
                        chatRepository.insertIncomingMessage(kuerzel, update.message)
                    }
                } else if (update != null && !silent) {
                    _localState.update { it.copy(errorMessage = update.sessionCreatedError ?: "Failed to fetch") }
                } else if (update == null && !silent) {
                    _localState.update { it.copy(errorMessage = "Timeout fetching last messages") }
                }
            } catch (e: Exception) {
                if (!silent) {
                    _localState.update { it.copy(errorMessage = "Fetch failed: ${e.message}") }
                }
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
