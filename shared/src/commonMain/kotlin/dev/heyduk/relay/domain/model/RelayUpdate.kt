package dev.heyduk.relay.domain.model

data class RelayUpdate(
    val updateId: Long,
    val type: RelayMessageType,
    val session: String,           // kuerzel
    val status: SessionStatus?,
    val message: String,
    val toolName: String? = null,
    val command: String? = null,
    val filePath: String? = null,
    val timestamp: Long,
    val isFromRelay: Boolean = false  // true = sent by the Relay app
)
