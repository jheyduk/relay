# Stack Research

**Domain:** Native Android companion app (Telegram Bot API transport, on-device speech, session-aware chat)
**Researched:** 2026-04-02
**Confidence:** HIGH

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

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// build.gradle.kts (project)
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
}

// build.gradle.kts (app)
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "dev.heyduk.relay"
    compileSdk = 36
    defaultConfig {
        minSdk = 28  // Android 9 -- covers 95%+ of devices, needed for TLS 1.3
        targetSdk = 36
    }
    buildFeatures { compose = true }
    // NDK for whisper.cpp JNI
    externalNativeBuild {
        cmake { path = file("src/main/cpp/CMakeLists.txt") }
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2026.03.00")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Architecture
    implementation("com.google.dagger:hilt-android:2.56")
    ksp("com.google.dagger:hilt-compiler:2.56")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")
    implementation("androidx.navigation:navigation-compose:2.9.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")

    // Networking (Telegram Bot API)
    implementation("io.ktor:ktor-client-okhttp:3.4.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.4.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.2")

    // Storage
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.2.1")

    // Background
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")
}
```

## Telegram Bot API Integration Strategy

No Kotlin Telegram library is needed. The Telegram Bot API is a simple REST API -- use Ktor Client directly.

**Rationale:** The existing Kotlin Telegram bot libraries (kotlin-telegram-bot, vendelieu/telegram-bot) are designed for server-side bots, not Android clients consuming bot messages. They bring unnecessary server abstractions. Relay only needs:

1. `getUpdates` (long polling) -- single GET with offset/timeout params
2. `sendMessage` -- POST with chat_id and text
3. `answerCallbackQuery` -- POST for permission button responses

This is ~3 Ktor calls wrapped in a repository class. No library needed.

```kotlin
// Example: Telegram API as simple Ktor calls
@Serializable
data class TelegramResponse<T>(val ok: Boolean, val result: T)

@Serializable
data class Update(val update_id: Long, val message: Message? = null, val callback_query: CallbackQuery? = null)

class TelegramRepository(private val client: HttpClient, private val token: String) {
    private val baseUrl = "https://api.telegram.org/bot$token"

    suspend fun getUpdates(offset: Long, timeout: Int = 30): List<Update> {
        val response = client.get("$baseUrl/getUpdates") {
            parameter("offset", offset)
            parameter("timeout", timeout)
        }
        return response.body<TelegramResponse<List<Update>>>().result
    }

    suspend fun sendMessage(chatId: Long, text: String): Message {
        val response = client.post("$baseUrl/sendMessage") {
            contentType(ContentType.Application.Json)
            setBody(mapOf("chat_id" to chatId, "text" to text))
        }
        return response.body<TelegramResponse<Message>>().result
    }
}
```

## Whisper Integration Strategy

Use the official whisper.cpp Android example as the foundation. The approach:

1. **CMake build** -- whisper.cpp compiles as a native library via CMake in the Android build
2. **JNI bridge** -- Thin JNI layer exposes `initContext`, `transcribeAudio`, `freeContext`
3. **Model bundling** -- Ship ggml-base.en in assets (~141 MB APK impact) or download on first launch
4. **Audio capture** -- Android AudioRecord API captures PCM at 16kHz (Whisper's expected format)

**Model choice:** Use `ggml-base.en` (English-only base model). The `.en` variant is faster and more accurate for English-only use. The base model provides significantly better accuracy than tiny for only ~66 MB more. For a single-user developer tool, this tradeoff is worth it.

**Performance expectation:** ~4 seconds to transcribe 11 seconds of audio on modern Android hardware (based on community benchmarks with tiny model; base will be ~2x slower but still sub-real-time).

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

**MVVM + Repository pattern** with unidirectional data flow.

- **UI Layer:** Compose screens observe StateFlow from ViewModels
- **ViewModel Layer:** Business logic, state management, exposes StateFlow
- **Repository Layer:** Data access abstraction (TelegramRepository, SessionRepository, WhisperRepository)
- **Data Layer:** Ktor client, Room DAOs, DataStore, whisper.cpp JNI bridge

This is the standard Android architecture recommended by Google and used by the majority of production Compose apps.

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

---
*Stack research for: Relay -- Android companion app for Claude Code sessions*
*Researched: 2026-04-02*
