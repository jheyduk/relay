---
status: partial
phase: 05-voice-pipeline
source: [05-VERIFICATION.md]
started: 2026-04-03T00:30:00Z
updated: 2026-04-03T00:30:00Z
---

## Current Test

[awaiting human testing — blocked on zellij-claude Relay bot integration]

## Tests

### 1. Voice recording + Whisper transcription
expected: Hold mic, speak, release. Red button while recording, progress indicator during transcription, transcript preview appears.
result: [pending — requires device + 141MB model + zellij-claude integration]

### 2. Transcript send flow
expected: After transcription, tap send. Message appears in conversation history as text, delivered to Telegram session.
result: [pending]

### 3. TTS playback
expected: Tap speaker icon on incoming message. Audio plays, icon toggles to Stop, Stop halts playback.
result: [pending — requires messages from zellij-claude via Relay bot]

### 4. TTS code block stripping
expected: On message with fenced code blocks, tap speaker. "code block omitted" spoken instead of raw code.
result: [pending]

### 5. RECORD_AUDIO permission
expected: Fresh install: system permission dialog on first mic press, recording starts after grant.
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps

Blocked on: zellij-claude Telegram MCP plugin does not mirror reply messages to Relay bot.
The hook-based notifications (permissions, ask) are mirrored, but command responses (/ls, /last) are not.
