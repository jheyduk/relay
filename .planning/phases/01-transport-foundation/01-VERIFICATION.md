---
phase: 01-transport-foundation
verified: 2026-04-02T18:40:00Z
status: human_needed
score: 5/5 must-haves verified
re_verification: false
human_verification:
  - test: "Install APK and complete end-to-end polling on physical Android device"
    expected: "App installs, setup screen accepts tokens, foreground service starts with notification, incoming JSON messages appear in status screen, test command reaches Mac-side bot, WiFi toggle shows Reconnecting then Connected, backgrounding keeps polling alive"
    why_human: "Device-side behavior -- notification visibility, actual Telegram API connectivity, network transition handling, and background polling persistence cannot be verified without a physical device and real bot tokens"
  - test: "Verify token validation sends no stray messages to production chat"
    expected: "Validation should either use getMe (non-intrusive) or the user should be aware that 'Relay bot connected' and 'Command bot connected' messages will appear in their Telegram chat on each validation"
    why_human: "SetupViewModel validates by sending actual messages rather than calling getMe. This is functionally correct but may surprise the user when they first configure the app. Human judgment on whether this is acceptable UX."
---

# Phase 1: Transport & Foundation Verification Report

**Phase Goal:** The app can connect to the Telegram Bot API, receive messages reliably, and survive network transitions -- with the 409 single-consumer conflict fully resolved
**Verified:** 2026-04-02T18:40:00Z
**Status:** human_needed
**Re-verification:** No -- initial verification

---

## Goal Achievement

### Observable Truths (from ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | App connects to Telegram Bot API and receives updates via long polling without triggering 409 conflict with the Mac-side poller | VERIFIED | Two-bot architecture: `TelegramApiImpl` uses separate `relayBotToken` for `getUpdates`, `commandBotToken` for `sendMessage`. `PollingService` constructs relay API independently from Koin singletons. Named qualifiers `relayApi`/`commandApi` enforce separation in `SharedModule.kt`. |
| 2 | App can send a message in zellij-claude format (`@kuerzel message`) and see it arrive on the Mac side | VERIFIED | `TelegramRepositoryImpl.sendCommand()` calls `commandApi.sendMessage("@$kuerzel $message")` (line 68). `StatusViewModel.sendRawCommand()` delegates to `repository.sendRawCommand()`. StatusScreen wires a command input field that calls this path. |
| 3 | App correctly parses incoming bot messages (distinguishes status updates, responses, and permission requests) | VERIFIED | `RelayMessageParser.parse()` decodes JSON into `RelayMessage` DTO, maps `RelayMessageTypeDto` (STATUS/RESPONSE/PERMISSION/QUESTION/COMPLETION) to domain `RelayMessageType` via exhaustive `when` extension functions. Returns null for non-JSON/plain-text. |
| 4 | App maintains polling connection when switching between WiFi and mobile data | VERIFIED | `NetworkMonitor` wraps `ConnectivityManager.NetworkCallback` as `Flow<Boolean>`, emitting on `onAvailable`/`onLost`. `PollingService` calls `networkMonitor.awaitConnected()` before each `pollLoop()` iteration. Notification text updates to "Reconnecting..." on loss. |
| 5 | Foreground service keeps polling alive when the app is backgrounded or the screen is off | VERIFIED | `PollingService` extends `Service`, calls `startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC)` with `FOREGROUND_SERVICE_DATA_SYNC` permission in manifest. `SupervisorJob() + Dispatchers.IO` scope persists independently of UI lifecycle. `onTimeout` handles Android 15+ forced stop. |

**Score:** 5/5 truths verified (automated checks)

---

### Required Artifacts

