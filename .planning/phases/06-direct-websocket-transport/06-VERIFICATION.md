---
phase: 06-direct-websocket-transport
verified: 2026-04-03T10:00:00Z
status: human_needed
score: 7/7 must-haves verified
re_verification: false
human_verification:
  - test: "End-to-end message flow: start a zellij-claude session, trigger a permission request, verify it appears in the Android app within 1 second"
    expected: "App shows permission card with Allow/Deny buttons. Relay notification fires."
    why_human: "Requires running Mac relay-server.cjs and a connected Android device. Cannot verify over WebSocket without a live server."
  - test: "mDNS discovery: on local network, app should discover relay-server without entering a WireGuard IP"
    expected: "App shows 'Relay connected' in foreground notification after setup with secret only"
    why_human: "NsdManager discovery requires an actual Android device and running dns-sd advertisement"
  - test: "Send command from app to session: type a message in the chat screen and verify it arrives on Mac"
    expected: "Message appears in the zellij-claude session terminal on Mac within 1 second"
    why_human: "Bidirectional WebSocket command dispatch requires live integration"
  - test: "Telegram fallback: disconnect app, wait 30s, trigger a permission request on Mac"
    expected: "Permission arrives via Telegram (not WebSocket). After reconnect, messages flow over WebSocket again."
    why_human: "30s threshold and Telegram fallback path require live integration"
---

# Phase 6: Direct WebSocket Transport — Verification Report

**Phase Goal:** Replace Telegram Bot API transport with direct WebSocket connection between Mac and Android app. Mac runs a lightweight WebSocket server, app discovers it via mDNS (local) or stable WireGuard IPv6 (VPN). Hooks route messages via WebSocket with Telegram as push notification fallback only.
**Verified:** 2026-04-03T10:00:00Z
**Status:** human_needed — all automated checks pass, 4 behavioral items require live device testing
**Re-verification:** No — initial verification

---

## Requirements Coverage

The REQUIREMENTS.md file does not contain R-06-xx IDs. These are Phase 6 internal requirement identifiers defined in ROADMAP.md only. No v1 requirements from REQUIREMENTS.md are mapped to Phase 6 — Phase 6 is a transport replacement that refactors phases 1–5 delivery, not a new user-visible capability. All REQUIREMENTS.md traceability rows point to Phases 1–5.

