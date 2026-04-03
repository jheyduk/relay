# Phase 6: Direct WebSocket Transport - Research

**Researched:** 2026-04-03
**Domain:** WebSocket transport, mDNS service discovery, Node.js server, Android client
**Confidence:** HIGH

## Summary

Phase 6 replaces the Telegram Bot API transport with a direct WebSocket connection. The Mac runs a Node.js WebSocket server (using the `ws` library) that hooks already call via `sendRelay()`. The Android app connects via Ktor's WebSocket client (same engine already in use: OkHttp). Discovery uses mDNS/Bonjour on local networks (macOS `dns-sd` command or `bonjour-service` npm) and a manually configured WireGuard IPv6 for remote. Telegram is stripped from the app entirely and kept only as a Mac-side push notification fallback.

The existing codebase is well-structured for this change. The `RelayMessageParser` and `RelayUpdate` domain model are transport-agnostic -- they parse JSON regardless of source. The hooks already produce the correct JSON format. The main work is: (1) a new Node.js WebSocket server singleton, (2) a new `WebSocketClient` in the shared KMP module replacing `TelegramPoller`, (3) refactoring `PollingService` into `WebSocketService`, (4) new setup screen fields, and (5) hook routing logic in `telegram-helper.cjs`.

**Primary recommendation:** Use `ws` 8.x on Node.js for the server (zero dependencies, already proven in the ecosystem), Ktor Client WebSocket plugin with OkHttp engine on Android (zero new dependencies beyond adding `ktor-client-websockets`), and macOS `dns-sd -R` for Bonjour advertisement (zero npm dependency for mDNS publishing).

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Node.js with `ws` library for WebSocket server -- hooks are already Node.js/CJS
- Singleton process started by session-start.cjs (same pattern as Telegram singleton)
- Same JSON message format as current Relay messages -- zero parser changes
- Shared secret token for auth -- generated during setup, stored in `~/.config/zellij-claude/relay.json` on Mac and DataStore on Android
- mDNS/Bonjour service advertisement (`_relay._tcp`) for local discovery
- Configured WireGuard IPv6 address for remote access (user enters once)
- Exponential backoff reconnect (1s->2s->4s->30s cap) with mDNS re-discovery on connection loss
- Switch to Telegram notification fallback after 60s disconnect
- Use most recently seen Mac when multiple mDNS results
- Hard cutover: WebSocket only in app, no Telegram polling, no TransportProvider abstraction
- Remove TelegramPoller, TelegramApi (relay side), and all polling-related code
- PollingService becomes WebSocketService (foreground service)
- No transport selection UI -- only connection status indicator
- Config-driven hook routing in `~/.config/zellij-claude/relay.json`
- Telegram used only for push notifications when app disconnected >30s
- Only permission requests and completions via Telegram fallback
- App does NOT need Telegram bot tokens

### Claude's Discretion
- Exact WebSocket server port selection strategy (fixed vs dynamic with mDNS advertisement)
- Reconnection timing details beyond the backoff cap
- Exact mDNS service record fields
- WebSocket ping/pong interval for keepalive

### Deferred Ideas (OUT OF SCOPE)
- FCM push notifications as alternative to Telegram fallback
- WebSocket compression (permessage-deflate)
- Message queue/replay on reconnection
- End-to-end encryption beyond shared secret
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| R-06-01 | WebSocket server on Mac (Node.js, runs alongside zellij-claude) | `ws` 8.20.0, singleton via PID file in session-start.cjs, CJS compatible |
| R-06-02 | mDNS/Bonjour service advertisement for local network discovery | macOS `dns-sd -R` command (zero dependency) or `bonjour-service` 1.3.0 npm |
| R-06-03 | Stable WireGuard IPv6 as fallback connection target | Manual config in DataStore, no WireGuard tooling needed on Mac for this phase |
| R-06-04 | Configurable transport layer in zellij-claude hooks | `relay.json` config with `transport` field, `sendRelay()` routes to WebSocket or Telegram |
| R-06-05 | App auto-discovers Mac via mDNS or configured WireGuard IP | Android NsdManager API (platform, no library), DataStore for WireGuard IP |
| R-06-06 | Bidirectional real-time messaging over WebSocket | Ktor Client WebSocket plugin + OkHttp engine (both already in project) |
| R-06-07 | Telegram degraded to push notification fallback (app not connected) | Mac-side only: existing `telegram-helper.cjs` sends when WebSocket client disconnected >30s |
</phase_requirements>

