package dev.heyduk.relay.util

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dev.heyduk.relay.service.WebSocketService

/**
 * Starts the WebSocketService as a foreground service.
 * Shared utility used by StatusScreen and auto-start logic.
 */
fun startWebSocketService(context: Context) {
    ContextCompat.startForegroundService(
        context,
        Intent(context, WebSocketService::class.java)
    )
}

/**
 * Invisible composable that requests POST_NOTIFICATIONS permission on Android 13+
 * and starts the WebSocketService. On older Android versions, starts immediately.
 *
 * Renders no visible UI -- purely side-effect driven.
 */
@Composable
fun RequestNotificationPermissionAndStartConnection() {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Start regardless of grant result -- service works without notification permission,
        // the foreground notification just won't be visible if denied.
        startWebSocketService(context)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startWebSocketService(context)
        }
    }
}
