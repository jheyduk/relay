package dev.heyduk.relay.presentation.setup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.heyduk.relay.data.remote.TelegramApiImpl
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the bot token setup screen.
 * Persists tokens to DataStore and validates them against Telegram's getMe endpoint.
 */
class SetupViewModel(
    private val dataStore: DataStore<Preferences>,
    private val httpClient: HttpClient
) : ViewModel() {

    data class SetupUiState(
        val relayBotToken: String = "",
        val commandBotToken: String = "",
        val chatId: String = "",
        val isValidating: Boolean = false,
        val validationResult: String? = null,
        val isConfigured: Boolean = false
    )

    companion object {
        val RELAY_BOT_TOKEN_KEY = stringPreferencesKey("relay_bot_token")
        val COMMAND_BOT_TOKEN_KEY = stringPreferencesKey("command_bot_token")
        val CHAT_ID_KEY = stringPreferencesKey("chat_id")
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
                    relayBotToken = prefs[RELAY_BOT_TOKEN_KEY] ?: "",
                    commandBotToken = prefs[COMMAND_BOT_TOKEN_KEY] ?: "",
                    chatId = prefs[CHAT_ID_KEY] ?: "",
                    isConfigured = !prefs[RELAY_BOT_TOKEN_KEY].isNullOrBlank()
                            && !prefs[COMMAND_BOT_TOKEN_KEY].isNullOrBlank()
                            && !prefs[CHAT_ID_KEY].isNullOrBlank()
                )
            }
        }
    }

    fun updateRelayBotToken(value: String) {
        _uiState.update { it.copy(relayBotToken = value, validationResult = null) }
    }

    fun updateCommandBotToken(value: String) {
        _uiState.update { it.copy(commandBotToken = value, validationResult = null) }
    }

    fun updateChatId(value: String) {
        _uiState.update { it.copy(chatId = value, validationResult = null) }
    }

    /**
     * Validates bot tokens by calling Telegram's getMe endpoint, then saves to DataStore.
     */
    fun validateAndSave() {
        viewModelScope.launch {
            _uiState.update { it.copy(isValidating = true, validationResult = null) }

            try {
                val state = _uiState.value
                if (state.relayBotToken.isBlank() || state.commandBotToken.isBlank() || state.chatId.isBlank()) {
                    _uiState.update {
                        it.copy(isValidating = false, validationResult = "All fields are required")
                    }
                    return@launch
                }

                // Validate relay bot token
                val relayApi = TelegramApiImpl(httpClient, state.relayBotToken, state.chatId)
                relayApi.sendMessage("Relay bot connected") // Simple connectivity test

                // Validate command bot token
                val cmdApi = TelegramApiImpl(httpClient, state.commandBotToken, state.chatId)
                cmdApi.sendMessage("Command bot connected") // Simple connectivity test

                // Save tokens to DataStore
                dataStore.edit { prefs ->
                    prefs[RELAY_BOT_TOKEN_KEY] = state.relayBotToken
                    prefs[COMMAND_BOT_TOKEN_KEY] = state.commandBotToken
                    prefs[CHAT_ID_KEY] = state.chatId
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
                        validationResult = "Validation failed: ${e.message}"
                    )
                }
            }
        }
    }
}
