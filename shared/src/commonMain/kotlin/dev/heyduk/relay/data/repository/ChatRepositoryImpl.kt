package dev.heyduk.relay.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.heyduk.relay.Messages
import dev.heyduk.relay.db.RelayDatabase
import dev.heyduk.relay.domain.model.ChatMessage
import dev.heyduk.relay.domain.model.QuestionData
import dev.heyduk.relay.domain.model.RelayMessageType
import dev.heyduk.relay.domain.model.RelayUpdate
import dev.heyduk.relay.util.currentTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
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

    /**
     * In-memory cache for transient question data from live WebSocket messages.
     * Question data is not persisted to the DB -- it's only needed for active questions.
     */
    private val questionDataCache = mutableMapOf<Long, QuestionData>()

    override fun messagesForSession(kuerzel: String): Flow<List<ChatMessage>> {
        return database.messagesQueries.getMessagesForSession(kuerzel)
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { row ->
                    row.toChatMessage().let { msg ->
                        // Attach cached question data for live questions
                        val cached = questionDataCache[msg.id]
                        if (cached != null) msg.copy(questionData = cached) else msg
                    }
                }
            }
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

    override suspend fun insertLocalMessage(kuerzel: String, text: String) {
        val now = currentTimeMillis()
        database.messagesQueries.insertOutgoing(
            update_id = -now,
            session = kuerzel,
            message = text,
            timestamp = now
        )
    }

    override suspend fun sendCommand(kuerzel: String, text: String) {
        relayRepository.sendCommand(kuerzel, text)
    }

    override suspend fun answerCallback(messageId: Long, kuerzel: String, response: String) {
        // Persist the decision locally so the UI reflects the answered state immediately
        database.messagesQueries.markAnswered(response, messageId)

        // Send callback via relay server (format: callback:allow:kuerzel / callback:deny:kuerzel)
        relayRepository.sendCommand(kuerzel, "callback:$response:$kuerzel")
    }

    override suspend fun answerQuestion(
        messageId: Long,
        kuerzel: String,
        type: String,
        selections: List<Int>,
        text: String?,
        optionCount: Int
    ) {
        // Mark as answered in DB (store a human-readable summary)
        val responseText = when (type) {
            "text" -> text ?: ""
            else -> selections.joinToString(", ") { "#$it" }
        }
        database.messagesQueries.markAnswered(responseText, messageId)

        // Send structured answer payload to server
        relayRepository.sendAnswer(kuerzel, type, selections, text, optionCount)

        // Clean up cache
        questionDataCache.remove(messageId)
    }

    override suspend fun sendAudio(kuerzel: String, audioData: ByteArray) {
        relayRepository.sendAudio(kuerzel, audioData)
    }

    override suspend fun sendAttachment(kuerzel: String, filename: String, base64Data: String) {
        relayRepository.sendAttachment(kuerzel, filename, base64Data)
    }

    override val transcripts: Flow<RelayUpdate> = relayRepository.updates
        .filter { it.type == RelayMessageType.TRANSCRIPT }

    /** Cache question data from a live RelayUpdate for later retrieval by the UI. */
    fun cacheQuestionData(updateId: Long, data: QuestionData) {
        questionDataCache[updateId] = data
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
