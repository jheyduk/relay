# Phase 1: Transport & Foundation - Research

**Researched:** 2026-04-02
**Domain:** Telegram Bot API long polling, Android foreground services, Ktor HTTP client, Room/DataStore persistence
**Confidence:** HIGH

## Summary

Phase 1 establishes bidirectional communication between the Relay Android app and zellij-claude on the Mac via Telegram Bot API. The critical architectural decision -- using a dedicated second bot token for Relay -- eliminates the 409 single-consumer conflict entirely. Each bot polls independently with its own token; no coordination mechanism is needed.

The core technical challenge is keeping the long-polling connection alive through Android's aggressive background restrictions. A foreground service with type `dataSync` is the correct approach, but Android 15+ imposes a **6-hour timeout per 24-hour period** for dataSync services. This is manageable for our use case (user opens the app regularly, resetting the timer), but requires implementing `Service.onTimeout()` to degrade gracefully. The Mac-side zellij-claude hooks need minimal changes: dual-send support to both bot tokens with different formats (emoji for existing bot, JSON for Relay bot).

**Primary recommendation:** Start with Android project scaffolding (Kotlin/Compose/Hilt/Ktor/Room), implement the Telegram polling loop with exponential backoff and offset persistence, then add the foreground service with network monitoring. The Mac-side hook changes are a separate, small work item that unblocks end-to-end testing.

<user_constraints>

## User Constraints (from CONTEXT.md)

### Locked Decisions
- Use a dedicated second Telegram bot token for Relay -- completely independent from the existing zellij-claude bot
- No coordination mechanism needed between the two bots -- they operate independently
- Mac-side zellij-claude hooks send notifications to both bot tokens in parallel (existing Telegram format to old bot, JSON to Relay bot)
- Relay sends commands to the existing zellij-claude bot (same format: `@kuerzel message`, `/ls`, etc.) -- @main session reads from that bot as before
- Mac-side sends structured JSON to the Relay bot (type, session, status, message, tool_details fields)
- Existing Telegram bot continues receiving emoji-formatted messages for human readability -- system works without the app
- zellij-claude config gets a `relay_bot_token` entry in `~/.config/zellij-claude/telegram.json` -- when not set, no Relay messages are sent
- Minimal zellij-claude changes required: hooks need dual-output support
- DataStore Preferences for polling offset (single Long value)
- Room DB for message storage with update_id as PK with IGNORE conflict strategy
- Foreground Service type `dataSync` for continuous polling
- ConnectivityManager Callback for network change detection + immediate reconnect
- Exponential backoff on errors: 1s -> 2s -> 4s -> max 30s

### Claude's Discretion
- Android project structure and package naming
- Exact Ktor client configuration (timeouts, retry policies)
- Room database schema for messages
- JSON serialization format details (kotlinx.serialization)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.

</user_constraints>

<phase_requirements>

## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| TRNS-01 | App connects to Telegram Bot API via long polling (getUpdates) | Ktor client with OkHttp engine, HttpTimeout plugin configured for long polling (socketTimeout 35s, requestTimeout disabled for poll requests). getUpdates with timeout=30 parameter. |
| TRNS-02 | App resolves 409 conflict with existing Mac-side bot polling | Dedicated second bot token eliminates 409 entirely. Two independent bots, two independent getUpdates streams. No conflict possible. |
| TRNS-03 | App sends messages in zellij-claude format (`@kuerzel message`) | sendMessage to existing bot's chat_id with `@kuerzel message` text format. Relay reads from its own bot, writes to the existing bot. |
| TRNS-04 | App receives and parses incoming bot messages (status, responses, permission requests) | Mac-side sends structured JSON to Relay bot. Parse with kotlinx.serialization into domain models (RelayMessage with type/session/status/message/tool_details). |
| TRNS-05 | App maintains connection through network transitions (WiFi/mobile) | ConnectivityManager.registerDefaultNetworkCallback() with callbackFlow, exponential backoff (1s/2s/4s/max 30s), foreground service with dataSync type. |

</phase_requirements>

## Standard Stack

