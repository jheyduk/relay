# Requirements: Relay v1.1 — Standalone Server

**Defined:** 2026-04-03
**Core Value:** Remote session control with per-session separation — see all Claude Code sessions at a glance, interact with any of them, and never miss a permission request or completion notification.

## v1.1 Requirements

### Server Independence

- [ ] **SERV-01**: relay-server.cjs lives in `server/` in the relay repo with its own package.json
- [ ] **SERV-02**: Claude Code hooks live in `server/hooks/` and are generic (not zellij-claude-specific)
- [ ] **SERV-03**: Server dispatches commands directly via `zellij action write-chars` (no npx zellij-claude dependency)
- [x] **SERV-04**: Server sends all active sessions to newly connected clients (reconnect sync)
- [x] **SERV-05**: zellij-claude hooks are removed/updated after migration

### Interactive Controls

- [x] **CTRL-01**: App can answer AskUserQuestion single-choice (number + Enter as keystrokes)
- [x] **CTRL-02**: App can answer AskUserQuestion multiple-choice (toggle numbers, Down to Submit, Enter)
- [x] **CTRL-03**: App can answer AskUserQuestion free-text (Down to Other, Enter, type text, Enter)

### Voice

- [ ] **VOIC-10**: Server receives audio data over WebSocket from the app
- [ ] **VOIC-11**: Server transcribes audio locally via whisper.cpp and sends transcript back
- [ ] **VOIC-12**: App sends audio instead of transcribing locally (on-device Whisper removed to reduce APK size)

## Future Requirements

Deferred to later milestones.

- **iOS UI** — SwiftUI frontend using KMP shared module
- **FCM Push** — Firebase push when app is backgrounded and WebSocket disconnected

## Out of Scope

| Feature | Reason |
|---------|--------|
| Cloud transcription | Local-only voice (on-device or Mac) |
| Multi-user support | Single developer tool |
| Web UI | Native mobile focus |
| Custom relay protocol | Keep JSON message format from v1.0 |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SERV-01 | Phase 7 | Pending |
| SERV-02 | Phase 7 | Pending |
| SERV-03 | Phase 7 | Pending |
| SERV-04 | Phase 8 | Complete |
| SERV-05 | Phase 7 | Complete |
| CTRL-01 | Phase 8 | Complete |
| CTRL-02 | Phase 8 | Complete |
| CTRL-03 | Phase 8 | Complete |
| VOIC-10 | Phase 9 | Pending |
| VOIC-11 | Phase 9 | Pending |
| VOIC-12 | Phase 9 | Pending |

**Coverage:**
- v1.1 requirements: 11 total
- Mapped to phases: 11
- Unmapped: 0

---
*Requirements defined: 2026-04-03*
*Traceability updated: 2026-04-03*
