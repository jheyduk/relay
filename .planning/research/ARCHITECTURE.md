# Architecture Research

**Domain:** Android companion app for CLI session management via Telegram Bot API transport
**Researched:** 2026-04-02
**Confidence:** HIGH

## System Overview

```
                        Telegram Bot API (HTTPS)
                               |
                               v
┌──────────────────────────────────────────────────────────────┐
│                      Android App: Relay                       │
├──────────────────────────────────────────────────────────────┤
│  Presentation Layer (Jetpack Compose + ViewModels)           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │ SessionList  │  │  ChatView    │  │  Settings    │       │
│  │  Screen/VM   │  │  Screen/VM   │  │  Screen/VM   │       │
│  └──────┬───────┘  └──────┬───────┘  └──────────────┘       │
│         │                 │                                  │
├─────────┴─────────────────┴──────────────────────────────────┤
│  Domain Layer (Use Cases + Models)                           │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌───────────┐ │
│  │ Session    │ │ Message    │ │ Permission │ │  Voice    │ │
│  │ UseCases   │ │ UseCases   │ │ UseCases   │ │ UseCases  │ │
│  └─────┬──────┘ └─────┬──────┘ └─────┬──────┘ └─────┬─────┘ │
│        │              │              │              │        │
├────────┴──────────────┴──────────────┴──────────────┴────────┤
│  Data Layer (Repositories + Data Sources)                    │
│  ┌──────────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │ TelegramRemote   │  │   Room DB    │  │ WhisperLocal  │  │
│  │   DataSource     │  │  DataSource  │  │  DataSource   │  │
│  └────────┬─────────┘  └──────────────┘  └───────────────┘  │
│           │                                                  │
├───────────┴──────────────────────────────────────────────────┤
│  Infrastructure Layer                                        │
│  ┌──────────────────┐  ┌──────────────┐  ┌───────────────┐  │
│  │  Polling Service │  │ Android TTS  │  │ Notification  │  │
│  │  (Foreground)    │  │   Engine     │  │   Manager     │  │
│  └──────────────────┘  └──────────────┘  └───────────────┘  │
└──────────────────────────────────────────────────────────────┘
         │
         v
┌──────────────────────────────────────────────────────────────┐
│              Mac: zellij-claude (existing, unchanged)         │
│  Zellij sessions --> Telegram Bot --> Bot API servers         │
└──────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|----------------|------------------------|
| **TelegramRemoteDataSource** | All HTTP communication with Telegram Bot API (getUpdates, sendMessage, answerCallbackQuery) | Ktor client with kotlinx.serialization |
| **PollingService** | Continuous long-polling loop, runs as Android foreground service | Foreground Service with coroutine loop |
| **SessionRepository** | Aggregates session state from Telegram updates, exposes Flow of sessions | Room DB + in-memory state |
| **MessageRepository** | Stores and retrieves chat messages per session | Room DB with Flow-based queries |
| **PermissionRepository** | Tracks pending permission requests (allow/deny), triggers notifications | In-memory with Room backup |
| **WhisperLocalDataSource** | On-device speech-to-text via whisper.cpp JNI bridge | whisper.cpp native lib + JNI wrapper |
| **Android TTS Engine** | Text-to-speech playback for Claude responses | Built-in android.speech.tts.TextToSpeech |
| **NotificationManager** | Android notifications for permission requests, completions, questions | NotificationCompat with channels |

## Recommended Project Structure

```
app/src/main/java/com/relay/
├── di/                          # Hilt dependency injection modules
│   ├── AppModule.kt             # App-scoped dependencies
│   ├── NetworkModule.kt         # Ktor client, Telegram API config
│   └── DatabaseModule.kt        # Room database provider
├── data/                        # Data layer
│   ├── remote/                  # Telegram Bot API communication
│   │   ├── TelegramApi.kt       # Raw API calls (getUpdates, sendMessage, etc.)
│   │   ├── dto/                 # Telegram API data transfer objects
│   │   │   ├── Update.kt
│   │   │   ├── Message.kt
│   │   │   └── CallbackQuery.kt
│   │   └── TelegramPoller.kt    # Long-polling loop logic
│   ├── local/                   # Local persistence
│   │   ├── RelayDatabase.kt     # Room database definition
│   │   ├── dao/                 # Data access objects
│   │   │   ├── SessionDao.kt
│   │   │   └── MessageDao.kt
│   │   └── entity/              # Room entities
│   │       ├── SessionEntity.kt
│   │       └── MessageEntity.kt
│   ├── voice/                   # Voice processing
│   │   └── WhisperEngine.kt     # whisper.cpp JNI wrapper
│   └── repository/              # Repository implementations
│       ├── SessionRepositoryImpl.kt
│       ├── MessageRepositoryImpl.kt
│       └── PermissionRepositoryImpl.kt
├── domain/                      # Domain layer (pure Kotlin, no Android deps)
│   ├── model/                   # Domain models
│   │   ├── Session.kt           # Session with kuerzel, status, lastActivity
│   │   ├── ChatMessage.kt       # Message with sender, content, timestamp
│   │   ├── PermissionRequest.kt # Allow/Deny request model
│   │   └── SessionStatus.kt     # Enum: Working, Waiting, Ready, Shell
│   ├── repository/              # Repository interfaces
│   │   ├── SessionRepository.kt
│   │   ├── MessageRepository.kt
│   │   └── PermissionRepository.kt
│   └── usecase/                 # Business logic
│       ├── ObserveSessionsUseCase.kt
│       ├── SendMessageUseCase.kt
│       ├── HandlePermissionUseCase.kt
│       ├── DiscoverSessionsUseCase.kt
│       └── TranscribeVoiceUseCase.kt
├── presentation/                # UI layer
│   ├── navigation/              # Compose navigation graph
│   │   └── RelayNavGraph.kt
│   ├── sessions/                # Session list screen
│   │   ├── SessionListScreen.kt
│   │   ├── SessionListViewModel.kt
│   │   └── components/          # Session card, status badge, etc.
│   ├── chat/                    # Chat screen per session
│   │   ├── ChatScreen.kt
│   │   ├── ChatViewModel.kt
│   │   └── components/          # Message bubble, voice button, permission card
│   ├── settings/                # Bot token config, Whisper model management
│   │   ├── SettingsScreen.kt
│   │   └── SettingsViewModel.kt
│   └── theme/                   # Material 3 theming
│       └── Theme.kt
├── service/                     # Android services
│   ├── PollingService.kt        # Foreground service for Telegram polling
│   └── TtsService.kt           # TTS playback management
└── RelayApp.kt                  # Application class with Hilt
```

### Structure Rationale

- **data/remote/**: Isolates all Telegram Bot API specifics. If the transport ever changes, only this package changes.
- **data/voice/**: whisper.cpp is a native library concern, not domain logic. Kept in data layer.
- **domain/**: Pure Kotlin, no Android imports. Repository interfaces live here; implementations in data. This is the lightest layer and enables unit testing without Android framework.
- **presentation/**: Feature-based grouping (sessions, chat, settings) rather than type-based (screens, viewmodels). Each feature is self-contained.
- **service/**: Android-specific lifecycle components that don't fit cleanly into the layer architecture.

## Architectural Patterns

### Pattern 1: Unidirectional Data Flow (UDF)

**What:** State flows down from ViewModel to Compose UI; events flow up from UI to ViewModel. ViewModels expose `StateFlow<UiState>` and accept sealed event classes.
**When to use:** Every screen. This is the default pattern.
**Trade-offs:** Slightly more boilerplate than two-way binding, but dramatically easier to debug, test, and reason about.

**Example:**
```kotlin
// State
data class SessionListUiState(
    val sessions: List<SessionUi> = emptyList(),
    val isLoading: Boolean = true,
    val pendingPermissions: Int = 0
)

