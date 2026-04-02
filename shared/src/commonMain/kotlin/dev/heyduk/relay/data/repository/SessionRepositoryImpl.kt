package dev.heyduk.relay.data.repository

import dev.heyduk.relay.domain.model.Session
import dev.heyduk.relay.domain.parser.SessionListParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Session repository implementation that discovers sessions via /ls command
 * and auto-parses responses from the Telegram update stream.
 *
 * Listens to [TelegramRepository.updates] and attempts to parse each incoming
 * message as /ls output. If at least one session is found, the session list
 * is updated. This avoids request/response correlation complexity.
 */
class SessionRepositoryImpl(
    private val telegramRepository: TelegramRepository,
    scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : SessionRepository {

    private val _sessions = MutableStateFlow<List<Session>>(emptyList())
    private val _selectedKuerzel = MutableStateFlow<String?>(null)

    override val sessions: Flow<List<Session>> = _sessions.asStateFlow()
    override val selectedKuerzel: Flow<String?> = _selectedKuerzel.asStateFlow()

    init {
        // Listen to all incoming updates and try to parse /ls responses
        scope.launch {
            telegramRepository.updates.collect { update ->
                val parsed = SessionListParser.parse(update.message)
                if (parsed.isNotEmpty()) {
                    _sessions.value = parsed
                }
            }
        }
    }

    override suspend fun refreshSessions() {
        telegramRepository.sendRawCommand("/ls")
    }

    override fun selectSession(kuerzel: String?) {
        _selectedKuerzel.value = kuerzel
    }

    override suspend fun getLastResponse(kuerzel: String): String? {
        val messages = telegramRepository.getMessagesForSession(kuerzel).firstOrNull()
        return messages?.lastOrNull()?.message
    }
}
