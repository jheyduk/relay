# Milestones

## v1.3 Session Management (Shipped: 2026-04-04)

**Phases completed:** 3 phases, 4 plans, 10 tasks

**Key accomplishments:**

- Project roots config reading and directory scanning via list_directories WebSocket action
- create_session WebSocket handler with path validation, kuerzel deduplication via zellij list-tabs, and new-tab creation
- WebSocket protocol layer for directory_list and create_session actions with DTO, parser, domain models, send methods, and 4 new tests
- Fullscreen session creation dialog with FZF-style fuzzy directory search, 4-phase flow (loading, directory selection, custom path, confirmation), and (+) FAB entry point replacing Refresh FAB
- Replace legacy truncate(2000) with tiered size-aware response logic

---

## v1.1 Standalone Server (Shipped: 2026-04-03)

**Phases completed:** 3 phases, 7 plans, 11 tasks

**Key accomplishments:**

- 6 hook files migrated to server/hooks/ with standalone Unix socket IPC -- zero Telegram dependencies, relay-only notifications
- Hook installer that registers relay server hooks in Claude Code settings.json and cleans up old zellij-claude entries
- Answer action handler with single/multi/text keystroke dispatch and reconnect session sync on WebSocket connect
- Full AskUserQuestion answer pipeline from interactive QuestionCard UI through ViewModel/Repository/WebSocket to server, supporting single-choice, multi-choice, and free-text answer modes
- Binary WebSocket audio receive with whisper-cli transcription on Mac — receives WAV from app, transcribes locally with Metal acceleration, returns JSON transcript
- Rewired app to send WAV audio to Mac server via binary WebSocket frames and receive transcript back, removing 141 MB on-device Whisper model and all native JNI code

---

## v1.0 Relay MVP (Shipped: 2026-04-03)

**Phases completed:** 6 phases, 18 plans, 40 tasks

**Key accomplishments:**

- KMP multi-module project with Ktor/SQLDelight/Koin, Telegram API DTOs, Relay JSON protocol models, and SQLDelight message schema
- Ktor-based Telegram Bot API client with long-polling, exponential backoff, two-bot read/write architecture, and Koin DI wiring
- Foreground polling service with network recovery, DataStore token storage, Compose setup/status screens, and conditional navigation producing a 72MB debug APK
- Session data model, /ls response parser, command router, and SessionRepository for session discovery and state management in shared KMP module
- Material 3 session list with Dynamic Color theming, color-coded status chips, pull-to-refresh, drawer navigation, and command input bar
- Koin DI wiring for SessionRepository singleton and SessionListViewModel injection, navigation updated to route to session list as main destination
- ChatRepository with optimistic send-and-persist, reactive SQLDelight queries, and ChatMessage domain model for per-session chat
- Per-session chat screen with sender-distinguished message bubbles, reverse-layout LazyColumn, and session-card-to-chat navigation wiring
- PermissionCard and QuestionCard composables with callback_response DB tracking and ChatRepository.answerCallback for native Allow/Deny and option selection
- Two-channel notification system with deep-link navigation for permission requests (HIGH priority) and session completions (DEFAULT priority)
- Type-aware ChatScreen rendering with PermissionCard/QuestionCard dispatch and ViewModel callback actions
- whisper.cpp v1.8.3 compiled as arm64-v8a native library via CMake with JNI bridge and WhisperManager Kotlin API registered in Koin
- Android TTS integration with play/stop icon on incoming message bubbles and code block stripping
- Hold-to-record voice input with on-device Whisper transcription, editable transcript preview, and send-as-text flow through existing ChatRepository
- WebSocket relay server with Unix socket IPC, mDNS discovery, and hook routing updated to WebSocket-first with Telegram fallback after 30s disconnect
- Ktor WebSocket client with exponential backoff reconnect, RelayRepository replacing TelegramRepository, and updated DI wiring
- WebSocketService replaces PollingService with mDNS discovery, simplified setup screen (shared secret only), and full Telegram code removal from Android layer
- Deleted all Telegram transport code (8 files, 656 lines) and verified clean build with WebSocket-only transport

---