#### Plan 01-01 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `settings.gradle.kts` | Multi-module project definition | VERIFIED | Contains `include(":shared", ":androidApp")` (line 16) |
| `gradle/libs.versions.toml` | Version catalog with all dependencies | VERIFIED | Contains `ktor = "3.4.2"`, `sqldelight = "2.0.2"`, `koin = "4.0.4"` |
| `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/TelegramUpdate.kt` | Telegram API response models | VERIFIED | `@Serializable`, `@SerialName("update_id")`, `TelegramUpdate`, `TelegramMessage`, `TelegramUser`, `TelegramChat`, `CallbackQuery` all present |
| `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/RelayMessage.kt` | Relay JSON protocol models | VERIFIED | `@Serializable`, `RelayMessageType` enum, `@SerialName("tool_details")`, `@SerialName("__relay")`, `ToolDetails` all present |
| `shared/src/commonMain/sqldelight/dev/heyduk/relay/Messages.sq` | SQLDelight message schema | VERIFIED | `CREATE TABLE messages`, `INSERT OR IGNORE`, `getMessagesForSession`, `getRecentMessages`, `getLastMessageForSession` all present |

#### Plan 01-02 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/TelegramApi.kt` | Ktor-based Telegram Bot API client | VERIFIED | `interface TelegramApi` with `getUpdates` and `sendMessage`; `TelegramApiImpl` with proper long-poll timeouts (`socketTimeoutMillis = (timeout+5)*1000L`, `requestTimeoutMillis = (timeout+10)*1000L`); `TelegramApiException` |
| `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/TelegramPoller.kt` | Long-polling loop with backoff and offset persistence | VERIFIED | `suspend fun pollLoop()`, exponential backoff `coerceAtMost(30_000L)`, offset persisted before processing, `CancellationException` re-thrown, `interface OffsetProvider` |
| `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/RelayMessageParser.kt` | JSON-to-domain model parser | VERIFIED | `object RelayMessageParser`, `fun parse(updateId: Long, messageText: String, timestamp: Long)`, `ignoreUnknownKeys = true`, DTO-to-domain extension functions |
| `shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/TelegramRepositoryImpl.kt` | Repository wiring polling to DB storage | VERIFIED | `class TelegramRepositoryImpl`, two-bot architecture, SQLDelight `insertOrIgnore` in `init` block, `asFlow().mapToList()` for DB queries |
| `shared/src/commonTest/kotlin/dev/heyduk/relay/data/remote/TelegramApiTest.kt` | Unit tests for API client | VERIFIED | `class TelegramApiTest` present with MockEngine tests |

