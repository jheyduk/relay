package dev.heyduk.relay.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.heyduk.relay.db.RelayDatabase
import dev.heyduk.relay.domain.model.Session
import dev.heyduk.relay.domain.model.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Session repository implementation that discovers sessions from the database.
 *
 * The WebSocketService writes incoming updates into SQLDelight. This repository
 * observes distinct sessions from the DB reactively, so any new message
 * automatically surfaces the session in the list.
 */
class SessionRepositoryImpl(
    private val relayRepository: RelayRepository,
    private val database: RelayDatabase
) : SessionRepository {

    private val _selectedKuerzel = MutableStateFlow<String?>(null)

    override val sessions: Flow<List<Session>> =
        database.messagesQueries.getDistinctSessions()
            .asFlow()
            .mapToList(Dispatchers.Default)
            .map { rows ->
                rows.map { row ->
                    val status = row.status?.let { s ->
                        SessionStatus.entries.find { it.name.equals(s, ignoreCase = true) }
                    } ?: SessionStatus.READY
                    Session(
                        kuerzel = row.session,
                        status = status,
                        lastActivity = null,
                        isActive = status == SessionStatus.WORKING
                    )
                }
            }

    override val selectedKuerzel: Flow<String?> = _selectedKuerzel

    override suspend fun refreshSessions() {
        relayRepository.sendRawCommand("/ls")
    }

    override fun selectSession(kuerzel: String?) {
        _selectedKuerzel.value = kuerzel
    }

    override suspend fun getLastResponse(kuerzel: String): String? {
        val list = relayRepository.getMessagesForSession(kuerzel).first()
        return list.lastOrNull()?.message
    }
}
