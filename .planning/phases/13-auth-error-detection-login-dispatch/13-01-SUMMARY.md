---
phase: 13-auth-error-detection-login-dispatch
plan: 01
subsystem: server
tags: [websocket, auth, zellij, state-machine, protocol]

# Dependency graph
requires: []
provides:
  - Auth error detection in relay-server (AUTH_ERROR_PATTERN regex)
  - Automatic /login dispatch on auth failure
  - Recovery state machine (authRecoveryState Map)
  - AUTH_REQUIRED WebSocket message type (server + app protocol)
affects: [14-oauth-url-extraction, 15-app-auth-recovery-ui]

# Tech tracking
tech-stack:
  added: []
  patterns: [auth recovery state machine, screen scan on status transition]

key-files:
  created: []
  modified:
    - server/relay-server.cjs
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/RelayMessage.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayMessageType.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/RelayMessageParser.kt
    - androidApp/src/main/java/dev/heyduk/relay/service/WebSocketService.kt

key-decisions:
  - "Auth scan triggered only on working->ready transitions, not every poll cycle"
  - "30s cooldown per session prevents hammering zellij with screen dumps"
  - "5-minute recovery timeout allows re-detection after failed recovery"
  - "AUTH_REQUIRED added as non-chat message type (DB-skipped)"

patterns-established:
  - "Auth recovery state machine: detected -> login_sent -> (waiting_url -> recovered | timeout)"
  - "Screen scan on status transition pattern for post-hoc analysis"

requirements-completed: [AUTH-01, AUTH-02]

# Metrics
duration: 2min
completed: 2026-04-07
---

# Phase 13 Plan 01: Auth Error Detection & Login Dispatch Summary

**Server-side auth error detection with regex pattern matching on working->ready transitions, automatic /login dispatch, recovery state machine with 30s cooldown and 5-min timeout, plus AUTH_REQUIRED app protocol support**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-07T12:26:48Z
- **Completed:** 2026-04-07T12:28:41Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Auth error detection scans terminal output when sessions transition from working to ready
- Automatic /login dispatch via existing dispatchCommand() infrastructure
- Recovery state machine prevents duplicate dispatch (30s cooldown, 5-min timeout, success detection)
- AUTH_REQUIRED message type registered in all 4 convention places (DTO, domain, parser, DB-skip)
- Server restart verified and running

## Task Commits

Each task was committed atomically:

1. **Task 1: Auth error detection, recovery state machine, and /login dispatch in relay-server** - `b249bbe` (feat)
2. **Task 2: Add AUTH_REQUIRED message type to app protocol (4-place convention)** - `a7daefc` (feat)

## Files Created/Modified
- `server/relay-server.cjs` - Auth recovery state, scanForAuthError(), timeout checks, cleanup
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/RelayMessage.kt` - AUTH_REQUIRED DTO enum
- `shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayMessageType.kt` - AUTH_REQUIRED domain enum
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/RelayMessageParser.kt` - AUTH_REQUIRED toDomain mapping
- `androidApp/src/main/java/dev/heyduk/relay/service/WebSocketService.kt` - AUTH_REQUIRED in DB-skip list

## Decisions Made
- Auth scan triggered only on working->ready transitions (not every poll cycle) to minimize zellij overhead
- 30s cooldown per session prevents repeated screen dumps during rapid status changes
- 5-minute recovery timeout resets state for re-detection if recovery fails
- AUTH_REQUIRED is a signaling message (not persisted to chat DB)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Auth detection and /login dispatch are operational
- AUTH_REQUIRED message reaches the app (ready for Phase 14: OAuth URL extraction)
- Recovery state machine tracks lifecycle for Phase 15: App-side auth recovery UI

---
*Phase: 13-auth-error-detection-login-dispatch*
*Completed: 2026-04-07*
