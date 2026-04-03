# Phase 9: Mac-Side Voice - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Voice transcription happens on the Mac instead of the phone. App streams audio to server via WebSocket, server transcribes via whisper.cpp CLI, sends transcript back. On-device Whisper removed from APK (saves 141 MB).

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
- App sends PCM/WAV audio as WebSocket binary frame
- Server writes temp file, runs whisper.cpp CLI, returns transcript as JSON
- App removes WhisperManager, JNI bridge, CMake build, model asset
- whisper.cpp must be available on the Mac

</decisions>

<code_context>
## Existing Code Insights

Key files to modify:
- server/relay-server.cjs — add audio receive + transcription handler
- androidApp voice/ directory — AudioRecorder stays, WhisperManager removed
- androidApp/src/main/cpp/ — entire whisper.cpp native build removed
- androidApp/src/main/assets/ggml-base.en.bin — 141 MB model removed

</code_context>

<specifics>
## Specific Ideas

None — infrastructure phase.

</specifics>

<deferred>
## Deferred Ideas

None.

</deferred>
