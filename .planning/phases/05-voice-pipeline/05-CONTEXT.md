# Phase 5: Voice Pipeline - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Add on-device Whisper speech-to-text and Android TTS for hands-free interaction. Users can hold-to-record voice, see a transcript preview, and send it as text. Incoming messages get a play button for TTS readback. This is the most technically complex phase due to whisper.cpp JNI integration.

</domain>

<decisions>
## Implementation Decisions

### Voice Input
- Hold-to-Record FAB — microphone icon, press-and-hold to record, release to transcribe
- Whisper model: ggml-base.en quantized (~18MB), bundled in APK assets
- Recording → inline waveform animation → transcript preview before sending → user can edit/cancel/send
- Audio format: WAV 16kHz mono (native whisper.cpp format, no transcoding needed)
- whisper.cpp via JNI — native library for Android ARM64

### TTS Playback
- Android built-in TextToSpeech engine — zero external dependencies
- Small speaker icon on every incoming message bubble — tap to read aloud
- Uses system default language setting
- Stop button replaces play while speaking

### Claude's Discretion
- whisper.cpp JNI bridge implementation details
- Exact waveform animation
- Audio recording lifecycle management
- TTS queue management for long messages
- CMake/NDK build configuration

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ChatScreen.kt` — add record button to bottom bar, TTS icon to message bubbles
- `ChatViewModel.kt` — add transcription flow and TTS control
- `ChatRepository.sendMessage()` — transcribed text uses same send path as typed text
- `MessageBubble.kt` — add TTS play/stop icon
- `CommandInput.kt` — add microphone button next to send button

### Established Patterns
- Compose UI with StateFlow
- Koin DI for all dependencies
- Repository pattern

### Integration Points
- `androidApp/build.gradle.kts` — add NDK/CMake configuration for whisper.cpp
- `androidApp/src/main/jniLibs/` or CMake native build — whisper.cpp shared library
- `androidApp/src/main/assets/` — ggml-base.en.bin model file
- `AndroidManifest.xml` — RECORD_AUDIO permission

</code_context>

<specifics>
## Specific Ideas

- The whisper.cpp JNI bridge should expose a simple `transcribe(wavFilePath: String): String` interface
- Model should be copied from assets to internal storage on first launch (assets can't be mmap'd)
- Recording should show a visual timer and waveform
- TTS should handle code blocks gracefully (maybe skip or summarize them)

</specifics>

<deferred>
## Deferred Ideas

- Voice-to-session pipeline with session name detection (v2 — VOIC-04)
- Unified chat stream with audio playback inline (v2 — VOIC-05)

</deferred>
