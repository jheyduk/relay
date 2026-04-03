---
phase: 06-direct-websocket-transport
plan: 02
subsystem: transport
tags: [websocket, ktor, kmp, coroutines, reconnect]

requires:
  - phase: 01-project-setup
    provides: Ktor client, SQLDelight, Koin DI, RelayMessageParser
provides:
  - WebSocketClient with exponential backoff reconnect
  - ConnectionState enum for UI connection indicators
  - RelayRepository interface replacing TelegramRepository
  - RelayRepositoryImpl backed by WebSocketClient
  - Updated ChatRepositoryImpl using WebSocket transport
  - Shared DI module wired for WebSocket
affects: [06-03-android-integration, 06-04-cleanup]

tech-stack:
  added: [ktor-client-websockets]
  patterns: [WebSocket reconnect loop with exponential backoff, ConnectionState StateFlow for UI binding]

key-files:
  created:
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/ConnectionState.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/WebSocketClient.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/RelayRepository.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/RelayRepositoryImpl.kt
  modified:
    - gradle/libs.versions.toml
    - shared/build.gradle.kts
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepositoryImpl.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/di/SharedModule.kt

key-decisions:
  - "Backoff resets to 1000ms (not 0) after successful connection to prevent rapid reconnect on transient disconnect"
  - "WebSocketClient uses JSON map encoding for sendCommand/sendRawCommand payloads"
  - "Old Telegram files not deleted yet -- Plan 03 handles Android-side cleanup"

patterns-established:
  - "ConnectionState StateFlow pattern for reactive connection UI"
  - "WebSocket reconnect loop: DISCONNECTED->CONNECTING->CONNECTED with backoff"

requirements-completed: [R-06-03, R-06-05, R-06-06]

duration: 2min
completed: 2026-04-03
---

# Phase 6 Plan 2: WebSocket Client Summary

**Ktor WebSocket client with exponential backoff reconnect, RelayRepository replacing TelegramRepository, and updated DI wiring**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-03T09:24:12Z
- **Completed:** 2026-04-03T09:26:30Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- WebSocketClient connects to relay-server with token auth, parses incoming JSON via RelayMessageParser
- ConnectionState enum exposed as StateFlow for UI binding
- RelayRepository replaces TelegramRepository with same contract plus connectionState
- SharedModule DI fully rewired: Telegram singletons removed, WebSocket transport wired

## Task Commits

Each task was committed atomically:

1. **Task 1: Add ktor-client-websockets dependency and create WebSocketClient + ConnectionState** - `73e80f7` (feat)
2. **Task 2: Replace TelegramRepository with RelayRepository, update DI and ChatRepository** - `a04a2cc` (feat)

## Files Created/Modified
- `gradle/libs.versions.toml` - Added ktor-client-websockets dependency
- `shared/build.gradle.kts` - Added websockets to commonMain dependencies
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/ConnectionState.kt` - Connection state enum (DISCONNECTED, CONNECTING, CONNECTED)
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/WebSocketClient.kt` - Ktor WebSocket client with reconnect, send, disconnect
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/RelayRepository.kt` - Repository interface with connectionState StateFlow
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/RelayRepositoryImpl.kt` - WebSocket-backed repository implementation
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepositoryImpl.kt` - Updated to use RelayRepository
- `shared/src/commonMain/kotlin/dev/heyduk/relay/di/SharedModule.kt` - Rewired DI for WebSocket transport

## Decisions Made
- Backoff resets to 1000ms (not 0) after successful connection that later disconnects, preventing rapid reconnect storms
- WebSocketClient sends commands as JSON maps with "action", "kuerzel", "message" keys
- Old Telegram files (TelegramRepository.kt, TelegramRepositoryImpl.kt, TelegramPoller.kt, TelegramApi.kt) deliberately not deleted -- Plan 03 handles cleanup to avoid breaking Android module references

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- WebSocket transport layer complete in shared module
- Plan 03 (Android integration) can now wire WebSocketClient into Android service layer
- Plan 04 (cleanup) can remove old Telegram files

---
*Phase: 06-direct-websocket-transport*
*Completed: 2026-04-03*
