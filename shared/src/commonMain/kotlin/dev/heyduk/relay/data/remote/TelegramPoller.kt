package dev.heyduk.relay.data.remote

import dev.heyduk.relay.domain.model.RelayUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.isActive

/**
 * Long-polling loop that fetches Telegram updates with exponential backoff.
 * Emits parsed [RelayUpdate] objects to the [updates] flow.
 *
 * Backoff schedule on error: 0 -> 1s -> 2s -> 4s -> 8s -> 16s -> 30s (capped).
 * Resets to 0 on success.
 */
class TelegramPoller(
    private val api: TelegramApi,
    private val parser: RelayMessageParser,
    private val offsetProvider: OffsetProvider
) {
    private val _updates = MutableSharedFlow<RelayUpdate>(extraBufferCapacity = 64)
    val updates: SharedFlow<RelayUpdate> = _updates

    suspend fun pollLoop() {
        var backoffMs = 0L
        while (currentCoroutineContext().isActive) {
            if (backoffMs > 0) {
                delay(backoffMs)
            }
            try {
                val offset = offsetProvider.getOffset()
                val telegramUpdates = api.getUpdates(offset = offset, timeout = 30)

                if (telegramUpdates.isNotEmpty()) {
                    val newOffset = telegramUpdates.maxOf { it.updateId } + 1
                    // Persist offset BEFORE processing (at-least-once delivery)
                    offsetProvider.setOffset(newOffset)

                    for (update in telegramUpdates) {
                        val text = update.message?.text ?: continue
                        val parsed = parser.parse(
                            updateId = update.updateId,
                            messageText = text,
                            timestamp = update.message.date
                        )
                        if (parsed != null) {
                            _updates.emit(parsed)
                        }
                    }
                }
                backoffMs = 0 // Reset on success
            } catch (e: CancellationException) {
                throw e // CRITICAL: never swallow cancellation
            } catch (_: Exception) {
                backoffMs = if (backoffMs == 0L) 1000L
                           else (backoffMs * 2).coerceAtMost(30_000L)
            }
        }
    }
}

/**
 * Interface for offset persistence.
 * Platform-specific implementations use DataStore or similar.
 */
interface OffsetProvider {
    suspend fun getOffset(): Long
    suspend fun setOffset(offset: Long)
}