#### Plan 01-03 Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `androidApp/src/main/java/dev/heyduk/relay/service/PollingService.kt` | Foreground service hosting polling coroutine | VERIFIED | `FOREGROUND_SERVICE_TYPE_DATA_SYNC`, `override fun onTimeout(startId: Int, fgsType: Int)`, `SupervisorJob()`, reads tokens from `dataStore.data.first()`, `stopSelf()` guard on missing config, constructs `TelegramApiImpl` with real tokens |
| `androidApp/src/main/java/dev/heyduk/relay/service/NetworkMonitor.kt` | ConnectivityManager wrapper as Flow<Boolean> | VERIFIED | `registerDefaultNetworkCallback`, `suspend fun awaitConnected()`, `callbackFlow`, `distinctUntilChanged()` |
| `androidApp/src/main/java/dev/heyduk/relay/data/DataStoreOffsetProvider.kt` | OffsetProvider implementation using DataStore | VERIFIED | `class DataStoreOffsetProvider(... ) : OffsetProvider`, `longPreferencesKey`, `dataStore.edit` for persistence |
| `androidApp/src/main/java/dev/heyduk/relay/presentation/status/StatusScreen.kt` | Connection status and recent messages UI | VERIFIED | `@Composable`, connection indicator, `LazyColumn` items from `uiState.recentMessages`, `ContextCompat.startForegroundService` call |
| `androidApp/src/main/java/dev/heyduk/relay/presentation/setup/SetupScreen.kt` | Bot token configuration screen | VERIFIED | `@Composable`, `OutlinedTextField` fields, uses key `relay_bot_token` (via `SetupViewModel.RELAY_BOT_TOKEN_KEY`) |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `shared/build.gradle.kts` | `gradle/libs.versions.toml` | version catalog aliases | WIRED | Uses `alias(libs.plugins.*)` and `implementation(libs.ktor.client.core)` etc. |
| `androidApp/build.gradle.kts` | `:shared` | project dependency | WIRED | `implementation(project(":shared"))` at line 38 |
| `TelegramPoller` | `TelegramApi.getUpdates` | suspend call in loop | WIRED | `api.getUpdates(offset = offset, timeout = 30)` at line 34 |
| `TelegramPoller` | `RelayMessageParser` | parse incoming message text | WIRED | `parser.parse(updateId = ..., messageText = text, timestamp = ...)` at line 43 |
| `TelegramRepositoryImpl` | `TelegramPoller` | collects updates flow | WIRED | `poller.updates.collect { update -> ... }` in `init` block; `poller.updates` delegated to `updates` property |
| `TelegramRepositoryImpl` | SQLDelight messages table | `insertOrIgnore` on each update | WIRED | `database.messagesQueries.insertOrIgnore(...)` in both `init` collector and `PollingService` |
| `PollingService` | `TelegramPoller.pollLoop` | coroutine launch in `onStartCommand` | WIRED | `poller.pollLoop()` at line 114 inside `while (isActive)` loop |
| `PollingService` | `NetworkMonitor` | awaits connectivity before polling resumes | WIRED | `networkMonitor.awaitConnected()` at line 112; `networkMonitor.isConnected.collect` for notification updates |
| `DataStoreOffsetProvider` | `TelegramPoller` | implements `OffsetProvider` interface | WIRED | `class DataStoreOffsetProvider(...) : OffsetProvider` implements `getOffset()`/`setOffset()` |
| `PollingService` | DataStore | reads tokens in `onStartCommand` | WIRED | `dataStore.data.first()` at line 61, extracts all three token keys before constructing `TelegramApiImpl` |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `StatusScreen.kt` | `uiState.recentMessages` | `StatusViewModel.uiState` (StateFlow) | Yes -- `repository.getRecentMessages()` queries SQLDelight `messages` table via `asFlow().mapToList()` | FLOWING |
| `StatusScreen.kt` | `uiState.isConnected` | `networkMonitor.isConnected` Flow | Yes -- `ConnectivityManager.NetworkCallback` emits real connectivity events | FLOWING |
| `SetupScreen.kt` | `uiState.relayBotToken` etc. | `SetupViewModel.loadExisting()` reads DataStore | Yes -- `dataStore.data.first()` reads persisted keys | FLOWING |

---

### Behavioral Spot-Checks

Step 7b: SKIPPED for device-dependent behaviors (foreground service, actual Telegram API calls, network transitions). The APK exists at `androidApp/build/outputs/apk/debug/androidApp-debug.apk` (69 MB), confirming `assembleDebug` succeeded.

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| APK assembled | `ls -lh androidApp/build/outputs/apk/debug/androidApp-debug.apk` | 69M Apr 2 18:34 | PASS |
| Test classes exist | `grep "class.*Test"` in all 3 test files | TelegramApiTest, RelayMessageParserTest, TelegramPollerTest found | PASS |
| No Hilt/Room in source | `grep -rn "Hilt\|@RoomDatabase" androidApp/src/ shared/src/` | No matches | PASS |
| `@kuerzel` format enforced | `grep '@\$kuerzel' TelegramRepositoryImpl.kt` | `commandApi.sendMessage("@$kuerzel $message")` at line 68 | PASS |
| 409 resolution: two separate API instances | `grep "named.*relayApi\|named.*commandApi"` | Both named qualifiers present in SharedModule | PASS |
| CancellationException not swallowed | `grep "CancellationException"` in TelegramPoller | `throw e` at line 54 | PASS |

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| TRNS-01 | 01-01, 01-02, 01-03 | App connects to Telegram Bot API via long polling (getUpdates) | SATISFIED | `TelegramApiImpl.getUpdates()` calls `$baseUrl/getUpdates` with proper long-poll timeouts; `TelegramPoller.pollLoop()` runs continuous loop; `PollingService` hosts the loop as foreground service |
| TRNS-02 | 01-01, 01-02 | App resolves 409 conflict with existing Mac-side bot polling | SATISFIED | Two-bot architecture: Relay app uses its own dedicated bot token (`relay_bot_token`) for reading. Mac-side poller uses a different bot token. No two consumers share the same bot token. Named Koin qualifiers enforce separation at the DI layer. |
| TRNS-03 | 01-02, 01-03 | App sends messages in zellij-claude format (`@kuerzel message`) | SATISFIED | `TelegramRepositoryImpl.sendCommand(kuerzel, message)` formats as `"@$kuerzel $message"` and sends via `commandApi` (the existing zellij-claude bot token) |
| TRNS-04 | 01-01, 01-02 | App receives and parses incoming bot messages (status, responses, permission requests) | SATISFIED | `RelayMessageParser` handles all five message types (STATUS/RESPONSE/PERMISSION/QUESTION/COMPLETION); DTO-to-domain mapping is exhaustive; invalid JSON and plain text return null gracefully |
| TRNS-05 | 01-03 | App maintains connection through network transitions (WiFi/mobile) | SATISFIED | `NetworkMonitor` detects transitions via `ConnectivityManager.NetworkCallback`; `PollingService` awaits connectivity with `networkMonitor.awaitConnected()` before each poll iteration; notification updates to "Reconnecting..." on loss |

