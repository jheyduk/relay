package dev.heyduk.relay.domain.model

/**
 * Represents a project directory available for session creation.
 * Received from the server in response to a list_directories action.
 */
data class DirectoryEntry(val path: String, val name: String)