### Core (Phase 1 specific)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin | 2.3.20 | Language | Required by AGP 9. Verified in project STACK.md. |
| AGP | 9.1.0 | Build system | Current stable. Verified in project STACK.md. |
| Jetpack Compose BOM | 2026.03.00 | UI toolkit (minimal in Phase 1) | For the basic settings/status screen. |
| Hilt | 2.56 | Dependency injection | KSP-based. Provides @HiltAndroidApp, @HiltViewModel, @Inject. |
| Ktor Client | 3.4.2 | HTTP client for Telegram API | OkHttp engine for Android. kotlinx.serialization integration. |
| Room | 2.8.4 | Message persistence | KSP processor. Stay on 2.x per STACK.md guidance. |
| kotlinx-serialization-json | 1.10.0 | JSON parsing | Telegram API responses + Relay JSON protocol. |
| DataStore Preferences | 1.2.1 | Polling offset storage | Single Long value, coroutine-based. |
| Timber | 5.0.1 | Logging | Zero-overhead in release. Essential for debugging polling. |

### Supporting (Phase 1)
| Library | Purpose | When to Use |
|---------|---------|-------------|
| Lifecycle Runtime Compose | `collectAsStateWithLifecycle()` | Every Flow collection in Compose |
| Navigation Compose 2.9.7 | Minimal navigation (settings <-> status) | Screen transitions |

**Installation:** Managed via Gradle version catalogs (`libs.versions.toml`). No `npm install` -- this is an Android/Gradle project.

## Architecture Patterns

### Recommended Project Structure (Phase 1 subset)
```
app/src/main/java/com/relay/
├── RelayApp.kt                  # @HiltAndroidApp
├── di/
│   ├── AppModule.kt             # App-scoped: DataStore, SharedFlow<Update>
│   ├── NetworkModule.kt         # Ktor HttpClient with OkHttp engine
│   └── DatabaseModule.kt        # Room database provider
├── data/
│   ├── remote/
│   │   ├── TelegramApi.kt       # getUpdates, sendMessage (raw Ktor calls)
│   │   ├── TelegramPoller.kt    # Long-polling loop with backoff
│   │   └── dto/
│   │       ├── TelegramUpdate.kt    # Telegram API response models
│   │       └── RelayMessage.kt      # JSON protocol from Mac-side
│   ├── local/
│   │   ├── RelayDatabase.kt     # Room @Database
│   │   ├── dao/MessageDao.kt
│   │   └── entity/MessageEntity.kt
│   └── repository/
│       └── TelegramRepositoryImpl.kt
├── domain/
│   ├── model/
│   │   ├── SessionStatus.kt     # Enum: Working, Waiting, Ready, Shell
│   │   └── RelayUpdate.kt       # Parsed update domain model
│   └── repository/
│       └── TelegramRepository.kt  # Interface
├── service/
│   ├── PollingService.kt        # Foreground service (dataSync)
│   └── NetworkMonitor.kt        # ConnectivityManager wrapper
├── presentation/
│   └── status/
│       └── StatusScreen.kt      # Minimal: connection status + last messages
└── MainActivity.kt
```

### Pattern 1: Two-Bot Architecture
**What:** Relay uses its own dedicated bot token. It reads from its own bot (receiving JSON from Mac-side) and writes to the existing zellij-claude bot (sending commands in `@kuerzel` format).
**When to use:** Always -- this is the core architecture decision.
**Key insight:** The Relay app needs TWO tokens configured:
1. `relay_bot_token` -- for getUpdates (reading) and receiving JSON status messages
2. `existing_bot_token` -- for sendMessage (writing commands that @main reads)

