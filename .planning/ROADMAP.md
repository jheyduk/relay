# Roadmap: Relay

## Milestones

- ✅ **v1.0 MVP** - Phases 1-6 (shipped 2026-04-03)
- 🚧 **v1.1 Standalone Server** - Phases 7-9 (in progress)

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

<details>
<summary>✅ v1.0 MVP (Phases 1-6) - SHIPPED 2026-04-03</summary>

### Phase 1: Transport & Foundation
**Goal**: The app can connect to the Telegram Bot API, receive messages reliably, and survive network transitions -- with the 409 single-consumer conflict fully resolved
**Requirements**: TRNS-01, TRNS-02, TRNS-03, TRNS-04, TRNS-05
**Plans**: 3 plans

Plans:
- [x] 01-01-PLAN.md -- KMP project scaffolding, domain models, Telegram DTOs, SQLDelight schema
- [x] 01-02-PLAN.md -- Telegram API client, polling loop with backoff, message parser, repository
- [x] 01-03-PLAN.md -- Android foreground service, network monitor, setup UI, status UI, E2E verification

### Phase 2: Session Discovery & Status
**Goal**: Users can see all active zellij-claude sessions at a glance with live status indicators and execute session management commands
**Requirements**: SESS-01, SESS-02, SESS-03, SESS-04, SESS-05, UI-01
**Plans**: 3 plans

Plans:
- [x] 02-01-PLAN.md -- Session domain model, /ls parser, command router, SessionRepository (shared module)
- [x] 02-02-PLAN.md -- RelayTheme, session cards, status chips, session list screen, command input (UI layer)
- [x] 02-03-PLAN.md -- Koin DI wiring, navigation update, MainActivity theming, visual verification

### Phase 3: Messaging & Conversations
**Goal**: Users can have text conversations with individual sessions and review history across app restarts
**Requirements**: MSG-01, MSG-02, MSG-05
**Plans**: 2 plans

Plans:
- [x] 03-01-PLAN.md -- ChatMessage model, Messages.sq outgoing persistence, ChatRepository with send+persist
- [x] 03-02-PLAN.md -- ChatScreen with message bubbles, ChatViewModel, navigation wiring, visual verification

### Phase 4: Permissions & Notifications
**Goal**: Users never miss a permission request or session completion -- native Allow/Deny buttons replace Telegram inline keyboards, with push notifications surfacing time-critical events
**Requirements**: MSG-03, MSG-04, MSG-06, NOTF-01, NOTF-02, NOTF-03, NOTF-04
**Plans**: 3 plans

Plans:
- [x] 04-01-PLAN.md -- Permission and question card composables, callback repository layer, DB schema extension
- [x] 04-02-PLAN.md -- Notification channels, PollingService notification triggers, deep-link navigation
- [x] 04-03-PLAN.md -- ChatScreen wiring for interactive cards, ChatViewModel callback actions, visual verification

### Phase 5: Voice Pipeline
**Goal**: Users can speak to sessions and hear responses -- on-device Whisper transcription and Android TTS enable hands-free interaction
**Requirements**: VOIC-01, VOIC-02, VOIC-03
**Plans**: 3 plans

Plans:
- [x] 05-01-PLAN.md -- whisper.cpp native CMake build, JNI bridge, WhisperManager with model file handling
- [x] 05-02-PLAN.md -- TtsManager with Android TextToSpeech, speaker icon on message bubbles, play/stop controls
- [x] 05-03-PLAN.md -- AudioRecorder, hold-to-record button, transcript preview, voice-to-text-to-send pipeline wiring

### Phase 6: Direct WebSocket Transport
**Goal**: Replace Telegram Bot API transport with direct WebSocket connection between Mac and Android app
**Requirements**: R-06-01, R-06-02, R-06-03, R-06-04, R-06-05, R-06-06, R-06-07
**Plans**: 4 plans

Plans:
- [x] 06-01-PLAN.md -- Mac-side WebSocket server (relay-server.cjs), mDNS advertisement, hook routing update
- [x] 06-02-PLAN.md -- App-side WebSocket client, ConnectionState, RelayRepository replacing TelegramRepository
- [x] 06-03-PLAN.md -- WebSocketService, NSD discovery, setup screen rewrite, Telegram code removal from Android
- [x] 06-04-PLAN.md -- Telegram code deletion, build verification, end-to-end integration checkpoint

</details>

### 🚧 v1.1 Standalone Server (In Progress)

**Milestone Goal:** Make relay-server a standalone component in the relay repo, eliminate zellij-claude dependency, add interactive AskUserQuestion controls, and move voice transcription to the Mac for speed.

