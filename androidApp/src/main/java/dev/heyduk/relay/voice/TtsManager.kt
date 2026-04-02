package dev.heyduk.relay.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.util.Locale

/**
 * Wraps Android TextToSpeech with lifecycle management and state tracking.
 * Exposes a StateFlow of the currently-speaking message ID (null = idle).
 */
class TtsManager(context: Context) {

    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _speakingMessageId = MutableStateFlow<Long?>(null)
    /** The ID of the message currently being spoken, or null if idle. */
    val speakingMessageId: StateFlow<Long?> = _speakingMessageId

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                isInitialized = true
                Timber.i("TTS initialized successfully")
            } else {
                Timber.e("TTS initialization failed with status $status")
            }
        }

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Already set in speak()
            }
            override fun onDone(utteranceId: String?) {
                _speakingMessageId.value = null
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _speakingMessageId.value = null
                Timber.e("TTS error for utterance $utteranceId")
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                _speakingMessageId.value = null
                Timber.e("TTS error $errorCode for utterance $utteranceId")
            }
        })
    }

    /**
     * Speak the given text aloud, associated with a message ID.
     * Strips code blocks (``` ... ```) from the text before speaking.
     * Stops any currently speaking utterance first.
     */
    fun speak(messageId: Long, text: String) {
        if (!isInitialized) {
            Timber.w("TTS not initialized, ignoring speak request")
            return
        }
        val cleanText = stripCodeBlocks(text)
        if (cleanText.isBlank()) {
            Timber.w("Nothing to speak after stripping code blocks")
            return
        }
        _speakingMessageId.value = messageId
        tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, messageId.toString())
    }

    /** Stop any current TTS playback. */
    fun stop() {
        tts?.stop()
        _speakingMessageId.value = null
    }

    /** Release TTS resources. Call when the app is shutting down. */
    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        _speakingMessageId.value = null
    }

    /**
     * Remove fenced code blocks from text for cleaner TTS output.
     * Replaces ```...``` blocks with "code block omitted".
     */
    private fun stripCodeBlocks(text: String): String {
        return text.replace(Regex("```[\\s\\S]*?```"), " code block omitted ")
            .replace(Regex("`[^`]+`"), "")  // also strip inline code
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
