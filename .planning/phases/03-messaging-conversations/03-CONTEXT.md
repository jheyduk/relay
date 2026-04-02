# Phase 3: Messaging & Conversations - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Build the per-session chat interface where users can send text messages and view conversation history that persists across app restarts. This transforms Relay from a session dashboard into a usable communication tool.

</domain>

<decisions>
## Implementation Decisions

### Chat UI Layout
- LazyColumn reverse — newest messages at bottom, like any chat app
- Message bubbles with sender distinction (User outgoing = right-aligned, Claude incoming = left-aligned)
- Tap on Session Card opens dedicated Chat Screen for that session. Back button returns to session list
- Bottom bar CommandInput from Phase 2 reused — in chat context, plain text sends as `@kürzel message` automatically

### Message Persistence
- Store ALL messages — incoming (Claude responses) + outgoing (user commands). Per session via kürzel
- SQLDelight (NOT Room) — per KMP architecture decision. Success criteria mentions "Room" but project uses SQLDelight
- Extend existing Messages.sq schema: add `is_outgoing` (Boolean), `message_type` (text enum) fields
- Offline: show cached messages without connection. Queue outgoing messages until connection restored

### Claude's Discretion
- Exact message bubble styling and animations
- Timestamp formatting and grouping
- Queue implementation details for offline messages
- Error state for failed sends

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Messages.sq` — SQLDelight schema already has session_kuerzel, content, timestamp, update_id, message_type
- `TelegramRepository.sendCommand()` — sends `@kürzel message` to command bot
- `TelegramRepository.updates` SharedFlow — incoming messages stream
- `CommandInput.kt` — text input composable from Phase 2
- `SessionListScreen.kt` — has session selection, can navigate to chat

### Established Patterns
- MVVM with StateFlow + Compose
- Koin DI with named qualifiers
- Repository pattern, SQLDelight for persistence
- Navigation via RelayNavGraph

### Integration Points
- `RelayNavGraph.kt` — add chat/{kuerzel} route
- `SessionCard` — add onTap to navigate to chat
- `SharedModule.kt` — register ChatRepository/MessageRepository
- `AndroidModule.kt` — register ChatViewModel

</code_context>

<specifics>
## Specific Ideas

- Messages should show timestamp, sender indicator, and content
- Long messages should be scrollable/expandable
- Send button should show loading state while message is being sent
- Empty state for new sessions with no messages yet

</specifics>

<deferred>
## Deferred Ideas

- Unified chat stream with voice messages (v2 — VOIC-05)
- Message search across sessions (v2 — UI-03)

</deferred>
