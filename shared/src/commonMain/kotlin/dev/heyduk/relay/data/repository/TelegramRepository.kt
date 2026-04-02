package dev.heyduk.relay.data.repository

import dev.heyduk.relay.domain.model.RelayUpdate
import kotlinx.coroutines.flow.Flow

/**
 * Repository for Telegram Bot API interactions.
 * Provides two-bot architecture: reads from relay bot, writes to command bot.
 */
interface TelegramRepository {
    /** Flow of parsed relay updates from the polling loop. */
    val updates: Flow<RelayUpdate>

    /** Get all messages for a specific session, ordered by timestamp ascending. */
    fun getMessagesForSession(session: String): Flow<List<RelayUpdate>>

    /** Get the most recent messages across all sessions. */
    fun getRecentMessages(): Flow<List<RelayUpdate>>

    /** Send a command to a specific session via the command bot. */
    suspend fun sendCommand(kuerzel: String, message: String)

    /** Send a raw command (e.g. /ls, /last) via the command bot. */
    suspend fun sendRawCommand(command: String)
}