- [ ] **Phase 7: Server Migration** - Move relay-server and hooks to relay repo with direct Zellij dispatching
- [ ] **Phase 8: Interactive Controls & Reconnect** - AskUserQuestion keystroke mapping and session sync on reconnect
- [ ] **Phase 9: Mac-Side Voice** - Server-side Whisper transcription replacing on-device processing

## Phase Details

### Phase 7: Server Migration
**Goal**: relay-server is a standalone Node.js component in the relay repo that dispatches directly to Zellij -- no dependency on zellij-claude for anything except Claude Code itself
**Depends on**: Phase 6 (WebSocket transport)
**Requirements**: SERV-01, SERV-02, SERV-03, SERV-05
**Success Criteria** (what must be TRUE):
  1. relay-server.cjs runs from `server/` in the relay repo with its own package.json and dependencies
  2. Hooks (session-start, session-stop, permission-notify, ask-notify) live in `server/hooks/` and reference relay-server directly (not zellij-claude)
  3. Server dispatches user responses to sessions via `zellij action write-chars` instead of `npx zellij-claude send`
  4. zellij-claude hooks directory no longer contains relay-server or relay-specific hooks
**Plans**: 3 plans

Plans:
- [x] 07-01-PLAN.md -- Standalone relay-server.cjs with package.json and direct Zellij dispatch
- [x] 07-02-PLAN.md -- Migrate hooks to server/hooks/ with standalone sendRelay (no Telegram)
- [ ] 07-03-PLAN.md -- Hook installer and zellij-claude cleanup

### Phase 8: Interactive Controls & Reconnect
**Goal**: Users can fully answer AskUserQuestion prompts from the app (single choice, multiple choice, free text) and see existing sessions after reconnecting
**Depends on**: Phase 7 (direct Zellij dispatch needed for keystroke sequences)
**Requirements**: CTRL-01, CTRL-02, CTRL-03, SERV-04
**Success Criteria** (what must be TRUE):
  1. User can answer a single-choice AskUserQuestion by tapping an option, which sends the correct number key + Enter as keystrokes to the session
  2. User can answer a multiple-choice AskUserQuestion by toggling options (number keys), navigating to Submit (Down arrow), and confirming (Enter)
  3. User can answer a free-text AskUserQuestion by selecting Other (Down + Enter), typing text, and confirming (Enter)
  4. User sees all active sessions immediately after the app reconnects to relay-server (no manual /ls needed)
**Plans**: 2 plans

Plans:
- [x] 08-01-PLAN.md -- Server answer action handler with keystroke computation + reconnect sync
- [x] 08-02-PLAN.md -- App answer protocol (DTOs, transport, repository) + QuestionCard UI overhaul
**UI hint**: yes

### Phase 9: Mac-Side Voice
**Goal**: Voice transcription happens on the Mac (fast, high quality) instead of the phone -- the app streams audio to the server and receives text back
**Depends on**: Phase 7 (server must be in relay repo to add Whisper capability)
**Requirements**: VOIC-10, VOIC-11, VOIC-12
**Success Criteria** (what must be TRUE):
  1. User can hold-to-record and audio data is sent over WebSocket to relay-server (not transcribed on device)
  2. Server transcribes received audio locally via whisper.cpp and sends the transcript back to the app
  3. APK no longer bundles the 141 MB Whisper model -- on-device transcription code is removed
**Plans**: 2 plans

Plans:
- [x] 09-01-PLAN.md -- Server-side audio receive and whisper-cli transcription
- [ ] 09-02-PLAN.md -- App sends audio to server, removes on-device Whisper (141 MB savings)

## Progress

**Execution Order:**
Phases execute in numeric order: 7 -> 8 -> 9

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Transport & Foundation | v1.0 | 3/3 | Complete | 2026-04-02 |
| 2. Session Discovery & Status | v1.0 | 3/3 | Complete | 2026-04-02 |
| 3. Messaging & Conversations | v1.0 | 2/2 | Complete | 2026-04-02 |
| 4. Permissions & Notifications | v1.0 | 3/3 | Complete | 2026-04-02 |
| 5. Voice Pipeline | v1.0 | 3/3 | Complete | 2026-04-02 |
| 6. Direct WebSocket Transport | v1.0 | 4/4 | Complete | 2026-04-03 |
| 7. Server Migration | v1.1 | 0/3 | Planning | - |
| 8. Interactive Controls & Reconnect | v1.1 | 2/2 | Complete | 2026-04-03 |
| 9. Mac-Side Voice | v1.1 | 0/2 | Planning | - |
