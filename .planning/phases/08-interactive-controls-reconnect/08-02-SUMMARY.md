---
phase: 08-interactive-controls-reconnect
plan: 02
subsystem: ui, transport
tags: [compose, websocket, question-answer, kotlinx-serialization, material3]

# Dependency graph
requires:
  - phase: 08-01
    provides: "Reconnect sync and server answer handler"
  - phase: 07
    provides: "relay-server with direct Zellij dispatch"
provides:
  - "QuestionData DTO and domain models for structured question rendering"
  - "sendAnswer WebSocket transport with JSON payload"
  - "Interactive QuestionCard UI with single/multi/text answer modes"
  - "End-to-end answer pipeline from UI tap to server dispatch"
affects: [09-voice-pipeline]

# Tech tracking
tech-stack:
  added: []
  patterns: ["In-memory questionData cache for transient live data", "buildJsonObject for heterogeneous JSON payloads"]

key-files:
  created: []
  modified:
    - "shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/RelayMessage.kt"
    - "shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/RelayMessageParser.kt"
    - "shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayUpdate.kt"
    - "shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/ChatMessage.kt"
    - "shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/WebSocketClient.kt"
    - "shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/RelayRepository.kt"
    - "shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/RelayRepositoryImpl.kt"
    - "shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepository.kt"
    - "shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepositoryImpl.kt"
    - "shared/src/commonMain/kotlin/dev/heyduk/relay/di/SharedModule.kt"
    - "androidApp/src/main/java/dev/heyduk/relay/service/WebSocketService.kt"
    - "androidApp/src/main/java/dev/heyduk/relay/presentation/chat/QuestionCard.kt"
    - "androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatViewModel.kt"
    - "androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatScreen.kt"

key-decisions:
  - "In-memory questionDataCache in ChatRepositoryImpl for transient question data (not persisted to DB)"
  - "Dual Koin registration (concrete + interface) for ChatRepositoryImpl to support cache access from WebSocketService"
  - "buildJsonObject for sendAnswer payload since kotlinx.serialization cannot encode Map<String, Any>"

patterns-established:
  - "Transient data cache: live WebSocket data not stored in DB, cached in-memory for active session"
  - "1-based option indexing matching server-side TUI keystroke mapping"

requirements-completed: [CTRL-01, CTRL-02, CTRL-03]

# Metrics
duration: 6min
completed: 2026-04-03
---

# Phase 08 Plan 02: Answer Protocol Summary

**Full AskUserQuestion answer pipeline from interactive QuestionCard UI through ViewModel/Repository/WebSocket to server, supporting single-choice, multi-choice, and free-text answer modes**

## Performance

- **Duration:** 6 min
- **Started:** 2026-04-03T16:57:51Z
- **Completed:** 2026-04-03T17:03:51Z
- **Tasks:** 3 (2 auto + 1 auto-approved checkpoint)
- **Files modified:** 14

## Accomplishments
- Added QuestionData DTOs and domain models with full deserialization from server JSON
- Implemented sendAnswer on WebSocketClient with structured JSON payload matching server expectations
- Overhauled QuestionCard UI to support three answer modes: single-choice (tap), multi-choice (toggle + submit), free-text (Other + TextField)
- Wired the full answer pipeline through ChatViewModel -> ChatRepository -> RelayRepository -> WebSocketClient

## Task Commits

Each task was committed atomically:

1. **Task 1: Add question_data to DTOs, domain model, parser, and sendAnswer to transport layer** - `ec048a6` (feat)
2. **Task 2: Overhaul QuestionCard UI and wire ChatViewModel for structured answers** - `4dde335` (feat)
3. **Task 3: Visual verification (auto-approved)** - no commit (checkpoint)

## Files Created/Modified
- `shared/.../dto/RelayMessage.kt` - Added QuestionDataDto, QuestionOptionDto, question_data field
- `shared/.../RelayMessageParser.kt` - Maps question_data DTO to domain QuestionData
- `shared/.../model/RelayUpdate.kt` - Added QuestionData, QuestionOption domain models
- `shared/.../model/ChatMessage.kt` - Added questionData field for UI consumption
- `shared/.../WebSocketClient.kt` - sendAnswer method with buildJsonObject payload
- `shared/.../RelayRepository.kt` - sendAnswer interface method
- `shared/.../RelayRepositoryImpl.kt` - sendAnswer delegation to WebSocketClient
- `shared/.../ChatRepository.kt` - answerQuestion interface method
- `shared/.../ChatRepositoryImpl.kt` - answerQuestion impl with DB marking and cache
- `shared/.../di/SharedModule.kt` - Dual Koin registration for ChatRepositoryImpl
- `androidApp/.../WebSocketService.kt` - Question data caching on incoming updates
- `androidApp/.../QuestionCard.kt` - Rewritten with single/multi/text modes
- `androidApp/.../ChatViewModel.kt` - Replaced answerQuestion with structured variant
- `androidApp/.../ChatScreen.kt` - Updated QuestionCard callback wiring

## Decisions Made
- Used in-memory questionDataCache rather than DB persistence for question data, since questions are only answerable while live
- Registered ChatRepositoryImpl as both concrete and interface type in Koin so WebSocketService can call cacheQuestionData()
- Used buildJsonObject from kotlinx.serialization.json for the sendAnswer payload since Map<String, Any> is not directly serializable

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added question data caching in WebSocketService**
- **Found during:** Task 1
- **Issue:** Plan described questionDataCache but didn't explicitly address where caching happens in the incoming message flow
- **Fix:** Added caching call in WebSocketService update collector, dual Koin registration for concrete type access
- **Files modified:** WebSocketService.kt, SharedModule.kt
- **Committed in:** ec048a6 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Essential wiring for question data to flow from WebSocket to UI. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All interactive controls (CTRL-01, CTRL-02, CTRL-03) are implemented
- Phase 08 is complete -- ready for Phase 09 (Mac-side voice pipeline)
- End-to-end verification pending real device testing

---
*Phase: 08-interactive-controls-reconnect*
*Completed: 2026-04-03*
