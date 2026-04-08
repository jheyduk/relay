package dev.heyduk.relay.presentation.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Presentation-only enum for auth recovery lifecycle phases. */
enum class AuthPhase {
    AUTH_REQUIRED,
    OPEN_URL,
    ENTER_CODE,
    RECOVERED,
    TIMED_OUT
}

private val AuthAmber = Color(0xFFFF8F00)
private val AuthAmberBg = Color(0xFFFFF3E0)
private val StatusBlue = Color(0xFF1976D2)
private val StatusGreen = Color(0xFF4CAF50)
private val StatusRed = Color(0xFFF44336)

/**
 * Auth recovery card shown as a persistent overlay above the chat input
 * when an auth error is detected. Renders different content for each
 * phase of the recovery lifecycle.
 */
@Composable
fun AuthRecoveryCard(
    session: String,
    authPhase: AuthPhase,
    authUrl: String?,
    onOpenUrl: () -> Unit,
    onSendCode: (String) -> Unit,
    onRetry: () -> Unit,
    isSending: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AuthAmberBg,
        tonalElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Session label
            Text(
                text = "@$session",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Status chip with colored circle
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (circleColor, statusText) = when (authPhase) {
                    AuthPhase.AUTH_REQUIRED -> AuthAmber to "Auth Required"
                    AuthPhase.OPEN_URL -> AuthAmber to "Open URL to Authenticate"
                    AuthPhase.ENTER_CODE -> StatusBlue to "Enter Authorization Code"
                    AuthPhase.RECOVERED -> StatusGreen to "Recovered"
                    AuthPhase.TIMED_OUT -> StatusRed to "Auth Recovery Timed Out"
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(circleColor)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelLarge,
                    color = circleColor
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Phase-specific content
            when (authPhase) {
                AuthPhase.AUTH_REQUIRED -> {
                    Text(
                        text = "Authentication expired. Login has been triggered automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                AuthPhase.OPEN_URL -> {
                    Text(
                        text = "Open this URL to authenticate:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (authUrl != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = authUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onOpenUrl,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuthAmber
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open in Browser")
                    }
                }

                AuthPhase.ENTER_CODE -> {
                    var codeText by remember { mutableStateOf("") }
                    var codeSent by remember { mutableStateOf(false) }

                    Text(
                        text = "Paste the authorization code from your browser:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { codeText = it },
                        label = { Text("Authorization code") },
                        singleLine = true,
                        enabled = !codeSent && !isSending,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            codeSent = true
                            onSendCode(codeText)
                        },
                        enabled = codeText.isNotBlank() && !codeSent && !isSending,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuthAmber
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSending) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Send Code")
                        }
                    }
                }

                AuthPhase.RECOVERED -> {
                    Text(
                        text = "Authentication restored successfully.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusGreen
                    )
                }

                AuthPhase.TIMED_OUT -> {
                    Text(
                        text = "Recovery timed out.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = onRetry,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Retry")
                    }
                }
            }
        }
    }
}
