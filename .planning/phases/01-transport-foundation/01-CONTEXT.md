# Phase 1: Transport & Foundation - Context

**Gathered:** 2026-04-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Establish reliable bidirectional communication between the mobile app and zellij-claude on the Mac via Telegram Bot API. Solve the 409 single-consumer conflict by using a dedicated second bot token for Relay. Set up the KMP project scaffolding with shared module (Ktor client, kotlinx.serialization, SQLDelight, Coroutines/Flow) and Android app module (Compose UI, Hilt DI, Foreground Service for polling).

</domain>

<decisions>
## Implementation Decisions

### 409 Conflict Resolution
- Use a dedicated second Telegram bot token for Relay — completely independent from the existing zellij-claude bot
- No coordination mechanism needed between the two bots — they operate independently
- Mac-side zellij-claude hooks send notifications to both bot tokens in parallel (existing Telegram format to old bot, JSON to Relay bot)
- Relay sends commands to the existing zellij-claude bot (same format: `@kürzel message`, `/ls`, etc.) — @main session reads from that bot as before

### Message Protocol
- Mac-side sends structured JSON to the Relay bot (type, session, status, message, tool_details fields)
- Existing Telegram bot continues receiving emoji-formatted messages for human readability — system works without the app
- zellij-claude config gets a `relay_bot_token` entry in `~/.config/zellij-claude/telegram.json` — when not set, no Relay messages are sent
- Minimal zellij-claude changes required: hooks need dual-output support (both bot tokens, different formats)

### Message Persistence
- DataStore Preferences for polling offset (single Long value, no DB schema needed)
- Room DB for message storage (full conversation history)
- Message deduplication via update_id as Primary Key with IGNORE Conflict Strategy

### Foreground Service & Network
- Foreground Service type `dataSync` for continuous polling — persistent notification shows connection status
- ConnectivityManager Callback for network change detection + immediate reconnect
- Exponential backoff on errors: 1s → 2s → 4s → max 30s
- Service runs continuously in background — persistent notification shows "Connected" / "Reconnecting"
- Battery optimization warning shown during initial app setup

### KMP Architecture (NEW — added after discuss)
- KMP multi-module structure: `:shared` (common business logic), `:androidApp` (Compose UI + platform services), future `:iosApp`
- Shared module contains: Telegram API client (Ktor), message models, repositories, domain use cases
- Platform-specific via expect/actual: DataStore for offset persistence, platform-specific networking config
- SQLDelight instead of Room for KMP-compatible database (shared schema across platforms)
- Koin instead of Hilt for DI (Hilt is Android-only, Koin supports KMP)

### Claude's Discretion
- Exact KMP module boundaries and package naming
- Exact Ktor client configuration (timeouts, retry policies)
- SQLDelight schema for messages
- JSON serialization format details (kotlinx.serialization)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- zellij-claude `hooks/telegram-helper.cjs` — reference implementation for Telegram Bot API calls (sendMessage, sendWithButtons)
- zellij-claude `src/dispatch.js` — message format reference (parseCommand, deriveStatus patterns)
- zellij-claude `hooks/permission-notify.cjs` — reference for permission message format and callback patterns
- zellij-claude `hooks/session-stop.cjs` — reference for completion notification format

### Established Patterns
- Bot API via direct HTTPS calls (curl in zellij-claude, Ktor in Relay)
- Session identification by kürzel (short name from directory basename)
- Status derived from pane title: spinner = working, permission keywords = waiting, else = ready
- Permission callbacks: `callback:allow:{kürzel}` / `callback:deny:{kürzel}`

### Integration Points
- `~/.config/zellij-claude/telegram.json` — will need `relay_bot_token` and `relay_chat_id` fields
- zellij-claude hooks (`telegram-helper.cjs`) — needs dual-send capability
- Telegram Bot API endpoints: getUpdates, sendMessage, answerCallbackQuery

</code_context>

<specifics>
## Specific Ideas

- The JSON format from Mac-side should include: `type` (status|response|permission|question|completion), `session` (kürzel), `status` (working|waiting|ready|shell), `message` (text content), `tool_details` (for permissions: command, file_path), `timestamp`
- zellij-claude changes should be backward-compatible: if `relay_bot_token` is not configured, behavior is identical to current

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