All 5 requirements for Phase 1 are satisfied in code. No orphaned requirements found (REQUIREMENTS.md traceability table maps only TRNS-01 through TRNS-05 to Phase 1).

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `SetupViewModel.kt` | 95, 99 | Validation calls `sendMessage("Relay bot connected")` instead of `getMe` as documented | Warning | Sends visible test messages to the Telegram chat on every "Validate & Save" press. Comments in the file say "getMe endpoint" but implementation uses `sendMessage`. Functionally validates connectivity and write permission, but pollutes the chat. |

No blocker anti-patterns found. No TODO/FIXME/placeholder comments in transport code. No empty implementations. No hardcoded static returns from DB queries.

---

### Human Verification Required

#### 1. End-to-End Device Test

**Test:** Install `androidApp/build/outputs/apk/debug/androidApp-debug.apk` on a physical Android device. Open the app. Enter Relay bot token (new bot from @BotFather), Command bot token (existing zellij-claude token from `~/.config/zellij-claude/telegram.json`), and Chat ID. Tap "Validate & Save". Navigate to Status screen. Tap "Start Polling". Send a test JSON message to the relay bot via curl. Type `/ls` in the command input and tap Send.

**Expected:**
- Setup screen appears on first launch
- After "Validate & Save": confirmation message appears, app navigates to Status
- Foreground notification "Relay connected" is visible in notification drawer
- Incoming JSON message appears in the message list on the Status screen within seconds
- `/ls` command arrives in the zellij-claude bot chat on the Mac
- Toggle WiFi off: notification changes to "Reconnecting..."
- Toggle WiFi on: notification changes back to "Relay connected"
- Background the app: polling notification persists, new messages still appear

**Why human:** Network behavior, notification visibility, actual Telegram API connectivity, and background service persistence require a physical device with real bot credentials.

#### 2. Validate Token Validation UX

**Test:** On the Setup screen, tap "Validate & Save" with valid tokens.

**Expected:** The user should see a "Configuration saved successfully" message. The user should also verify whether receiving "Relay bot connected" and "Command bot connected" messages in their Telegram chat is acceptable. If not, the validation logic in `SetupViewModel.validateAndSave()` should be changed to use `getMe` instead of `sendMessage` for the connectivity test.

**Why human:** UX judgment on whether test messages appearing in production chat is acceptable.

---

### Gaps Summary

No blocking gaps found. All five success criteria are satisfied by verified code. All five requirements (TRNS-01 through TRNS-05) have implementation evidence. All artifacts exist, are substantive, and are wired. Data flows are connected end-to-end from SQLDelight through the repository to the UI.

The single warning (validation via `sendMessage` rather than `getMe`) is not a blocker -- the app works correctly and the validation tests real connectivity. It is flagged for the human reviewer to decide whether the UX is acceptable.

The phase cannot be declared fully complete without device-side human verification, as the E2E checkpoint in Plan 03 was auto-approved without actual device testing.

---

_Verified: 2026-04-02T18:40:00Z_
_Verifier: Claude (gsd-verifier)_
