---
phase: 05-voice-pipeline
verified: 2026-04-02T12:00:00Z
status: human_needed
score: 7/7 must-haves verified
re_verification: false
human_verification:
  - test: "Hold mic button to record voice, release to transcribe"
    expected: "Button turns red while recording; after release a LinearProgressIndicator appears briefly; transcript preview replaces the command input bar with the transcribed text"
    why_human: "Requires physical device with microphone and the 141MB ggml-base.en.bin model present in assets; cannot verify on-device Whisper output without running app"
  - test: "Edit transcript and send"
    expected: "Edited text appears in conversation history as a normal outgoing text message, indistinguishable from a typed message"
    why_human: "Requires running app and active Telegram session"
  - test: "Tap speaker icon on an incoming message"
    expected: "Android TTS reads the message content aloud; icon changes from VolumeUp to Stop while speaking; tapping Stop halts playback immediately"
    why_human: "Requires physical device with audio output; cannot verify TTS audio output programmatically"
  - test: "TTS code block stripping"
    expected: "Fenced code blocks (``` ... ```) are replaced with 'code block omitted' in TTS output; inline code backticks are silently removed"
    why_human: "Requires listening to TTS output to confirm stripping; logic can be unit-tested but on-device audio confirmation needed"
  - test: "RECORD_AUDIO permission flow"
    expected: "On first mic press, system permission dialog appears; after granting, recording starts immediately without another press"
    why_human: "Permission dialogs require device interaction"
---

# Phase 5: Voice Pipeline Verification Report

**Phase Goal:** Users can speak to sessions and hear responses -- on-device Whisper transcription and Android TTS enable hands-free interaction
**Verified:** 2026-04-02T12:00:00Z
**Status:** human_needed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

All three success criteria from ROADMAP.md are used as the authoritative truths.

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can hold a button to record voice, transcribed on-device by Whisper without network access | VERIFIED | AudioRecorder.kt produces 16kHz mono WAV; WhisperManager.transcribe() calls whisperJni.transcribeAudio() locally; ChatViewModel.stopRecording() wires them together |
| 2 | Transcribed text is sent to active session as regular message (appears in conversation history as text) | VERIFIED | ChatViewModel.sendTranscript() calls sendMessage() which calls chatRepository.sendMessage(); same path as typed text |
| 3 | User can tap play button on any Claude response to hear it read aloud via TTS | VERIFIED | MessageBubble renders VolumeUp/Stop IconButton on incoming messages; ChatViewModel.playTts() delegates to TtsManager.speak(); ttsPlayingMessageId StateFlow drives play/stop toggle |

**Score:** 3/3 success criteria verified (all must-haves pass automated checks)

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `androidApp/src/main/cpp/CMakeLists.txt` | CMake build for whisper.cpp + JNI bridge | VERIFIED | 55 lines; FetchContent for ggml; arm64-v8a NEON optimization; links whisper-jni shared library |
| `androidApp/src/main/cpp/whisper-jni.cpp` | C++ JNI bridge with initContext, transcribeAudio, freeContext | VERIFIED | 79 lines; all three extern-C functions present with proper JNI naming for `dev.heyduk.relay.voice.WhisperJni` |
| `androidApp/src/main/java/dev/heyduk/relay/voice/WhisperJni.kt` | Kotlin JNI declarations matching native methods | VERIFIED | System.loadLibrary("whisper-jni") in companion init; three external funs matching native signatures |
| `androidApp/src/main/java/dev/heyduk/relay/voice/WhisperManager.kt` | High-level Kotlin API: init, transcribe, release | VERIFIED | 117 lines; initialize(), transcribe(wavFile), release(); model extraction from assets via context.assets.open; Mutex for thread safety |
| `androidApp/src/main/java/dev/heyduk/relay/voice/TtsManager.kt` | TTS lifecycle wrapper with speak/stop and utterance tracking | VERIFIED | 98 lines; speakingMessageId StateFlow; speak(messageId, text), stop(), shutdown(); stripCodeBlocks() with fenced + inline regex |
| `androidApp/src/main/java/dev/heyduk/relay/voice/AudioRecorder.kt` | AudioRecord-based WAV recorder producing 16kHz mono PCM | VERIFIED | 126 lines; SAMPLE_RATE=16000, CHANNEL_IN_MONO, ENCODING_PCM_16BIT; WAV header written as placeholder, finalized via RandomAccessFile |
| `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/VoiceRecordButton.kt` | Hold-to-record FAB with visual feedback | VERIFIED | 63 lines; detectTapGestures onPress/tryAwaitRelease pattern; animateColorAsState red when recording |
| `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/TranscriptPreview.kt` | Editable transcript with cancel/send buttons | VERIFIED | 81 lines; OutlinedTextField with remember(transcript) initial state; Close and Send IconButtons wired to onCancel/onSend |

