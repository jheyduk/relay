package dev.heyduk.relay.data.remote

import dev.heyduk.relay.data.remote.dto.RelayMessage
import dev.heyduk.relay.data.remote.dto.RelayMessageTypeDto
import dev.heyduk.relay.data.remote.dto.SessionStatusDto
import dev.heyduk.relay.domain.model.DirectoryEntry
import dev.heyduk.relay.domain.model.QuestionData
import dev.heyduk.relay.domain.model.QuestionOption
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
            // For transcript messages, prefer "text" field over "message"
            val messageContent = if (relay.type == RelayMessageTypeDto.TRANSCRIPT) {
                relay.text ?: relay.message
            } else {
                relay.message
            }
            RelayUpdate(
                updateId = updateId,
                type = relay.type.toDomain(),
                session = relay.session,
                status = relay.status?.toDomain(),
                message = messageContent,
                toolName = relay.toolDetails?.toolName,
                command = relay.toolDetails?.command,
                filePath = relay.toolDetails?.filePath,
                questionData = relay.questionData?.let { qd ->
                    QuestionData(
                        question = qd.question,
                        header = qd.header,
                        multiSelect = qd.multiSelect,
                        options = qd.options.map { QuestionOption(it.label, it.description) },
                        context = relay.context
                    )
                },
                timestamp = if (relay.timestamp > 0) relay.timestamp else timestamp,
                directoryList = relay.directories?.map { DirectoryEntry(it.path, it.name) },
                defaultFlags = relay.defaultFlags,
                sessionCreatedKuerzel = relay.kuerzel,
                sessionCreatedPath = relay.path,
                sessionCreatedSuccess = relay.success,
                sessionCreatedError = relay.error,
                authUrl = relay.url,
                noChange = relay.noChange ?: false,
                activeSessionNames = relay.sessions
                    ?.filter { it.active }
                    ?.map { it.name }
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
    RelayMessageTypeDto.TRANSCRIPT -> RelayMessageType.TRANSCRIPT
    RelayMessageTypeDto.DIRECTORY_LIST -> RelayMessageType.DIRECTORY_LIST
    RelayMessageTypeDto.SESSION_CREATED -> RelayMessageType.SESSION_CREATED
    RelayMessageTypeDto.LAST_RESPONSE -> RelayMessageType.LAST_RESPONSE
    RelayMessageTypeDto.AUTH_REQUIRED -> RelayMessageType.AUTH_REQUIRED
    RelayMessageTypeDto.AUTH_URL -> RelayMessageType.AUTH_URL
    RelayMessageTypeDto.AUTH_TIMEOUT -> RelayMessageType.AUTH_TIMEOUT
    RelayMessageTypeDto.SESSION_LIST -> RelayMessageType.SESSION_LIST
}

fun SessionStatusDto.toDomain(): SessionStatus = when (this) {
    SessionStatusDto.WORKING -> SessionStatus.WORKING
    SessionStatusDto.WAITING -> SessionStatus.WAITING
    SessionStatusDto.READY -> SessionStatus.READY
    SessionStatusDto.SHELL -> SessionStatus.SHELL
}
