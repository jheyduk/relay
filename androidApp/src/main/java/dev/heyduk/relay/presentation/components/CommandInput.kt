package dev.heyduk.relay.presentation.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
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
 * Persistent bottom bar command input with send, attach, and voice buttons.
 */
@Composable
fun CommandInput(
    selectedKuerzel: String?,
    onSendCommand: (String) -> Unit,
    isRecording: Boolean = false,
    onMicPressed: (() -> Unit)? = null,
    onMicReleased: (() -> Unit)? = null,
    onAttach: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var text by rememberSaveable { mutableStateOf("") }

    Surface(
        tonalElevation = 3.dp,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            // Attach button
            if (onAttach != null) {
                IconButton(onClick = onAttach) {
                    Icon(
                        imageVector = Icons.Filled.AttachFile,
                        contentDescription = "Attach file",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Non-editable session prefix
            if (selectedKuerzel != null) {
                Text(
                    text = "@$selectedKuerzel ",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium
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
