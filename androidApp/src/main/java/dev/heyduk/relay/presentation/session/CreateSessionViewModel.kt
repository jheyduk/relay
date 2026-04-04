package dev.heyduk.relay.presentation.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.heyduk.relay.data.repository.RelayRepository
import dev.heyduk.relay.domain.model.DirectoryEntry
import dev.heyduk.relay.domain.model.RelayMessageType
import dev.heyduk.relay.util.fuzzyMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * ViewModel managing the session creation dialog state and WebSocket interactions.
 * Handles directory listing, fuzzy search filtering, and session creation flow.
 */
class CreateSessionViewModel(
    private val relayRepository: RelayRepository
) : ViewModel() {

    data class CreateSessionUiState(
        val phase: DialogPhase = DialogPhase.LOADING,
        val directories: List<DirectoryEntry> = emptyList(),
        val filteredDirectories: List<DirectoryEntry> = emptyList(),
        val searchQuery: String = "",
        val selectedPath: String = "",
        val kuerzel: String = "",
        val kuerzelError: String? = null,
        val flags: String = "",
        val defaultFlags: String = "",
        val flagsEnabled: Boolean = true,
        val isCreating: Boolean = false,
        val errorMessage: String? = null,
        val createdKuerzel: String? = null  // non-null = success, navigate
    )

    enum class DialogPhase { LOADING, DIRECTORY_SELECTION, CUSTOM_PATH, CONFIRMATION }

    private val _uiState = MutableStateFlow(CreateSessionUiState())
    val uiState: StateFlow<CreateSessionUiState> = _uiState.asStateFlow()

    companion object {
        private const val SERVER_TIMEOUT_MS = 10_000L
    }

    /**
     * Request the directory list from the server. Called when dialog opens.
     * Sends list_directories action and waits for DIRECTORY_LIST response.
     */
    fun loadDirectories() {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = DialogPhase.LOADING, errorMessage = null) }
            try {
                relayRepository.sendListDirectories()

                val update = withTimeoutOrNull(SERVER_TIMEOUT_MS) {
                    relayRepository.updates
                        .filter { it.type == RelayMessageType.DIRECTORY_LIST }
                        .first()
                }

                if (update != null) {
                    val dirs = update.directoryList ?: emptyList()
                    val flags = update.defaultFlags ?: ""
                    _uiState.update {
                        it.copy(
                            phase = DialogPhase.DIRECTORY_SELECTION,
                            directories = dirs,
                            filteredDirectories = dirs,
                            defaultFlags = flags,
                            flags = flags,
                            flagsEnabled = flags.isNotEmpty()
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            phase = DialogPhase.DIRECTORY_SELECTION,
                            errorMessage = "Timeout waiting for directory list"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        phase = DialogPhase.DIRECTORY_SELECTION,
                        errorMessage = "Failed to load directories: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * Update the search query and filter directories using fuzzy matching.
     * Empty query shows all directories. Results sorted by match score descending.
     */
    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isEmpty()) {
                state.directories
            } else {
                state.directories
                    .map { entry -> entry to fuzzyMatch(query, entry.name) }
                    .filter { (_, score) -> score > 0 }
                    .sortedByDescending { (_, score) -> score }
                    .map { (entry, _) -> entry }
            }
            state.copy(searchQuery = query, filteredDirectories = filtered)
        }
    }

    /** Select a directory from the list and proceed to confirmation. */
    fun onDirectorySelected(entry: DirectoryEntry) {
        _uiState.update {
            it.copy(
                phase = DialogPhase.CONFIRMATION,
                selectedPath = entry.path,
                kuerzel = entry.name,
                kuerzelError = null
            )
        }
    }

    /** Switch to the custom path entry phase. */
    fun onCustomPathSelected() {
        _uiState.update { it.copy(phase = DialogPhase.CUSTOM_PATH) }
    }

    /** Confirm a manually entered path and proceed to confirmation. */
    fun onCustomPathConfirmed(path: String) {
        val name = path.trimEnd('/').substringAfterLast('/')
        _uiState.update {
            it.copy(
                phase = DialogPhase.CONFIRMATION,
                selectedPath = path,
                kuerzel = name,
                kuerzelError = null
            )
        }
    }

    /** Update the kuerzel field and clear any previous validation error. */
    fun onKuerzelChanged(value: String) {
        _uiState.update { it.copy(kuerzel = value, kuerzelError = null) }
    }

    /** Toggle the flags (--dangerously-skip-permissions). */
    fun onFlagsToggled() {
        _uiState.update { state ->
            val enabled = !state.flagsEnabled
            state.copy(
                flagsEnabled = enabled,
                flags = if (enabled) state.defaultFlags else ""
            )
        }
    }

    /**
     * Validate inputs and send create_session to the server.
     * On success, sets createdKuerzel to trigger navigation.
     * On error, shows error message via snackbar.
     */
    fun onCreateSession() {
        val state = _uiState.value
        if (state.kuerzel.isBlank()) {
            _uiState.update { it.copy(kuerzelError = "Kuerzel is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isCreating = true, errorMessage = null) }
            try {
                relayRepository.sendCreateSession(
                    path = state.selectedPath,
                    kuerzel = state.kuerzel,
                    flags = state.flags
                )

                val update = withTimeoutOrNull(SERVER_TIMEOUT_MS) {
                    relayRepository.updates
                        .filter { it.type == RelayMessageType.SESSION_CREATED }
                        .first()
                }

                if (update != null) {
                    if (update.sessionCreatedSuccess == true) {
                        _uiState.update {
                            it.copy(
                                isCreating = false,
                                createdKuerzel = update.sessionCreatedKuerzel ?: state.kuerzel
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isCreating = false,
                                errorMessage = update.sessionCreatedError ?: "Session creation failed"
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isCreating = false,
                            errorMessage = "Timeout waiting for session creation response"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isCreating = false,
                        errorMessage = "Failed to create session: ${e.message}"
                    )
                }
            }
        }
    }

    /** Go back to directory selection from confirmation or custom path. */
    fun onBackToDirectorySelection() {
        _uiState.update {
            it.copy(
                phase = DialogPhase.DIRECTORY_SELECTION,
                selectedPath = "",
                kuerzel = "",
                kuerzelError = null
            )
        }
    }

    /** Clear the current error message (e.g. after snackbar dismiss). */
    fun onDismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
