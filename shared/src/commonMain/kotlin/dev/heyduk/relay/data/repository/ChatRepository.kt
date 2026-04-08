package dev.heyduk.relay.data.repository

import dev.heyduk.relay.domain.model.ChatMessage
import dev.heyduk.relay.domain.model.RelayUpdate
import dev.heyduk.relay.domain.model.SessionStatus
import kotlinx.coroutines.flow.Flow

/**
 * Repository for per-session chat interactions.
 * Provides reactive access to chat history and message sending.
 */
interface ChatRepository {
    /** Reactive stream of chat messages for a session, ordered chronologically. */
    fun messagesForSession(kuerzel: String): Flow<List<ChatMessage>>

    /** Send a text message to a session. Persists outgoing message to DB and sends via Telegram. */
    suspend fun sendMessage(kuerzel: String, text: String)

    /** Answer a permission callback. Marks the message as answered in DB and sends the callback to Telegram. */
    suspend fun answerCallback(messageId: Long, kuerzel: String, response: String)

    /** Answer a question with a structured payload (single/multi/text). */
    suspend fun answerQuestion(messageId: Long, kuerzel: String, type: String, selections: List<Int>, text: String?, optionCount: Int)

    /** Insert a local-only outgoing message into DB (no network send). For attachment display. */
    suspend fun insertLocalMessage(kuerzel: String, text: String)

    /** Insert an incoming server message into DB (displayed as server-side bubble). */
    suspend fun insertIncomingMessage(kuerzel: String, text: String)

    /** Send an OAuth authorization code to a session's terminal for auth recovery. */
    suspend fun sendAuthCode(kuerzel: String, code: String)

    /** Send a command to the terminal without DB insert. */
    suspend fun sendCommand(kuerzel: String, text: String)

    /** Send audio data to the server for transcription. */
    suspend fun sendAudio(kuerzel: String, audioData: ByteArray)

    /** Send a file attachment. Server saves and types path into the session. */
    suspend fun sendAttachment(kuerzel: String, filename: String, base64Data: String)

    /** Flow of incoming transcript updates from the server (type=TRANSCRIPT). */
    val transcripts: Flow<RelayUpdate>

    /** Flow of session status updates for a specific kuerzel. */
    fun statusUpdates(kuerzel: String): Flow<SessionStatus>

    /** Request the last N messages from a session via server. */
    suspend fun sendGetLast(kuerzel: String, count: Int = 2)

    /** Flow of all relay updates (for filtering transient message types). */
    val relayUpdates: Flow<RelayUpdate>
}
