package dev.heyduk.relay.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import dev.heyduk.relay.db.RelayDatabase
import dev.heyduk.relay.domain.model.Session
import dev.heyduk.relay.domain.model.SessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber

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
    private val _activeSessionNames = MutableStateFlow<Set<String>?>(null)

    override val sessions: Flow<List<Session>> =
        combine(
            database.messagesQueries.getDistinctSessions()
                .asFlow()
                .mapToList(Dispatchers.Default),
            _activeSessionNames
        ) { rows, activeNames ->
            val allSessions = rows.map { row ->
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
            if (activeNames == null) {
                allSessions // No session_list received yet -- show all as fallback
            } else {
                allSessions.filter { it.kuerzel in activeNames }
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

    override suspend fun updateActiveSessions(activeNames: List<String>) {
        val activeSet = activeNames.toSet()
        _activeSessionNames.value = activeSet

        // Auto-cleanup: delete messages for sessions not in the active list
        val allDbSessions = database.messagesQueries.getDistinctSessions().executeAsList()
        val staleSessions = allDbSessions
            .map { it.session }
            .filter { it !in activeSet }

        for (session in staleSessions) {
            Timber.d("Auto-cleanup: deleting messages for inactive session '%s'", session)
            database.messagesQueries.deleteMessagesForSession(session)
        }

        if (staleSessions.isNotEmpty()) {
            Timber.i("Auto-cleanup: removed %d inactive session(s): %s", staleSessions.size, staleSessions)
        }
    }
}
