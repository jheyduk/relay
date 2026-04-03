package dev.heyduk.relay.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.heyduk.relay.presentation.chat.VoiceRecordButton

/**
 * Persistent bottom bar command input.
 *
 * Shows @kuerzel prefix when a session is selected.
 * The ViewModel handles command routing -- this composable does NOT add the @prefix to the text.
 */
@Composable
fun CommandInput(
    selectedKuerzel: String?,
    onSendCommand: (String) -> Unit,
    isRecording: Boolean = false,
    onMicPressed: (() -> Unit)? = null,
    onMicReleased: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }

    Surface(
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Non-editable session prefix
            if (selectedKuerzel != null) {
                Text(
                    text = "@$selectedKuerzel ",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text("Type command...") },
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    if (text.isNotBlank()) {
                        onSendCommand(text)
                        text = ""
                    }
                }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send"
                )
            }

            if (onMicPressed != null && onMicReleased != null) {
                VoiceRecordButton(
                    isRecording = isRecording,
                    onStartRecording = onMicPressed,
                    onStopRecording = onMicReleased
                )
            }
        }
        }
    }
}
