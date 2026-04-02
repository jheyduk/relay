---
phase: 03-messaging-conversations
plan: 01
subsystem: data
tags: [sqldelight, koin, coroutines, flow, repository]

requires:
  - phase: 01-telegram-transport
    provides: TelegramRepository with sendCommand, Messages.sq schema, SQLDelight database
provides:
  - ChatMessage domain model for UI consumption
  - ChatRepository interface and implementation with send-and-persist
  - Reactive Flow of messages per session
  - expect/actual currentTimeMillis KMP clock abstraction
affects: [03-02, chat-ui, messaging]

tech-stack:
  added: []
  patterns: [optimistic-insert-before-send, negative-synthetic-ids, expect-actual-clock]

key-files:
  created:
    - shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/ChatMessage.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepository.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepositoryImpl.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/util/Clock.kt
    - shared/src/androidMain/kotlin/dev/heyduk/relay/util/Clock.android.kt
    - shared/src/jvmMain/kotlin/dev/heyduk/relay/util/Clock.jvm.kt
  modified:
    - shared/src/commonMain/sqldelight/dev/heyduk/relay/Messages.sq
    - shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayMessageType.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/di/SharedModule.kt

key-decisions:
  - "Negative epoch millis as synthetic update_id to avoid collision with Telegram's positive IDs"
  - "Optimistic insert before network send for offline resilience"
  - "expect/actual currentTimeMillis instead of adding kotlinx-datetime dependency"
  - "Added TEXT to RelayMessageType enum for outgoing message type compatibility"

patterns-established:
  - "Optimistic persistence: insert to DB before network call for crash resilience"
  - "Negative synthetic IDs: outgoing messages use -(epochMillis) to avoid PK collision with Telegram update IDs"
  - "expect/actual for platform clock: dev.heyduk.relay.util.currentTimeMillis()"

requirements-completed: [MSG-01, MSG-05]

duration: 3min
completed: 2026-04-02
---

# Phase 3 Plan 1: Chat Data Layer Summary

**ChatRepository with optimistic send-and-persist, reactive SQLDelight queries, and ChatMessage domain model for per-session chat**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-02T20:28:45Z
- **Completed:** 2026-04-02T20:31:57Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- ChatMessage data class as UI-friendly projection of messages table with sender direction
- ChatRepository with reactive Flow of messages per session and send-with-persist
- Outgoing messages persist to SQLDelight before Telegram send for crash resilience
- KMP expect/actual clock abstraction for synthetic ID generation

## Task Commits

Each task was committed atomically:

1. **Task 1: ChatMessage model and Messages.sq outgoing persistence** - `5501ade` (feat)
2. **Task 2: ChatRepository interface, implementation, and DI registration** - `126b4a1` (feat)

## Files Created/Modified
- `shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/ChatMessage.kt` - UI-friendly message model with sender direction
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepository.kt` - Chat data access interface
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepositoryImpl.kt` - Optimistic persist + Telegram send implementation
- `shared/src/commonMain/kotlin/dev/heyduk/relay/util/Clock.kt` - expect fun currentTimeMillis
- `shared/src/androidMain/kotlin/dev/heyduk/relay/util/Clock.android.kt` - actual Android clock
- `shared/src/jvmMain/kotlin/dev/heyduk/relay/util/Clock.jvm.kt` - actual JVM clock
- `shared/src/commonMain/sqldelight/dev/heyduk/relay/Messages.sq` - Added insertOutgoing and countOutgoingForSession queries
- `shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayMessageType.kt` - Added TEXT enum value
- `shared/src/commonMain/kotlin/dev/heyduk/relay/di/SharedModule.kt` - Registered ChatRepository singleton

## Decisions Made
- Negative epoch millis as synthetic update_id to avoid collision with Telegram's positive IDs
- Optimistic insert before network send -- message persists even if Telegram call fails
- Created expect/actual currentTimeMillis instead of adding kotlinx-datetime as a new dependency
- Added TEXT to RelayMessageType enum since insertOutgoing hardcodes 'TEXT' type

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added TEXT to RelayMessageType enum**
- **Found during:** Task 1 (ChatMessage model)
- **Issue:** insertOutgoing query hardcodes type='TEXT' but RelayMessageType enum only had STATUS, RESPONSE, PERMISSION, QUESTION, COMPLETION -- valueOf("TEXT") would crash at runtime
- **Fix:** Added TEXT to the RelayMessageType enum
- **Files modified:** shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayMessageType.kt
- **Verification:** SQLDelight generation succeeds, compilation passes
- **Committed in:** 5501ade (Task 1 commit)

**2. [Rule 3 - Blocking] Created expect/actual currentTimeMillis for KMP**
- **Found during:** Task 2 (ChatRepositoryImpl)
- **Issue:** No platform-independent way to get epoch millis in commonMain -- System.currentTimeMillis() is JVM-only
- **Fix:** Created expect/actual in dev.heyduk.relay.util with android and jvm actuals
- **Files modified:** Clock.kt, Clock.android.kt, Clock.jvm.kt
- **Verification:** compileKotlinJvm succeeds
- **Committed in:** 126b4a1 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 bug, 1 blocking)
**Impact on plan:** Both auto-fixes necessary for correctness. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all data flows are wired to SQLDelight and TelegramRepository.

## Next Phase Readiness
- ChatRepository is ready for consumption by chat UI (Plan 03-02)
- messagesForSession provides reactive Flow for Compose collectAsStateWithLifecycle
- sendMessage provides the send action for chat input

## Self-Check: PASSED

All 6 created files verified present. Both task commits (5501ade, 126b4a1) verified in git log.

---
*Phase: 03-messaging-conversations*
*Completed: 2026-04-02*
