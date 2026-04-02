# Phase 2: Session Discovery & Status - Context

**Gathered:** 2026-04-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Build the session-aware UI that shows all active zellij-claude sessions with live status indicators and enables session management commands. This is the primary screen users see — the session list with status dashboard. Includes drawer navigation, command input, and Material 3 dark mode theming.

</domain>

<decisions>
## Implementation Decisions

### Session List Layout
- Vertical list with Material 3 Cards — each session as a Card showing kürzel, status badge, last activity
- Status visualization via color-coded Chips: Working=Blue (animated), Waiting=Orange, Ready=Green, Shell=Gray
- Pull-to-refresh AND a FAB with refresh icon as fallback for session list refresh
- Drawer navigation (hamburger menu) with session list — scales better with many sessions than tabs

### Session Commands UX
- Text input field at bottom bar (persistent, like a chat input) for slash commands
- App auto-prefixes `@kürzel` when a session is selected — user only types the command/text
- `/last` output displayed as inline expandable section within the session card

### Dark Mode & Theming
- Follow system dark mode setting via Material 3 `isSystemInDarkTheme()` — no manual toggle
- Material 3 Dynamic Color (Android 12+) — derive colors from wallpaper, fallback to custom theme

### Claude's Discretion
- Exact Card layout and spacing
- Animation details for "working" status
- Drawer menu implementation details
- Error state presentation

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `StatusScreen.kt` and `StatusViewModel.kt` from Phase 1 — basic status display, can be evolved into full session list
- `RelayNavGraph.kt` — existing navigation setup, add drawer and session detail routes
- `TelegramRepository` — already provides `getRecentMessages()` and `sendCommand()` 
- `SessionStatus` enum — Working, Waiting, Ready, Shell already defined in domain model
- `RelayUpdate` domain model — contains session kürzel and message type

### Established Patterns
- MVVM with Compose + StateFlow (from Phase 1)
- Koin DI with named qualifiers
- Repository pattern for data access
- Coroutines/Flow for async operations

### Integration Points
- `TelegramRepository.sendCommand()` — for sending `/ls`, `/last`, `/open`, `/goto`, `/rename`
- `TelegramPoller` emits updates via SharedFlow — session status derived from incoming messages
- `MainActivity.kt` — entry point, needs drawer integration
- Parse `/ls` response to extract session list with statuses

</code_context>

<specifics>
## Specific Ideas

- The `/ls` command response from zellij-claude returns a formatted list like `@kürzel  status  (active)` — parse this into the session list
- When user selects a session from the drawer or list, the command input auto-prefixes with `@kürzel`
- Session cards should show: kürzel (bold), status chip (color-coded), time since last activity
- The existing StatusScreen from Phase 1 becomes the "no session selected" / overview screen

</specifics>

<deferred>
## Deferred Ideas

- Multi-session dashboard with grid/card view (v2 — SESS-06)
- Session-aware auto-routing without manual selection (v2 — SESS-07)

</deferred>
