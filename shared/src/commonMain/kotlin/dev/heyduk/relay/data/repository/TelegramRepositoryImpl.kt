package dev.heyduk.relay.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.heyduk.relay.Messages
import dev.heyduk.relay.data.remote.TelegramApi
import dev.heyduk.relay.data.remote.TelegramPoller
import dev.heyduk.relay.db.RelayDatabase
import dev.heyduk.relay.domain.model.RelayMessageType
import dev.heyduk.relay.domain.model.RelayUpdate
import dev.heyduk.relay.domain.model.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Two-bot architecture implementation:
 * - Reads from relay bot via [TelegramPoller]
 * - Writes to command bot (existing zellij-claude bot) via [commandApi]
 * - Persists messages to SQLDelight database
 */
class TelegramRepositoryImpl(
    private val poller: TelegramPoller,
    private val commandApi: TelegramApi,
    private val database: RelayDatabase,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : TelegramRepository {

    init {
        // Collect polling updates and persist to database
        scope.launch {
            poller.updates.collect { update ->
                database.messagesQueries.insertOrIgnore(
                    update_id = update.updateId,
                    session = update.session,
                    type = update.type.name,
                    message = update.message,
                    status = update.status?.name,
                    tool_name = update.toolName,
                    command = update.command,
                    file_path = update.filePath,
                    timestamp = update.timestamp,
                    is_from_relay = if (update.isFromRelay) 1L else 0L,
                    callback_response = null
                )
            }
        }
    }

    override val updates: Flow<RelayUpdate> = poller.updates

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
        commandApi.sendMessage("@$kuerzel $message")
    }

    override suspend fun sendRawCommand(command: String) {
        commandApi.sendMessage(command)
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