## Standard Stack

### Core (Mac-Side -- WebSocket Server)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| ws | 8.20.0 | WebSocket server | De-facto Node.js WebSocket library. Zero dependencies. 50M+ weekly downloads. Used by Next.js, Socket.io internally. |
| node:crypto | built-in | Shared secret generation | `crypto.randomBytes(32).toString('hex')` for token generation. No npm package needed. |
| dns-sd (macOS) | system | Bonjour service advertisement | Native macOS command. Zero npm dependency. More reliable than pure-JS mDNS on macOS. |

### Core (App-Side -- WebSocket Client)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| ktor-client-websockets | 3.4.2 | WebSocket client | Same Ktor version already in project. OkHttp engine supports WebSocket natively. Coroutine-first API. |
| Android NsdManager | Platform API | mDNS service discovery | Built-in Android API (API 16+). Zero library dependency. Works with Bonjour/mDNS natively. |

### Supporting
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| bonjour-service | 1.3.0 | Node.js mDNS (backup) | Only if `dns-sd` subprocess approach proves unreliable. Pure JS, no native deps. |

**Installation (Mac-side, in zellij-claude):**
```bash
npm install ws
# No install needed for dns-sd (macOS built-in) or node:crypto
```

**Installation (App-side, in libs.versions.toml):**
```toml
# Add to [libraries] section:
ktor-client-websockets = { module = "io.ktor:ktor-client-websockets", version.ref = "ktor" }
```

### Discretion Recommendations

**Port selection:** Use fixed port 9784 (mnemonic: "R-E-L-A" on phone keypad = 7352, but 9784 is less likely to collide). Advertise via mDNS so the app never needs to know the port number directly. If port is taken, increment and re-advertise.

**Ping/pong interval:** 30 seconds. OkHttp WebSocket client supports ping via `preconfigured` builder. `ws` server auto-responds to pings. This keeps NAT mappings alive and detects dead connections within ~60s.

**mDNS service record fields:**
- Service type: `_relay._tcp`
- Service name: `Relay-<hostname>` (e.g., `Relay-Joerg-MBP`)
- TXT record: `token_hash=<first 8 chars of sha256(secret)>` -- allows app to verify it found the right server without exposing the full secret

## Architecture Patterns

### Mac-Side: WebSocket Server Singleton

```
~/.config/zellij-claude/
  relay.json              # Config: port, secret, transport mode

/tmp/
  zellij-claude-relay.pid # PID of running WebSocket server

hooks/
  relay-server.cjs        # WebSocket server (NEW)
  telegram-helper.cjs     # Modified: sendRelay() routes to WS or Telegram
  session-start.cjs       # Modified: starts relay-server singleton
  session-stop.cjs        # Modified: checks if server should stop
```

### App-Side: WebSocket Client

```
shared/src/commonMain/kotlin/dev/heyduk/relay/
  data/
    remote/
      WebSocketClient.kt       # NEW: replaces TelegramPoller
      RelayMessageParser.kt    # UNCHANGED
      dto/RelayMessage.kt      # UNCHANGED
    repository/
      RelayRepository.kt       # RENAMED from TelegramRepository
      RelayRepositoryImpl.kt   # REWRITTEN: WebSocket-based
  di/
    SharedModule.kt             # MODIFIED: WebSocket instead of Telegram DI

androidApp/src/main/java/dev/heyduk/relay/
  service/
    WebSocketService.kt         # RENAMED from PollingService
    NsdDiscovery.kt             # NEW: mDNS discovery wrapper
  presentation/
    setup/
      SetupViewModel.kt         # REWRITTEN: server address/secret fields
      SetupScreen.kt            # REWRITTEN: new UI
```

