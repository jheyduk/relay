package dev.heyduk.relay.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.heyduk.relay.Messages
import dev.heyduk.relay.db.RelayDatabase
import dev.heyduk.relay.domain.model.ChatMessage
import dev.heyduk.relay.domain.model.RelayMessageType
import dev.heyduk.relay.util.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of [ChatRepository] that persists outgoing messages
 * to SQLDelight and delegates sending to [RelayRepository].
 *
 * Outgoing messages are inserted optimistically before the network call,
 * so chat history survives process death even if send fails.
 */
class ChatRepositoryImpl(
    private val relayRepository: RelayRepository,
    private val database: RelayDatabase
) : ChatRepository {

    override fun messagesForSession(kuerzel: String): Flow<List<ChatMessage>> {
        return database.messagesQueries.getMessagesForSession(kuerzel)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toChatMessage() } }
    }

    override suspend fun sendMessage(kuerzel: String, text: String) {
        val now = currentTimeMillis()
        val syntheticId = -now // negative to avoid collision with Telegram's positive update IDs

        // Optimistic insert: persist before sending so history survives crashes
        database.messagesQueries.insertOutgoing(
            update_id = syntheticId,
            session = kuerzel,
            message = text,
            timestamp = now
        )

        // Delegate actual delivery to relay server
        relayRepository.sendCommand(kuerzel, text)
    }

    override suspend fun answerCallback(messageId: Long, kuerzel: String, response: String) {
        // Persist the decision locally so the UI reflects the answered state immediately
        database.messagesQueries.markAnswered(response, messageId)

        // Send callback via relay server (format: callback:allow:kuerzel / callback:deny:kuerzel)
        relayRepository.sendCommand(kuerzel, "callback:$response:$kuerzel")
    }
}

/**
 * Maps SQLDelight [Messages] row to UI-friendly [ChatMessage].
 */
private fun Messages.toChatMessage() = ChatMessage(
    id = update_id,
    session = session,
    content = message,
    timestamp = timestamp,
    isOutgoing = is_from_relay != 0L,
    type = RelayMessageType.valueOf(type),
    toolName = tool_name,
    command = command,
    filePath = file_path,
    callbackResponse = callback_response
)
