---
phase: 07-server-migration
plan: 03
subsystem: infra
tags: [nodejs, hooks, claude-code, settings, installer]

requires:
  - phase: 07-01
    provides: relay-server.cjs standalone server in server/
  - phase: 07-02
    provides: hooks migrated to server/hooks/
provides:
  - Hook installer (server/install.cjs) that registers relay hooks in Claude Code settings.json
  - Automatic removal of old zellij-claude hook entries for migrated hooks
  - npm install-hooks script for convenience
affects: [08-interactive-controls]

tech-stack:
  added: []
  patterns: [idempotent-installer, settings-json-management]

key-files:
  created: [server/install.cjs]
  modified: [server/package.json]

key-decisions:
  - "CommonJS for install.cjs consistent with all server/ files"
  - "Only remove zellij-claude hooks that map to migrated hooks — pretool-cache stays"
  - "Idempotent: checks for existing relay hooks before adding"

patterns-established:
  - "Hook installer pattern: read settings.json, filter old, add new, write back"

requirements-completed: [SERV-05]

duration: 1min
completed: 2026-04-03
---

# Phase 7 Plan 3: Hook Installer Summary

**Hook installer that registers relay server hooks in Claude Code settings.json and cleans up old zellij-claude entries**

## Performance

- **Duration:** 1 min
- **Started:** 2026-04-03T16:40:39Z
- **Completed:** 2026-04-03T16:41:50Z
- **Tasks:** 1 (+ 1 auto-approved checkpoint)
- **Files modified:** 2

## Accomplishments
- Created server/install.cjs that manages Claude Code hook registration for relay
- Removes old zellij-claude hook entries for the 4 migrated hooks (session-start, session-stop, permission-notify, ask-notify)
- Preserves pretool-cache and other non-relay zellij-claude hooks
- Idempotent — running twice creates no duplicates
- Added install-hooks script to server/package.json

## Task Commits

Each task was committed atomically:

1. **Task 1: Create server/install.cjs hook installer** - `cb5eeb9` (feat)
2. **Task 2: Verify hook installation and relay-server startup** - auto-approved checkpoint

**Plan metadata:** (pending)

## Files Created/Modified
- `server/install.cjs` - Hook installer: reads/writes ~/.claude/settings.json, removes old zellij-claude hooks, adds relay hooks
- `server/package.json` - Added install-hooks script

## Decisions Made
- Used CommonJS (require) consistent with all other server/ files — zellij-claude's install.js is ESM but relay server is CJS
- Only removes zellij-claude hooks that correspond to the 4 migrated hooks — pretool-cache stays in zellij-claude
- Checks for existing relay hooks by matching both "relay" and the hook filename in command strings

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Hook installer complete, ready for end-to-end testing
- All 3 plans of Phase 07 (server-migration) now complete
- Ready for Phase 08 (interactive-controls) which builds on the relay server hooks

## Self-Check: PASSED

- FOUND: server/install.cjs
- FOUND: .planning/phases/07-server-migration/07-03-SUMMARY.md
- FOUND: cb5eeb9

---
*Phase: 07-server-migration*
*Completed: 2026-04-03*
