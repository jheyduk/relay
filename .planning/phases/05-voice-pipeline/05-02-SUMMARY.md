---
phase: 05-voice-pipeline
plan: 02
subsystem: voice
tags: [android-tts, text-to-speech, compose, stateflow]

# Dependency graph
requires:
  - phase: 03-messaging
    provides: ChatMessage model, ChatViewModel, MessageBubble composable
provides:
  - TtsManager wrapping Android TextToSpeech with StateFlow tracking
  - Play/stop TTS icon on incoming message bubbles
  - Code block stripping for cleaner TTS output
affects: [05-voice-pipeline]

# Tech tracking
tech-stack:
  added: [android.speech.tts.TextToSpeech]
  patterns: [TtsManager singleton via Koin, speakingMessageId StateFlow combined into ChatUiState]

key-files:
  created:
    - androidApp/src/main/java/dev/heyduk/relay/voice/TtsManager.kt
  modified:
    - androidApp/src/main/java/dev/heyduk/relay/di/AndroidModule.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatViewModel.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/chat/MessageBubble.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatScreen.kt

key-decisions:
  - "TtsManager as Koin singleton -- single TTS engine instance shared across screens"
  - "speakingMessageId as StateFlow combined into ChatUiState for reactive UI updates"
  - "Code blocks stripped with regex before speaking, inline code also removed"

patterns-established:
  - "Voice module lives under dev.heyduk.relay.voice package"
  - "TtsManager exposes StateFlow for speaking state, combined into ViewModel UiState"

requirements-completed: [VOIC-03]

# Metrics
duration: 2min
completed: 2026-04-02
---

# Phase 05 Plan 02: TTS Playback Summary

**Android TTS integration with play/stop icon on incoming message bubbles and code block stripping**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-02T23:14:00Z
- **Completed:** 2026-04-02T23:16:00Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- TtsManager wraps Android TextToSpeech with lifecycle management and StateFlow-based speaking state
- Incoming message bubbles show a speaker icon that toggles between play (VolumeUp) and stop
- Code blocks (fenced and inline) are stripped before speaking for cleaner TTS output
- TTS state flows reactively from TtsManager through ChatUiState to MessageBubble

## Task Commits

Each task was committed atomically:

1. **Task 1: TtsManager and Koin registration** - `b07070d` (feat)
2. **Task 2: TTS play/stop in MessageBubble and ChatViewModel** - `d50e6b3` (feat)

## Files Created/Modified
- `androidApp/src/main/java/dev/heyduk/relay/voice/TtsManager.kt` - TTS lifecycle wrapper with speak/stop, utterance tracking, code block stripping
- `androidApp/src/main/java/dev/heyduk/relay/di/AndroidModule.kt` - Added TtsManager Koin singleton and updated ChatViewModel factory
- `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatViewModel.kt` - Added TtsManager dependency, ttsPlayingMessageId in UiState, playTts/stopTts methods
- `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/MessageBubble.kt` - Added TTS play/stop IconButton for incoming messages
- `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatScreen.kt` - Passes TTS callbacks and state to MessageBubble

## Decisions Made
- TtsManager registered as Koin singleton -- single TTS engine instance shared across the app
- speakingMessageId as StateFlow combined into ChatUiState via 4-way combine for reactive UI
- Fenced code blocks replaced with "code block omitted", inline code silently removed
- Locale.US as default TTS language

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- TTS playback complete, ready for Phase 05 Plan 03 (Whisper integration)
- TtsManager.shutdown() available for app lifecycle cleanup

---
*Phase: 05-voice-pipeline*
*Completed: 2026-04-02*
