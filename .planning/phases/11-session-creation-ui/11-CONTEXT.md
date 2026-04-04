# Phase 11: Session Creation UI - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Users can create a new Claude Code session from the app by selecting a project directory via fuzzy search and confirming session parameters. This phase adds the app-side WebSocket protocol methods (send + receive), the fullscreen dialog UI, fuzzy matching, and the FAB entry point. Server protocol (Phase 10) is already implemented.

</domain>

<decisions>
## Implementation Decisions

### Entry Points
- Single FAB (+) replaces the existing Refresh FAB on SessionListScreen (pull-to-refresh already handles refresh)
- SESS-02 (NavigationDrawer entry) is dropped — drawer was removed in v1.2. FAB is the only entry point.
- Update REQUIREMENTS.md to mark SESS-02 as superseded

### Dialog & Flow
- Fullscreen dialog for directory picker (per approved design spec)
- Centered CircularProgressIndicator while fetching directories from server
- Two phases within the dialog: directory selection → confirmation

### Custom Path & Error Handling
- "Custom Path..." as a fixed item at the bottom of the directory list (always visible)
- Snackbar for error display within the dialog — user stays in dialog and can retry
- Inline error text below kuerzel field for validation errors (e.g. empty kuerzel)

### Claude's Discretion
- Fuzzy match scoring algorithm details (consecutive matches, word boundaries, case insensitivity)
- ViewModel structure: separate CreateSessionViewModel or extend SessionListViewModel
- Exact Material 3 component choices within the design spec constraints

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SessionListScreen.kt` — existing Scaffold with TopAppBar, FAB, pull-to-refresh
- `SessionListViewModel.kt` — session management, DataStore favorites, command routing
- `WebSocketClient.kt` — existing sendCommand, sendRawCommand, sendAnswer, sendAudio, sendAttachment methods
- `RelayMessageParser.kt` — JSON parsing with ignoreUnknownKeys, type-based routing
- `RelayMessage.kt` DTO — extensible with new optional fields
- `RelayUpdate.kt` — domain model for parsed messages

### Established Patterns
- WebSocket send methods: `sendXxx()` on WebSocketClient, delegated through RelayRepository
- DTO → Domain mapping in RelayMessageParser with extension functions
- StateFlow + collectAsStateWithLifecycle for Compose state observation
- Koin DI for ViewModel injection

### Integration Points
- `RelayMessageTypeDto` enum needs `DIRECTORY_LIST` and `SESSION_CREATED` variants
- `RelayMessage` DTO needs optional `directories`, `defaultFlags`, `success`, `error` fields
- `WebSocketClient` needs `sendListDirectories()` and `sendCreateSession()` methods
- `RelayNavGraph.kt` — navigation wiring for new dialog
- `SessionListScreen.kt` — FAB replacement

</code_context>

<specifics>
## Specific Ideas

- Design spec approved at `docs/superpowers/specs/2026-04-04-session-creation-design.md`
- FZF-style fuzzy match: consecutive char matches score higher, start-of-word bonus, case insensitive
- Pre-fill kuerzel from directory basename
- Default flags toggle from server's `defaultFlags` config value
- Integration checker found: RelayMessageParser silently drops unknown types — MUST add enum variants before UI can receive responses

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
