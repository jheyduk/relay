package dev.heyduk.relay.domain.model

/**
 * UI-friendly projection of a message from the messages table.
 * Used by the chat UI to render conversation history.
 */
data class ChatMessage(
    val id: Long,               // update_id from DB
    val session: String,        // kuerzel
    val content: String,        // message text
    val timestamp: Long,        // epoch millis
    val isOutgoing: Boolean,    // true = sent by user via Relay
    val type: RelayMessageType  // message type for rendering hints
)
