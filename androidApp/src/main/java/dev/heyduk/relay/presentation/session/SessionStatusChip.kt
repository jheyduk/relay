package dev.heyduk.relay.presentation.session

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.heyduk.relay.domain.model.SessionStatus

// Status color mapping per user decision
private val WorkingColor = Color(0xFF2196F3) // Blue
private val WaitingColor = Color(0xFFFF9800) // Orange
private val ReadyColor = Color(0xFF4CAF50)   // Green
private val ShellColor = Color(0xFF9E9E9E)   // Gray

/**
 * Color-coded status chip for a session.
 * WORKING status gets an animated pulsing indicator as leading icon.
 */
@Composable
fun SessionStatusChip(status: SessionStatus, modifier: Modifier = Modifier) {
    val color = when (status) {
        SessionStatus.WORKING -> WorkingColor
        SessionStatus.WAITING -> WaitingColor
        SessionStatus.READY -> ReadyColor
        SessionStatus.SHELL -> ShellColor
    }

    AssistChip(
        onClick = { /* Non-interactive status indicator */ },
        label = { Text(status.name.lowercase().replaceFirstChar { it.uppercase() }) },
        leadingIcon = if (status == SessionStatus.WORKING) {
            { AnimatedWorkingIndicator() }
        } else {
            null
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.15f),
            labelColor = color
        ),
        border = null,
        modifier = modifier
    )
}

/**
 * Animated pulsing circle indicator for the WORKING status.
 * Pulses alpha between 0.3 and 1.0 over 800ms with reverse repeat.
 */
@Composable
fun AnimatedWorkingIndicator(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "working_pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "working_alpha"
    )

    Box(
        modifier = modifier
            .size(8.dp)
            .alpha(alpha)
            .clip(CircleShape)
            .background(WorkingColor)
    )
}
