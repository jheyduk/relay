---
phase: 04-permissions-notifications
plan: 01
subsystem: ui
tags: [compose, material3, permissions, callbacks, sqldelight]

requires:
  - phase: 03-messaging-conversations
    provides: ChatRepository, ChatMessage model, Messages.sq schema, MessageBubble composable
provides:
  - PermissionCard composable with Allow/Deny buttons and answered state
  - QuestionCard composable with option chips and answered state
  - ChatRepository.answerCallback for persisting and sending permission responses
  - callback_response column in messages table for tracking answered state
  - ChatMessage extended with toolName, command, filePath, callbackResponse fields
affects: [04-permissions-notifications, chat-screen-integration]

tech-stack:
  added: []
  patterns:
    - "Local variable extraction for cross-module smart casts in Compose"
    - "parseQuestionAndOptions heuristic: multi-line or pipe-separated options"

key-files:
  created:
    - androidApp/src/main/java/dev/heyduk/relay/presentation/chat/PermissionCard.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/chat/QuestionCard.kt
  modified:
    - shared/src/commonMain/sqldelight/dev/heyduk/relay/Messages.sq
    - shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/ChatMessage.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepository.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepositoryImpl.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/TelegramRepositoryImpl.kt
    - androidApp/src/main/java/dev/heyduk/relay/service/PollingService.kt

key-decisions:
  - "callback format: callback:{response}:{kuerzel} sent via sendCommand (per D-01 CONTEXT)"
  - "Local variable extraction for cross-module nullable smart casts in PermissionCard"

patterns-established:
  - "Interactive card pattern: composable with ChatMessage + callback lambdas + answered state"
  - "Option parsing heuristic: first line = question, remaining lines = options, pipe fallback"

requirements-completed: [MSG-03, MSG-04, MSG-06]

duration: 3min
completed: 2026-04-02
---

# Phase 04 Plan 01: Permission & Question Cards Summary

**PermissionCard and QuestionCard composables with callback_response DB tracking and ChatRepository.answerCallback for native Allow/Deny and option selection**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-02T20:48:51Z
- **Completed:** 2026-04-02T20:52:00Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- Extended Messages.sq schema with callback_response column and markAnswered query
- Extended ChatMessage with toolName, command, filePath, and callbackResponse fields
- Added answerCallback to ChatRepository interface and implementation (DB persist + Telegram callback send)
- Created PermissionCard composable with tool details, Allow/Deny buttons, sending spinner, and answered-state display
- Created QuestionCard composable with option chips parsed from message content and selected-state highlighting

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend DB schema and ChatRepository with callback support** - `76a3ae4` (feat)
2. **Task 2: PermissionCard and QuestionCard composables** - `b6464a8` (feat)

## Files Created/Modified
- `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/PermissionCard.kt` - Interactive permission card with Allow/Deny buttons and decision state
- `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/QuestionCard.kt` - Question card with option chips and selection tracking
- `shared/src/commonMain/sqldelight/dev/heyduk/relay/Messages.sq` - Added callback_response column and markAnswered query
- `shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/ChatMessage.kt` - Added toolName, command, filePath, callbackResponse fields
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepository.kt` - Added answerCallback method
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepositoryImpl.kt` - Implemented answerCallback with DB + Telegram callback
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/TelegramRepositoryImpl.kt` - Updated insertOrIgnore call with callback_response param
- `androidApp/src/main/java/dev/heyduk/relay/service/PollingService.kt` - Updated insertOrIgnore call with callback_response param

## Decisions Made
- Callback format follows D-01 CONTEXT decision: `callback:{response}:{kuerzel}` sent via sendCommand
- Used local variable extraction for cross-module nullable smart casts (Kotlin compiler limitation with properties from different modules)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated insertOrIgnore call sites for new column**
- **Found during:** Task 1 (DB schema extension)
- **Issue:** Adding callback_response column to insertOrIgnore query requires all call sites to pass the new parameter
- **Fix:** Updated PollingService.kt and TelegramRepositoryImpl.kt to pass `callback_response = null` for incoming messages
- **Files modified:** PollingService.kt, TelegramRepositoryImpl.kt
- **Verification:** compileKotlinJvm passes
- **Committed in:** 76a3ae4 (Task 1 commit)

**2. [Rule 1 - Bug] Fixed cross-module smart cast in PermissionCard**
- **Found during:** Task 2 (composable creation)
- **Issue:** Kotlin smart cast to String is impossible for public API property from different module
- **Fix:** Extracted `message.toolName` to local `val toolName` before null check
- **Files modified:** PermissionCard.kt
- **Verification:** compileDebugKotlin passes
- **Committed in:** b6464a8 (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both auto-fixes necessary for compilation. No scope creep.

## Issues Encountered
None beyond the auto-fixed deviations above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- PermissionCard and QuestionCard are ready to be integrated into ChatScreen (plan 04-02)
- ChatViewModel needs answerCallback wiring to connect card button taps to repository
- ChatScreen needs to dispatch PERMISSION/QUESTION message types to the new card composables instead of MessageBubble

## Self-Check: PASSED

All 7 key files verified present. Both task commits (76a3ae4, b6464a8) verified in git log.

---
*Phase: 04-permissions-notifications*
*Completed: 2026-04-02*
