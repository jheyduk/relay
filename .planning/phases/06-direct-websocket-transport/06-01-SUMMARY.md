---
phase: 06-direct-websocket-transport
plan: 01
subsystem: transport
tags: [websocket, ws, mdns, unix-socket, ipc, node]

requires:
  - phase: 01-05
    provides: "Existing zellij-claude hook infrastructure (telegram-helper, session-start, session-stop)"
provides:
  - "WebSocket server singleton (relay-server.cjs) with token auth, Unix socket IPC, mDNS"
  - "WebSocket-first message routing in sendRelay() with Telegram fallback"
  - "Relay server lifecycle management (start on first session, stop on last)"
affects: [06-02, 06-03, 06-04, android-app-websocket-client]

tech-stack:
  added: [ws]
  patterns: [unix-socket-ipc, mdns-discovery, singleton-process-management]

key-files:
  created:
    - /Users/jheyduk/prj/zellij-claude/hooks/relay-server.cjs
  modified:
    - /Users/jheyduk/prj/zellij-claude/hooks/telegram-helper.cjs
    - /Users/jheyduk/prj/zellij-claude/hooks/session-start.cjs
    - /Users/jheyduk/prj/zellij-claude/hooks/session-stop.cjs
    - /Users/jheyduk/prj/zellij-claude/package.json

key-decisions:
  - "Unix domain socket for hook-to-server IPC instead of HTTP localhost"
  - "dns-sd CLI for mDNS advertisement (macOS built-in, no npm dependency)"
  - "Telegram fallback only for permission/completion after 30s disconnect threshold"

patterns-established:
  - "Singleton process pattern: PID file check + detached spawn via ensureRelayServer()"
  - "IPC pattern: Unix socket at /tmp/zellij-claude-relay.sock with JSON request/response"
  - "Fallback pattern: WebSocket-first routing with conditional Telegram fallback"

requirements-completed: [R-06-01, R-06-02, R-06-04, R-06-07]

duration: 3min
completed: 2026-04-03
---

# Phase 6 Plan 1: Mac-Side WebSocket Server Summary

**WebSocket relay server with Unix socket IPC, mDNS discovery, and hook routing updated to WebSocket-first with Telegram fallback after 30s disconnect**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-03T09:24:12Z
- **Completed:** 2026-04-03T09:26:43Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- Created relay-server.cjs: WebSocket server with token auth, Unix socket IPC, mDNS advertisement, PID management, and graceful shutdown
- Updated sendRelay() to route via WebSocket first, falling back to Telegram for permission/completion after 30s disconnect
- Session lifecycle: relay-server starts on first session-start, stops when last session ends

## Task Commits

Each task was committed atomically:

1. **Task 1: Create relay-server.cjs WebSocket server with Unix socket IPC and mDNS** - `0f292b3` (feat)
2. **Task 2: Update hook routing -- sendRelay via WebSocket with Telegram fallback** - `09b2c4b` (feat)

## Files Created/Modified
- `hooks/relay-server.cjs` - Standalone WebSocket server with auth, Unix socket IPC, mDNS advertisement, PID management
- `hooks/telegram-helper.cjs` - Added sendViaWebSocket(), sendViaTelegram(), async sendRelay() with fallback logic
- `hooks/session-start.cjs` - Added ensureRelayServer() singleton spawn before Telegram enforcement
- `hooks/session-stop.cjs` - Added stopRelayServerIfEmpty() to SIGTERM relay server when no sessions remain
- `package.json` - Added ws dependency

## Decisions Made
- Unix domain socket for hook IPC: faster than HTTP localhost, no port conflicts, natural for same-machine communication
- dns-sd CLI for mDNS: macOS built-in binary, avoids adding another npm dependency (like bonjour/mdns)
- 30s disconnect threshold for Telegram fallback: avoids spamming Telegram during brief disconnects (e.g., network switch)
- Auto-generate relay.json config with secret if missing: zero-config first run experience

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - relay.json auto-generates with a random secret on first relay-server start.

## Next Phase Readiness
- relay-server.cjs ready for Android app WebSocket client connections
- Unix socket IPC operational for all hooks
- mDNS advertisement ready for Android NsdManager discovery
- Plans 06-02 through 06-04 can proceed (Android client, protocol, command dispatch)

---
*Phase: 06-direct-websocket-transport*
*Completed: 2026-04-03*
