---
phase: 05-voice-pipeline
plan: 01
subsystem: voice
tags: [whisper.cpp, jni, ndk, cmake, native, speech-to-text]

requires:
  - phase: 01-telegram-foundation
    provides: Koin DI module pattern and build infrastructure
provides:
  - whisper.cpp native library compiled via CMake for arm64-v8a
  - JNI bridge with initContext/transcribeAudio/freeContext
  - WhisperManager Kotlin API with model file management
  - Koin singleton registration for WhisperManager
affects: [05-voice-pipeline]

tech-stack:
  added: [whisper.cpp 1.8.3, NDK 27.2.12479018, CMake 3.22.1]
  patterns: [JNI bridge pattern, native CMake build via FetchContent, asset-to-filesDir model extraction]

key-files:
  created:
    - androidApp/src/main/cpp/CMakeLists.txt
    - androidApp/src/main/cpp/whisper-jni.cpp
    - androidApp/src/main/java/dev/heyduk/relay/voice/WhisperJni.kt
    - androidApp/src/main/java/dev/heyduk/relay/voice/WhisperManager.kt
  modified:
    - androidApp/build.gradle.kts
    - androidApp/src/main/java/dev/heyduk/relay/di/AndroidModule.kt

key-decisions:
  - "FetchContent for ggml build from whisper.cpp source tree (official whisper.android approach)"
  - "arm64-v8a only ABI filter -- single architecture for initial build, x86_64 emulator support deferred"
  - "Float array JNI interface instead of file path -- more flexible for streaming audio"
  - "WHISPER_VERSION macro added manually since we build whisper.cpp outside its own CMake project"

patterns-established:
  - "JNI bridge: C++ extern-C functions map to Kotlin external funs in companion-loaded class"
  - "Model management: assets -> filesDir copy on first use with existence check"
  - "WAV parsing: skip 44-byte header, normalize 16-bit PCM to [-1,1] float"

requirements-completed: [VOIC-01]

duration: 5min
completed: 2026-04-03
---

# Phase 5 Plan 1: Whisper Native Build Summary

**whisper.cpp v1.8.3 compiled as arm64-v8a native library via CMake with JNI bridge and WhisperManager Kotlin API registered in Koin**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-02T21:55:06Z
- **Completed:** 2026-04-03T00:00:30Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- whisper.cpp compiles as shared native library (libwhisper-jni.so, 2.2MB) in ARM64 APK
- JNI bridge exposes initContext, transcribeAudio, freeContext with proper memory management
- WhisperManager provides thread-safe suspend API with model extraction from assets
- ggml-base.en.bin model (141MB) downloaded and gitignored

## Task Commits

Each task was committed atomically:

1. **Task 1: whisper.cpp native build and JNI bridge** - `5b51124` (feat)
2. **Task 2: WhisperManager and model file handling** - `efbce71` (feat)

## Files Created/Modified
- `androidApp/src/main/cpp/CMakeLists.txt` - CMake build with FetchContent ggml, whisper.cpp sources, ARM NEON optimization
- `androidApp/src/main/cpp/whisper-jni.cpp` - C++ JNI bridge with init/transcribe/free functions
- `androidApp/src/main/java/dev/heyduk/relay/voice/WhisperJni.kt` - Kotlin JNI declarations
- `androidApp/src/main/java/dev/heyduk/relay/voice/WhisperManager.kt` - High-level Kotlin API with model management
- `androidApp/build.gradle.kts` - Added NDK version, ABI filter, externalNativeBuild cmake config
- `androidApp/src/main/java/dev/heyduk/relay/di/AndroidModule.kt` - Added WhisperManager Koin singleton

## Decisions Made
- FetchContent for ggml instead of manually globbing source files (matches official whisper.android example)
- arm64-v8a only -- no emulator support yet, keeps build fast
- Float array JNI interface for flexibility (caller converts WAV to samples)
- Added WHISPER_VERSION="1.8.3" compile definition since we build whisper.cpp outside its root CMake project

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] NDK not properly installed**
- **Found during:** Task 1 (build verification)
- **Issue:** NDK 27.2.12479018 directory existed but was empty stub (no source.properties)
- **Fix:** Re-installed NDK via sdkmanager
- **Files modified:** None (SDK toolchain)
- **Verification:** Build succeeded after reinstall
- **Committed in:** 5b51124 (Task 1 commit)

**2. [Rule 1 - Bug] Missing WHISPER_VERSION macro**
- **Found during:** Task 1 (build verification)
- **Issue:** whisper.cpp source uses WHISPER_VERSION macro defined in its root CMakeLists.txt, not available in our standalone build
- **Fix:** Added `WHISPER_VERSION="1.8.3"` to target_compile_definitions
- **Files modified:** androidApp/src/main/cpp/CMakeLists.txt
- **Verification:** Build compiles successfully
- **Committed in:** 5b51124 (Task 1 commit)

**3. [Rule 3 - Blocking] CMake not installed in Android SDK**
- **Found during:** Task 1 (build setup)
- **Issue:** CMake 3.22.1 not present in SDK
- **Fix:** Installed via sdkmanager "cmake;3.22.1"
- **Files modified:** None (SDK toolchain)
- **Verification:** Build succeeded
- **Committed in:** 5b51124 (Task 1 commit)

---

**Total deviations:** 3 auto-fixed (1 bug, 2 blocking)
**Impact on plan:** All fixes necessary for compilation. No scope creep.

## Issues Encountered
None beyond the auto-fixed deviations above.

## User Setup Required
- **Whisper model file:** Download `ggml-base.en.bin` to `androidApp/src/main/assets/` (141MB, not in git). Already done for this build.
- **NDK 27.2.12479018:** Must be installed via SDK Manager
- **CMake 3.22.1:** Must be installed via SDK Manager

## Next Phase Readiness
- WhisperManager.transcribe(wavFile) is ready for audio recording integration (Plan 05-02)
- Model loads from assets on first use, no additional setup needed at runtime
- Thread-safe via Mutex, safe for concurrent ViewModel access

---
*Phase: 05-voice-pipeline*
*Completed: 2026-04-03*
