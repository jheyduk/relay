---
phase: 09-mac-side-voice
plan: 02
subsystem: voice
tags: [websocket, binary-frames, whisper, audio, transcription]

requires:
  - phase: 09-mac-side-voice-01
    provides: Mac-side whisper server endpoint that accepts binary audio frames
provides:
  - sendAudio binary frame method on WebSocketClient (kuerzel+WAV protocol)
  - TRANSCRIPT message type in relay protocol
  - Server-side transcription flow (record -> send -> receive transcript)
  - Removed 141 MB on-device Whisper model and native JNI code
affects: [voice-pipeline, apk-size]

tech-stack:
  added: []
  patterns:
    - "Binary WebSocket frame protocol: uint16 kuerzel length + kuerzel UTF-8 + WAV data"
    - "Server-side transcription: audio sent to Mac, transcript returned as JSON"
    - "Transcript messages skip DB persistence, flow directly via SharedFlow to ViewModel"

key-files:
  created: []
  modified:
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/WebSocketClient.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/RelayMessageParser.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/RelayMessage.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/RelayRepository.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/RelayRepositoryImpl.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepository.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepositoryImpl.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayMessageType.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatViewModel.kt
    - androidApp/src/main/java/dev/heyduk/relay/di/AndroidModule.kt
    - androidApp/build.gradle.kts
    - androidApp/src/main/java/dev/heyduk/relay/service/WebSocketService.kt

key-decisions:
  - "Transcript messages skip DB persistence to avoid polluting chat history -- they flow directly to ViewModel via SharedFlow"
  - "Binary frame protocol: 2-byte big-endian kuerzel length prefix + kuerzel UTF-8 + WAV payload"
  - "RelayMessage DTO message field made optional (default empty) to support transcript JSON format"

patterns-established:
  - "Binary WebSocket frames for non-text data (audio) with session-prefixed protocol"
  - "SharedFlow filtering for transient messages that bypass DB persistence"

requirements-completed: [VOIC-12]

duration: 4min
completed: 2026-04-03
---

# Phase 09 Plan 02: Server Transcription Client Summary

**Rewired app to send WAV audio to Mac server via binary WebSocket frames and receive transcript back, removing 141 MB on-device Whisper model and all native JNI code**

## Performance

- **Duration:** 4 min
- **Started:** 2026-04-03T17:12:10Z
- **Completed:** 2026-04-03T17:16:37Z
- **Tasks:** 2
- **Files modified:** 12 (plus 4 deleted)

## Accomplishments
- WebSocketClient.sendAudio() builds binary frames with kuerzel+WAV protocol matching server Plan 01
- RelayMessageParser handles incoming transcript JSON messages via new TRANSCRIPT type
- ChatViewModel sends recorded audio to server instead of transcribing locally
- Deleted WhisperManager.kt, WhisperJni.kt, whisper-jni.cpp, CMakeLists.txt -- APK no longer contains native code or 141 MB model
- Removed ndkVersion, ndk abiFilters, externalNativeBuild from build.gradle.kts

## Task Commits

Each task was committed atomically:

1. **Task 1: Add sendAudio to WebSocket client and repository, handle transcript messages** - `f6ff6d6` (feat)
2. **Task 2: Rewire ChatViewModel to use server transcription, remove on-device Whisper** - `397b659` (feat)

## Files Created/Modified
- `shared/.../WebSocketClient.kt` - Added sendAudio() binary frame method
- `shared/.../RelayMessageParser.kt` - Handle transcript type, prefer text field
- `shared/.../dto/RelayMessage.kt` - Added TRANSCRIPT enum, optional text field
- `shared/.../RelayRepository.kt` - Added sendAudio interface method
- `shared/.../RelayRepositoryImpl.kt` - Implemented sendAudio delegation
- `shared/.../ChatRepository.kt` - Added sendAudio and transcripts flow
- `shared/.../ChatRepositoryImpl.kt` - Implemented sendAudio and transcript filtering
- `shared/.../RelayMessageType.kt` - Added TRANSCRIPT enum value
- `androidApp/.../ChatViewModel.kt` - Removed WhisperManager, wired server transcription
- `androidApp/.../AndroidModule.kt` - Removed WhisperManager DI registration
- `androidApp/build.gradle.kts` - Removed cmake/NDK/native build config
- `androidApp/.../WebSocketService.kt` - Skip DB persistence for transcript messages

### Deleted Files
- `androidApp/.../voice/WhisperManager.kt` - On-device Whisper orchestration
- `androidApp/.../voice/WhisperJni.kt` - JNI bridge for whisper.cpp
- `androidApp/src/main/cpp/whisper-jni.cpp` - Native JNI implementation
- `androidApp/src/main/cpp/CMakeLists.txt` - CMake build config for native code

## Decisions Made
- Transcript messages skip DB persistence -- they are transient and flow directly to the ViewModel via SharedFlow. This avoids polluting chat history with intermediate transcription results.
- Made RelayMessage.message field default to empty string so transcript JSON (which uses "text" not "message") parses correctly.
- Added transcripts Flow to ChatRepository rather than injecting RelayRepository into ChatViewModel -- keeps the existing dependency pattern.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added transcript flow to ChatRepository**
- **Found during:** Task 2 (ChatViewModel rewire)
- **Issue:** Plan didn't specify how transcript messages would reach ViewModel's transcriptPreview state. ChatViewModel only has ChatRepository, not RelayRepository.
- **Fix:** Added `transcripts` Flow to ChatRepository interface and implementation, filtering updates for TRANSCRIPT type. ViewModel observes this flow in init block.
- **Files modified:** ChatRepository.kt, ChatRepositoryImpl.kt, ChatViewModel.kt
- **Committed in:** 397b659

**2. [Rule 2 - Missing Critical] Skip transcript DB persistence in WebSocketService**
- **Found during:** Task 2 (ChatViewModel rewire)
- **Issue:** WebSocketService inserts all updates into DB. Transcript messages should not be persisted as chat messages -- they are transient preview data.
- **Fix:** Added early return in WebSocketService.updates collector for TRANSCRIPT type messages.
- **Files modified:** WebSocketService.kt
- **Committed in:** 397b659

**3. [Rule 3 - Blocking] Made RelayMessage.message optional with default**
- **Found during:** Task 1 (RelayMessageParser)
- **Issue:** Server transcript JSON uses "text" field, not "message". RelayMessage DTO had message as required non-null, causing parse failure for transcript messages.
- **Fix:** Made message default to empty string, added optional text field, parser prefers text for transcript type.
- **Files modified:** RelayMessage.kt, RelayMessageParser.kt
- **Committed in:** f6ff6d6

---

**Total deviations:** 3 auto-fixed (2 missing critical, 1 blocking)
**Impact on plan:** All auto-fixes necessary for correctness. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Server-side transcription client fully wired -- requires Plan 01's server endpoint to be running
- AudioRecorder and TtsManager unchanged -- voice recording UX preserved
- APK size significantly reduced without the 141 MB model and native code

---
*Phase: 09-mac-side-voice*
*Completed: 2026-04-03*
