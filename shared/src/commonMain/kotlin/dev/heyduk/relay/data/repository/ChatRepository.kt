package dev.heyduk.relay.data.repository

import dev.heyduk.relay.domain.model.ChatMessage
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
}
