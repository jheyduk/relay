package dev.heyduk.relay.data.remote

import dev.heyduk.relay.data.remote.dto.RelayMessage
import dev.heyduk.relay.data.remote.dto.RelayMessageTypeDto
import dev.heyduk.relay.data.remote.dto.SessionStatusDto
import dev.heyduk.relay.domain.model.RelayMessageType
import dev.heyduk.relay.domain.model.RelayUpdate
import dev.heyduk.relay.domain.model.SessionStatus
import kotlinx.serialization.json.Json

/**
 * Parses JSON text from the relay bot into domain [RelayUpdate] objects.
 * Returns null for non-JSON or non-relay messages.
 */
object RelayMessageParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(updateId: Long, messageText: String, timestamp: Long): RelayUpdate? {
        return try {
            val relay = json.decodeFromString<RelayMessage>(messageText)
            RelayUpdate(
                updateId = updateId,
                type = relay.type.toDomain(),
                session = relay.session,
                status = relay.status?.toDomain(),
                message = relay.message,
                toolName = relay.toolDetails?.toolName,
                command = relay.toolDetails?.command,
                filePath = relay.toolDetails?.filePath,
                timestamp = if (relay.timestamp > 0) relay.timestamp else timestamp
            )
        } catch (_: Exception) {
            null // Not a relay JSON message
        }
    }
}

// Extension functions for DTO-to-domain mapping

fun RelayMessageTypeDto.toDomain(): RelayMessageType = when (this) {
    RelayMessageTypeDto.STATUS -> RelayMessageType.STATUS
    RelayMessageTypeDto.RESPONSE -> RelayMessageType.RESPONSE
    RelayMessageTypeDto.PERMISSION -> RelayMessageType.PERMISSION
    RelayMessageTypeDto.QUESTION -> RelayMessageType.QUESTION
    RelayMessageTypeDto.COMPLETION -> RelayMessageType.COMPLETION
}

fun SessionStatusDto.toDomain(): SessionStatus = when (this) {
    SessionStatusDto.WORKING -> SessionStatus.WORKING
    SessionStatusDto.WAITING -> SessionStatus.WAITING
    SessionStatusDto.READY -> SessionStatus.READY
    SessionStatusDto.SHELL -> SessionStatus.SHELL
}