Both tokens share the same `chat_id` (the user's Telegram chat).

```kotlin
// Two API instances in NetworkModule
@Provides @Singleton @Named("relay")
fun provideRelayApi(client: HttpClient, prefs: DataStore<Preferences>): TelegramApi {
    // Uses relay_bot_token -- for polling (getUpdates)
    return TelegramApi(client, relayBotToken)
}

@Provides @Singleton @Named("command")
fun provideCommandApi(client: HttpClient, prefs: DataStore<Preferences>): TelegramApi {
    // Uses existing zellij-claude bot token -- for sending commands
    return TelegramApi(client, existingBotToken)
}
```

### Pattern 2: Long-Polling Loop with Structured Backoff
**What:** Infinite coroutine loop calling getUpdates with timeout=30s. On success, process updates and continue. On error, exponential backoff (1s -> 2s -> 4s -> max 30s). On success after error, reset backoff.
**When to use:** The single mechanism for receiving updates.

```kotlin
class TelegramPoller @Inject constructor(
    @Named("relay") private val api: TelegramApi,
    private val offsetStore: DataStore<Preferences>,
    private val updateFlow: MutableSharedFlow<TelegramUpdate>,
    private val networkMonitor: NetworkMonitor
) {
    private val OFFSET_KEY = longPreferencesKey("polling_offset")

    suspend fun pollLoop() {
        var backoffMs = 0L
        while (currentCoroutineContext().isActive) {
            if (backoffMs > 0) {
                delay(backoffMs)
            }

            try {
                // Wait for network before polling
                networkMonitor.awaitConnected()

                val offset = offsetStore.data.first()[OFFSET_KEY] ?: 0L
                val updates = api.getUpdates(
                    offset = offset,
                    timeout = 30,
                    allowedUpdates = listOf("message", "callback_query")
                )

                if (updates.isNotEmpty()) {
                    val newOffset = updates.maxOf { it.updateId } + 1
                    // Persist offset BEFORE processing (at-least-once delivery)
                    offsetStore.edit { it[OFFSET_KEY] = newOffset }
                    updates.forEach { updateFlow.emit(it) }
                }

                backoffMs = 0 // Reset on success
            } catch (e: CancellationException) {
                throw e // Don't catch coroutine cancellation
            } catch (e: Exception) {
                Timber.w(e, "Polling error, backing off")
                backoffMs = (backoffMs * 2).coerceIn(1000, 30_000)
                if (backoffMs == 0L) backoffMs = 1000
            }
        }
    }
}
```

### Pattern 3: ConnectivityManager as callbackFlow
**What:** Wraps Android's ConnectivityManager.NetworkCallback into a Kotlin Flow<Boolean> for reactive network state.
**When to use:** Polling loop waits for network availability before making requests.

```kotlin
class NetworkMonitor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val isConnected: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
        }
        // Emit initial state
        val current = connectivityManager.activeNetwork != null
        trySend(current)

        connectivityManager.registerDefaultNetworkCallback(callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    suspend fun awaitConnected() {
        isConnected.first { it }
    }
}
```

### Pattern 4: Foreground Service with dataSync Type
**What:** Android foreground service that hosts the polling coroutine. Shows persistent notification with connection status.
**Critical:** Must implement `Service.onTimeout(int, int)` for Android 15+ (6-hour limit per 24 hours).

```kotlin
class PollingService : Service() {
    @Inject lateinit var poller: TelegramPoller
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Relay connected")
        startForeground(NOTIFICATION_ID, notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        scope.launch { poller.pollLoop() }
        return START_STICKY
    }

    // Android 15+ timeout handler
    override fun onTimeout(startId: Int, fgsType: Int) {
        Timber.w("dataSync timeout reached, stopping polling")
        stopSelf()
        // Service will restart when user opens app (resets 6-hour timer)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
```

### Anti-Patterns to Avoid
- **Using a single bot token for both Relay and zellij-claude polling:** Causes 409 conflict. The entire phase revolves around avoiding this.
- **Acknowledging offset before persisting:** If app crashes between acknowledge and persist, messages are lost permanently. Always persist offset first.
- **WorkManager for polling:** 15-minute minimum interval is unacceptable for real-time messaging. Use foreground service.
- **Hardcoding bot tokens:** Store in DataStore (encrypted if desired) with a first-launch setup screen.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HTTP client | Raw HttpURLConnection | Ktor Client 3.4.2 with OkHttp engine | Connection pooling, coroutine integration, JSON serialization, timeout handling |
| JSON parsing | Manual string parsing | kotlinx.serialization | Compile-time safe, @Serializable data classes, no reflection |
| Background persistence | SharedPreferences for offset | DataStore Preferences | Thread-safe, coroutine-based, no ANR risk |
| Message dedup | Manual HashMap tracking | Room IGNORE conflict strategy on update_id PK | Database-level dedup, survives restarts |
| Network monitoring | BroadcastReceiver for CONNECTIVITY_ACTION | ConnectivityManager.registerDefaultNetworkCallback | The broadcast was deprecated in API 28. Callback is the modern API. |

**Key insight:** Every component in this phase has a well-established Android Jetpack solution. The only custom logic is the message protocol adapter (parsing relay JSON format).

## Common Pitfalls

### Pitfall 1: Ktor Socket Timeout Kills Long Poll
**What goes wrong:** Default Ktor socket timeout (15s) fires before the Telegram long-poll timeout (30s) returns, causing constant reconnections and missed updates.
**Why it happens:** Ktor's `socketTimeoutMillis` measures inactivity between data packets. During a 30-second long poll with no updates, there's 30 seconds of inactivity.
**How to avoid:** Set `socketTimeoutMillis` to at least 35000ms (30s poll + 5s buffer). Disable `requestTimeoutMillis` for poll requests or set it very high (60000ms). Keep short timeouts for sendMessage calls.
**Warning signs:** Frequent `SocketTimeoutException` in logs, updates arriving in bursts instead of real-time.

```kotlin
// Global client config
install(HttpTimeout) {
    connectTimeoutMillis = 10_000
    socketTimeoutMillis = 15_000   // Default for normal requests
    requestTimeoutMillis = 15_000
}

// Per-request override for long polling
val updates = client.get("$baseUrl/getUpdates") {
    parameter("offset", offset)
    parameter("timeout", 30)
    timeout {
        requestTimeoutMillis = 60_000   // 60s for the entire poll cycle
        socketTimeoutMillis = 35_000    // 35s to exceed Telegram's 30s hold
    }
}
```

### Pitfall 2: dataSync Foreground Service 6-Hour Timeout (Android 15+)
**What goes wrong:** After 6 cumulative hours of dataSync foreground service runtime in a 24-hour period, Android 15+ calls `onTimeout()` and forces the service to stop. If not handled, the system throws `ForegroundServiceDidNotStopInTimeException`.
**Why it happens:** Android 15 introduced quota limits on dataSync and mediaProcessing service types. The 6-hour limit is shared across all dataSync services in the app.
**How to avoid:** Implement `Service.onTimeout(int, int)` and call `stopSelf()` immediately. The timer resets when the user brings the app to the foreground. For a developer tool that the user checks regularly, this is manageable. Show a notification prompting the user to open the app if the service was stopped.
**Warning signs:** `ForegroundServiceStartNotAllowedException` when trying to start the service.

### Pitfall 3: Offset Persistence Race Condition
**What goes wrong:** App crashes after calling getUpdates (which implicitly acknowledges via the next offset) but before persisting the new offset locally. On restart, the app re-fetches already-processed updates, causing duplicates. Or worse: if offset is persisted but processing fails, messages are lost.
**Why it happens:** getUpdates confirmation is implicit (next call with higher offset confirms all previous). Local persistence and remote confirmation are two separate operations.
**How to avoid:** Persist the new offset locally BEFORE the next getUpdates call. Use Room's IGNORE conflict strategy on update_id to handle re-delivered messages gracefully. This gives at-least-once delivery semantics.
**Warning signs:** Duplicate messages after app restart. Missing messages when the app was killed during processing.

### Pitfall 4: CancellationException Swallowed in Catch-All
**What goes wrong:** The polling loop catches `Exception` broadly for backoff, accidentally catching `CancellationException`, which prevents the coroutine from cancelling cleanly. The polling loop becomes unstoppable.
**Why it happens:** `CancellationException` is a subclass of `Exception` in Kotlin. A `catch (e: Exception)` block catches it.
**How to avoid:** Always rethrow `CancellationException` first: `catch (e: CancellationException) { throw e }` before any general catch block. Or use `runCatching` with `getOrElse` which handles this correctly.
**Warning signs:** Foreground service doesn't stop when expected. Memory leaks from lingering coroutines.

### Pitfall 5: Missing FOREGROUND_SERVICE Permission on Android 14+
**What goes wrong:** App crashes on Android 14+ when trying to start a foreground service because `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_DATA_SYNC` permissions aren't declared.
**Why it happens:** Android 14 requires both the base `FOREGROUND_SERVICE` permission and the type-specific permission in the manifest.
**How to avoid:** Declare both permissions and the service type in AndroidManifest.xml:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />  <!-- Android 13+ -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<service
    android:name=".service.PollingService"
    android:foregroundServiceType="dataSync"
    android:exported="false" />
```

## Code Examples

### Telegram API Data Transfer Objects (kotlinx.serialization)

```kotlin
// Telegram Bot API response wrapper
@Serializable
data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T? = null,
    val description: String? = null,
    @SerialName("error_code") val errorCode: Int? = null
)

