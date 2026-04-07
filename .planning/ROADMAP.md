# Roadmap: Relay

## Milestones

- ✅ **v1.0 MVP** - Phases 1-6 (shipped 2026-04-03)
- ✅ **v1.1 Standalone Server** - Phases 7-9 (shipped 2026-04-03)
- ✅ **v1.3 Session Management** - Phases 10-12 (shipped 2026-04-04)
- 🚧 **v1.4 Auth Recovery & Smart Responses** - Phases 13-16 (in progress)

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

<details>
<summary>v1.3 Session Management (Phases 10-12) - SHIPPED 2026-04-04</summary>

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
- [x] 10-01-PLAN.md -- Config reading + directory listing handler (CONF-01, CONF-02)
- [x] 10-02-PLAN.md -- Session creation handler with kuerzel dedup (CONF-03)

### Phase 11: Session Creation UI
**Goal**: Users can create a new Claude Code session from the app by selecting a project directory and confirming session parameters
**Depends on**: Phase 10 (server protocol must exist)
**Requirements**: SESS-01, SESS-02, SESS-03, SESS-04, SESS-05, SESS-06, SESS-07
**Success Criteria** (what must be TRUE):
  1. User can open the session creation dialog from a FAB (+) on the session list screen (SESS-02 superseded -- drawer removed)
  2. User can fuzzy-search project directories with instant filtering as they type (no network roundtrip per keystroke)
  3. User sees a confirmation dialog with editable kuerzel, the selected path, and a toggle for `--dangerously-skip-permissions`
  4. User can enter a custom path not in the directory list and proceed to confirmation
  5. User taps "Create" and the app navigates to the new session after successful creation (or shows an error on failure)
**Plans**: 2 plans

Plans:
- [x] 11-01-PLAN.md -- Protocol DTOs, parser, domain models, WebSocket send methods (SESS-01, SESS-02, SESS-03, SESS-07)
- [x] 11-02-PLAN.md -- FuzzyMatch, CreateSessionViewModel, CreateSessionDialog, FAB entry point (SESS-01, SESS-03, SESS-04, SESS-05, SESS-06, SESS-07)

### Phase 12: Smart Response Handling
**Goal**: Session-stop notifications include complete or intelligently sized responses instead of hard-truncated text
**Depends on**: Nothing (independent of session creation)
**Requirements**: RESP-01
**Success Criteria** (what must be TRUE):
  1. When a session stops and the last response is 4 KB or less, both last responses are included in the notification (untruncated)
  2. When a session stops and the combined responses exceed 4 KB, only the last response is sent (untruncated up to 16 KB)
  3. Responses exceeding 16 KB are truncated with a visible marker instead of silently cutting off at 2000 characters
**Plans**: [To be planned]

</details>

### v1.4 Auth Recovery & Smart Responses (In Progress)

**Milestone Goal:** Enable remote OAuth re-authentication from the app when Claude Code sessions hit auth errors, and eliminate duplicate `/last` responses via checksum-based deduplication.

- [x] **Phase 13: Auth Error Detection & Login Dispatch** - Server parses terminal output for auth failures and automatically dispatches /login (completed 2026-04-07)
- [ ] **Phase 14: OAuth URL Extraction & Forwarding** - Server extracts OAuth URL from terminal and sends it to app via WebSocket
- [ ] **Phase 15: App-Side Auth Recovery UI** - User opens OAuth URL on phone, pastes auth code back, sees recovery status
- [ ] **Phase 16: Last-Response Dedup** - Checksum-based deduplication for /last responses on the server

## Phase Details

### Phase 13: Auth Error Detection & Login Dispatch
**Goal**: Server detects when Claude Code sessions lose authentication and automatically triggers the login flow without user intervention
**Depends on**: Phase 12 (relay-server with terminal output parsing)
**Requirements**: AUTH-01, AUTH-02
**Success Criteria** (what must be TRUE):
  1. When a Claude Code session outputs an auth error (401, session expired, token revoked), the server detects it within one polling cycle
  2. Server automatically dispatches `/login` into the affected session's Zellij pane after detecting an auth failure
  3. Server does not dispatch duplicate `/login` commands if the same session is already in a login recovery flow
**Plans**: 1 plan

Plans:
- [x] 13-01-PLAN.md -- Auth error detection, recovery state machine, login dispatch, AUTH_REQUIRED protocol

### Phase 14: OAuth URL Extraction & Forwarding
**Goal**: Server captures the OAuth authorization URL that appears after `/login` and delivers it to the app so the user can authenticate from their phone
**Depends on**: Phase 13 (login must be dispatched first to produce the URL)
**Requirements**: AUTH-03, AUTH-04
**Success Criteria** (what must be TRUE):
  1. Server extracts the OAuth authorization URL from terminal output after `/login` produces the authentication prompt
  2. Server sends the OAuth URL to the app as an `AUTH_URL` WebSocket message type with the correct session identifier
  3. App receives and can parse the `AUTH_URL` message (protocol layer ready for Phase 15 UI)
**Plans**: TBD

### Phase 15: App-Side Auth Recovery UI
**Goal**: User can complete the entire OAuth re-authentication flow from their phone -- open the URL, get the code, paste it back, and see the session recover
**Depends on**: Phase 14 (OAuth URL must be available in the app)
**Requirements**: AUTH-05, AUTH-06, AUTH-07
**Success Criteria** (what must be TRUE):
  1. User can tap the OAuth URL in the app and it opens in their phone's browser
  2. User can paste the authorization code into the app and it gets dispatched to the correct Claude Code terminal session
  3. App shows a status indicator tracking the auth recovery lifecycle: detected -> login triggered -> waiting for code -> recovered
  4. After successful recovery, the session returns to normal operating state in the app
**Plans**: TBD
**UI hint**: yes

### Phase 16: Last-Response Dedup
**Goal**: Users no longer receive duplicate `/last` responses when nothing has changed in a session -- the server tracks content and reports "No updates" instead
**Depends on**: Nothing (independent of auth recovery)
**Requirements**: RESP-01, RESP-02, RESP-03
**Success Criteria** (what must be TRUE):
  1. Server stores a checksum of the last sent `/last` response for each session
  2. When the user requests `/last` and the content is unchanged, the server responds with a "No updates" message instead of resending the same content
  3. When the `/last` content has actually changed, the full response is sent as before (checksum updated)
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 13 -> 14 -> 15 -> 16

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
| 10. Server Config & Protocol | v1.3 | 2/2 | Complete | 2026-04-04 |
| 11. Session Creation UI | v1.3 | 2/2 | Complete | 2026-04-04 |
| 12. Smart Response Handling | v1.3 | 0/0 | Complete | 2026-04-04 |
| 13. Auth Error Detection & Login Dispatch | v1.4 | 1/1 | Complete   | 2026-04-07 |
| 14. OAuth URL Extraction & Forwarding | v1.4 | 0/0 | Not started | - |
| 15. App-Side Auth Recovery UI | v1.4 | 0/0 | Not started | - |
| 16. Last-Response Dedup | v1.4 | 0/0 | Not started | - |