### Pattern 1: WebSocket Server Singleton (Mac-Side)
**What:** A standalone Node.js process that `session-start.cjs` spawns as a detached child. Uses PID file at `/tmp/zellij-claude-relay.pid` to prevent duplicates.
**When to use:** On first session start, check PID file. If process alive, skip. If dead/missing, spawn and record PID.

```javascript
// relay-server.cjs (simplified)
const { WebSocketServer } = require('ws');
const { readFileSync } = require('fs');
const { join } = require('path');
const { homedir } = require('os');

const CONFIG_FILE = join(homedir(), '.config', 'zellij-claude', 'relay.json');
const config = JSON.parse(readFileSync(CONFIG_FILE, 'utf8'));

const wss = new WebSocketServer({ port: config.port || 9784 });
let appConnected = false;
let appSocket = null;
let disconnectedAt = null;

wss.on('connection', (ws, req) => {
  // Verify shared secret from query param or first message
  const url = new URL(req.url, `http://${req.headers.host}`);
  const token = url.searchParams.get('token');
  if (token !== config.secret) {
    ws.close(4001, 'Unauthorized');
    return;
  }
  appSocket = ws;
  appConnected = true;
  disconnectedAt = null;

  ws.on('message', (data) => {
    // Handle incoming commands from app (e.g., "@kuerzel message")
    const msg = JSON.parse(data);
    // Route to zellij-claude dispatch
  });

  ws.on('close', () => {
    appConnected = false;
    appSocket = null;
    disconnectedAt = Date.now();
  });
});

// Export for hooks to call
function sendToApp(jsonPayload) {
  if (appSocket && appSocket.readyState === 1) {
    appSocket.send(JSON.stringify(jsonPayload));
    return true;
  }
  return false;
}
```

### Pattern 2: Ktor WebSocket Client (App-Side)
**What:** Coroutine-based WebSocket connection using Ktor's `webSocket` extension. Emits parsed `RelayUpdate` objects via `SharedFlow`, same pattern as `TelegramPoller`.
**When to use:** Replaces `TelegramPoller.pollLoop()` with a WebSocket session that stays open.

```kotlin
// WebSocketClient.kt (simplified)
class WebSocketClient(
    private val httpClient: HttpClient,
    private val parser: RelayMessageParser
) {
    private val _updates = MutableSharedFlow<RelayUpdate>(extraBufferCapacity = 64)
    val updates: SharedFlow<RelayUpdate> = _updates

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState

    suspend fun connect(serverUrl: String, secret: String) {
        _connectionState.value = ConnectionState.CONNECTING
        httpClient.webSocket("$serverUrl?token=$secret") {
            _connectionState.value = ConnectionState.CONNECTED
            for (frame in incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val updateId = Clock.currentTimeMillis() // synthetic ID
                    val parsed = parser.parse(updateId, text, Clock.currentTimeMillis() / 1000)
                    if (parsed != null) {
                        _updates.emit(parsed)
                    }
                }
            }
        }
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    suspend fun send(message: String, session: DefaultClientWebSocketSession) {
        session.send(Frame.Text(message))
    }
}
```

### Pattern 3: Inter-Process Communication for Hook -> Server
**What:** Hooks run as short-lived processes. They cannot import the running server's state. Use a local HTTP endpoint or Unix domain socket on the WebSocket server for hook -> server communication.
**When to use:** When `permission-notify.cjs` or `ask-notify.cjs` needs to send a message through the WebSocket server to the app.

**Recommended approach:** The WebSocket server also listens on a Unix domain socket at `/tmp/zellij-claude-relay.sock`. Hooks send JSON messages to this socket. This avoids port conflicts and is fast.

```javascript
// In relay-server.cjs: add Unix socket listener for hooks
const net = require('net');
const HOOK_SOCKET = '/tmp/zellij-claude-relay.sock';

