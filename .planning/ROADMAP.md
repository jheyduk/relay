# Roadmap: Relay

## Overview

Relay goes from zero to a fully functional Android companion for Claude Code sessions in five phases. The journey follows the architecture's natural dependency chain: establish reliable Telegram transport first (solving the critical 409 polling conflict), then build the session-aware UI, add text messaging with persistent history, layer in the permission and notification system, and finally integrate the on-device voice pipeline. Each phase delivers a testable, usable capability that builds on the previous one.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: Transport & Foundation** - Establish reliable Telegram Bot API polling with 409 conflict resolution and project scaffolding
- [ ] **Phase 2: Session Discovery & Status** - Display all active sessions with live status and session commands
- [ ] **Phase 3: Messaging & Conversations** - Send and receive text messages per session with persistent history
- [ ] **Phase 4: Permissions & Notifications** - Native Allow/Deny permission handling with prioritized push notifications
- [ ] **Phase 5: Voice Pipeline** - On-device Whisper transcription and TTS playback for hands-free interaction

## Phase Details

### Phase 1: Transport & Foundation
**Goal**: The app can connect to the Telegram Bot API, receive messages reliably, and survive network transitions -- with the 409 single-consumer conflict fully resolved
**Depends on**: Nothing (first phase)
**Requirements**: TRNS-01, TRNS-02, TRNS-03, TRNS-04, TRNS-05
**Success Criteria** (what must be TRUE):
  1. App connects to Telegram Bot API and receives updates via long polling without triggering 409 conflict with the Mac-side poller
  2. App can send a message in zellij-claude format (`@kuerzel message`) and see it arrive on the Mac side
  3. App correctly parses incoming bot messages (distinguishes status updates, responses, and permission requests)
  4. App maintains polling connection when switching between WiFi and mobile data
  5. Foreground service keeps polling alive when the app is backgrounded or the screen is off
**Plans**: TBD

Plans:
- [ ] 01-01: TBD
- [ ] 01-02: TBD
- [ ] 01-03: TBD

### Phase 2: Session Discovery & Status
**Goal**: Users can see all active zellij-claude sessions at a glance with live status indicators and execute session management commands
**Depends on**: Phase 1
**Requirements**: SESS-01, SESS-02, SESS-03, SESS-04, SESS-05, UI-01
**Success Criteria** (what must be TRUE):
  1. User can see a list of all active sessions with their kuerzel and current status (working, waiting, ready, shell) visually distinguished
  2. User can pull-to-refresh or tap a button to execute `/ls` and see the session list update
  3. User can execute `/last @kuerzel` to view the last response from a specific session
  4. User can execute `/open`, `/goto`, `/rename` commands for session management
  5. App renders in dark mode via Material 3 dynamic theming
**Plans**: TBD
**UI hint**: yes

Plans:
- [ ] 02-01: TBD
- [ ] 02-02: TBD
- [ ] 02-03: TBD

### Phase 3: Messaging & Conversations
**Goal**: Users can have text conversations with individual sessions and review history across app restarts
**Depends on**: Phase 2
**Requirements**: MSG-01, MSG-02, MSG-05
**Success Criteria** (what must be TRUE):
  1. User can select a session and send a text message that arrives at the correct zellij-claude session on the Mac
  2. User can view the full conversation history for any session, with messages displayed in chronological order
  3. Conversation history persists across app restarts (stored in Room database)
**Plans**: TBD
**UI hint**: yes

Plans:
- [ ] 03-01: TBD
- [ ] 03-02: TBD
- [ ] 03-03: TBD

### Phase 4: Permissions & Notifications
**Goal**: Users never miss a permission request or session completion -- native Allow/Deny buttons replace Telegram inline keyboards, with push notifications surfacing time-critical events
**Depends on**: Phase 3
**Requirements**: MSG-03, MSG-04, MSG-06, NOTF-01, NOTF-02, NOTF-03, NOTF-04
**Success Criteria** (what must be TRUE):
  1. User sees native Allow/Deny buttons on permission request messages, showing tool details (command, file path)
  2. Tapping Allow or Deny sends the correct callback to Telegram and the permission is processed on the Mac side
  3. User can answer AskUserQuestion prompts by tapping native option buttons (multiple choice)
  4. User receives a high-priority push notification when any session requests permission, even with the app backgrounded
  5. User receives a normal-priority notification when a session completes its task
  6. Tapping a notification opens the app directly to the relevant session
**Plans**: TBD
**UI hint**: yes

Plans:
- [ ] 04-01: TBD
- [ ] 04-02: TBD
- [ ] 04-03: TBD

### Phase 5: Voice Pipeline
**Goal**: Users can speak to sessions and hear responses -- on-device Whisper transcription and Android TTS enable hands-free interaction
**Depends on**: Phase 3
**Requirements**: VOIC-01, VOIC-02, VOIC-03
**Success Criteria** (what must be TRUE):
  1. User can hold a button to record voice, and the recording is transcribed on-device by Whisper without network access
  2. Transcribed text is automatically sent to the active session as a regular message (appears in conversation history as text)
  3. User can tap a play button on any Claude response to hear it read aloud via TTS
**Plans**: TBD

Plans:
- [ ] 05-01: TBD
- [ ] 05-02: TBD
- [ ] 05-03: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 -> 2 -> 3 -> 4 -> 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Transport & Foundation | 0/3 | Not started | - |
| 2. Session Discovery & Status | 0/3 | Not started | - |
| 3. Messaging & Conversations | 0/3 | Not started | - |
| 4. Permissions & Notifications | 0/3 | Not started | - |
| 5. Voice Pipeline | 0/3 | Not started | - |
