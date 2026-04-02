---
phase: 01-transport-foundation
plan: 02
subsystem: transport
tags: [ktor, telegram-bot-api, long-polling, koin, sqldelight, coroutines]

# Dependency graph
requires:
  - phase: 01-01
    provides: KMP project structure, domain models, DTOs, SQLDelight schema
provides:
  - TelegramApi interface and Ktor implementation for Bot API
  - TelegramPoller with exponential backoff long-polling loop
  - RelayMessageParser for JSON-to-domain conversion
  - TelegramRepository with two-bot read/write architecture
  - SharedModule Koin DI wiring
  - OffsetProvider interface for platform-specific persistence
affects: [01-03, android-platform-integration, session-management]

# Tech tracking
tech-stack:
  added: [ktor-client-mock, kotlinx-coroutines-test]
  patterns: [interface-based API abstraction, two-bot architecture, exponential backoff with cap, at-least-once delivery via offset persistence]

key-files:
  created:
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/TelegramApi.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/TelegramPoller.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/RelayMessageParser.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/TelegramRepository.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/TelegramRepositoryImpl.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/di/SharedModule.kt
    - shared/src/commonTest/kotlin/dev/heyduk/relay/data/remote/TelegramApiTest.kt
    - shared/src/commonTest/kotlin/dev/heyduk/relay/data/remote/RelayMessageParserTest.kt
    - shared/src/commonTest/kotlin/dev/heyduk/relay/data/remote/TelegramPollerTest.kt
  modified: []

key-decisions:
  - "Extracted TelegramApi as interface (not just class) for testability -- enables fake implementations in poller and repository tests"
  - "Two-bot architecture: named Koin qualifiers (relayApi/commandApi) for read/write separation"
  - "At-least-once delivery: offset persisted before processing updates"

patterns-established:
  - "Interface-based API abstraction: TelegramApi interface with TelegramApiImpl Ktor implementation"
  - "DTO-to-domain mapping via extension functions (toDomain())"
  - "SQLDelight Messages-to-domain mapping in repository layer"
  - "Exponential backoff: 0 -> 1s -> 2s -> 4s -> ... -> 30s cap, reset on success"

requirements-completed: [TRNS-01, TRNS-02, TRNS-03, TRNS-04]

# Metrics
duration: 9min
completed: 2026-04-02
---

# Phase 01 Plan 02: Telegram Transport Summary

**Ktor-based Telegram Bot API client with long-polling, exponential backoff, two-bot read/write architecture, and Koin DI wiring**

## Performance

- **Duration:** 9 min
- **Started:** 2026-04-02T16:17:08Z
- **Completed:** 2026-04-02T16:26:43Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- TelegramApi interface and TelegramApiImpl with getUpdates (long-poll timeouts: socket 35s, request 60s) and sendMessage
- RelayMessageParser converts relay bot JSON text to domain RelayUpdate objects, gracefully handles invalid/plain text
- TelegramPoller runs continuous loop with exponential backoff (1s to 30s cap), persists offset before processing (at-least-once delivery)
- TelegramRepository provides two-bot architecture: reads from relay bot, writes commands to existing zellij-claude bot via @kuerzel format
- SharedModule Koin module with named qualifiers for relay/command bot instances
- 16 unit tests across 3 test classes, all passing

## Task Commits

Each task was committed atomically:

1. **Task 1: TelegramApi client and RelayMessageParser with unit tests** - `0b1bb56` (feat)
2. **Task 2: TelegramPoller with backoff, TelegramRepository, and Koin module** - `16e21c8` (feat)

## Files Created/Modified
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/TelegramApi.kt` - TelegramApi interface + TelegramApiImpl Ktor implementation + TelegramApiException
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/RelayMessageParser.kt` - JSON-to-domain parser with DTO mapping extensions
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/TelegramPoller.kt` - Long-polling loop with backoff + OffsetProvider interface
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/TelegramRepository.kt` - Repository interface
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/TelegramRepositoryImpl.kt` - Two-bot implementation with SQLDelight persistence
- `shared/src/commonMain/kotlin/dev/heyduk/relay/di/SharedModule.kt` - Koin module with named qualifiers
- `shared/src/commonTest/kotlin/dev/heyduk/relay/data/remote/TelegramApiTest.kt` - 5 tests with MockEngine
- `shared/src/commonTest/kotlin/dev/heyduk/relay/data/remote/RelayMessageParserTest.kt` - 6 tests for JSON parsing
- `shared/src/commonTest/kotlin/dev/heyduk/relay/data/remote/TelegramPollerTest.kt` - 5 tests with virtual time

## Decisions Made
- Extracted TelegramApi as an interface (deviation from plan which specified a class) for testability in TelegramPoller and repository tests
- Two-bot architecture wired via named Koin qualifiers: `relayApi` for reading, `commandApi` for writing
- At-least-once delivery semantics: offset persisted before message processing to avoid data loss on crash

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Extracted TelegramApi interface for testability**
- **Found during:** Task 2 (TelegramPoller tests needed a fake API)
- **Issue:** Plan specified TelegramApi as a class; TelegramPoller tests need a fake implementation
- **Fix:** Extracted TelegramApi interface, renamed implementation to TelegramApiImpl, updated tests
- **Files modified:** TelegramApi.kt, TelegramApiTest.kt
- **Verification:** All 16 tests pass
- **Committed in:** 16e21c8 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Interface extraction is standard testability practice. No scope creep. SharedModule uses TelegramApiImpl correctly.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all code is fully wired with real implementations.

## Next Phase Readiness
- Transport layer complete: TelegramApi, TelegramPoller, RelayMessageParser, TelegramRepository
- Plan 01-03 (Android platform integration) can wire platform-specific providers: OffsetProvider (DataStore), RelayDatabase (SQLDelight Android driver), bot tokens
- SharedModule expects named string parameters (relayBotToken, commandBotToken, chatId) from Android module

## Self-Check: PASSED

All 9 created files verified present. Both task commits (0b1bb56, 16e21c8) verified in git log. 16 tests passing.

---
*Phase: 01-transport-foundation*
*Completed: 2026-04-02*
