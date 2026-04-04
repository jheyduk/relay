package dev.heyduk.relay.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DirectoryEntryDto(val path: String, val name: String)

@Serializable
data class RelayMessage(
    val type: RelayMessageTypeDto,
    val session: String = "",
    val status: SessionStatusDto? = null,
    val message: String = "",
    val text: String? = null,
    @SerialName("tool_details") val toolDetails: ToolDetails? = null,
    val context: String? = null,
    @SerialName("question_data") val questionData: QuestionDataDto? = null,
    val timestamp: Long = 0,
    @SerialName("__relay") val relayMarker: Boolean? = null,
    val directories: List<DirectoryEntryDto>? = null,
    val defaultFlags: String? = null,
    val success: Boolean? = null,
    val error: String? = null,
    val kuerzel: String? = null,
    val path: String? = null
)

@Serializable
data class QuestionOptionDto(
    val label: String,
    val description: String? = null
)

@Serializable
data class QuestionDataDto(
    val question: String,
    val header: String? = null,
    val multiSelect: Boolean = false,
    val options: List<QuestionOptionDto> = emptyList()
)

@Serializable
enum class RelayMessageTypeDto {
    @SerialName("status") STATUS,
    @SerialName("response") RESPONSE,
    @SerialName("permission") PERMISSION,
    @SerialName("question") QUESTION,
    @SerialName("completion") COMPLETION,
    @SerialName("transcript") TRANSCRIPT,
    @SerialName("directory_list") DIRECTORY_LIST,
    @SerialName("session_created") SESSION_CREATED,
    @SerialName("last_response") LAST_RESPONSE
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
