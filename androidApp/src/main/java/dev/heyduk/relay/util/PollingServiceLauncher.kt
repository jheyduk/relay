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
import dev.heyduk.relay.service.PollingService

/**
 * Starts the PollingService as a foreground service.
 * Shared utility used by StatusScreen and auto-start logic.
 */
fun startPollingService(context: Context) {
    ContextCompat.startForegroundService(
        context,
        Intent(context, PollingService::class.java)
    )
}

/**
 * Invisible composable that requests POST_NOTIFICATIONS permission on Android 13+
 * and starts the PollingService. On older Android versions, starts immediately.
 *
 * Renders no visible UI -- purely side-effect driven.
 */
@Composable
fun RequestNotificationPermissionAndStartPolling() {
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Start regardless of grant result -- service works without notification permission,
        // the foreground notification just won't be visible if denied.
        startPollingService(context)
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startPollingService(context)
        }
    }
}
