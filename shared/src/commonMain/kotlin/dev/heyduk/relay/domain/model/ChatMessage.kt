package dev.heyduk.relay.domain.model

/**
 * UI-friendly projection of a message from the messages table.
 * Used by the chat UI to render conversation history.
 */
data class ChatMessage(
    val id: Long,                           // update_id from DB
    val session: String,                    // kuerzel
    val content: String,                    // message text
    val timestamp: Long,                    // epoch millis
    val isOutgoing: Boolean,                // true = sent by user via Relay
    val type: RelayMessageType,             // message type for rendering hints
    val toolName: String? = null,           // tool name for permission display (e.g. "Bash", "Edit")
    val command: String? = null,            // command details for permission display
    val filePath: String? = null,           // file path for permission display
    val callbackResponse: String? = null    // answered state: "allow", "deny", or selected option text
)
