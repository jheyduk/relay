package dev.heyduk.relay.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.heyduk.relay.data.repository.SessionRepository
import dev.heyduk.relay.data.repository.TelegramRepository
import dev.heyduk.relay.domain.CommandRouter
import dev.heyduk.relay.domain.model.Session
import dev.heyduk.relay.service.NetworkMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the session list screen.
 * Manages session list state, selection, command routing, and /last responses.
 */
class SessionListViewModel(
    private val sessionRepository: SessionRepository,
    private val telegramRepository: TelegramRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    data class SessionListUiState(
        val sessions: List<Session> = emptyList(),
        val selectedKuerzel: String? = null,
        val isRefreshing: Boolean = false,
        val isConnected: Boolean = false,
        val lastResponses: Map<String, String> = emptyMap(),
        val expandedCards: Set<String> = emptySet(),
        val errorMessage: String? = null
    )

    // Local mutable state for fields not backed by repository flows
    private val _localState = MutableStateFlow(LocalState())

    private data class LocalState(
        val isRefreshing: Boolean = false,
        val lastResponses: Map<String, String> = emptyMap(),
        val expandedCards: Set<String> = emptySet(),
        val errorMessage: String? = null
    )

    val uiState: StateFlow<SessionListUiState> = combine(
        sessionRepository.sessions,
        sessionRepository.selectedKuerzel,
        networkMonitor.isConnected,
        _localState
    ) { sessions, selectedKuerzel, isConnected, local ->
        SessionListUiState(
            sessions = sessions,
            selectedKuerzel = selectedKuerzel,
            isRefreshing = local.isRefreshing,
            isConnected = isConnected,
            lastResponses = local.lastResponses,
            expandedCards = local.expandedCards,
            errorMessage = local.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SessionListUiState()
    )

    init {
        // Trigger initial /ls on screen load
        refreshSessions()
    }

    /**
     * Trigger session discovery by sending /ls command.
     * Enforces a minimum 1-second delay for UX feedback.
     */
    fun refreshSessions() {
        viewModelScope.launch {
            _localState.update { it.copy(isRefreshing = true, errorMessage = null) }
            try {
                val startTime = System.currentTimeMillis()
                sessionRepository.refreshSessions()
                // Minimum delay for UX
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed < 1000) delay(1000 - elapsed)
            } catch (e: Exception) {
                _localState.update { it.copy(errorMessage = "Failed to refresh: ${e.message}") }
            } finally {
                _localState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    /** Select a session by kuerzel for command targeting. */
    fun selectSession(kuerzel: String) {
        sessionRepository.selectSession(kuerzel)
    }

    /** Toggle expanded state of a session card's /last response section. */
    fun toggleCardExpanded(kuerzel: String) {
        _localState.update { state ->
            val expanded = state.expandedCards.toMutableSet()
            if (kuerzel in expanded) expanded.remove(kuerzel) else expanded.add(kuerzel)
            state.copy(expandedCards = expanded)
        }
    }

    /** Fetch the last response for a specific session. */
    fun fetchLastResponse(kuerzel: String) {
        viewModelScope.launch {
            try {
                val response = sessionRepository.getLastResponse(kuerzel)
                if (response != null) {
                    _localState.update { state ->
                        state.copy(lastResponses = state.lastResponses + (kuerzel to response))
                    }
                }
            } catch (_: Exception) {
                // Non-fatal: last response fetch failure
            }
        }
    }

    /**
     * Route and execute user command input.
     * Uses [CommandRouter] for type-safe command categorization.
     */
    fun handleCommandInput(input: String) {
        viewModelScope.launch {
            val result = CommandRouter.route(input, uiState.value.selectedKuerzel)
            try {
                when (result) {
                    is CommandRouter.CommandResult.Global ->
                        telegramRepository.sendRawCommand(result.command)
                    is CommandRouter.CommandResult.SessionTargeted ->
                        telegramRepository.sendRawCommand(result.command)
                    is CommandRouter.CommandResult.Message ->
                        telegramRepository.sendCommand(result.kuerzel, result.text)
                    is CommandRouter.CommandResult.NoSessionSelected ->
                        _localState.update { it.copy(errorMessage = "Select a session first") }
                }
            } catch (e: Exception) {
                _localState.update { it.copy(errorMessage = "Command failed: ${e.message}") }
            }
        }
    }

    /** Clear any currently displayed error message. */
    fun clearError() {
        _localState.update { it.copy(errorMessage = null) }
    }
}
