package dev.heyduk.relay.util

/**
 * Returns the current time in epoch milliseconds.
 * Platform-specific implementation.
 */
internal expect fun currentTimeMillis(): Long