const hookServer = net.createServer((conn) => {
  let data = '';
  conn.on('data', chunk => data += chunk);
  conn.on('end', () => {
    try {
      const msg = JSON.parse(data);
      if (msg.action === 'send') {
        const sent = sendToApp(msg.payload);
        conn.end(JSON.stringify({ sent, fallback: !sent }));
      }
    } catch {}
  });
});
hookServer.listen(HOOK_SOCKET);

// In telegram-helper.cjs: sendRelay() updated
function sendRelay(jsonPayload) {
  // Try WebSocket first via Unix socket
  try {
    const result = sendViaUnixSocket(jsonPayload);
    if (result.sent) return result;
  } catch {}
  // Fall back to Telegram if configured and >30s disconnected
  return sendViaTelegram(jsonPayload);
}
```

### Anti-Patterns to Avoid
- **Embedding server in hooks:** Each hook runs as a separate process. Do NOT try to share a WebSocket server instance between hook processes -- use IPC.
- **HTTP polling fallback:** Do NOT add HTTP polling as a WebSocket fallback. The exponential backoff reconnect handles transient failures. If WebSocket is down, Telegram is the fallback for critical notifications.
- **Transport abstraction layer in app:** CONTEXT.md explicitly says no TransportProvider abstraction. Do NOT build an interface that can swap between Telegram and WebSocket at runtime. It is a hard cutover.
- **WebSocket server in the app (reversed topology):** The Mac is the server, the app is the client. This is correct because the Mac has a stable network position and the hooks originate there.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| WebSocket protocol | Raw TCP socket handling | `ws` library | Protocol framing, masking, fragmentation, close handshake -- hundreds of edge cases |
| mDNS on macOS | Pure-JS multicast DNS | `dns-sd -R` subprocess | macOS Bonjour daemon is authoritative. Pure-JS implementations fight the system daemon. |
| mDNS on Android | Custom UDP multicast | `NsdManager` platform API | Handles Wi-Fi multicast locks, power management, system integration automatically. |
| WebSocket keepalive | Custom ping/pong timer | OkHttp `pingInterval` + ws auto-pong | Both sides handle this natively. OkHttp sends pings, ws auto-responds. |
| JSON serialization | Manual string building | `kotlinx-serialization` / `JSON.stringify` | Already in use, compile-time safe on Kotlin side |
| Reconnection backoff | Sleep loops | Coroutine `delay()` with backoff math | Already proven in `TelegramPoller` -- copy the pattern |

## Common Pitfalls

### Pitfall 1: CJS vs ESM in hooks
**What goes wrong:** zellij-claude hooks are `.cjs` (CommonJS) but `ws` library ships as ESM in newer versions.
**Why it happens:** `ws` 8.x supports both CJS and ESM. But if you accidentally require the wrong entry point or use `import` in a `.cjs` file, it fails.
**How to avoid:** Use `const { WebSocketServer } = require('ws');` in CJS files. ws 8.x explicitly supports this. Verify after install with a quick `node -e "const ws = require('ws'); console.log(ws.WebSocketServer)"`.
**Warning signs:** `ERR_REQUIRE_ESM` or `SyntaxError: Cannot use import statement`.

### Pitfall 2: Hook IPC race condition
**What goes wrong:** Hook sends message via Unix socket before the WebSocket server has started, or after it crashed.
**Why it happens:** Hooks are fire-and-forget processes. The WebSocket server might not be running when a hook fires.
**How to avoid:** `sendRelay()` must handle connection failure gracefully -- try Unix socket, catch error, fall back to Telegram. Never block on server availability.
**Warning signs:** Hook processes hanging or timing out.

### Pitfall 3: Android NsdManager callback threading
**What goes wrong:** NsdManager callbacks arrive on a random system thread, not the main thread or a coroutine dispatcher.
**Why it happens:** Android NSD uses a background Looper internally.
**How to avoid:** Wrap NsdManager results in a `callbackFlow` or `suspendCancellableCoroutine` that posts to the correct dispatcher. Never update UI state directly from NSD callbacks.
**Warning signs:** `CalledFromWrongThreadException` or state inconsistencies.

### Pitfall 4: WebSocket close during send
**What goes wrong:** App sends a message while the WebSocket is in the CLOSING state, causing an exception.
**Why it happens:** Network loss can trigger close asynchronously while a coroutine is mid-send.
**How to avoid:** Check `readyState` before send, catch `WebSocketException` around all send calls. The Ktor WebSocket session handles most of this, but wrapping in try-catch is still necessary.
**Warning signs:** Unhandled exceptions crashing the foreground service.

### Pitfall 5: Foreground service type change
**What goes wrong:** Android 14+ enforces foreground service type declarations. Changing from `dataSync` might trigger runtime errors.
**Why it happens:** `PollingService` uses `FOREGROUND_SERVICE_TYPE_DATA_SYNC`. WebSocket is a persistent data connection, which also fits `dataSync`. But some developers mistakenly switch to `connectedDevice`.
**How to avoid:** Keep `FOREGROUND_SERVICE_TYPE_DATA_SYNC` -- WebSocket is data synchronization. No manifest change needed for the service type.
**Warning signs:** `ForegroundServiceStartNotAllowedException`.

### Pitfall 6: mDNS not working on Android emulator
**What goes wrong:** NsdManager discovery returns zero results on emulator.
**Why it happens:** Android emulators often don't bridge multicast traffic to the host network.
**How to avoid:** Test mDNS on a physical device. For emulator testing, use the manual WireGuard IP configuration path (enter `10.0.2.2:9784` for host loopback).
**Warning signs:** `onDiscoveryStarted` fires but `onServiceFound` never fires on emulator.

### Pitfall 7: Unix domain socket cleanup
**What goes wrong:** Stale `/tmp/zellij-claude-relay.sock` prevents new server from starting.
**Why it happens:** Server crashed without cleanup. `net.createServer().listen()` fails with `EADDRINUSE` on existing socket files.
**How to avoid:** On server start, `fs.unlinkSync(HOOK_SOCKET)` before `listen()`. Also check PID file -- if PID is dead, clean up both PID file and socket.
**Warning signs:** `EADDRINUSE` error on server start.

## Code Examples

### relay.json Configuration Schema
```json
{
  "chatId": "948808872",
  "relayBotToken": "8728...",
  "port": 9784,
  "secret": "a1b2c3d4e5f6...",
  "transport": "websocket"
}
```

### Starting mDNS Advertisement (macOS)
```javascript
// Spawn dns-sd as detached child process
const { spawn } = require('child_process');
const mdns = spawn('dns-sd', [
  '-R', 'Relay', '_relay._tcp', 'local', String(port),
  `token_hash=${tokenHash}`
], { detached: true, stdio: 'ignore' });
mdns.unref();
// Store mdns.pid for cleanup on server stop
```

### Android NsdManager Discovery (Kotlin)
```kotlin
// NsdDiscovery.kt
class NsdDiscovery(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    fun discover(): Flow<NsdServiceInfo> = callbackFlow {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                if (info.serviceType == "_relay._tcp.") {
                    nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            trySend(resolved)
                        }
                        override fun onResolveFailed(info: NsdServiceInfo, code: Int) {}
                    })
                }
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) { close() }
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
        }
        nsdManager.discoverServices("_relay._tcp.", NsdManager.PROTOCOL_DNS_SD, listener)
        awaitClose { nsdManager.stopServiceDiscovery(listener) }
    }
}
```

### Ktor WebSocket Client Connection
```kotlin
// Add to HttpClient config in SharedModule
install(WebSockets) {
    pingIntervalMillis = 30_000 // Note: ignored for OkHttp engine
}

