package dev.heyduk.relay.presentation.status

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.heyduk.relay.data.repository.RelayRepository
import dev.heyduk.relay.domain.model.RelayUpdate
import dev.heyduk.relay.service.NetworkMonitor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the status screen.
 * Combines network connectivity state with recent messages from the repository.
 */
class StatusViewModel(
    private val repository: RelayRepository,
    private val networkMonitor: NetworkMonitor
) : ViewModel() {

    data class StatusUiState(
        val isConnected: Boolean = false,
        val isPollingActive: Boolean = false,
        val recentMessages: List<RelayUpdate> = emptyList(),
        val errorMessage: String? = null
    )

    val uiState: StateFlow<StatusUiState> = combine(
        networkMonitor.isConnected,
        repository.getRecentMessages()
    ) { connected, messages ->
        StatusUiState(
            isConnected = connected,
            isPollingActive = connected,
            recentMessages = messages,
            errorMessage = null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = StatusUiState()
    )

    /**
     * Send a raw command (e.g. /ls) via the relay server.
     */
    suspend fun sendRawCommand(command: String) {
        try {
            repository.sendRawCommand(command)
        } catch (_: Exception) {
            // Command sending failures are non-fatal
        }
    }
}
