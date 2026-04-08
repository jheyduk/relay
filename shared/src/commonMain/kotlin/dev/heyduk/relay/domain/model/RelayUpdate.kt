package dev.heyduk.relay.domain.model

data class QuestionOption(val label: String, val description: String? = null)

data class QuestionData(
    val question: String,
    val header: String? = null,
    val multiSelect: Boolean = false,
    val options: List<QuestionOption> = emptyList(),
    val context: String? = null
)

data class RelayUpdate(
    val updateId: Long,
    val type: RelayMessageType,
    val session: String,           // kuerzel
    val status: SessionStatus?,
    val message: String,
    val toolName: String? = null,
    val command: String? = null,
    val filePath: String? = null,
    val questionData: QuestionData? = null,
    val timestamp: Long,
    val isFromRelay: Boolean = false,  // true = sent by the Relay app
    val directoryList: List<DirectoryEntry>? = null,
    val defaultFlags: String? = null,
    val sessionCreatedKuerzel: String? = null,
    val sessionCreatedPath: String? = null,
    val sessionCreatedSuccess: Boolean? = null,
    val sessionCreatedError: String? = null,
    val authUrl: String? = null
)
