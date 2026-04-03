package dev.heyduk.relay.data.repository

import dev.heyduk.relay.data.remote.ConnectionState
import dev.heyduk.relay.domain.model.RelayUpdate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository for relay server interactions via WebSocket.
 * Replaces TelegramRepository with direct WebSocket transport.
 */
interface RelayRepository {
    /** Flow of parsed relay updates from the WebSocket connection. */
    val updates: Flow<RelayUpdate>

    /** Current WebSocket connection state. */
    val connectionState: StateFlow<ConnectionState>

    /** Get all messages for a specific session, ordered by timestamp ascending. */
    fun getMessagesForSession(session: String): Flow<List<RelayUpdate>>

    /** Get the most recent messages across all sessions. */
    fun getRecentMessages(): Flow<List<RelayUpdate>>

    /** Send a command to a specific session via the relay server. */
    suspend fun sendCommand(kuerzel: String, message: String)

    /** Send a raw command (e.g. /ls, /last) via the relay server. */
    suspend fun sendRawCommand(command: String)

    /** Send a structured answer payload for an AskUserQuestion prompt. */
    suspend fun sendAnswer(kuerzel: String, type: String, selections: List<Int>, text: String?, optionCount: Int)

    /** Send audio data to the server for transcription via binary WebSocket frame. */
    suspend fun sendAudio(kuerzel: String, audioData: ByteArray)
}