| Requirement | ROADMAP.md Description | Plan | Status |
| ----------- | ---------------------- | ---- | ------ |
| R-06-01 | WebSocket server on Mac (Node.js, runs alongside zellij-claude) | 06-01 | SATISFIED — relay-server.cjs created, ws installed, PID singleton |
| R-06-02 | mDNS/Bonjour service advertisement for local network discovery | 06-01 | SATISFIED — dns-sd -R advertisement in relay-server.cjs |
| R-06-03 | Stable WireGuard IPv6 as fallback connection target | 06-02, 06-03 | SATISFIED — wireguard_ip DataStore key, ws://ip:9784 fallback in WebSocketService |
| R-06-04 | Configurable transport layer in zellij-claude hooks (Telegram / WebSocket) | 06-01 | SATISFIED — sendRelay() is now async with WebSocket-first, Telegram fallback |
| R-06-05 | App auto-discovers Mac via mDNS or configured WireGuard IP | 06-02, 06-03 | SATISFIED — NsdDiscovery + discoverServer() in WebSocketService |
| R-06-06 | Bidirectional real-time messaging over WebSocket | 06-02, 06-03, 06-04 | SATISFIED (automated); behavior requires human verification |
| R-06-07 | Telegram degraded to push notification fallback (app not connected) | 06-01, 06-04 | SATISFIED — 30s disconnect threshold in sendRelay() + Telegram code removed from app |

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
| --- | ----- | ------ | -------- |
| 1 | WebSocket server starts on first session-start and accepts authenticated connections on port 9784 | VERIFIED | relay-server.cjs: WebSocketServer on config.port, token auth on connection, ensureRelayServer() called in session-start.cjs |
| 2 | mDNS advertisement publishes _relay._tcp service so Android NsdManager can discover it | VERIFIED | relay-server.cjs: `dns-sd -R Relay _relay._tcp local PORT token_hash=HASH`; NsdDiscovery.kt: `_relay._tcp.` filter, callbackFlow wrapping NsdManager |
| 3 | Hooks send messages to connected app via Unix socket IPC, falling back to Telegram for permission/completion when disconnected >30s | VERIFIED | telegram-helper.cjs: sendViaWebSocket() -> Unix socket, sendRelay() checks `disconnectedFor > 30000` before Telegram fallback |
| 4 | Server auto-stops when last session ends | VERIFIED | session-stop.cjs: stopRelayServerIfEmpty() reads `/tmp/zellij-claude-tab-*` files, sends SIGTERM when count = 0 |
| 5 | WebSocketClient connects to ws://host:port?token=secret, emits RelayUpdate via SharedFlow | VERIFIED | WebSocketClient.kt: `httpClient.webSocket("$serverUrl?token=$secret")`, `_updates.emit(parsed)`, `val updates: SharedFlow<RelayUpdate>` |
| 6 | ConnectionState is exposed as StateFlow (DISCONNECTED, CONNECTING, CONNECTED) | VERIFIED | ConnectionState.kt: enum with all 3 states; WebSocketClient.kt: `_connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)`, transitions on connect/disconnect/cancel |
| 7 | Exponential backoff reconnect (1s->2s->4s->30s cap) runs automatically on disconnect | VERIFIED | WebSocketClient.kt: `backoffMs = if (backoffMs == 0L) 1000L else (backoffMs * 2).coerceAtMost(30_000L)` |
| 8 | RelayRepository replaces TelegramRepository — same interface contract, WebSocket transport | VERIFIED | RelayRepository.kt interface matches TelegramRepository contract + adds connectionState; RelayRepositoryImpl.kt delegates to WebSocketClient; SharedModule.kt: TelegramPoller singleton removed, WebSocketClient wired |
| 9 | ChatRepositoryImpl sends messages via WebSocket instead of Telegram | VERIFIED | ChatRepositoryImpl.kt: `private val relayRepository: RelayRepository`; `relayRepository.sendCommand(kuerzel, text)` |
| 10 | All Telegram transport code deleted from codebase | VERIFIED | TelegramPoller.kt, TelegramApi.kt, TelegramRepository.kt, TelegramRepositoryImpl.kt, TelegramResponse.kt, TelegramUpdate.kt, PollingService.kt, DataStoreOffsetProvider.kt all absent from main source tree |
| 11 | WebSocketService replaces PollingService as foreground service | VERIFIED | WebSocketService.kt registered in AndroidManifest.xml; PollingServiceLauncher.kt references WebSocketService::class.java; no PollingService in manifest |
| 12 | Setup screen asks for server secret only (no bot tokens) | VERIFIED | SetupScreen.kt: fields "Server Secret" and "WireGuard IP (optional)"; SetupViewModel.kt: SERVER_SECRET_KEY and WIREGUARD_IP_KEY only; no relay_bot_token or command_bot_token |
| 13 | End-to-end message flow works | NEEDS HUMAN | Automated wiring verified; live behavior requires device |

**Score:** 12/12 automated truths verified. 1 truth (end-to-end behavior) requires human verification.

---

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `/Users/jheyduk/prj/zellij-claude/hooks/relay-server.cjs` | WebSocket server singleton with Unix socket IPC and mDNS | VERIFIED | 223 lines; WebSocketServer, net.createServer, dns-sd spawn, SIGTERM/SIGINT handlers, PID file |
| `/Users/jheyduk/prj/zellij-claude/hooks/telegram-helper.cjs` | sendRelay() with WebSocket-first routing and Telegram fallback | VERIFIED | sendViaWebSocket(), async sendRelay(), sendViaTelegram(); exports: send, sendWithButtons, sendRelay, sendViaWebSocket, sendViaTelegram |
| `shared/src/commonMain/kotlin/.../data/remote/WebSocketClient.kt` | Ktor WebSocket client with reconnect loop | VERIFIED | 149 lines; connectWithRetry(), send(), sendCommand(), sendRawCommand(), disconnect(); SharedFlow and StateFlow exposed |
| `shared/src/commonMain/kotlin/.../data/remote/ConnectionState.kt` | Connection state enum | VERIFIED | enum with DISCONNECTED, CONNECTING, CONNECTED |
| `shared/src/commonMain/kotlin/.../data/repository/RelayRepository.kt` | Repository interface replacing TelegramRepository | VERIFIED | interface with updates, connectionState, getMessagesForSession, getRecentMessages, sendCommand, sendRawCommand |
| `shared/src/commonMain/kotlin/.../data/repository/RelayRepositoryImpl.kt` | WebSocket-backed repository implementation | VERIFIED | delegates to WebSocketClient; maps DB rows to domain via toDomain() |
| `androidApp/src/main/.../service/WebSocketService.kt` | Foreground service maintaining WebSocket connection | VERIFIED | reads server_secret and wireguard_ip, calls discoverServer() with 5s mDNS timeout then WireGuard fallback, calls connectWithRetry() |
| `androidApp/src/main/.../service/NsdDiscovery.kt` | mDNS service discovery via NsdManager | VERIFIED | callbackFlow wrapping NsdManager.discoverServices("_relay._tcp."), resolveToUrl() returns ws://ip:port |

