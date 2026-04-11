package dev.heyduk.relay.data.repository

import dev.heyduk.relay.domain.model.Session
import kotlinx.coroutines.flow.Flow

/**
 * Repository for session discovery and state management.
 * Sessions are discovered via /ls command and tracked in memory.
 */
interface SessionRepository {
    /** Observable list of discovered sessions. Updated when /ls responses are parsed. */
    val sessions: Flow<List<Session>>

    /** Currently selected session kuerzel. */
    val selectedKuerzel: Flow<String?>

    /** Trigger session discovery by sending /ls command. */
    suspend fun refreshSessions()

    /** Select a session by kuerzel for command targeting. */
    fun selectSession(kuerzel: String?)

    /** Get the last response text for a specific session. */
    suspend fun getLastResponse(kuerzel: String): String?

    /** Update active session set and auto-cleanup stale sessions from DB. */
    suspend fun updateActiveSessions(activeNames: List<String>)

    /** Delete all messages for a session from the local DB. */
    suspend fun clearSession(kuerzel: String)
}