// Telegram Update object
@Serializable
data class TelegramUpdate(
    @SerialName("update_id") val updateId: Long,
    val message: TelegramMessage? = null,
    @SerialName("callback_query") val callbackQuery: CallbackQuery? = null
)

@Serializable
data class TelegramMessage(
    @SerialName("message_id") val messageId: Long,
    val text: String? = null,
    val date: Long,
    val from: TelegramUser? = null,
    val chat: TelegramChat
)

@Serializable
data class TelegramUser(
    val id: Long,
    @SerialName("is_bot") val isBot: Boolean,
    @SerialName("first_name") val firstName: String
)

@Serializable
data class TelegramChat(
    val id: Long,
    val type: String
)

@Serializable
data class CallbackQuery(
    val id: String,
    val data: String? = null,
    val message: TelegramMessage? = null
)
```

### Relay JSON Protocol (from Mac-side hooks)

Based on the CONTEXT.md specification and existing hook code analysis:

```kotlin
// JSON messages sent by Mac-side hooks to Relay bot
@Serializable
data class RelayMessage(
    val type: RelayMessageType,   // status, response, permission, question, completion
    val session: String,           // kuerzel
    val status: SessionStatus? = null,
    val message: String,
    @SerialName("tool_details") val toolDetails: ToolDetails? = null,
    val timestamp: Long = 0
)

