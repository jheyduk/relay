<!-- GSD:project-start source:PROJECT.md -->
## Project

**Relay**

A mobile companion app for Claude Code sessions. Relay provides a session-aware UI where each zellij-claude session gets its own visual space, with text and voice input unified in a single conversation stream. Built with Kotlin Multiplatform (KMP) for shared business logic. Communicates directly via WebSocket to a lightweight relay-server on the Mac.

**Core Value:** Remote session control with per-session separation — see all Claude Code sessions at a glance, interact with any of them, and never miss a permission request or completion notification.

### Constraints

- **Transport**: Direct WebSocket to relay-server on Mac (no Telegram, no cloud backend)
- **Platform**: Kotlin Multiplatform (KMP) — shared business logic, Compose for Android UI
- **Voice**: Mac-side whisper.cpp transcription (medium model, German + English)
- **Protocol**: JSON message format over WebSocket (`{type, session, message, status, ...}`)
- **Single user**: App is for the developer only, shared secret auth

### Server (relay-server)

The relay-server (`server/relay-server.cjs`) is the critical bridge between the Mac and the app. It runs as a **macOS launchd service** with automatic restart on crash.

**Lifecycle management:**
```bash
server/install-service.sh install   # Install + start launchd service
server/install-service.sh restart   # Restart after code changes
server/install-service.sh status    # Check if running
server/install-service.sh logs      # View stdout/stderr logs
server/install-service.sh uninstall # Remove service
```

**After editing `server/relay-server.cjs` or hooks, always restart:**
```bash
server/install-service.sh restart
```

**Config:** `~/.config/relay/server.json` (auto-created), `~/.config/relay/project-roots.json` (optional)
**Logs:** `/tmp/relay-server.stdout.log`, `/tmp/relay-server.stderr.log`
**Plist:** `server/dev.heyduk.relay-server.plist` → `~/Library/LaunchAgents/`
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

