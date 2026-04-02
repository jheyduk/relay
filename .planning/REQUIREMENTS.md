# Requirements: Relay

**Defined:** 2026-04-02
**Core Value:** Remote session control with per-session separation -- see all Claude Code sessions at a glance, interact with any of them, and never miss a permission request or completion notification.

## v1 Requirements

Requirements for initial release. Each maps to roadmap phases.

### Transport

- [ ] **TRNS-01**: App connects to Telegram Bot API via long polling (getUpdates)
- [ ] **TRNS-02**: App resolves 409 conflict with existing Mac-side bot polling
- [ ] **TRNS-03**: App sends messages in zellij-claude format (`@kuerzel message`)
- [ ] **TRNS-04**: App receives and parses incoming bot messages (status, responses, permission requests)
- [ ] **TRNS-05**: App maintains connection through network transitions (WiFi/mobile)

### Sessions

- [ ] **SESS-01**: User can view list of all active zellij-claude sessions with status
- [ ] **SESS-02**: User can see session status (working, waiting, ready, shell) with visual indicator
- [ ] **SESS-03**: User can execute `/ls` to refresh session list
- [ ] **SESS-04**: User can execute `/last @kuerzel` to see last response
- [ ] **SESS-05**: User can execute `/open`, `/goto`, `/rename` session commands

### Messaging

- [ ] **MSG-01**: User can send text messages to a specific session
- [ ] **MSG-02**: User can view per-session conversation history
- [ ] **MSG-03**: User can tap Allow or Deny on permission requests with native buttons
- [ ] **MSG-04**: Permission requests show tool details (command, file path)
- [ ] **MSG-05**: Conversation history persists across app restarts (Room DB)
- [ ] **MSG-06**: User can answer AskUserQuestion prompts with native option buttons (multiple choice)

### Voice

- [ ] **VOIC-01**: User can record voice and get on-device Whisper transcription
- [ ] **VOIC-02**: Transcribed text is sent to the active session as a regular message
- [ ] **VOIC-03**: User can play back Claude responses via TTS

### Notifications

- [ ] **NOTF-01**: User receives push notification when a session needs permission
- [ ] **NOTF-02**: User receives push notification when a session completes
- [ ] **NOTF-03**: Permission notifications use high-priority channel
- [ ] **NOTF-04**: Completion/info notifications use normal-priority channel

### UI

- [ ] **UI-01**: App supports dark mode via Material 3 dynamic theming

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Session Management

- **SESS-06**: Multi-session dashboard with color-coded grid/card view
- **SESS-07**: Session-aware routing (auto-prefix messages with active session kuerzel)

### Voice

- **VOIC-04**: Voice-to-session pipeline (speak to a specific session by name)
- **VOIC-05**: Unified chat stream (text + voice in one timeline with transcript + playback)

### Notifications

- **NOTF-05**: FCM push for real-time notifications when app is backgrounded (replaces polling)

### UI

- **UI-02**: Configurable notification sounds per session
- **UI-03**: Message search across sessions

## Out of Scope

Explicitly excluded. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Code editor / file browser | Relay is a control surface, not an IDE |
| Terminal emulator | Structured messaging is the interaction model, not raw terminal |
| Custom relay server | Telegram Bot API handles routing, persistence, delivery |
| Multi-user / team features | Single developer tool, bot token is the auth |
| iOS version | Android only for v1, Kotlin/Compose |
| Git operations | Git happens through Claude Code on the Mac |
| Cloud-based voice | On-device Whisper is the differentiator |
| Kanban / project management | Relay is a control surface, not a project manager |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| TRNS-01 | Phase 1 | Pending |
| TRNS-02 | Phase 1 | Pending |
| TRNS-03 | Phase 1 | Pending |
| TRNS-04 | Phase 1 | Pending |
| TRNS-05 | Phase 1 | Pending |
| SESS-01 | Phase 2 | Pending |
| SESS-02 | Phase 2 | Pending |
| SESS-03 | Phase 2 | Pending |
| SESS-04 | Phase 2 | Pending |
| SESS-05 | Phase 2 | Pending |
| MSG-01 | Phase 3 | Pending |
| MSG-02 | Phase 3 | Pending |
| MSG-03 | Phase 4 | Pending |
| MSG-04 | Phase 4 | Pending |
| MSG-05 | Phase 3 | Pending |
| VOIC-01 | Phase 5 | Pending |
| VOIC-02 | Phase 5 | Pending |
| VOIC-03 | Phase 5 | Pending |
| NOTF-01 | Phase 4 | Pending |
| NOTF-02 | Phase 4 | Pending |
| NOTF-03 | Phase 4 | Pending |
| NOTF-04 | Phase 4 | Pending |
| UI-01 | Phase 2 | Pending |
| MSG-06 | Phase 4 | Pending |

**Coverage:**
- v1 requirements: 24 total
- Mapped to phases: 24
- Unmapped: 0

---
*Requirements defined: 2026-04-02*
*Last updated: 2026-04-02 after roadmap creation*
