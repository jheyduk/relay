package dev.heyduk.relay.presentation.theme

import androidx.compose.ui.graphics.Color
import dev.heyduk.relay.domain.model.SessionStatus

val WorkingColor = Color(0xFF2196F3)  // Blue
val WaitingColor = Color(0xFFFF9800)  // Orange
val ReadyColor = Color(0xFF4CAF50)    // Green
val ShellColor = Color(0xFF9E9E9E)    // Gray

fun statusColor(status: SessionStatus?): Color = when (status) {
    SessionStatus.WORKING -> WorkingColor
    SessionStatus.WAITING -> WaitingColor
    SessionStatus.READY -> ReadyColor
    SessionStatus.SHELL -> ShellColor
    null -> Color.Transparent
}