## Recommended Stack
### Core Technologies
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Kotlin | 2.3.20 | Language | Latest stable tooling release. KSP2 support, context parameters coming in 2.4. Required by AGP 9. |
| Android Gradle Plugin | 9.1.0 | Build system | Current stable. 9.2.0 is beta. Requires Kotlin 2.3+. Use version catalogs (libs.versions.toml). |
| Jetpack Compose BOM | 2026.03.00 | UI toolkit | Latest stable BOM. Material 3 1.4+, Compose 1.10+. Single BOM manages all Compose version alignment. |
| Hilt | 2.56 | Dependency injection | Google's recommended DI for Android. KSP processor (drop KAPT). Integrates with ViewModel, WorkManager, Navigation. |
| Ktor Client | 3.4.2 | HTTP client (Telegram API) | Pure Kotlin, coroutine-native, multiplatform. Use OkHttp engine on Android for best compatibility. kotlinx.serialization integration built-in. |
| Room | 2.8.4 | Local database | Session metadata, message history, offline cache. KSP processor. Stay on 2.x -- Room 3.0 just shipped (March 2026) and is too fresh for production. |
| whisper.cpp | 1.8.3 | On-device speech-to-text | C/C++ Whisper implementation with official Android/Kotlin JNI example. Best-in-class on-device ASR. Ship ggml-base.en model (~141 MB) for quality; offer ggml-tiny.en (~75 MB) as fallback. |
| Android TTS | Platform API | Text-to-speech | android.speech.tts.TextToSpeech -- built-in, zero dependencies, works offline. No reason to add a third-party library for this use case. |
### Supporting Libraries
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| kotlinx-serialization-json | 1.10.0 | JSON parsing | Telegram Bot API responses. Compile-time safe, no reflection. Pairs with Ktor's ContentNegotiation plugin. |
| Navigation Compose | 2.9.7 | Screen navigation | Type-safe routes with @Serializable. Proven and stable. Do NOT use Navigation3 (1.0.1) yet -- too new, ecosystem hasn't caught up. |
| DataStore Preferences | 1.2.1 | Key-value settings | Bot token, user preferences, selected model size. Replaces SharedPreferences. Coroutine-based. |
| Coil Compose | 3.x | Image loading | Session avatars, status icons if needed. Compose-native, coroutine-based. Lightweight. |
| WorkManager | 2.10.x | Background scheduling | Periodic polling when app is backgrounded. Handles Doze mode, battery optimization. |
| Lifecycle Runtime Compose | 2.9.x | Lifecycle-aware state | collectAsStateWithLifecycle() for safe Flow collection in Compose. |
| Timber | 5.0.1 | Logging | Debug logging with zero-overhead in release builds. |
### Development Tools
| Tool | Purpose | Notes |
|------|---------|-------|
| Android Studio Meerkat (2025.2+) | IDE | Required for AGP 9.x support. Compose preview, Layout Inspector. |
| KSP | Annotation processing | Replaces KAPT entirely. Used by Hilt, Room, kotlinx-serialization. 2x faster builds. |
| LeakCanary | Memory leak detection | Critical for JNI/native code (whisper.cpp). Dev builds only. |
| Gradle Version Catalogs | Dependency management | libs.versions.toml for centralized version management. Standard since AGP 8+. |
## Project Setup
## Telegram Bot API Integration Strategy
## Whisper Integration Strategy
## Alternatives Considered
| Recommended | Alternative | Why Not |
|-------------|-------------|---------|
| Ktor Client | Retrofit | Ktor is pure Kotlin with coroutine-first design. Retrofit needs converter factories and is Java-heritage. For a greenfield Kotlin project, Ktor is the natural choice. |
| Ktor Client (direct) | kotlin-telegram-bot | Server-side bot library with unnecessary abstractions. We're a client consuming the API, not hosting a bot. |
| Hilt | Koin | Hilt provides compile-time DI validation. Koin is runtime-only, meaning DI errors crash at runtime instead of failing at build time. For a solo developer, catching errors at compile time is more valuable. |
| Room 2.8.4 | Room 3.0 | Room 3.0 shipped March 2026 -- too fresh. Breaking changes (suspend-only DAOs, new package). Stick with 2.8.x for stability. Migrate later. |
| whisper.cpp JNI | Google ML Kit Speech | ML Kit requires Google Play Services and network. whisper.cpp is fully offline, open source, and gives us control over model selection. |
| whisper.cpp JNI | WhisperKit Android | WhisperKit Android is deprecated. Being replaced by argmax-sdk-kotlin which isn't ready yet. |
| Navigation 2.9.7 | Navigation3 1.0.1 | Navigation3 is Compose-first and promising but only stable since Nov 2025. Ecosystem tooling (deep links, Hilt integration) hasn't caught up. Use Nav 2.x which is battle-tested. |
| Android TTS | Pico TTS / third-party | Built-in TTS works offline, supports multiple voices, handles queuing. No reason to add complexity for this use case. |
| DataStore | SharedPreferences | SharedPreferences has threading issues and no type safety. DataStore is the official replacement with coroutine support. |
| Long polling + WorkManager | FCM push | FCM requires a server to send push messages. We have no custom server -- only Telegram Bot API. Long polling is the correct approach. FCM can be layered on later if a lightweight relay is added. |
## What NOT to Use
| Avoid | Why | Use Instead |
|-------|-----|-------------|
| KAPT | Deprecated annotation processing. 2x slower than KSP. AGP 9 is pushing KSP-only. | KSP for all processors (Hilt, Room) |
| Gson | Reflection-based JSON. Slower, no compile-time safety, Java-only API. | kotlinx-serialization (compile-time, multiplatform) |
| LiveData | Legacy reactive type. Compose works natively with Kotlin Flow/StateFlow. LiveData requires extra adapters. | StateFlow + collectAsStateWithLifecycle() |
| Retrofit | Java-heritage HTTP client. Needs adapters for coroutines, converter factories for serialization. | Ktor Client (Kotlin-native, coroutine-first) |
| Navigation3 | Too new (stable Nov 2025). Limited ecosystem tooling, Hilt integration unclear. | Navigation Compose 2.9.7 |
| Room 3.0 | Released March 2026. New package namespace, breaking API changes. Let it mature. | Room 2.8.4 |
| Jetpack Compose + XML mixing | Mixing View system with Compose adds complexity. Pure Compose for greenfield. | Pure Jetpack Compose |
| SharedPreferences | Threading issues, no Flow support, no type safety. Officially superseded. | DataStore Preferences |
| Google ML Kit ASR | Requires Play Services, may need network, less control over models. | whisper.cpp with JNI |
## Architecture Pattern
- **UI Layer:** Compose screens observe StateFlow from ViewModels
- **ViewModel Layer:** Business logic, state management, exposes StateFlow
- **Repository Layer:** Data access abstraction (TelegramRepository, SessionRepository, WhisperRepository)
- **Data Layer:** Ktor client, Room DAOs, DataStore, whisper.cpp JNI bridge
## Version Compatibility Matrix
| Component | Version | Requires |
|-----------|---------|----------|
| AGP 9.1.0 | 9.1.0 | Kotlin 2.3+, Gradle 8.11+ |
| Kotlin 2.3.20 | 2.3.20 | AGP 9.x compatible |
| Compose BOM 2026.03.00 | 2026.03.00 | Kotlin 2.3+, AGP 9.x |
| Hilt 2.56 | 2.56 | KSP 2.3.20-1.0.x (aligned with Kotlin version) |
| Room 2.8.4 | 2.8.4 | KSP, Kotlin 2.0+ |
| Ktor 3.4.2 | 3.4.2 | Kotlin 2.3+, OkHttp engine for Android |
| Navigation 2.9.7 | 2.9.7 | Compose BOM compatible |
| whisper.cpp 1.8.3 | 1.8.3 | NDK, CMake, minSdk 28+ |
## Sources
- [Kotlin 2.3.20 Release Blog](https://blog.jetbrains.com/kotlin/2026/03/kotlin-2-3-20-released/) -- current Kotlin version (HIGH confidence)
- [AGP 9.1.0 Release Notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes) -- AGP version (HIGH confidence)
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom) -- BOM 2026.03.00 (HIGH confidence)
- [Ktor 3.4.2 Releases](https://github.com/ktorio/ktor/releases) -- Ktor version (HIGH confidence)
- [whisper.cpp GitHub](https://github.com/ggml-org/whisper.cpp) -- v1.8.3, Android example (HIGH confidence)
- [whisper.cpp Android Example](https://github.com/ggml-org/whisper.cpp/tree/master/examples/whisper.android) -- JNI approach (HIGH confidence)
- [Hilt Documentation](https://developer.android.com/training/dependency-injection/hilt-android) -- version 2.56 (HIGH confidence)
- [Room Releases](https://developer.android.com/jetpack/androidx/releases/room) -- Room 2.8.4 (HIGH confidence)
- [Room 3.0 Announcement](https://android-developers.googleblog.com/2026/03/room-30-modernizing-room.html) -- why to avoid for now (HIGH confidence)
- [Navigation Releases](https://developer.android.com/jetpack/androidx/releases/navigation) -- Nav 2.9.7 (HIGH confidence)
- [Navigation3 Releases](https://developer.android.com/jetpack/androidx/releases/navigation3) -- Nav3 1.0.1 too new (HIGH confidence)
- [DataStore Releases](https://developer.android.com/jetpack/androidx/releases/datastore) -- DataStore 1.2.1 (HIGH confidence)
- [FCM Best Practices](https://firebase.blog/posts/2025/04/fcm-on-android/) -- WorkManager integration (MEDIUM confidence)
- [Whisper Model Sizes](https://openwhispr.com/blog/whisper-model-sizes-explained) -- model selection rationale (MEDIUM confidence)
- [Android TTS API](https://developer.android.com/reference/kotlin/android/speech/tts/TextToSpeech) -- platform TTS (HIGH confidence)
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

- **Server restart required** after any edit to `server/relay-server.cjs` or `server/hooks/*.cjs` — run `server/install-service.sh restart`
- **New WebSocket message types** need changes in 4 places: server handler, `RelayMessageTypeDto` enum, `RelayMessageType` enum, `RelayMessageParser` mapping, and `WebSocketService` DB-skip list for non-chat types
- **Non-chat message types** (DIRECTORY_LIST, SESSION_CREATED, LAST_RESPONSE) must be skipped in `WebSocketService.kt` DB persistence
- **Git commits** in English, no Co-Author line
- **Code comments** in English, explanations in German
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd:quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd:debug` for investigation and bug fixing
- `/gsd:execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd:profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
