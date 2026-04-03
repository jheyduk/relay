# Phase 6: Direct WebSocket Transport - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Replace the Telegram Bot API transport with a direct WebSocket connection between Mac and Android app. The Mac runs a lightweight Node.js WebSocket server (singleton, started by zellij-claude hooks). The app discovers it via mDNS on local networks or a configured WireGuard IPv6 address when remote. Telegram is completely removed as app transport — it remains only as a Mac-side push notification fallback for when the app is not connected.

</domain>

<decisions>
## Implementation Decisions

### WebSocket Server Architecture
- Node.js with `ws` library — zellij-claude hooks are already Node.js/CJS, zero new runtime dependency
- Singleton process started by session-start.cjs — auto-starts on first session, auto-stops when no sessions remain (same pattern as Telegram singleton)
- Same JSON message format as current Relay messages (`{type, session, message, status, tool_details, timestamp, __relay}`) — zero parser changes in the app
- Shared secret token for auth — generated during setup, stored in `~/.config/zellij-claude/relay.json` on Mac and DataStore on Android

### Discovery & Connection
- mDNS/Bonjour service advertisement (`_relay._tcp`) for local network discovery — zero-config, app discovers automatically
- Configured WireGuard IPv6 address for remote access — stored in app settings, user enters once during setup
- Exponential backoff reconnect (1s→2s→4s→30s cap) with mDNS re-discovery on connection loss
- Switch to Telegram notification fallback after 60s disconnect
- Use most recently seen Mac when multiple mDNS results — single developer, one Mac

### Transport (App-Side) — Hard Cutover
- WebSocket only in the app — no Telegram polling, no TransportProvider abstraction
- Remove all Telegram bot token configuration from the app setup flow
- Remove TelegramPoller, TelegramApi (relay side), and all polling-related code
- PollingService becomes WebSocketService — foreground service maintains WebSocket connection
- No transport selection UI — only connection status indicator (connected/disconnected)

### Transport (Mac-Side Hook Routing)
- Config-driven in `~/.config/zellij-claude/relay.json` — hooks send messages over WebSocket to connected app
- Telegram used only for push notifications when app is disconnected (>30s)
- Only permission requests and completions sent via Telegram fallback — no status updates or responses

### Telegram Notification Fallback
- Mac-side WebSocket server tracks connection state (connected/disconnected boolean)
- When app disconnects for >30s, hooks fall back to Telegram for permission and completion notifications
- App does NOT need Telegram bot tokens — only the Mac needs the bot token for fallback
- Simplifies app setup: only WebSocket server address/secret needed

### Claude's Discretion
- Exact WebSocket server port selection strategy (fixed vs dynamic with mDNS advertisement)
- Reconnection timing details beyond the backoff cap
- Exact mDNS service record fields
- WebSocket ping/pong interval for keepalive

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `TelegramPoller` pattern (exponential backoff, SharedFlow emission) — reusable for WebSocket reconnection logic
- `RelayMessageParser` — reusable as-is since JSON format stays the same
- `RelayUpdate` domain model — unchanged
- `PollingService` foreground service pattern — adaptable to WebSocketService
- `telegram-helper.cjs` — `sendRelay()` function pattern reusable for WebSocket sending
- All existing hooks already produce the right JSON format

### Established Patterns
- Singleton process management in `session-start.cjs` (PID file, process detection)
- Koin DI with named qualifiers for transport instances
- DataStore for connection configuration persistence
- Network connectivity monitoring in PollingService (ConnectivityManager callback)

### Integration Points
- `~/.config/zellij-claude/relay.json` — new config file for WebSocket server settings
- `session-start.cjs` — needs to start/ensure WebSocket server singleton
- `session-stop.cjs` — needs to check if server should stop (no remaining sessions)
- `telegram-helper.cjs` — needs WebSocket send function alongside existing Telegram
- `permission-notify.cjs`, `ask-notify.cjs` — need WebSocket routing with Telegram fallback
- Android `SetupScreen` — needs new fields (server address/secret instead of bot tokens)
- Android `PollingService` → `WebSocketService` transformation

</code_context>

<specifics>
## Specific Ideas

- Telegram transport is fundamentally broken for app use (bot cannot receive its own messages via getUpdates) — confirmed during debugging session 2026-04-03
- Hard cutover, not gradual migration: remove Telegram transport code from app entirely
- Keep the same JSON message format to avoid breaking the message parser chain
- mDNS discovery should "just work" on the local network — minimal setup friction

</specifics>

<deferred>
## Deferred Ideas

- FCM push notifications as alternative to Telegram fallback (requires Firebase setup)
- WebSocket compression (permessage-deflate) for bandwidth optimization
- Message queue/replay on reconnection (server buffers missed messages)
- End-to-end encryption beyond shared secret

</deferred>
