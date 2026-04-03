---
phase: 09-mac-side-voice
plan: 01
subsystem: voice
tags: [whisper, whisper-cli, websocket, binary-frames, transcription, node]

requires:
  - phase: 07-server-migration
    provides: relay-server.cjs in relay repo with WebSocket + IPC
provides:
  - Binary WebSocket frame handler for audio data (kuerzel+WAV protocol)
  - whisper-cli transcription integration with configurable model/CLI paths
  - JSON transcript response messages ({type: transcript, session, text})
affects: [09-02-mac-side-voice, app-voice-ui]

tech-stack:
  added: [whisper-cli]
  patterns: [binary-frame-protocol, async-transcription-with-cleanup]

key-files:
  created: []
  modified: [server/relay-server.cjs]

key-decisions:
  - "30s timeout for whisper-cli execFile to handle longer audio clips"
  - "Non-fatal whisper availability check at startup — server still works for text if whisper missing"
  - "Binary protocol: 2-byte big-endian kuerzel length prefix + kuerzel UTF-8 + WAV data"

patterns-established:
  - "Binary frame detection before JSON parse in WebSocket message handler"
  - "Temp file lifecycle: write -> process -> cleanup in finally block"

requirements-completed: [VOIC-10, VOIC-11]

duration: 2min
completed: 2026-04-03
---

# Phase 09 Plan 01: Audio Receive and Whisper Transcription Summary

**Binary WebSocket audio receive with whisper-cli transcription on Mac — receives WAV from app, transcribes locally with Metal acceleration, returns JSON transcript**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-03T17:12:03Z
- **Completed:** 2026-04-03T17:14:00Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Binary WebSocket frame handler with kuerzel+WAV protocol parsing
- whisper-cli integration with configurable paths in server.json
- Graceful degradation when whisper-cli or model not available
- Temp file cleanup in finally block for reliability

## Task Commits

Each task was committed atomically:

1. **Task 1: Add whisper config and audio transcription handler** - `f4936c9` (feat)

## Files Created/Modified
- `server/relay-server.cjs` - Added whisper config, binary frame handler, transcribeAudio function, startup availability check

## Decisions Made
- 30-second timeout for whisper-cli execFile to handle longer audio clips without hanging
- Non-fatal startup check for whisper availability — server continues working for text commands if whisper is missing
- Binary protocol uses 2-byte big-endian uint16 for kuerzel length prefix, matching the app-side implementation

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - whisper-cli and model must already be installed at configured paths (defaults: /opt/homebrew/bin/whisper-cli and ~/.cache/whisper/ggml-base.bin).

## Next Phase Readiness
- Server ready to receive audio from app and return transcripts
- Plan 09-02 can implement app-side audio recording and binary frame sending

---
*Phase: 09-mac-side-voice*
*Completed: 2026-04-03*
