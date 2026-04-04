# Roadmap: Relay

## Milestones

- ✅ **v1.0 MVP** - Phases 1-6 (shipped 2026-04-03)
- ✅ **v1.1 Standalone Server** - Phases 7-9 (shipped 2026-04-03)
- 🚧 **v1.3 Session Management** - Phases 10-12 (in progress)

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

<details>
<summary>v1.0 MVP (Phases 1-6) - SHIPPED 2026-04-03</summary>

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

<details>
<summary>v1.1 Standalone Server (Phases 7-9) - SHIPPED 2026-04-03</summary>

### Phase 7: Server Migration
**Goal**: relay-server is a standalone Node.js component in the relay repo that dispatches directly to Zellij -- no dependency on zellij-claude for anything except Claude Code itself
**Requirements**: SERV-01, SERV-02, SERV-03, SERV-05
**Plans**: 3 plans

Plans:
- [x] 07-01-PLAN.md -- Standalone relay-server.cjs with package.json and direct Zellij dispatch
- [x] 07-02-PLAN.md -- Migrate hooks to server/hooks/ with standalone sendRelay (no Telegram)
- [x] 07-03-PLAN.md -- Hook installer and zellij-claude cleanup

### Phase 8: Interactive Controls & Reconnect
**Goal**: Users can fully answer AskUserQuestion prompts from the app (single choice, multiple choice, free text) and see existing sessions after reconnecting
**Requirements**: CTRL-01, CTRL-02, CTRL-03, SERV-04
**Plans**: 2 plans

Plans:
- [x] 08-01-PLAN.md -- Server answer action handler with keystroke computation + reconnect sync
- [x] 08-02-PLAN.md -- App answer protocol (DTOs, transport, repository) + QuestionCard UI overhaul

### Phase 9: Mac-Side Voice
**Goal**: Voice transcription happens on the Mac (fast, high quality) instead of the phone -- the app streams audio to the server and receives text back
**Requirements**: VOIC-10, VOIC-11, VOIC-12
**Plans**: 2 plans

Plans:
- [x] 09-01-PLAN.md -- Server-side audio receive and whisper-cli transcription
- [x] 09-02-PLAN.md -- App sends audio to server, removes on-device Whisper (141 MB savings)

</details>

### v1.3 Session Management (In Progress)

**Milestone Goal:** Create new Claude Code sessions from the app with FZF-style fuzzy directory search and improve response handling in session-stop hooks.

- [x] **Phase 10: Server Config & Protocol** - Server-side config, directory listing, and session creation handlers (completed 2026-04-04)
- [ ] **Phase 11: Session Creation UI** - Fullscreen dialog with fuzzy search, confirmation, and session launch
- [ ] **Phase 12: Smart Response Handling** - Size-aware response logic replacing legacy truncation

## Phase Details

### Phase 10: Server Config & Protocol
**Goal**: Server can read project root configuration, scan directories, and create new Claude Code sessions on request from the app
**Depends on**: Phase 9 (relay-server in relay repo)
**Requirements**: CONF-01, CONF-02, CONF-03
**Success Criteria** (what must be TRUE):
  1. Server reads project roots from `~/.config/relay/project-roots.json` on each `list_directories` request (with sensible defaults if file is missing)
  2. Server returns a flat list of directories scanned 2 levels deep from configured roots via the `directory_list` WebSocket message
  3. Server handles `create_session` action: validates path, deduplicates kuerzel against existing zellij tabs, and creates a new `@kuerzel` tab with `claude` running in it
  4. Newly created session triggers existing session-start hook automatically, making it appear in the app's session list
**Plans**: 2 plans

Plans:
- [x] 10-01-PLAN.md — Config reading + directory listing handler (CONF-01, CONF-02)
- [x] 10-02-PLAN.md — Session creation handler with kuerzel dedup (CONF-03)

### Phase 11: Session Creation UI
**Goal**: Users can create a new Claude Code session from the app by selecting a project directory and confirming session parameters
**Depends on**: Phase 10 (server protocol must exist)
**Requirements**: SESS-01, SESS-02, SESS-03, SESS-04, SESS-05, SESS-06, SESS-07
**Success Criteria** (what must be TRUE):
  1. User can open the session creation dialog from a FAB (+) on the session list screen or from the navigation drawer
  2. User can fuzzy-search project directories with instant filtering as they type (no network roundtrip per keystroke)
  3. User sees a confirmation dialog with editable kuerzel, the selected path, and a toggle for `--dangerously-skip-permissions`
  4. User can enter a custom path not in the directory list and proceed to confirmation
  5. User taps "Create" and the app navigates to the new session after successful creation (or shows an error on failure)
**Plans**: 2 plans

Plans:
- [x] 10-01-PLAN.md — Config reading + directory listing handler (CONF-01, CONF-02)
- [x] 10-02-PLAN.md — Session creation handler with kuerzel dedup (CONF-03)
**UI hint**: yes

### Phase 12: Smart Response Handling
**Goal**: Session-stop notifications include complete or intelligently sized responses instead of hard-truncated text
**Depends on**: Nothing (independent of session creation)
**Requirements**: RESP-01
**Success Criteria** (what must be TRUE):
  1. When a session stops and the last response is 4 KB or less, both last responses are included in the notification (untruncated)
  2. When a session stops and the combined responses exceed 4 KB, only the last response is sent (untruncated up to 16 KB)
  3. Responses exceeding 16 KB are truncated with a visible marker instead of silently cutting off at 2000 characters
**Plans**: 2 plans

Plans:
- [ ] 10-01-PLAN.md — Config reading + directory listing handler (CONF-01, CONF-02)
- [ ] 10-02-PLAN.md — Session creation handler with kuerzel dedup (CONF-03)

## Progress

**Execution Order:**
Phases execute in numeric order: 10 -> 11 -> 12

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Transport & Foundation | v1.0 | 3/3 | Complete | 2026-04-02 |
| 2. Session Discovery & Status | v1.0 | 3/3 | Complete | 2026-04-02 |
| 3. Messaging & Conversations | v1.0 | 2/2 | Complete | 2026-04-02 |
| 4. Permissions & Notifications | v1.0 | 3/3 | Complete | 2026-04-02 |
| 5. Voice Pipeline | v1.0 | 3/3 | Complete | 2026-04-02 |
| 6. Direct WebSocket Transport | v1.0 | 4/4 | Complete | 2026-04-03 |
| 7. Server Migration | v1.1 | 3/3 | Complete | 2026-04-03 |
| 8. Interactive Controls & Reconnect | v1.1 | 2/2 | Complete | 2026-04-03 |
| 9. Mac-Side Voice | v1.1 | 2/2 | Complete | 2026-04-03 |
| 10. Server Config & Protocol | v1.3 | 0/0 | Complete    | 2026-04-04 |
| 11. Session Creation UI | v1.3 | 0/0 | Not started | - |
| 12. Smart Response Handling | v1.3 | 0/0 | Not started | - |