@Serializable
enum class RelayMessageType {
    @SerialName("status") STATUS,
    @SerialName("response") RESPONSE,
    @SerialName("permission") PERMISSION,
    @SerialName("question") QUESTION,
    @SerialName("completion") COMPLETION
}

@Serializable
enum class SessionStatus {
    @SerialName("working") WORKING,
    @SerialName("waiting") WAITING,
    @SerialName("ready") READY,
    @SerialName("shell") SHELL
}

@Serializable
data class ToolDetails(
    @SerialName("tool_name") val toolName: String? = null,
    val command: String? = null,
    @SerialName("file_path") val filePath: String? = null
)
```

### Room Message Entity

```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "update_id") val updateId: Long,
    val session: String,             // kuerzel
    val type: String,                // status/response/permission/question/completion
    val message: String,
    val status: String? = null,
    val timestamp: Long,
    @ColumnInfo(name = "is_from_relay") val isFromRelay: Boolean = false  // true = sent by app
)

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE session = :session ORDER BY timestamp ASC")
    fun getMessagesForSession(session: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT 50")
    fun getRecentMessages(): Flow<List<MessageEntity>>
}
```

### TelegramApi Implementation

```kotlin
class TelegramApi(
    private val client: HttpClient,
    private val botToken: String,
    private val chatId: String
) {
    private val baseUrl = "https://api.telegram.org/bot$botToken"

    suspend fun getUpdates(
        offset: Long = 0,
        timeout: Int = 30,
        allowedUpdates: List<String> = listOf("message", "callback_query")
    ): List<TelegramUpdate> {
        val response: TelegramResponse<List<TelegramUpdate>> = client.get("$baseUrl/getUpdates") {
            parameter("offset", offset)
            parameter("timeout", timeout)
            parameter("allowed_updates", Json.encodeToString(allowedUpdates))
            timeout {
                requestTimeoutMillis = (timeout + 10) * 1000L
                socketTimeoutMillis = (timeout + 5) * 1000L
            }
        }.body()

        if (!response.ok) {
            throw TelegramApiException(response.errorCode ?: 0, response.description ?: "Unknown error")
        }
        return response.result ?: emptyList()
    }

    suspend fun sendMessage(text: String): TelegramMessage {
        val response: TelegramResponse<TelegramMessage> = client.post("$baseUrl/sendMessage") {
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("chat_id", chatId)
                put("text", text)
            })
        }.body()

        if (!response.ok) {
            throw TelegramApiException(response.errorCode ?: 0, response.description ?: "Unknown error")
        }
        return response.result!!
    }
}

class TelegramApiException(val errorCode: Int, message: String) : Exception(message)
```

### Mac-Side Hook Changes (telegram-helper.cjs)

The existing `telegram-helper.cjs` needs a `sendToRelay()` function. Minimal change:

```javascript
// Addition to telegram-helper.cjs
function loadRelayConfig() {
  try {
    const cfg = JSON.parse(readFileSync(CONFIG_FILE, 'utf8'));
    return {
      token: cfg.relay_bot_token || null,
      chatId: cfg.relay_chat_id || cfg.chatId  // Falls back to same chat
    };
  } catch { return { token: null, chatId: null }; }
}