### Key Link Verification

All key links from plan frontmatter verified:

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `androidApp/build.gradle.kts` | `CMakeLists.txt` | externalNativeBuild.cmake.path | WIRED | Line 32: `path = file("src/main/cpp/CMakeLists.txt")`; ndkVersion = "27.2.12479018"; abiFilters arm64-v8a |
| `WhisperManager.kt` | `WhisperJni.kt` | JNI method calls | WIRED | whisperJni.initContext(), whisperJni.transcribeAudio(), whisperJni.freeContext() called at lines 36, 62, 73 |
| `WhisperManager.kt` | assets/ggml-base.en.bin | context.assets.open copy to filesDir | WIRED | ensureModelFile() at line 83 uses context.assets.open(modelFileName) with copyTo |
| `MessageBubble.kt` | `ChatViewModel.kt` | onPlayTts/onStopTts callbacks | WIRED | ChatScreen.kt line 169-173 passes `{ viewModel.playTts(message.id, message.content) }` and `{ viewModel.stopTts() }` |
| `ChatViewModel.kt` | `TtsManager.kt` | speak/stop calls | WIRED | playTts() calls ttsManager.speak(); stopTts() calls ttsManager.stop() |
| `ChatScreen.kt` | `MessageBubble.kt` | ttsPlayingMessageId passed to determine play/stop state | WIRED | `isTtsPlaying = uiState.ttsPlayingMessageId == message.id` at line 168 |
| `VoiceRecordButton.kt` | `ChatViewModel.kt` | onRecordingComplete callback triggering transcription | WIRED | CommandInput passes onMicPressed/onMicReleased from ChatScreen which calls viewModel.startRecording()/stopRecording() |
| `ChatViewModel.kt` | `WhisperManager.kt` | transcribe(wavFile) call after recording stops | WIRED | stopRecording() at line 168 calls whisperManager.transcribe(wavFile) |
| `ChatViewModel.kt` | `ChatRepository.sendMessage()` | sendMessage(kuerzel, text) after user confirms | WIRED | sendTranscript() calls sendMessage(text) which calls chatRepository.sendMessage(kuerzel, text) |
| `CommandInput.kt` | `VoiceRecordButton.kt` | Mic icon button next to send button | WIRED | CommandInput imports VoiceRecordButton; renders it when onMicPressed != null (lines 80-86) |

### Data-Flow Trace (Level 4)

Tracing from UI rendering back to data source for all dynamic voice state:

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| `MessageBubble.kt` | `isTtsPlaying` | `TtsManager.speakingMessageId` StateFlow via ChatUiState | TtsManager sets _speakingMessageId.value on speak/stop/utterance completion | FLOWING |
| `ChatScreen.kt` | `uiState.transcriptPreview` | WhisperManager.transcribeAudio() result via LocalState | Native whisper.cpp inference on WAV samples | FLOWING |
| `ChatScreen.kt` | `uiState.isRecording` / `uiState.isTranscribing` | _localState MutableStateFlow updated in startRecording/stopRecording | State-driven booleans, no hollow data | FLOWING |
| `VoiceRecordButton.kt` (via CommandInput) | `isRecording` | uiState.isRecording from ChatViewModel | See row above | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED (no runnable entry points available without device and model file; native .so compilation requires Android build environment)

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| VOIC-01 | 05-01, 05-03 | User can record voice and get on-device Whisper transcription | SATISFIED | WhisperJni/WhisperManager/AudioRecorder all present and wired; CMake builds libwhisper-jni.so; transcribe() pipes WAV samples through native whisper_full() |
| VOIC-02 | 05-03 | Transcribed text is sent to active session as regular message | SATISFIED | sendTranscript() -> sendMessage() -> chatRepository.sendMessage(); same code path as typed text |
| VOIC-03 | 05-02 | User can play back Claude responses via TTS | SATISFIED (code) / PENDING (REQUIREMENTS.md checkbox) | TtsManager, speaker icon in MessageBubble, playTts/stopTts in ChatViewModel all implemented and wired; REQUIREMENTS.md traceability table shows "Pending" but the implementation is complete -- checkbox discrepancy only |

