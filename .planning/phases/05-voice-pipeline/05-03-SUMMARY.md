---
phase: 05-voice-pipeline
plan: 03
subsystem: voice
tags: [audio-recording, whisper, transcription, compose, voice-input]

# Dependency graph
requires:
  - phase: 05-voice-pipeline
    provides: WhisperManager for on-device transcription, TtsManager and ChatViewModel with TTS
provides:
  - AudioRecorder producing 16kHz mono WAV files for whisper.cpp
  - Hold-to-record VoiceRecordButton with visual feedback
  - Editable TranscriptPreview with cancel/send flow
  - Full voice-to-text-to-send pipeline wired through ChatViewModel
  - Runtime RECORD_AUDIO permission request
affects: []

# Tech tracking
tech-stack:
  added: [android.media.AudioRecord]
  patterns: [hold-to-record gesture via detectTapGestures onPress, transcript preview replacing input bar]

key-files:
  created:
    - androidApp/src/main/java/dev/heyduk/relay/voice/AudioRecorder.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/chat/VoiceRecordButton.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/chat/TranscriptPreview.kt
  modified:
    - androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatViewModel.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatScreen.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/components/CommandInput.kt
    - androidApp/src/main/java/dev/heyduk/relay/di/AndroidModule.kt
    - androidApp/src/main/AndroidManifest.xml

key-decisions:
  - "Lazy WhisperManager initialization on first recording -- avoids loading 141MB model at app start"
  - "TranscriptPreview replaces CommandInput when transcript available -- clean state-driven UI swap"
  - "AudioRecorder writes WAV header placeholder then finalizes after recording -- avoids buffering all audio in memory"

patterns-established:
  - "Voice recording: AudioRecorder -> WhisperManager.transcribe -> TranscriptPreview -> sendMessage"
  - "Permission request: rememberLauncherForActivityResult with ContextCompat.checkSelfPermission guard"

requirements-completed: [VOIC-01, VOIC-02]

# Metrics
duration: 3min
completed: 2026-04-03
---

# Phase 05 Plan 03: Voice Recording Pipeline Summary

**Hold-to-record voice input with on-device Whisper transcription, editable transcript preview, and send-as-text flow through existing ChatRepository**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-02T22:02:42Z
- **Completed:** 2026-04-02T22:05:59Z
- **Tasks:** 2 (+ 1 auto-approved checkpoint)
- **Files modified:** 8

## Accomplishments
- AudioRecorder produces 16kHz mono 16-bit PCM WAV files native to whisper.cpp
- Hold-to-record FAB turns red while recording, triggers transcription on release
- Editable transcript preview with cancel/send replaces command input bar
- Full pipeline: hold mic -> record -> transcribe on-device -> preview/edit -> send as text message
- Runtime RECORD_AUDIO permission requested before first recording

## Task Commits

Each task was committed atomically:

1. **Task 1: AudioRecorder and voice recording UI components** - `f09e537` (feat)
2. **Task 2: Wire voice recording into ChatScreen and ChatViewModel** - `3875029` (feat)

## Files Created/Modified
- `androidApp/src/main/java/dev/heyduk/relay/voice/AudioRecorder.kt` - WAV recorder using AudioRecord API with proper header writing
- `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/VoiceRecordButton.kt` - Hold-to-record FAB with animated color state
- `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/TranscriptPreview.kt` - Editable transcript bar with cancel/send
- `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatViewModel.kt` - Added voice state, recording/transcription methods
- `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatScreen.kt` - Permission launcher, transcript preview in bottomBar
- `androidApp/src/main/java/dev/heyduk/relay/presentation/components/CommandInput.kt` - Added mic button parameters
- `androidApp/src/main/java/dev/heyduk/relay/di/AndroidModule.kt` - AudioRecorder singleton, updated ChatViewModel factory
- `androidApp/src/main/AndroidManifest.xml` - RECORD_AUDIO permission

## Decisions Made
- Lazy WhisperManager initialization on first recording to avoid loading 141MB model at app start
- TranscriptPreview replaces CommandInput via state-driven conditional rendering
- WAV header written as placeholder then finalized via RandomAccessFile seek to avoid memory buffering

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Voice pipeline complete: recording, transcription, preview, and send all wired
- Phase 05 (voice-pipeline) is fully implemented with all 3 plans complete
- Ready for end-to-end device testing

---
*Phase: 05-voice-pipeline*
*Completed: 2026-04-03*