function sendToRelay(jsonPayload) {
  const relay = loadRelayConfig();
  if (!relay.token || !relay.chatId) return null;  // Relay not configured, skip silently
  try {
    const result = execSync(
      `curl -s -X POST "https://api.telegram.org/bot${relay.token}/sendMessage" -H "Content-Type: application/json" --data-binary @-`,
      { input: JSON.stringify({ chat_id: relay.chatId, text: JSON.stringify(jsonPayload) }), timeout: 5000, encoding: 'utf8' }
    );
    return JSON.parse(result);
  } catch { return null; }
}

module.exports = { send, sendWithButtons, sendToRelay, apiCall, getKuerzel: getKürzel, loadToken, getChatId };
```

The hooks (permission-notify.cjs, session-stop.cjs, ask-notify.cjs) each add a parallel `sendToRelay()` call with structured JSON alongside their existing `send()`/`sendWithButtons()` calls.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| SharedPreferences | DataStore Preferences | 2021+ (stable) | Thread-safe, coroutine-based, no ANR risk |
| KAPT annotation processing | KSP | 2023+ (mature) | 2x faster builds, required by AGP 9 |
| BroadcastReceiver for network | ConnectivityManager.NetworkCallback | API 28+ (2018) | The old broadcast is deprecated |
| Unlimited foreground services | dataSync 6h quota (Android 15) | 2024 | Must implement onTimeout() |
| CONNECTIVITY_ACTION broadcast | registerDefaultNetworkCallback | API 24+ | Old broadcast no longer delivered to manifest receivers |

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java/JDK | Gradle/AGP build | Yes | OpenJDK 25.0.2 | -- |
| Gradle | Build system | Yes | 9.4.1 | -- |
| Android SDK | Build, run, test | **No** | -- | Install via `sdkmanager` or Android Studio |
| Android Studio | IDE, emulator | **No** | -- | CLI-only with sdkmanager + Gradle wrapper |
| ADB | Device deployment | **No** | -- | Comes with Android SDK platform-tools |
| ANDROID_HOME env var | SDK discovery | **No** | not set | Set after SDK install |

**Missing dependencies with no fallback:**
- **Android SDK** -- Required to compile. Must be installed before any code can build. Install via Android Studio (recommended) or command-line tools.
- **ANDROID_HOME** -- Environment variable pointing to SDK location. Required by Gradle.

**Missing dependencies with fallback:**
- **Android Studio** -- Not strictly required if using CLI tools + Neovim, but provides emulator management and Layout Inspector. The user uses Neovim, so CLI-based SDK management is viable.

**Recommended setup commands:**
```bash
# Option 1: Install Android command-line tools via Homebrew
brew install --cask android-commandlinetools
export ANDROID_HOME="$HOME/Library/Android/sdk"

# Then install required SDK components
sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"

