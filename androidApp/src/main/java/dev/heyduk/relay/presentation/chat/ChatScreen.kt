package dev.heyduk.relay.presentation.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import dev.heyduk.relay.domain.model.SessionStatus
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val mimeType = context.contentResolver.getType(uri)
                val filename = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
                } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
                val bytes: ByteArray

                // Resize images to fit Claude Code's 2000x2000 Read tool limit
                if (mimeType?.startsWith("image/") == true) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: return@rememberLauncherForActivityResult
                    val original = android.graphics.BitmapFactory.decodeStream(input)
                    input.close()
                    val maxDim = 1920
                    val scaled = if (original.width > maxDim || original.height > maxDim) {
                        val ratio = minOf(maxDim.toFloat() / original.width, maxDim.toFloat() / original.height)
                        android.graphics.Bitmap.createScaledBitmap(
                            original, (original.width * ratio).toInt(), (original.height * ratio).toInt(), true
                        )
                    } else original
                    val stream = java.io.ByteArrayOutputStream()
                    val format = if (mimeType == "image/png")
                        android.graphics.Bitmap.CompressFormat.PNG
                    else
                        android.graphics.Bitmap.CompressFormat.JPEG
                    scaled.compress(format, 95, stream)
                    bytes = stream.toByteArray()
                    if (scaled !== original) scaled.recycle()
                    original.recycle()
                } else {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: return@rememberLauncherForActivityResult
                    bytes = input.readBytes()
                    input.close()
                }

                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                viewModel.stageAttachment(filename, base64)
            } catch (_: Exception) {
                viewModel.showMessage("Failed to load file")
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.startRecording()
    }

    fun handleMicPress() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.startRecording()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Show error messages via snackbar
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    val statusColor = dev.heyduk.relay.presentation.theme.statusColor(uiState.sessionStatus)
    val statusLabel = uiState.sessionStatus?.name?.lowercase() ?: ""

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("@$kuerzel")
                            if (statusLabel.isNotEmpty()) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = statusColor
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                )
                // Status color bar under the top bar — animated when working
                if (uiState.sessionStatus != null) {
                    if (uiState.sessionStatus == SessionStatus.WORKING) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = statusColor,
                            trackColor = statusColor.copy(alpha = 0.2f)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(statusColor)
                        )
                    }
                }
            }
        },
        bottomBar = {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
                if (uiState.isTranscribing) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                val transcript = uiState.transcriptPreview
                if (transcript != null) {
                    TranscriptPreview(
                        transcript = transcript,
                        onSend = viewModel::sendTranscript,
                        onCancel = viewModel::cancelTranscript,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    CommandInput(
                        selectedKuerzel = kuerzel,
                        onSendCommand = viewModel::sendMessage,
                        isRecording = uiState.isRecording,
                        onMicPressed = { handleMicPress() },
                        onMicReleased = { viewModel.stopRecording() },
                        onAttach = { filePickerLauncher.launch("*/*") },
                        pendingAttachmentName = uiState.pendingAttachment?.filename,
                        onClearAttachment = viewModel::clearAttachment,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
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

        val listState = rememberLazyListState()

        // Auto-scroll to bottom when new messages arrive
        LaunchedEffect(uiState.messages.size) {
            if (uiState.messages.isNotEmpty()) {
                listState.animateScrollToItem(uiState.messages.size - 1)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(
                items = uiState.messages,
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
                        onAnswer = { type, selections, text, optionCount ->
                            viewModel.answerQuestion(message.id, type, selections, text, optionCount)
                        }
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
