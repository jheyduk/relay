# Phase 13: Auth Error Detection & Login Dispatch - Context

**Gathered:** 2026-04-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Server-side detection of authentication failures in Claude Code terminal output and automatic dispatch of `/login` command into the affected session. This phase covers detection only — URL extraction and app-side UI are in subsequent phases.

</domain>

<decisions>
## Implementation Decisions

### Detection Mechanism
- Scan via `dump-screen` on status transition to "ready" — only checks when a session stops working, not every poll cycle
- Add detection logic in `startStatusPolling()` — trigger screen scan when status changes from "working" → "ready"
- Broad regex matching: `/(session has expired|oauth token (has expired|revoked)|not logged in|please run \/login|API Error: 401|authentication_error)/i`
- Max one screen scan per 30s per session (cooldown) to avoid hammering zellij

### Login Dispatch
- Use existing `dispatchCommand(kuerzel, '/login')` — reuses write-chars + Enter pattern
- Dispatch immediately after detection — no artificial delay
- Per-session `authRecoveryState` Map tracking recovery lifecycle (detected/login_sent/waiting_url/recovered)
- Send `{type: 'auth_required', session: kuerzel, error: matchedPattern}` to app immediately on detection

### Recovery State Management
- 5 minute timeout for recovery flow
- On timeout: reset state, allow re-detection on next scan
- Detect successful recovery when status transitions back to "working" → reset recovery state
- In-memory Map only — no persistence across server restart

### Claude's Discretion
- Exact placement of auth scanning within the polling loop
- Error logging verbosity for auth detection events

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `dispatchCommand(kuerzel, message)` — existing command dispatch to zellij panes
- `startStatusPolling()` — polling loop with status change detection, already tracks `lastStatusMap`
- `screen-parse.cjs` → `extractResponses()` — terminal output parser
- `dumpScreen(sessionId, paneId)` — existing zellij screen dump function
- `findPaneForTab(sessionId, kuerzel)` — pane resolution for screen dump

### Established Patterns
- Status tracking via `lastStatusMap: Map<kuerzel, status>`
- Adaptive polling: 3s active, 30s idle
- Poll suppression after command dispatch (10s)
- WebSocket broadcast via `appSocket.send(JSON.stringify(msg))`
- Hook-style JSON messages with `{type, session, ...}`

### Integration Points
- `startStatusPolling()` — add auth check on status transition
- WebSocket message handler — new `auth_required` outgoing message type
- `dispatchCommand()` — reuse for `/login` dispatch

</code_context>

<specifics>
## Specific Ideas

- Auth error patterns researched earlier in this session — comprehensive list from Claude Code source code analysis
- Key patterns: "Your session has expired", "OAuth token has expired", "Please run /login", "API Error: 401"
- Recovery state machine: detected → login_sent → waiting_url → recovered (or timeout)

</specifics>

<deferred>
## Deferred Ideas

- OAuth URL extraction from terminal output (Phase 14)
- App-side auth recovery UI (Phase 15)
- Proactive session expiry warning before it happens (Out of Scope)

</deferred>