# Option 2: Install Android Studio (includes SDK)
brew install --cask android-studio
# Then configure ANDROID_HOME via Android Studio's SDK Manager
```

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 5 + kotlinx-coroutines-test + Turbine (for Flow testing) |
| Config file | None yet -- Wave 0 creates test infrastructure |
| Quick run command | `./gradlew testDebugUnitTest` |
| Full suite command | `./gradlew testDebugUnitTest connectedDebugAndroidTest` |

### Phase Requirements -> Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TRNS-01 | getUpdates returns parsed updates | unit | `./gradlew testDebugUnitTest --tests "*.TelegramApiTest"` | No -- Wave 0 |
| TRNS-01 | Polling loop processes updates and persists offset | unit | `./gradlew testDebugUnitTest --tests "*.TelegramPollerTest"` | No -- Wave 0 |
| TRNS-02 | Two-bot architecture (separate tokens, no 409) | unit | `./gradlew testDebugUnitTest --tests "*.TwoTokenConfigTest"` | No -- Wave 0 |
| TRNS-03 | sendMessage formats `@kuerzel message` correctly | unit | `./gradlew testDebugUnitTest --tests "*.MessageProtocolTest"` | No -- Wave 0 |
| TRNS-04 | JSON relay messages parsed into domain models | unit | `./gradlew testDebugUnitTest --tests "*.RelayMessageParserTest"` | No -- Wave 0 |
| TRNS-05 | Backoff increases on error, resets on success | unit | `./gradlew testDebugUnitTest --tests "*.BackoffStrategyTest"` | No -- Wave 0 |
| TRNS-05 | Network monitor emits connectivity changes | unit | `./gradlew testDebugUnitTest --tests "*.NetworkMonitorTest"` | No -- Wave 0 |

### Sampling Rate
- **Per task commit:** `./gradlew testDebugUnitTest -x lint`
- **Per wave merge:** `./gradlew testDebugUnitTest`
- **Phase gate:** Full unit test suite green before `/gsd:verify-work`

### Wave 0 Gaps
- [ ] `app/src/test/` directory structure -- does not exist yet
- [ ] Test dependencies in `build.gradle.kts`: JUnit 5, kotlinx-coroutines-test, Turbine, MockK
- [ ] Ktor mock engine dependency for API testing: `ktor-client-mock`
- [ ] Room in-memory database for DAO testing
- [ ] `app/src/test/java/com/relay/data/remote/TelegramApiTest.kt` -- covers TRNS-01
- [ ] `app/src/test/java/com/relay/data/remote/TelegramPollerTest.kt` -- covers TRNS-01, TRNS-05
- [ ] `app/src/test/java/com/relay/data/remote/RelayMessageParserTest.kt` -- covers TRNS-04
- [ ] `app/src/test/java/com/relay/data/remote/MessageProtocolTest.kt` -- covers TRNS-03

## Open Questions

1. **Bot Token Setup UX**
   - What we know: The app needs two tokens (relay bot + existing bot) and a chat_id. These are entered once during initial setup.
   - What's unclear: Should there be a QR code / deep link flow, or just a simple text field? For a personal dev tool, plain text input is likely sufficient.
   - Recommendation: Simple settings screen with three text fields. Validate by calling getMe on each token. Low priority for Phase 1 -- can hardcode for initial development.

2. **Relay JSON inside Telegram text field**
   - What we know: Mac-side sends JSON as the `text` field of a sendMessage call. Relay parses the message text as JSON.
   - What's unclear: Should the JSON be the raw text, or should there be a marker/prefix to distinguish relay JSON from human messages?
   - Recommendation: Attempt JSON parse on every message from the relay bot. If parse fails, treat as raw text. The relay bot only receives messages from the Mac-side hooks, so all messages should be valid JSON. Add a `"__relay": true` field as a safety marker.

3. **Notification Channel for Foreground Service**
   - What we know: Foreground service requires a persistent notification. Android requires notification channels (API 26+).
   - What's unclear: Exact channel configuration (importance level, sound).
   - Recommendation: Use IMPORTANCE_LOW for the polling service notification (no sound, minimal presence). Create a separate IMPORTANCE_HIGH channel for permission requests (Phase 4).

## Sources

### Primary (HIGH confidence)
- Telegram Bot API official documentation (getUpdates, sendMessage, Update object) -- https://core.telegram.org/bots/api
- Android foreground service types documentation -- https://developer.android.com/develop/background-work/services/fgs/service-types
- Android foreground service timeout documentation -- https://developer.android.com/develop/background-work/services/fgs/timeout
- Ktor HttpTimeout plugin documentation -- https://ktor.io/docs/client-timeout.html
- Project STACK.md -- verified library versions and compatibility matrix
- zellij-claude source code -- hooks/telegram-helper.cjs, src/dispatch.js, hooks/permission-notify.cjs, hooks/session-stop.cjs, hooks/ask-notify.cjs
- Existing telegram.json config -- `~/.config/zellij-claude/telegram.json` (currently only `chatId`)

### Secondary (MEDIUM confidence)
- Android ConnectivityManager.NetworkCallback with Flow pattern -- multiple Medium articles, consistent pattern
- dataSync 6-hour timeout behavior -- confirmed via Android developer docs + GitHub issues (Anki-Android #19749, Orbot #1263)

### Tertiary (LOW confidence)
- None -- all findings verified with primary or secondary sources

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all versions verified in project STACK.md, compatibility matrix confirmed
- Architecture: HIGH -- two-bot pattern is clean separation, polling/backoff patterns are well-established
- Pitfalls: HIGH -- 409 conflict, offset management, Ktor timeout, dataSync quota all documented extensively
- Environment: HIGH -- verified Android SDK is not installed on this machine

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (stable domain, 30 days)