// Events
sealed interface SessionListEvent {
    data object RefreshSessions : SessionListEvent
    data class SelectSession(val kuerzel: String) : SessionListEvent
}

// ViewModel
@HiltViewModel
class SessionListViewModel @Inject constructor(
    observeSessions: ObserveSessionsUseCase
) : ViewModel() {
    val uiState: StateFlow<SessionListUiState> = observeSessions()
        .map { sessions -> SessionListUiState(sessions = sessions.map { it.toUi() }, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionListUiState())

    fun onEvent(event: SessionListEvent) { /* ... */ }
}
```

### Pattern 2: Message Protocol Adapter

**What:** A dedicated component that translates between the Telegram Bot API message format and the app's domain models. The existing zellij-claude protocol uses `@kuerzel` prefixes, status patterns in pane titles, and `callback:allow:kuerzel` / `callback:deny:kuerzel` formats.
**When to use:** At the boundary between TelegramRemoteDataSource and the repositories.
**Trade-offs:** Extra layer of indirection, but completely decouples domain models from Telegram's wire format.

**Example:**
```kotlin
object MessageProtocol {
    // Parse incoming: "@infra Claude has finished the task"
    fun parseIncoming(text: String): Pair<String, String>? {
        val match = Regex("^@(\\S+)\\s+(.+)$", RegexOption.DOT_MATCHES_ALL).find(text)
        return match?.let { it.groupValues[1] to it.groupValues[2] }
    }

    // Format outgoing: user types "deploy the thing" for session "infra"
    fun formatOutgoing(kuerzel: String, message: String): String = "@$kuerzel $message"

    // Format permission response
    fun formatPermissionResponse(action: String, kuerzel: String): String = "callback:$action:$kuerzel"

    // Parse session status from /ls response
    fun parseSessionList(text: String): List<SessionInfo> { /* ... */ }
}
```

### Pattern 3: Foreground Service Polling with Coroutine Flow

**What:** A foreground service runs a coroutine-based long-polling loop against `getUpdates`. Updates are broadcast internally via a SharedFlow that repositories subscribe to.
**When to use:** This is the single mechanism for receiving data from Telegram.
**Trade-offs:** Foreground service shows a persistent notification (required by Android), which is actually desirable here -- it shows "Relay is connected" status. Battery impact depends on polling interval and timeout.

**Example:**
```kotlin
class TelegramPoller @Inject constructor(
    private val api: TelegramApi,
    private val updateDispatcher: MutableSharedFlow<Update>
) {
    private var offset: Long = 0

    suspend fun pollLoop() {
        while (currentCoroutineContext().isActive) {
            try {
                val updates = api.getUpdates(offset = offset, timeout = 30)
                updates.forEach { update ->
                    updateDispatcher.emit(update)
                    offset = update.updateId + 1
                }
            } catch (e: Exception) {
                delay(5_000) // backoff on error
            }
        }
    }
}
```

## Data Flow

### Incoming Message Flow (Telegram -> UI)

```
Telegram Bot API
    |  (HTTPS long poll, 30s timeout)
    v
PollingService (Foreground Service)
    |  runs TelegramPoller coroutine
    v
TelegramPoller.pollLoop()
    |  emits Update objects
    v
SharedFlow<Update> (app-wide event bus)
    |          |            |
    v          v            v
Session     Message     Permission
Repository  Repository  Repository
    |          |            |
    v          v            v
Room DB     Room DB     In-Memory + Notification
    |          |            |
    v          v            v
Flow<List<  Flow<List<  Flow<List<
 Session>>   Message>>   PermReq>>
    |          |            |
    v          v            v
ViewModel   ViewModel   ViewModel (or same VM)
    |          |            |
    v          v            v
Compose UI  Compose UI  Compose UI + System Notification
```

### Outgoing Message Flow (UI -> Telegram)

```
User types message or taps Allow/Deny
    |
    v
ViewModel.onEvent(SendMessage / HandlePermission)
    |
    v
UseCase (formats via MessageProtocol)
    |
    v
Repository.send()
    |
    v
TelegramApi.sendMessage() / TelegramApi.answerCallbackQuery()
    |  (HTTPS POST)
    v
Telegram Bot API --> Mac (zellij-claude receives it)
```

### Voice Input Flow

```
User holds voice button
    |
    v
ChatScreen starts AudioRecorder
    |  (records PCM/WAV to temp file)
    v
TranscribeVoiceUseCase
    |
    v
WhisperEngine.transcribe(audioFile)
    |  (JNI call to whisper.cpp, on-device)
    v
Transcription text returned
    |
    v
Displayed in chat input (editable before send)
    |  user confirms
    v
SendMessageUseCase (same as text flow)
```

### Session Discovery Flow

```
App launch / Pull-to-refresh
    |
    v
DiscoverSessionsUseCase
    |
    v
TelegramApi.sendMessage("/ls")
    |
    v
... polling picks up /ls response ...
    |
    v
MessageProtocol.parseSessionList(responseText)
    |
    v
SessionRepository updates session list
    |
    v
UI recomposes with new sessions
```

### Key Data Flows

1. **Session status tracking:** Each incoming message's `@kuerzel` prefix and content patterns (spinner emoji = working, "permission" keyword = waiting) update session status in SessionRepository. This is parsed from message content, not from a dedicated status API.
2. **Permission lifecycle:** Permission requests arrive as messages with inline keyboard markup. The app renders native Allow/Deny UI. User action sends `callback:allow:kuerzel` or `callback:deny:kuerzel` back. The permission is then marked resolved locally.
3. **Unified chat stream:** Both text input and voice input result in the same domain model (ChatMessage with optional `voiceTranscript` flag). The UI renders them identically except for a small microphone indicator on voice-originated messages.

## Scaling Considerations

This is a single-user app. "Scaling" here means handling many concurrent sessions gracefully.

| Concern | At 1-5 sessions | At 10-20 sessions | Notes |
|---------|------------------|---------------------|-------|
| Polling load | Negligible | Same (single poll stream) | All sessions share one getUpdates call |
| UI performance | Trivial | LazyColumn handles it fine | Session list is a flat list |
| Room DB size | Negligible | Hundreds of messages per session, still small | Consider message retention policy eventually |
| Whisper model memory | ~40MB (tiny) to ~500MB (small) | Same (one model loaded) | Model stays loaded while app is active |
| Notification volume | Low | Could get noisy with many permission requests | Group notifications by session |

### Scaling Priorities

1. **First bottleneck:** Notification noise with many active sessions. Mitigation: group notifications, priority levels per session.
2. **Second bottleneck:** Message history growth over months. Mitigation: configurable retention (delete messages older than N days).

## Anti-Patterns

### Anti-Pattern 1: Direct API Calls from ViewModel

**What people do:** Call TelegramApi directly from ViewModel, bypassing repository and use case layers.
**Why it's wrong:** Mixes transport concerns with presentation logic. Makes testing require mocking HTTP. Makes protocol changes touch every ViewModel.
**Do this instead:** Always go through UseCase -> Repository -> DataSource. ViewModels should not know Telegram exists.

### Anti-Pattern 2: Parsing Telegram Protocol in UI Layer

**What people do:** Parse `@kuerzel message` format in Composables or ViewModels.
**Why it's wrong:** Protocol logic scattered across the codebase. If the format changes, you hunt through UI code.
**Do this instead:** MessageProtocol adapter in the data layer. Domain models use clean types (Session, ChatMessage), never raw Telegram strings.

### Anti-Pattern 3: Using WorkManager for Polling

**What people do:** Schedule periodic WorkManager tasks to poll Telegram.
**Why it's wrong:** WorkManager minimum interval is 15 minutes. For a real-time chat app, that's unacceptable latency. WorkManager is for deferrable background work.
**Do this instead:** Foreground Service with a persistent notification. This is the correct Android mechanism for ongoing real-time communication. The notification doubles as a "connected" indicator.

### Anti-Pattern 4: Storing Bot Token in Code

**What people do:** Hardcode the Telegram bot token in source code or BuildConfig.
**Why it's wrong:** Even for a single-user app, tokens in source control are a bad habit.
**Do this instead:** Store in Android EncryptedSharedPreferences. First-launch setup screen prompts for the token.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Telegram Bot API | HTTPS REST via Ktor client | Base URL: `https://api.telegram.org/bot<token>/`. Only methods needed: getUpdates, sendMessage. No webhook -- app uses long polling. |
| whisper.cpp | JNI native library | Ship .so files for arm64-v8a (modern phones). Model file (~40-150MB) downloaded separately on first launch or bundled as asset. |
| Android TTS | Platform API (`android.speech.tts.TextToSpeech`) | No external dependency. Initialize in service/singleton, check language availability. |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| PollingService <-> Repositories | SharedFlow<Update> | Service emits; repositories filter and process. Decoupled via DI. |
| Repositories <-> ViewModels | StateFlow / Flow (collected in viewModelScope) | Standard reactive pattern. ViewModels never call suspend functions on repositories directly for reads. |
| WhisperEngine <-> ChatViewModel | Suspend function returning Result<String> | Whisper transcription is a one-shot operation, not a stream. Runs on Dispatchers.Default (CPU-bound). |
| Domain <-> Data | Repository interfaces in domain; implementations in data | Clean Architecture dependency rule. Domain has zero Android imports. |

## Build Order (Dependencies)

The architecture has clear dependency chains that dictate build order:

```
Phase 1: Foundation
  ├── Project setup (Hilt, Room, Ktor, Navigation)
  ├── Domain models (Session, ChatMessage, SessionStatus)
  ├── TelegramApi (raw HTTP calls)
  └── MessageProtocol adapter
       |
Phase 2: Core Communication
  ├── TelegramPoller + SharedFlow
  ├── PollingService (foreground service)
  ├── SessionRepository + MessageRepository
  └── Room DB (entities, DAOs)
       |
Phase 3: Session UI
  ├── SessionListScreen + ViewModel
  ├── ChatScreen + ViewModel (text only)
  ├── Session discovery (/ls command)
  └── Session status tracking
       |
Phase 4: Interaction
  ├── PermissionRepository + UI (Allow/Deny cards)
  ├── Notification system
  ├── Session commands (/last, /open, /goto, /rename)
  └── Settings screen (bot token, preferences)
       |
Phase 5: Voice
  ├── WhisperEngine (JNI integration)
  ├── Audio recording
  ├── TranscribeVoiceUseCase
  ├── TTS playback
  └── Voice UI components (hold-to-record, playback)
```

**Rationale:** Each phase builds on the previous. You cannot build the chat UI without the polling/repository infrastructure. You cannot build permissions without the message routing. Voice is the most independent feature and has the heaviest native integration work, so it comes last.

## Sources

- [Telegram Bot API official documentation](https://core.telegram.org/bots/api)
- [Android foreground service documentation](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [whisper.cpp GitHub with Android examples](https://github.com/ggml-org/whisper.cpp)
- [kotlin-telegram-bot library](https://github.com/kotlin-telegram-bot/kotlin-telegram-bot)
- [omarmiatello/telegram Ktor-based Telegram API](https://github.com/omarmiatello/telegram)
- [Jetpack Compose Clean Architecture patterns](https://medium.com/@jecky999/best-architecture-for-jetpack-compose-in-2025-mvvm-clean-architecture-guide-f3a3d903514b)
- [Room + Kotlin Flow architecture](https://barbeau.medium.com/room-kotlin-flow-the-modern-android-architecture-for-location-aware-apps-9c110e12e31a)
- [jet-tts: TTS with Jetpack Compose](https://github.com/miroslavhybler/jet-tts)

---
*Architecture research for: Relay Android companion app*
*Researched: 2026-04-02*
