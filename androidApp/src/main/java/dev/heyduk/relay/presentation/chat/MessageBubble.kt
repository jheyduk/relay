package dev.heyduk.relay.presentation.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.heyduk.relay.domain.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Chat message bubble with distinct styles for incoming (Claude) and outgoing (user) messages.
 * Incoming: left-aligned, subtle background, monospace for code-like content.
 * Outgoing: right-aligned, primary tint, chat-bubble tail shape.
 */
@Composable
fun MessageBubble(
    message: ChatMessage,
    isTtsPlaying: Boolean = false,
    onPlayTts: (() -> Unit)? = null,
    onStopTts: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isOutgoing = message.isOutgoing
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp

    val bubbleShape = if (isOutgoing) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomEnd = 4.dp,
            bottomStart = 16.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 4.dp,
            topEnd = 16.dp,
            bottomEnd = 16.dp,
            bottomStart = 16.dp
        )
    }

    val bubbleColor = if (isOutgoing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }

    val contentColor = if (isOutgoing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (isOutgoing) 48.dp else 0.dp,
                end = if (isOutgoing) 0.dp else 48.dp
            ),
        contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            tonalElevation = if (isOutgoing) 0.dp else 1.dp,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = screenWidth * 0.85f)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                val hasWideContent = message.content.lines().any { it.length > 60 }
                val scrollModifier = if (hasWideContent) {
                    Modifier.horizontalScroll(rememberScrollState())
                } else {
                    Modifier
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        lineHeight = 22.sp,
                        fontFamily = if (!isOutgoing) FontFamily.Monospace else FontFamily.Default
                    ),
                    color = contentColor,
                    softWrap = !hasWideContent,
                    modifier = scrollModifier
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTimestamp(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.5f)
                    )

                    if (!isOutgoing && onPlayTts != null) {
                        IconButton(
                            onClick = { if (isTtsPlaying) onStopTts?.invoke() else onPlayTts() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (isTtsPlaying) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                                contentDescription = if (isTtsPlaying) "Stop" else "Play",
                                tint = contentColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
