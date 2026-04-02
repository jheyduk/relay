package dev.heyduk.relay.voice

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-level manager for whisper.cpp transcription.
 * Handles model file extraction from assets, context lifecycle, and thread-safe transcription.
 */
class WhisperManager(
    private val context: Context,
    private val whisperJni: WhisperJni = WhisperJni()
) {
    private var contextPtr: Long = 0L
    private val mutex = Mutex()
    private val modelFileName = "ggml-base.en.bin"

    /** Whether the whisper context is initialized and ready. */
    val isReady: Boolean get() = contextPtr != 0L

    /**
     * Initialize whisper context. Copies model from assets to internal storage on first call.
     * Must be called before transcribe(). Safe to call multiple times (no-op if already init).
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (contextPtr != 0L) return@withContext

        val modelFile = ensureModelFile()
        val ptr = whisperJni.initContext(modelFile.absolutePath)
        if (ptr == 0L) {
            throw IllegalStateException(
                "Failed to initialize whisper context from ${modelFile.absolutePath}"
            )
        }
        contextPtr = ptr
        Timber.i("WhisperManager initialized with model: ${modelFile.name}")
    }

    /**
     * Transcribe a WAV file (16kHz, mono, 16-bit PCM) to text.
     * Returns the transcribed text, or empty string on failure.
     */
    suspend fun transcribe(wavFile: File): String = mutex.withLock {
        withContext(Dispatchers.Default) {
            check(contextPtr != 0L) {
                "WhisperManager not initialized. Call initialize() first."
            }

            val samples = readWavAsFloatArray(wavFile)
            if (samples.isEmpty()) {
                Timber.w("Empty audio data from ${wavFile.name}")
                return@withContext ""
            }

            val result = whisperJni.transcribeAudio(contextPtr, samples)
            Timber.d("Transcribed ${samples.size} samples -> ${result.length} chars")
            result.trim()
        }
    }

    /**
     * Release native whisper context. Call when no longer needed.
     */
    fun release() {
        if (contextPtr != 0L) {
            whisperJni.freeContext(contextPtr)
            contextPtr = 0L
            Timber.i("WhisperManager released")
        }
    }

    /**
     * Copy model from assets to internal storage if not already present.
     * Assets cannot be mmap'd directly, so a file copy is required.
     */
    private fun ensureModelFile(): File {
        val destFile = File(context.filesDir, modelFileName)
        if (destFile.exists() && destFile.length() > 0) {
            return destFile
        }
        Timber.i("Copying whisper model from assets to ${destFile.absolutePath}")
        context.assets.open(modelFileName).use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output, bufferSize = 8192)
            }
        }
        return destFile
    }

    /**
     * Read a 16kHz mono 16-bit PCM WAV file into a FloatArray normalized to [-1, 1].
     * Skips the 44-byte WAV header.
     */
    private fun readWavAsFloatArray(file: File): FloatArray {
        val bytes = file.readBytes()
        if (bytes.size <= 44) return floatArrayOf()

        // Skip 44-byte WAV header
        val pcmBytes = bytes.copyOfRange(44, bytes.size)
        val shortBuffer = ByteBuffer.wrap(pcmBytes)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val samples = FloatArray(shortBuffer.remaining())
        for (i in samples.indices) {
            samples[i] = shortBuffer.get(i).toFloat() / 32768.0f
        }
        return samples
    }
}