// OkHttp engine config with ping
single {
    HttpClient(OkHttp) {
        engine {
            preconfigured = OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)
                .build()
        }
        install(WebSockets)
        install(ContentNegotiation) { json(...) }
    }
}
```

### Hook Unix Socket Send (telegram-helper.cjs update)
```javascript
const net = require('net');
const RELAY_SOCKET = '/tmp/zellij-claude-relay.sock';

function sendViaWebSocket(jsonPayload) {
  return new Promise((resolve) => {
    const client = net.createConnection(RELAY_SOCKET, () => {
      client.end(JSON.stringify({ action: 'send', payload: jsonPayload }));
    });
    let response = '';
    client.on('data', chunk => response += chunk);
    client.on('end', () => {
      try { resolve(JSON.parse(response)); }
      catch { resolve({ sent: false }); }
    });
    client.on('error', () => resolve({ sent: false }));
    client.setTimeout(2000, () => { client.destroy(); resolve({ sent: false }); });
  });
}

async function sendRelay(jsonPayload) {
  // Try WebSocket first
  const result = await sendViaWebSocket(jsonPayload);
  if (result.sent) return result;

  // Fallback: Telegram (only for permission/completion, only if >30s disconnected)
  const type = jsonPayload.type;
  if (type === 'permission' || type === 'completion') {
    return sendViaTelegram(jsonPayload);
  }
  return null;
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Telegram Bot API transport | Direct WebSocket | Phase 6 (this phase) | Eliminates 409 conflict, enables bidirectional real-time communication |
| Two-bot architecture (relay + command) | Single WebSocket connection | Phase 6 | App no longer needs any bot tokens |
| Long polling with 30s timeout | Persistent WebSocket with ping/pong | Phase 6 | Sub-second message delivery instead of up-to-30s polling latency |
| Telegram as primary transport | Telegram as notification fallback only | Phase 6 | Telegram only for Mac->phone push when app disconnected |

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | WebSocket server | Yes | v22.17.1 | -- |
| npm | ws package install | Yes | 10.9.2 | -- |
| dns-sd | mDNS advertisement | Yes | system (macOS built-in) | bonjour-service npm |
| WireGuard | Remote access | No | -- | Manual IPv6 config only; WG setup is user responsibility, not phase scope |
| Android NsdManager | mDNS discovery | Yes | Platform API (minSdk 28) | -- |
| Ktor 3.4.2 | WebSocket client | Yes | Already in project | -- |

**Missing dependencies with no fallback:** None -- all required tools are available.

**Missing dependencies with fallback:**
- WireGuard CLI not installed on Mac, but this phase only needs the user to enter their WireGuard IP in the app. WireGuard setup is outside phase scope.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Kotlin Test + JUnit 4 + Turbine (coroutines) |
| Config file | shared/build.gradle.kts (commonTest dependencies) |
| Quick run command | `./gradlew :shared:jvmTest --tests "dev.heyduk.relay.*"` |
| Full suite command | `./gradlew :shared:allTests` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| R-06-01 | WebSocket server starts, accepts connections, verifies token | integration (Node.js) | `node --test test/relay-server.test.js` | No -- Wave 0 |
| R-06-02 | mDNS service advertised on server start | manual | Verify with `dns-sd -B _relay._tcp local` | N/A (manual) |
| R-06-03 | WireGuard IP stored and used as fallback | unit | `./gradlew :shared:jvmTest --tests "*WebSocketClientTest*"` | No -- Wave 0 |
| R-06-04 | Hook routing sends via WebSocket or Telegram based on config | unit (Node.js) | `node --test test/telegram-helper.test.js` | No -- Wave 0 |
| R-06-05 | NsdDiscovery emits resolved services | unit | `./gradlew :shared:jvmTest --tests "*NsdDiscoveryTest*"` | No -- Wave 0 |
| R-06-06 | Bidirectional messages over WebSocket | unit | `./gradlew :shared:jvmTest --tests "*WebSocketClientTest*"` | No -- Wave 0 |
| R-06-07 | Telegram fallback when app disconnected >30s | unit (Node.js) | `node --test test/relay-server.test.js` | No -- Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew :shared:jvmTest` + `node --test test/` (in zellij-claude)
- **Per wave merge:** `./gradlew :shared:allTests`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `shared/src/commonTest/kotlin/dev/heyduk/relay/data/remote/WebSocketClientTest.kt` -- covers R-06-03, R-06-06
- [ ] `test/relay-server.test.js` (in zellij-claude repo) -- covers R-06-01, R-06-07
- [ ] `test/telegram-helper.test.js` (in zellij-claude repo) -- covers R-06-04

## Project Constraints (from CLAUDE.md)

- **Git commits in English**, no Co-Author Claude Code reference
- **Code comments in English**, explanations in German
- **GSD workflow enforcement** -- all changes through GSD commands
- **Koin for DI** (not Hilt -- project uses Koin despite CLAUDE.md stack recommending Hilt; actual codebase uses Koin)
- **SQLDelight** for database (not Room -- actual codebase uses SQLDelight)
- **fish shell** -- developer uses fish, scripts must be POSIX-compatible or explicitly fish-compatible
- **zellij-claude lives in a separate repo** (`/Users/jheyduk/prj/zellij-claude/`) -- changes span two repos

**Important divergence from CLAUDE.md stack:** The actual project uses **Koin** (not Hilt) and **SQLDelight** (not Room). Research and planning must follow the actual codebase, not the recommended stack table in CLAUDE.md.

## Open Questions

1. **Cross-repo changes**
   - What we know: Hooks live in `/Users/jheyduk/prj/zellij-claude/`, app lives in `/Users/jheyduk/prj/relay/`
   - What's unclear: Should zellij-claude changes be committed to that repo separately, or should we copy hooks into the relay repo?
   - Recommendation: Make changes in zellij-claude repo directly. Plans should note which repo each task targets.

2. **App-to-Mac command routing**
   - What we know: Currently app sends commands via Telegram `sendMessage` to the command bot, which zellij-claude's MCP server reads.
   - What's unclear: How does the Mac-side WebSocket server route incoming commands (e.g., `@xyz allow`) to the correct Claude Code session?
   - Recommendation: The WebSocket server writes incoming app messages to the same channel the MCP server currently reads from Telegram (or dispatches via `zellij-claude` CLI directly). Needs investigation of how the MCP server currently reads Telegram messages.

3. **Simultaneous mDNS + WireGuard**
   - What we know: App should try mDNS first, fall back to configured WireGuard IP.
   - What's unclear: Should the app try both simultaneously, or sequential (mDNS first, timeout, then WireGuard)?
   - Recommendation: Parallel. Start mDNS discovery and WireGuard connection attempt simultaneously. Use whichever connects first. Prefer mDNS result if both succeed (lower latency on local network).

## Sources

### Primary (HIGH confidence)
- [ws GitHub repository](https://github.com/websockets/ws) -- ws 8.20.0, API reference, CJS support confirmed
- [Ktor Client WebSockets docs](https://ktor.io/docs/client-websockets.html) -- Ktor 3.x WebSocket plugin API
- [Android NsdManager docs](https://developer.android.com/reference/android/net/nsd/NsdManager) -- Platform mDNS API
- [Android NSD guide](https://developer.android.com/develop/connectivity/wifi/use-nsd) -- Implementation patterns
- macOS `dns-sd` man page -- Bonjour advertisement command syntax

### Secondary (MEDIUM confidence)
- [bonjour-service npm](https://www.npmjs.com/package/bonjour-service) -- v1.3.0, backup mDNS option
- [OkHttp WebSocket support](https://github.com/ktorio/ktor/blob/main/ktor-client/ktor-client-okhttp/jvm/src/io/ktor/client/engine/okhttp/OkHttpWebsocketSession.kt) -- Ktor OkHttp engine WebSocket implementation
- [WebSocket authentication patterns](https://oneuptime.com/blog/post/2026-01-24-websocket-authentication/view) -- Token auth via query parameter

### Tertiary (LOW confidence)
- None

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- ws and Ktor WebSocket are well-documented, mature libraries
- Architecture: HIGH -- patterns directly follow existing codebase structure (singleton, SharedFlow, foreground service)
- Pitfalls: HIGH -- based on direct codebase analysis and known Android/Node.js behavior

**Research date:** 2026-04-03
**Valid until:** 2026-05-03 (stable domain, no fast-moving changes expected)
