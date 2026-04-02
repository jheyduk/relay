package dev.heyduk.relay.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import dev.heyduk.relay.MainActivity
import dev.heyduk.relay.domain.model.RelayUpdate

/**
 * Handles notification channels and emission for permission requests
 * and session completion messages.
 *
 * Two channels:
 * - relay_permissions (HIGH): sound + vibration for permission requests
 * - relay_updates (DEFAULT): badge only for completions
 */
class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_PERMISSIONS = "relay_permissions"
        const val CHANNEL_UPDATES = "relay_updates"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureChannels()
    }

    private fun ensureChannels() {
        val permissionsChannel = NotificationChannel(
            CHANNEL_PERMISSIONS,
            "Permissions",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(true)
            setSound(
                android.provider.Settings.System.DEFAULT_NOTIFICATION_URI,
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            )
        }

        val updatesChannel = NotificationChannel(
            CHANNEL_UPDATES,
            "Updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            enableVibration(false)
            setSound(null, null)
        }

        notificationManager.createNotificationChannel(permissionsChannel)
        notificationManager.createNotificationChannel(updatesChannel)
    }

    /**
     * Emits a high-priority notification for a permission request.
     * Uses heads-up display with sound and vibration.
     */
    fun showPermissionNotification(update: RelayUpdate) {
        val contentText = buildPermissionContent(update)

        val notification = NotificationCompat.Builder(context, CHANNEL_PERMISSIONS)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Permission: @${update.session}")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(update.message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setGroup("relay_session_${update.session}")
            .setContentIntent(buildPendingIntent(update.session))
            .build()

        notificationManager.notify(update.updateId.toInt(), notification)
    }

    /**
     * Emits a default-priority notification for a session completion.
     */
    fun showCompletionNotification(update: RelayUpdate) {
        val contentText = update.message.take(80)

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Complete: @${update.session}")
            .setContentText(contentText)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setGroup("relay_session_${update.session}")
            .setContentIntent(buildPendingIntent(update.session))
            .build()

        notificationManager.notify(update.updateId.toInt(), notification)
    }

    private fun buildPermissionContent(update: RelayUpdate): String {
        val raw = "${update.toolName ?: "Tool"}: ${update.command ?: update.filePath ?: update.message}"
        return raw.take(100)
    }

    private fun buildPendingIntent(session: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            data = Uri.parse("relay://chat/$session")
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            session.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
