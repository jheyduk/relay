package dev.heyduk.relay.voice

/**
 * JNI bridge to whisper.cpp native library.
 * Methods map 1:1 to native functions in whisper-jni.cpp.
 */
class WhisperJni {
    companion object {
        init {
            System.loadLibrary("whisper-jni")
        }
    }

    /** Initialize whisper context from model file path. Returns context pointer (0 on failure). */
    external fun initContext(modelPath: String): Long

    /** Transcribe PCM float audio samples. Returns transcribed text. */
    external fun transcribeAudio(contextPtr: Long, audioData: FloatArray): String

    /** Free whisper context and release native memory. */
    external fun freeContext(contextPtr: Long)
}
