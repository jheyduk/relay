package dev.heyduk.relay.domain

/**
 * Routes user input to the correct command format for the Telegram bot.
 */
object CommandRouter {

    /** Result of routing user input. */
    sealed interface CommandResult {
        /** Global command that doesn't target a specific session (e.g. /ls, /help). */
        data class Global(val command: String) : CommandResult

        /** Command targeting a specific session with @kuerzel appended. */
        data class SessionTargeted(val command: String) : CommandResult

        /** Plain text message to a specific session. */
        data class Message(val kuerzel: String, val text: String) : CommandResult

        /** Input requires a selected session but none is selected. */
        data object NoSessionSelected : CommandResult
    }

    /**
     * Route user input to the correct command format.
     *
     * @param input Raw user input text
     * @param selectedKuerzel Currently selected session kuerzel, or null if none selected
     * @return The routed command result
     */
    fun route(input: String, selectedKuerzel: String?): CommandResult {
        // Stub - will be implemented in GREEN phase
        TODO("Not yet implemented")
    }
}