**Note on VOIC-03:** The REQUIREMENTS.md checkbox for VOIC-03 reads `[ ]` (unchecked/pending) while the traceability table below it says "Pending". However, all code for VOIC-03 is fully implemented in the codebase. The plan 05-02 summary declares `requirements-completed: [VOIC-03]`. This is a documentation tracking discrepancy -- the implementation is present. The checkbox should be updated to `[x]` once human on-device verification passes.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `VoiceRecordButton.kt` | 39 | `onClick = { /* handled by pointer input */ }` | Info | Intentional -- FloatingActionButton requires onClick, but the actual interaction is via detectTapGestures on the modifier. This is the correct pattern for hold-to-record and not a stub. |
| `WhisperManager.kt` | 103 | `if (bytes.size <= 44) return floatArrayOf()` | Info | WAV validation returns empty array for malformed/empty files -- this is correct defensive coding, not a stub. ChatViewModel handles empty transcript result with an error message. |

No blockers or warnings found. The one anti-pattern candidate in VoiceRecordButton is intentional architectural design.

### Human Verification Required

#### 1. Voice Recording and Transcription

**Test:** On a physical Android device, open any session chat screen. Hold the microphone button. Speak a sentence clearly. Release the button.
**Expected:** Button turns red while held; LinearProgressIndicator appears after release; transcript preview bar replaces the command input with the transcribed text.
**Why human:** Requires physical device microphone, arm64-v8a APK with libwhisper-jni.so, and ggml-base.en.bin model (141MB) copied from assets to filesDir on first run.

#### 2. Transcript Edit and Send Flow

**Test:** After transcription (test 1 above), optionally edit the transcript text, then tap the Send icon.
**Expected:** Transcript preview disappears, message appears in conversation history as a normal outgoing message bubble (same visual style as typed messages), and the text is sent to the Claude Code session via Telegram.
**Why human:** Requires active Telegram bot session and running Mac-side zellij-claude.

#### 3. TTS Playback on Incoming Messages

**Test:** With incoming messages visible in a chat, tap the VolumeUp speaker icon on any incoming message bubble.
**Expected:** Android TTS reads the message text aloud. The icon changes to a Stop icon while speaking. Tapping Stop halts playback immediately. After speech completes naturally, icon reverts to VolumeUp.
**Why human:** Requires device audio output; TTS engine voice quality and timing cannot be verified programmatically.

#### 4. TTS Code Block Stripping

**Test:** Find an incoming message containing a fenced code block (``` ... ```). Tap the speaker icon.
**Expected:** The code block content is not spoken verbatim; instead "code block omitted" is spoken in its place. Text surrounding the code block is spoken normally.
**Why human:** Requires listening to actual TTS audio output to confirm the regex stripping works as intended.

#### 5. RECORD_AUDIO Permission Request

**Test:** On a fresh app install (or after revoking mic permission in Settings), navigate to a chat screen and press the mic button for the first time.
**Expected:** Android system permission dialog appears asking for microphone access. After granting, recording starts immediately on the next press (no need to press again).
**Why human:** System permission dialogs require physical device interaction.

### Gaps Summary

No gaps found. All automated verification checks pass:

- All 8 artifacts exist, are substantive (no stubs), and are properly wired
- All 10 key links from plan frontmatter are verified present in code
- All 3 requirements (VOIC-01, VOIC-02, VOIC-03) have complete implementations
- Data flow traces show real data sources (native inference, Android TTS) with no hollow props or hardcoded empty values
- RECORD_AUDIO permission declared in AndroidManifest.xml
- whisper.cpp source tree and model file correctly gitignored
- Koin DI wires all voice components: WhisperManager, TtsManager, AudioRecorder as singletons; ChatViewModel factory injects all four dependencies

The phase is ready for on-device human verification (tests 1-5 above). The only open item is updating the VOIC-03 checkbox in REQUIREMENTS.md from `[ ]` to `[x]` after device testing confirms TTS works correctly.

---

_Verified: 2026-04-02T12:00:00Z_
_Verifier: Claude (gsd-verifier)_