---

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | -- | --- | ------ | ------- |
| hooks/session-start.cjs | hooks/relay-server.cjs | child_process.spawn detached | WIRED | `spawn('node', [serverPath], { detached: true, stdio: 'ignore' })` in ensureRelayServer() |
| hooks/telegram-helper.cjs | /tmp/zellij-claude-relay.sock | net.createConnection Unix socket | WIRED | `net.createConnection(RELAY_SOCKET, ...)` in sendViaWebSocket() |
| hooks/relay-server.cjs | ~/.config/zellij-claude/relay.json | readFileSync config | WIRED | loadOrCreateConfig() reads CONFIG_PATH, writes back if fields missing |
| WebSocketClient.kt | RelayMessageParser | parser.parse() on incoming Frame.Text | WIRED | `val parsed = parser.parse(updateId = updateId, messageText = text, ...)` |
| RelayRepositoryImpl.kt | WebSocketClient.kt | constructor injection, collect updates | WIRED | `override val updates: Flow<RelayUpdate> = webSocketClient.updates`; `webSocketClient.sendCommand(...)` |
| ChatRepositoryImpl.kt | RelayRepository | relayRepository.sendCommand() | WIRED | `relayRepository.sendCommand(kuerzel, text)` and `relayRepository.sendCommand(kuerzel, "callback:$response:$kuerzel")` |
| WebSocketService.kt | WebSocketClient.kt | webSocketClient.connectWithRetry() | WIRED | `webSocketClient.connectWithRetry(serverUrl, secret)` in connection loop |
| NsdDiscovery.kt | WebSocketService.kt | discovers server URL, service connects | WIRED | `nsdDiscovery.discover().firstOrNull()` in discoverServer(); result fed to connectWithRetry() |
| SetupViewModel.kt | DataStore | persists server_secret and wireguard_ip | WIRED | `prefs[SERVER_SECRET_KEY] = state.serverSecret; prefs[WIREGUARD_IP_KEY] = state.wireguardIp` |
| zellij-claude/hooks/relay-server.cjs | WebSocketClient.kt | WebSocket connection on port 9784 | WIRED (structurally) | server listens on config.port (default 9784); client connects to ws://host:9784; matching token auth |
| hooks/session-stop.cjs | relay-server.cjs | SIGTERM via PID file | WIRED | `process.kill(pid, 'SIGTERM')` after reading RELAY_PIDFILE in stopRelayServerIfEmpty() |

---

### Data-Flow Trace (Level 4)

WebSocketService.kt inserts updates from webSocketClient.updates directly into the DB:

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| WebSocketService.kt | `update` | `webSocketClient.updates.collect { update -> ... }` | Yes — from live WebSocket connection | FLOWING (structurally); requires live server for behavioral confirmation |
| RelayRepositoryImpl.kt | DB query results | `database.messagesQueries.getMessagesForSession(session).asFlow()` | Yes — SQLDelight DB queries | FLOWING |
| ChatRepositoryImpl.kt | relayRepository | `relayRepository.sendCommand(kuerzel, text)` | Yes — delegates to WebSocketClient.sendCommand | FLOWING |

