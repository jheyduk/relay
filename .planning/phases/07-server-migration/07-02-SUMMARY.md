---
phase: 07-server-migration
plan: 02
subsystem: server
tags: [nodejs, hooks, ipc, unix-socket, relay]

requires:
  - phase: 07-01
    provides: relay-server.cjs in server/
provides:
  - send-relay.cjs shared IPC utility (sendRelay, getKuerzel)
  - 4 Claude Code hooks migrated to relay-only (session-start, session-stop, permission-notify, ask-notify)
  - reconcile-tabs.cjs tab cleanup utility
affects: [07-03, 08-interactive-controls]

tech-stack:
  added: []
  patterns: [unix-socket-ipc-for-hooks, relay-only-notifications]

key-files:
  created:
    - server/hooks/send-relay.cjs
    - server/hooks/reconcile-tabs.cjs
    - server/hooks/session-start.cjs
    - server/hooks/session-stop.cjs
    - server/hooks/permission-notify.cjs
    - server/hooks/ask-notify.cjs
  modified: []

key-decisions:
  - "ASCII function name getKuerzel instead of getKuerzel with umlaut for cross-platform safety"
  - "ask-notify includes question_data struct for Phase 8 interactive UI rendering"
  - "Removed Telegram fallback entirely -- relay-only for all notifications"

patterns-established:
  - "Hook IPC pattern: require send-relay.cjs, call sendRelay() with typed JSON payload"
  - "No __relay flag needed -- all payloads are relay-native"

requirements-completed: [SERV-02]

duration: 2min
completed: 2026-04-03
---

# Phase 07 Plan 02: Hook Migration Summary

**6 hook files migrated to server/hooks/ with standalone Unix socket IPC -- zero Telegram dependencies, relay-only notifications**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-03T16:36:54Z
- **Completed:** 2026-04-03T16:38:43Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- Created send-relay.cjs with sendRelay() Unix socket IPC and getKuerzel() tab lookup -- zero npm dependencies
- Migrated all 4 hooks (session-start, session-stop, permission-notify, ask-notify) to relay-only
- Removed all Telegram code: send(), sendWithButtons(), enforceTelegramSingleton(), getLastResponse(), truncate()
- Added question_data struct to ask-notify for Phase 8 interactive control rendering

## Task Commits

Each task was committed atomically:

1. **Task 1: Create send-relay.cjs shared utility and reconcile-tabs.cjs** - `bf54b2b` (feat)
2. **Task 2: Migrate all four hooks to server/hooks/ with relay-only logic** - `10bac09` (feat)

## Files Created/Modified
- `server/hooks/send-relay.cjs` - Standalone relay IPC: sendRelay() via Unix socket, getKuerzel() tab name lookup
- `server/hooks/reconcile-tabs.cjs` - Tab file cleanup utility (stale /tmp file reconciliation)
- `server/hooks/session-start.cjs` - Session start: tab caching, reconcile, ensureRelayServer (path: ../relay-server.cjs)
- `server/hooks/session-stop.cjs` - Session stop: relay completion notification, stopRelayServerIfEmpty
- `server/hooks/permission-notify.cjs` - Permission requests via relay with tool_details
- `server/hooks/ask-notify.cjs` - AskUserQuestion via relay with question_data for interactive UI

## Decisions Made
- Used ASCII `getKuerzel` instead of `getKuerzel` with umlaut for cross-platform safety
- Included `question_data` struct in ask-notify relay payload (question, header, multiSelect, options) to enable Phase 8 interactive controls without re-touching this hook
- Removed Telegram fallback entirely -- the old sendRelay() fell back to Telegram after 30s disconnect, we drop that completely
- Removed formatToolDetail emoji markers in permission-notify (Telegram-era formatting)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 6 hook files ready in server/hooks/
- session-start.cjs correctly references ../relay-server.cjs (depends on 07-01 placing relay-server.cjs in server/)
- ask-notify.cjs question_data ready for Phase 8 interactive control implementation
- No blockers for 07-03

---
*Phase: 07-server-migration*
*Completed: 2026-04-03*
