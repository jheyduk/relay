# Phase 15: App-Side Auth Recovery UI - Context

**Gathered:** 2026-04-08
**Status:** Ready for planning

<domain>
## Phase Boundary

Android app UI for the complete OAuth re-authentication flow: display auth URL with browser-open button, provide auth code input field, dispatch code to terminal, and show recovery status lifecycle. Consumes `auth_required`, `auth_url`, and `auth_timeout` WebSocket messages from Phases 13-14.

</domain>

<decisions>
## Implementation Decisions

### Auth URL Handling
- Chat bubble with clickable URL + "Open in Browser" button — consistent with existing chat UI style
- Android `Intent.ACTION_VIEW` via `Uri.parse(url)` to open in default browser
- Scoped to the session that triggered auth recovery (not global)

### Auth Code Paste-Back
- Dedicated auth card in the chat with text input field — appears after URL is opened
- New WebSocket action `auth_code` with `{action: 'auth_code', kuerzel, code}` → server uses `dispatchCommand(kuerzel, code)` to write-chars into terminal
- Manual paste only — no clipboard auto-detection (avoids permission complexity)

### Recovery Status UI
- Colored status chip in chat (like existing session status bar) showing lifecycle: "Auth Required" → "Login Sent" → "Open URL" → "Enter Code" → "Recovered"
- Status shown in the affected session's chat view, inline with messages
- On `auth_timeout`: show "Auth Recovery Timed Out — Retry?" card with button to re-trigger
- On recovery success: status transitions to "Recovered", card auto-dismisses after 5s

### Claude's Discretion
- Exact Material 3 color scheme for auth status chips
- Animation details for card auto-dismiss
- Exact layout of auth code input card

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PermissionCard` / `QuestionCard` composables — existing card patterns in chat
- `ChatScreen` with type-aware rendering dispatch (RelayMessageType → composable)
- `SessionStatusBar` — colored status indicator already in use
- `RelayUpdate.authUrl` field — available from Phase 14

### Established Patterns
- Chat messages dispatched by type in ChatScreen composable
- Cards use Material 3 ElevatedCard with rounded corners
- Status shown via colored bars (working=blue, waiting=amber, ready=green)
- WebSocket actions sent via `RelayRepository.sendAction()`

### Integration Points
- `ChatScreen` — add AUTH_REQUIRED / AUTH_URL / AUTH_TIMEOUT card rendering
- `ChatViewModel` — handle auth state, expose auth recovery lifecycle
- `RelayRepository` — add `sendAuthCode(kuerzel, code)` method
- `WebSocketClient` — handle new action type `auth_code`
- Server `relay-server.cjs` — handle `auth_code` action, dispatch to terminal

</code_context>

<specifics>
## Specific Ideas

- Auth card should look similar to existing PermissionCard but with different color scheme (orange/amber for auth)
- The "Open in Browser" button should be prominent (filled button, not text-only)
- Auth code input should have a "Send" button that disables after first tap to prevent double-dispatch
- Recovery state should be tracked in ViewModel, not persisted to DB (transient)

</specifics>

<deferred>
## Deferred Ideas

- Push notification for auth recovery (could add later)
- Session list badge showing auth-needed sessions

</deferred>
