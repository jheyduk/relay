package dev.heyduk.relay.domain.model

/**
 * Represents a discovered session from /ls command output.
 *
 * @param kuerzel Short identifier for the session (e.g. "infra", "hub")
 * @param status Current session status
 * @param lastActivity Activity description if present (e.g. "active")
 * @param isActive Whether the session is currently active
 */
data class Session(
    val kuerzel: String,
    val status: SessionStatus,
    val lastActivity: String? = null,
    val isActive: Boolean = false
)
