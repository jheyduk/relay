package dev.heyduk.relay.presentation.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.heyduk.relay.domain.model.RelayMessageType
import dev.heyduk.relay.presentation.components.CommandInput
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Per-session chat screen.
 * Shows conversation history in a reverse-layout LazyColumn (newest at bottom)
 * and reuses CommandInput for message sending.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    kuerzel: String,
    onNavigateBack: () -> Unit,
    viewModel: ChatViewModel = koinViewModel { parametersOf(kuerzel) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Show error messages via snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("@$kuerzel") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        bottomBar = {
            CommandInput(
                selectedKuerzel = kuerzel,
                onSendCommand = viewModel::sendMessage,
                modifier = Modifier.fillMaxWidth()
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (uiState.messages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No messages yet.\nSend a message to start the conversation.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        LazyColumn(
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = uiState.messages.reversed(),
                key = { it.id }
            ) { message ->
                when (message.type) {
                    RelayMessageType.PERMISSION -> PermissionCard(
                        message = message,
                        onAllow = { viewModel.answerCallback(message.id, "allow") },
                        onDeny = { viewModel.answerCallback(message.id, "deny") },
                        isSending = message.id in uiState.sendingCallbackIds
                    )
                    RelayMessageType.QUESTION -> QuestionCard(
                        message = message,
                        onOptionSelected = { option -> viewModel.answerQuestion(message.id, option) }
                    )
                    else -> MessageBubble(
                        message = message,
                        isTtsPlaying = uiState.ttsPlayingMessageId == message.id,
                        onPlayTts = if (!message.isOutgoing) {
                            { viewModel.playTts(message.id, message.content) }
                        } else null,
                        onStopTts = { viewModel.stopTts() }
                    )
                }
            }
        }
    }
}