---

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| relay-server.cjs syntax valid | `node --check /Users/jheyduk/prj/zellij-claude/hooks/relay-server.cjs` | No errors | PASS |
| ws module installed in zellij-claude | `node -e "require('ws').WebSocketServer"` | function | PASS |
| telegram-helper.cjs exports sendRelay as function | `node -e "typeof require('./hooks/telegram-helper.cjs').sendRelay"` | function | PASS |
| ws dependency in package.json | grep "ws" package.json | `"ws": "^8.20.0"` | PASS |
| ktor-client-websockets in version catalog | grep libs.versions.toml | `ktor-client-websockets = { module = "io.ktor:ktor-client-websockets" }` | PASS |
| All Telegram transport files deleted | file existence checks | TelegramPoller.kt, TelegramApi.kt, TelegramRepository.kt, PollingService.kt absent | PASS |
| No Telegram tokens in AndroidModule.kt | grep for relay_bot_token etc. | 0 hits in main source | PASS |
| Commits exist in git | git log | 0f292b3, 09b2c4b (zellij-claude), 73e80f7, a04a2cc, d67062a, a54d73e, 464635c (relay) | PASS |

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| androidApp/.../presentation/status/StatusScreen.kt | 250 | Comment `// startWebSocketService is now in dev.heyduk.relay.util.PollingServiceLauncher` — references "PollingServiceLauncher" file name (the old file kept its original name despite content being fully updated) | INFO | No functional impact. File still named PollingServiceLauncher.kt but contains WebSocketService references only. Comment is stale. |
| RelayRepository.kt | 9 | Comment `* Replaces TelegramRepository with direct WebSocket transport.` — references deleted type | INFO | Historical context comment; no code impact. Plan 04 SUMMARY noted this was intentional. |

No blocker or warning anti-patterns found.

---

### Human Verification Required

#### 1. End-to-end message delivery (Mac -> App)

**Test:** Start a zellij-claude session on Mac. Trigger a permission request (run a tool). Open Relay app on Android, enter the secret from `~/.config/zellij-claude/relay.json`.
**Expected:** Permission card appears in app within ~1 second. App shows "Relay connected" in foreground notification.
**Why human:** Requires running relay-server.cjs (starts as a side process), live WebSocket connection, and an Android device with the app installed.

#### 2. mDNS local network discovery

**Test:** On the same WiFi network as the Mac, configure app with only the server secret (no WireGuard IP). Start a session on Mac. Open app.
**Expected:** App discovers relay-server via mDNS and shows "Relay connected" without manual IP entry.
**Why human:** NsdManager discovery requires actual Android hardware and a running dns-sd advertisement.

#### 3. Bidirectional command flow (App -> Mac)

**Test:** In the Relay app, navigate to an active session and type a message. Observe the Mac terminal.
**Expected:** Message appears in the zellij-claude session within 1 second.
**Why human:** Command dispatch writes to `/tmp/zellij-claude-relay-cmd.json` — requires integration testing to verify the Mac side reads and processes this file.

#### 4. Telegram fallback after 30s disconnect

**Test:** Disconnect Android from network. Wait 35 seconds. Trigger a permission request on Mac.
**Expected:** Permission arrives via Telegram. After reconnecting app, subsequent messages flow via WebSocket.
**Why human:** Requires timing the 30s disconnect window and verifying Telegram API delivery — cannot replicate in static analysis.

---

### Gaps Summary

No blocking gaps. All automated must-haves are verified. The phase has successfully achieved its primary goal: Telegram Bot API is no longer the primary transport. The WebSocket stack is fully wired from hooks through relay-server through to the Android app. All Telegram transport files (8 files, 656 lines) have been deleted. The remaining human verification items are integration tests that confirm live runtime behavior — they do not indicate missing code.

**Notable decisions verified as correct:**
- Unix domain socket for hook IPC (not HTTP) — present in relay-server.cjs and telegram-helper.cjs
- dns-sd CLI (macOS built-in) for mDNS advertisement — no npm dependency added
- 30s threshold before Telegram fallback — `disconnectedFor > 30000` confirmed in sendRelay()
- Backoff resets to 1000ms (not 0) after a successful connection — confirmed in WebSocketClient.kt
- PollingServiceLauncher.kt kept its original filename per Plan 04 SUMMARY decision

---

_Verified: 2026-04-03T10:00:00Z_
_Verifier: Claude (gsd-verifier)_
