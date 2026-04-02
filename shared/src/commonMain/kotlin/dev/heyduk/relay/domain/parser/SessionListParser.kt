package dev.heyduk.relay.domain.parser

import dev.heyduk.relay.domain.model.Session
import dev.heyduk.relay.domain.model.SessionStatus

/**
 * Parses /ls command response text into a list of Session objects.
 * Format: "@kuerzel  status  (activity)" per line.
 */
object SessionListParser {

    private val linePattern = Regex("""@(\S+)\s+(\w+)\s*(?:\((\w+)\))?""")

    /**
     * Parse /ls response text into sessions.
     * Lines that don't match the expected format are silently skipped.
     */
    fun parse(responseText: String): List<Session> {
        if (responseText.isBlank()) return emptyList()

        return responseText.lines().mapNotNull { line ->
            val match = linePattern.find(line) ?: return@mapNotNull null
            val (kuerzel, statusStr, activity) = match.destructured

            val status = SessionStatus.entries.find {
                it.name.equals(statusStr, ignoreCase = true)
            } ?: return@mapNotNull null

            Session(
                kuerzel = kuerzel,
                status = status,
                lastActivity = activity.ifEmpty { null },
                isActive = activity == "active"
            )
        }
    }
}
