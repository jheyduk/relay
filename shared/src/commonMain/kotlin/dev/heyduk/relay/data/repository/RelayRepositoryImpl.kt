package dev.heyduk.relay.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.heyduk.relay.Messages
import dev.heyduk.relay.data.remote.ConnectionState
import dev.heyduk.relay.data.remote.WebSocketClient
import dev.heyduk.relay.db.RelayDatabase
import dev.heyduk.relay.domain.model.RelayMessageType
import dev.heyduk.relay.domain.model.RelayUpdate
import dev.heyduk.relay.domain.model.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map

/**
 * WebSocket-based implementation of [RelayRepository].
 * Reads updates from [WebSocketClient], persists to and queries from SQLDelight database.
 */
class RelayRepositoryImpl(
    private val webSocketClient: WebSocketClient,
    private val database: RelayDatabase
) : RelayRepository {

    override val updates: Flow<RelayUpdate> = webSocketClient.updates

    override val connectionState: StateFlow<ConnectionState> = webSocketClient.connectionState

    override fun getMessagesForSession(session: String): Flow<List<RelayUpdate>> {
        return database.messagesQueries.getMessagesForSession(session)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { messages -> messages.map { it.toDomain() } }
    }

    override fun getRecentMessages(): Flow<List<RelayUpdate>> {
        return database.messagesQueries.getRecentMessages()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { messages -> messages.map { it.toDomain() } }
    }

    override suspend fun sendCommand(kuerzel: String, message: String) {
        webSocketClient.sendCommand(kuerzel, message)
    }

    override suspend fun sendRawCommand(command: String) {
        webSocketClient.sendRawCommand(command)
    }

    override suspend fun sendAnswer(kuerzel: String, type: String, selections: List<Int>, text: String?, optionCount: Int) {
        webSocketClient.sendAnswer(kuerzel, type, selections, text, optionCount)
    }

    override suspend fun sendAudio(kuerzel: String, audioData: ByteArray) {
        webSocketClient.sendAudio(kuerzel, audioData)
    }

    override suspend fun sendAttachment(kuerzel: String, filename: String, base64Data: String) {
        webSocketClient.sendAttachment(kuerzel, filename, base64Data)
    }

    override suspend fun sendListDirectories() {
        webSocketClient.sendListDirectories()
    }

    override suspend fun sendCreateSession(path: String, kuerzel: String, flags: String) {
        webSocketClient.sendCreateSession(path, kuerzel, flags)
    }
}

/**
 * Maps SQLDelight [Messages] row to domain [RelayUpdate].
 */
private fun Messages.toDomain(): RelayUpdate = RelayUpdate(
    updateId = update_id,
    type = RelayMessageType.valueOf(type),
    session = session,
    status = status?.let { SessionStatus.valueOf(it) },
    message = message,
    toolName = tool_name,
    command = command,
    filePath = file_path,
    timestamp = timestamp,
    isFromRelay = is_from_relay != 0L
)
