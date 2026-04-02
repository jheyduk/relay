package dev.heyduk.relay.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RelayMessage(
    val type: RelayMessageTypeDto,
    val session: String,
    val status: SessionStatusDto? = null,
    val message: String,
    @SerialName("tool_details") val toolDetails: ToolDetails? = null,
    val timestamp: Long = 0,
    @SerialName("__relay") val relayMarker: Boolean? = null
)

@Serializable
enum class RelayMessageTypeDto {
    @SerialName("status") STATUS,
    @SerialName("response") RESPONSE,
    @SerialName("permission") PERMISSION,
    @SerialName("question") QUESTION,
    @SerialName("completion") COMPLETION
}

@Serializable
enum class SessionStatusDto {
    @SerialName("working") WORKING,
    @SerialName("waiting") WAITING,
    @SerialName("ready") READY,
    @SerialName("shell") SHELL
}

@Serializable
data class ToolDetails(
    @SerialName("tool_name") val toolName: String? = null,
    val command: String? = null,
    @SerialName("file_path") val filePath: String? = null
)
