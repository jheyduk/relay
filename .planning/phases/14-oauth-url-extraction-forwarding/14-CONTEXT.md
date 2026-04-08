# Phase 14: OAuth URL Extraction & Forwarding - Context

**Gathered:** 2026-04-08
**Status:** Ready for planning

<domain>
## Phase Boundary

Server extracts the OAuth authorization URL from terminal output after `/login` is dispatched (Phase 13) and forwards it to the app via WebSocket. Also handles timeout when URL is not found within the recovery window.

</domain>

<decisions>
## Implementation Decisions

### URL Detection Mechanism
- Periodic screen scan while recovery state is `login_sent` — poll at 3s active interval
- URL pattern: `/(https:\/\/(?:console\.anthropic\.com|claude\.ai)\/oauth\S+)/i`
- State transition: `login_sent` → `waiting_url` → `url_sent` after sending to app
- New WebSocket message: `{type: 'auth_url', session: kuerzel, url: extractedUrl}`

### Protocol & Integration
- `auth_url` as distinct message type (not overloading `auth_required`)
- On 5-min timeout: send `{type: 'auth_timeout', session: kuerzel}` to app, reset recovery state
- URL scan logic in `startStatusPolling()` — check `authRecoveryState` each cycle when state is `login_sent`
- Kotlin 4-place protocol pattern for both `auth_url` and `auth_timeout`: DTO enum, domain enum, parser mapping, DB-skip list

### Claude's Discretion
- Exact regex boundary handling for URL extraction
- Error logging verbosity for URL scan events

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `authRecoveryState` Map from Phase 13 — already tracks per-session recovery lifecycle
- `scanForAuthError()` — pattern for screen scanning in polling loop
- `getLastResponses(kuerzel, 1)` — returns string of terminal output (NOT array — fixed in bugfix c090e09)
- `dumpScreen()` / `findPaneForTab()` — zellij screen dump primitives

### Established Patterns
- Phase 13 auth detection triggers on working→ready transition, sets state to `detected` then `login_sent`
- `AUTH_SCAN_COOLDOWN` = 30s per session
- `AUTH_RECOVERY_TIMEOUT` = 5 minutes
- 4-place protocol convention: DTO enum, domain enum, parser, DB-skip list

### Integration Points
- `authRecoveryState` Map — extend state machine with `url_sent` state
- `startStatusPolling()` — add URL scan when state is `login_sent`
- WebSocket broadcast — new `auth_url` and `auth_timeout` message types

</code_context>

<specifics>
## Specific Ideas

- Important: `getLastResponses()` returns a string, not array (bug fixed in Phase 13 post-execution)
- OAuth URLs from Claude Code follow pattern: `https://console.anthropic.com/oauth/...` or `https://claude.ai/oauth/...`
- URL scan should run at 3s intervals (active polling) since user is actively waiting

</specifics>

<deferred>
## Deferred Ideas

- App-side UI for auth recovery (Phase 15)
- Auth code paste-back to terminal (Phase 15)

</deferred>
