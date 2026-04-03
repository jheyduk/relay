package dev.heyduk.relay.presentation.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the server setup screen.
 * Persists the shared secret and optional WireGuard IP to DataStore.
 * No network validation needed -- the server may not be running during setup.
 */
class SetupViewModel(
    private val dataStore: DataStore<Preferences>
) : ViewModel() {

    data class SetupUiState(
        val serverSecret: String = "",
        val wireguardIp: String = "",
        val isValidating: Boolean = false,
        val validationResult: String? = null,
        val isConfigured: Boolean = false
    )

    companion object {
        val SERVER_SECRET_KEY = stringPreferencesKey("server_secret")
        val WIREGUARD_IP_KEY = stringPreferencesKey("wireguard_ip")
    }

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        loadExisting()
    }

    private fun loadExisting() {
        viewModelScope.launch {
            val prefs = dataStore.data.first()
            _uiState.update { state ->
                state.copy(
                    serverSecret = prefs[SERVER_SECRET_KEY] ?: "",
                    wireguardIp = prefs[WIREGUARD_IP_KEY] ?: "",
                    isConfigured = !prefs[SERVER_SECRET_KEY].isNullOrBlank()
                )
            }
        }
    }

    fun updateServerSecret(value: String) {
        _uiState.update { it.copy(serverSecret = value, validationResult = null) }
    }

    fun updateWireguardIp(value: String) {
        _uiState.update { it.copy(wireguardIp = value, validationResult = null) }
    }

    /**
     * Validates that the server secret is not blank, then saves to DataStore.
     * WireGuard IP is optional -- used as fallback when mDNS discovery fails.
     */
    fun validateAndSave() {
        viewModelScope.launch {
            _uiState.update { it.copy(isValidating = true, validationResult = null) }

            try {
                val state = _uiState.value
                if (state.serverSecret.isBlank()) {
                    _uiState.update {
                        it.copy(isValidating = false, validationResult = "Server secret is required")
                    }
                    return@launch
                }

                // Save to DataStore
                dataStore.edit { prefs ->
                    prefs[SERVER_SECRET_KEY] = state.serverSecret
                    prefs[WIREGUARD_IP_KEY] = state.wireguardIp
                }

                _uiState.update {
                    it.copy(
                        isValidating = false,
                        validationResult = "Configuration saved successfully",
                        isConfigured = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isValidating = false,
                        validationResult = "Save failed: ${e.message}"
                    )
                }
            }
        }
    }
}
