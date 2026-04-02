package dev.heyduk.relay.domain.parser

import dev.heyduk.relay.domain.model.Session

/**
 * Parses /ls command response text into a list of Session objects.
 * Format: "@kuerzel  status  (activity)" per line.
 */
object SessionListParser {

    /**
     * Parse /ls response text into sessions.
     * Lines that don't match the expected format are silently skipped.
     */
    fun parse(responseText: String): List<Session> {
        // Stub - will be implemented in GREEN phase
        TODO("Not yet implemented")
    }
}
