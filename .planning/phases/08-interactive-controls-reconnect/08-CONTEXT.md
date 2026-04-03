# Phase 8: Interactive Controls & Reconnect - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can fully answer AskUserQuestion prompts from the app (single choice, multiple choice, free text) and see existing sessions after reconnecting. The app sends structured keystroke sequences to the correct Zellij pane via the relay-server.

</domain>

<decisions>
## Implementation Decisions

### Keystroke Protocol
- AskUserQuestion hooks already send structured JSON with options, multiSelect flag, and option count to the app
- Server receives answer from app and translates to keystroke sequences sent via `zellij action write-chars`
- Single choice: send number key (e.g. "1") + Enter
- Multiple choice: send number keys to toggle selections, then Down-arrow keys to reach Submit, then Enter
- Free text ("Other"): Down-arrow keys past all options to reach "Other", Enter to select, type text, Enter to confirm
- The number of Down presses depends on the option count — app must include this in the answer payload

### Answer Protocol (App → Server)
- New WebSocket action: `{action: "answer", kuerzel: "session", type: "single|multi|text", selections: [1,3], text: "free text", option_count: 4}`
- Server computes keystroke sequence from the structured answer
- Server sends keystrokes to the correct Zellij pane via `zellij action write-chars`

### Session Reconnect Sync
- When a WebSocket client connects, relay-server reads all /tmp/zellij-claude-tab-* files
- Sends a batch of status messages (one per active session) immediately after auth
- App receives sessions without needing to trigger /ls manually

### Claude's Discretion
- Exact timing/delays between keystrokes (may need small delays for TUI to process)
- Whether to validate option_count against actual prompt state
- Error handling for stale/expired prompts
- UI design for the answer selection composables in the app

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `server/hooks/ask-notify.cjs` — already sends structured question data with options to the app
- `server/relay-server.cjs` — already has `zellij action write-chars` dispatch
- `QuestionCard.kt` — existing composable for displaying questions with option buttons
- `PermissionCard.kt` — existing composable for Allow/Deny

### Established Patterns
- WebSocket message protocol: `{action: "command"|"raw_command", ...}`
- ChatViewModel handles callback sending via RelayRepository
- Koin DI for ViewModels with parameters

### Integration Points
- `server/relay-server.cjs` — needs new `answer` action handler with keystroke computation
- `WebSocketClient.kt` — needs `sendAnswer()` method
- `RelayRepository.kt` — needs answer forwarding
- `ChatViewModel.kt` — needs to wire QuestionCard option taps to sendAnswer
- `server/relay-server.cjs` — needs session sync on new client connection

</code_context>

<specifics>
## Specific Ideas

- The ask-notify hook should include `question_data` with `options[]`, `multiSelect`, `header` in the relay message so the app can render proper selection UI
- Permission Allow/Deny already works via text callback — keep that path, don't change it
- Reconnect sync should happen server-side (server pushes sessions), not client-side (app requests /ls)

</specifics>

<deferred>
## Deferred Ideas

- Prompt state validation (checking if prompt is still active before sending keystrokes)
- Configurable keystroke delays
- Support for AskUserQuestion previews in the app

</deferred>
