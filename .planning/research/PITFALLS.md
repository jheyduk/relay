# Domain Pitfalls

**Domain:** Android app using Telegram Bot API as transport with on-device Whisper
**Researched:** 2026-04-02

## Critical Pitfalls

Mistakes that cause rewrites or major issues.

### Pitfall 1: Telegram getUpdates 409 Conflict — Only One Polling Client Allowed

**What goes wrong:** Telegram Bot API enforces that only one client may call `getUpdates` at a time per bot token. If both the existing zellij-claude Telegram integration AND Relay poll with the same token, Telegram returns `409 Conflict: terminated by other getUpdates request`. Updates get dropped, both clients become unreliable.

**Why it happens:** The existing system already polls via the Telegram bot. Adding a second polling client (Relay) on the same token is a protocol violation.

**Consequences:** Neither client receives updates reliably. Messages are lost, permission requests go unacknowledged, sessions appear dead.

**Prevention:**
- **Architecture decision required before coding:** Either (a) Relay replaces the existing Telegram polling entirely and becomes the sole consumer, or (b) switch the Mac-side to webhook mode and let Relay poll, or (c) use a shared message broker/relay server (contradicts "no custom backend" constraint).
- The simplest path: when Relay is active, it is the sole `getUpdates` consumer. The Mac-side bot stops polling. When Relay is not active, the Mac-side resumes. This requires a coordination mechanism (e.g., a `/claim` command or a "last poller wins" approach where the 409 is handled gracefully).
- Alternatively, switch Mac-side to webhook delivery and have Relay be the polling client. Webhook and polling can coexist on different bot tokens but NOT on the same token simultaneously.

**Detection:** 409 HTTP errors in getUpdates responses. Messages arriving on one client but not the other.

**Phase:** Must be resolved in Phase 1 (transport layer). This is an architectural blocker.

**Confidence:** HIGH — well-documented Telegram API limitation, confirmed across multiple sources.

---

### Pitfall 2: getUpdates Offset Mismanagement Causes Lost or Duplicate Messages

**What goes wrong:** Telegram's `getUpdates` returns the earliest 100 unconfirmed updates. Each call must pass `offset = last_update_id + 1` to acknowledge receipt. If the offset is not updated after each response, messages are re-delivered. If the offset advances too far (e.g., after a crash before processing), messages are permanently lost.

**Why it happens:** Offset tracking is effectively a manual cursor into an append-only log. Crash between "receive" and "acknowledge" loses or duplicates depending on implementation.

**Consequences:** Duplicate permission prompts confuse the user. Lost messages mean missed Allow/Deny requests, causing Claude Code sessions to hang indefinitely.

**Prevention:**
- Persist the last processed offset to local storage (Room/DataStore) BEFORE acknowledging to Telegram.
- Use an "at-least-once" delivery pattern: process message, persist offset, then acknowledge. Accept idempotent re-processing over message loss.
- On app restart, resume from persisted offset.
- Implement message deduplication by `update_id` in the UI layer.

**Detection:** Duplicate messages appearing in chat. Missing messages that the Mac-side confirms were sent.

**Phase:** Phase 1 (transport layer).

**Confidence:** HIGH — standard distributed systems concern, well-documented in Telegram Bot API docs.

---

### Pitfall 3: Whisper Model Too Large or Too Slow for Usable Voice Input

**What goes wrong:** Developers pick a Whisper model that is either too large (OOM on mid-range devices, 500MB+ download) or too slow (30+ seconds for a short utterance), making voice input feel broken.

**Why it happens:** whisper.cpp model performance varies dramatically by device. The "small" model (244M params, ~460MB) delivers good accuracy but is unusably slow on older devices. The "tiny" model (39M params, ~75MB) is fast but has poor accuracy for non-English or noisy environments.

**Consequences:** Users abandon voice input. Or worse: the app crashes with OOM during transcription, losing the audio and the user's patience.

**Prevention:**
- **Start with `base` model (74M params) with INT8 quantization** (~18MB on disk). This hits the sweet spot of ~1.5-2x real-time on flagship phones and acceptable accuracy.
- Ship the quantized model bundled in the APK (18MB is acceptable). Do NOT download at first launch — that's a terrible first-run experience.
- Always use `applicationContext` (not Activity context) when loading the native model to avoid memory leaks on configuration changes.
- Implement a hard timeout (e.g., 30 seconds) on transcription. If exceeded, show partial results or offer text fallback.
- Monitor peak memory usage during inference. On devices with <3GB free RAM, fall back to tiny model or disable voice.
- Use whisper.cpp's streaming/chunked mode for longer utterances rather than buffering the entire audio first.

**Detection:** Transcription latency >3x audio duration. OOM crashes in native code (check logcat for SIGKILL/SIGABRT). Model download stalling on first launch.

**Phase:** Phase 2 (voice input). Requires early prototyping to validate model choice on target devices.

**Confidence:** MEDIUM-HIGH — benchmarks from whisper.cpp community and ionio.ai edge deployment guide. Exact performance depends on target device.

---

### Pitfall 4: Android Kills Long Polling in Background — Doze Mode and App Standby

**What goes wrong:** Long polling requires a persistent HTTP connection. Android's Doze mode (idle device) and App Standby (unused app) aggressively kill network connections and background processing. The app stops receiving updates within minutes of screen-off.

**Why it happens:** Android progressively restricts background activity: Doze restricts network access, App Standby limits job execution, and Android 14+ enforces strict foreground service type declarations. A naive long-polling loop in a background thread simply dies.

**Consequences:** Permission requests from Claude Code go undelivered for hours. The core value proposition ("never miss a permission request") is broken.

**Prevention:**
- **Use a Foreground Service with type `dataSync`** for active polling. This keeps the connection alive but requires a persistent notification (acceptable for a developer tool).
- Declare `<service android:foregroundServiceType="dataSync">` in manifest (required Android 14+).
- Be aware: Android 16+ may exhaust foreground service job quotas for `dataSync` workers. Plan for direct foreground service launch as fallback.
- Request battery optimization exemption (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) — appropriate for a personal developer tool, would be rejected for Play Store apps.
- **FCM as fallback:** Add Firebase Cloud Messaging for a lightweight "wake up and poll" signal. The Mac-side can send a silent push notification when a permission request is pending. This avoids permanent polling.
- Implement connection health monitoring: if no response for >60s on a long-poll, reconnect. Detect network changes via `ConnectivityManager` callback and re-establish polling.

**Detection:** Updates stop arriving when device screen is off. Foreground service killed by system (check `ActivityManager` logs). Notification disappears.

**Phase:** Phase 1 (transport layer), but the FCM enhancement can be Phase 3+.

**Confidence:** HIGH — Android background execution limits are extensively documented by Google.

---

### Pitfall 5: Whisper JNI/NDK Integration Crashes and Memory Leaks

**What goes wrong:** whisper.cpp runs as native C++ code accessed via JNI. Native crashes (SIGSEGV, SIGABRT) bypass Java exception handling, killing the app instantly with no stack trace in standard crash reporting. Native memory allocated by ggml is invisible to Android's garbage collector, causing silent memory leaks.

**Why it happens:** JNI is a thin, unsafe bridge. Mismatched buffer sizes, dangling pointers, or forgetting to free native resources are invisible to Kotlin. The Android runtime cannot GC native heap allocations.

**Consequences:** Random app crashes during or after voice transcription. Gradual memory growth until OOM kill. Extremely difficult to debug without native crash symbolication.

**Prevention:**
- Use an established JNI wrapper (e.g., whisper.cpp's built-in Android example or whisper-jni library) rather than writing custom JNI bindings.
- Wrap all native calls in a dedicated `WhisperEngine` singleton with explicit `init()` and `release()` lifecycle methods tied to the Application lifecycle, not Activity.
- Use `try/finally` patterns in Kotlin to ensure native resources are freed even on exceptions.
- Set up native crash reporting (Firebase Crashlytics NDK or Bugsnag NDK) from day one — Java-only crash reporting misses native crashes entirely.
- Run memory profiling with Android Studio Profiler during voice sessions — watch for native heap growth that doesn't recede after transcription completes.
- Consider WhisperKit Android (by Argmax) as an alternative — it provides a higher-level SDK with QNN hardware acceleration support, abstracting away raw JNI management.

**Detection:** App crashes without Java stack trace. Native heap growing across transcription sessions. `tombstone` files in `/data/tombstones/`.

**Phase:** Phase 2 (voice input). The JNI integration architecture must be right from the start.

**Confidence:** MEDIUM — based on community reports and JNI best practices. WhisperKit Android is newer and less battle-tested.

---

## Moderate Pitfalls

### Pitfall 6: LazyColumn Recomposition Storm in Multi-Session Chat UI

**What goes wrong:** Every new message from any session triggers recomposition of the entire LazyColumn, causing visible jank (dropped frames, stuttering scroll) when multiple sessions are active simultaneously.

**Why it happens:** Jetpack Compose's LazyColumn recomposes all visible items when the backing list changes, unless items have stable keys and the data classes are stable (all `val`, no mutable state). Using `var` properties, unstable lambda callbacks, or missing `key` parameters defeats Compose's skip optimization.

**Prevention:**
- Provide stable `key` for every LazyColumn item: `items(messages, key = { it.id })`.
- Use only `val` properties in message data classes. Mark data classes as `@Immutable` or `@Stable` where appropriate.
- Use `derivedStateOf` for computed UI state (e.g., "should show scroll-to-bottom button").
- Profile with Layout Inspector's recomposition counter during development — catch recomposition issues early.
- Separate session message lists into distinct state flows. A message arriving in Session A should not trigger recomposition of Session B's list.

**Detection:** Layout Inspector shows high recomposition counts. Jank visible during scrolling while messages arrive. Profiler shows >16ms frame times.

**Phase:** Phase 1 (UI foundation). Architecture of state management must be correct from the start.

**Confidence:** HIGH — well-documented Compose performance issue with official Google guidance.

---

### Pitfall 7: Chat Scroll Position Jumps When New Messages Arrive

**What goes wrong:** User is reading history in one session. A new message arrives (in that session or another). The scroll position jumps to the bottom or to a random position, losing the user's reading context.

**Why it happens:** Naively appending to the message list triggers LazyColumn to re-layout. Without explicit scroll state management, the list "jumps" because item indices shift. This is a known issue in Compose's Jetchat sample (GitHub issue #696).

**Prevention:**
- Hoist `LazyListState` to the screen level and share it between the message list and scroll-to-bottom button.
- Only auto-scroll to bottom if the user was already at the bottom (check `lazyListState.firstVisibleItemIndex`).
- When a new message arrives and user is scrolled up, show a "New messages" indicator instead of scrolling.
- Use `animateScrollToItem` instead of `scrollToItem` for smoother UX.

**Detection:** Users report losing their place in chat history. QA testing with rapid message arrival while scrolled up.

**Phase:** Phase 1 (UI), refined in Phase 2.

**Confidence:** HIGH — documented as a known issue in Android's own compose-samples repository.

---

### Pitfall 8: TTS Initialization Race Condition and Resource Leaks

**What goes wrong:** `TextToSpeech` engine initialization is asynchronous. Calling `speak()` before `OnInitListener` fires results in silent failure (no error, no audio). Forgetting `shutdown()` leaks the TTS engine, eventually exhausting system audio resources.

**Why it happens:** Android's TTS API requires async initialization but provides no suspend/coroutine-friendly interface. Developers call `speak()` immediately after construction, or they tie TTS lifecycle to an Activity that gets recreated on rotation.

**Prevention:**
- Initialize TTS in Application or a ViewModel scoped to the navigation graph, NOT in a Composable or Activity.
- Gate all `speak()` calls behind an `isInitialized` flag set in `OnInitListener`.
- Use a Kotlin Channel or StateFlow to queue TTS requests that arrive before initialization completes.
- Call `shutdown()` in `onCleared()` of the ViewModel, not `onDestroy()` of Activity.
- Handle `TextToSpeech.ERROR` in OnInitListener — TTS engine may not be installed or may fail to load.
- Note: Android's native TTS cannot stream token-by-token. For long Claude responses, split text into sentence-sized chunks and queue them sequentially with `QUEUE_ADD` mode.

**Detection:** Silent TTS failures. Audio continuing to play after leaving the screen. Logcat warnings about TTS engine.

**Phase:** Phase 3 (TTS). Lower priority but easy to get wrong.

**Confidence:** HIGH — well-documented Android API behavior.

---

### Pitfall 9: Telegram Bot API Rate Limiting (30 msg/sec Global)

**What goes wrong:** The bot token has a global rate limit of 30 messages per second across ALL methods. If the Mac-side bot is sending status updates, session notifications, and message content while Relay is simultaneously sending commands and callback answers, the limit is hit. Telegram returns 429 with a `retry_after` header.

**Why it happens:** The 30 msg/s limit is shared across all API calls using the same bot token — both directions, all methods. A burst of session activity (e.g., 5 sessions completing simultaneously) can spike the rate.

**Prevention:**
- Implement client-side rate limiting with a token bucket (max 25 msg/s to leave headroom).
- Queue outgoing messages and drain at a controlled rate.
- Use the `retry_after` field in 429 responses (Telegram API 8.0+ provides adaptive retry suggestions).
- For reading updates, `getUpdates` itself counts toward the limit — don't poll more frequently than every 1-2 seconds with a long-poll timeout of 30s.
- Batch callback answers where possible.

**Detection:** 429 HTTP responses. Messages delayed or dropped. `retry_after` values in API responses.

**Phase:** Phase 1 (transport layer). Build rate limiting into the API client from the start.

**Confidence:** HIGH — documented Telegram Bot API limit, confirmed in API 8.0 changelog.

---

### Pitfall 10: Audio Recording Permissions and Focus Conflicts

**What goes wrong:** Voice recording fails silently because the app didn't request `RECORD_AUDIO` runtime permission, or because another app holds audio focus. On Android 12+, the microphone access indicator (green dot) may confuse users if recording seems to happen unexpectedly.

**Why it happens:** Android's runtime permission model requires explicit user consent. Audio focus is a shared resource — if a call or media app holds focus, recording may produce silence or be blocked entirely.

**Prevention:**
- Request `RECORD_AUDIO` permission at an appropriate moment (when user first taps the voice button, not at app launch).
- Handle permission denial gracefully — show text-only input mode.
- Request audio focus (`AudioManager.requestAudioFocus`) before recording and release after.
- If TTS is playing when user taps record, stop TTS first (duck or pause).
- On Android 12+, ensure the microphone indicator is expected and document it for the user (acceptable for a personal dev tool).

**Detection:** Empty or silent audio buffers. Permission denial callbacks. Users reporting "voice button does nothing".

**Phase:** Phase 2 (voice input).

**Confidence:** HIGH — standard Android audio permission requirements.

---

## Minor Pitfalls

### Pitfall 11: Message Format Parsing Fragility

**What goes wrong:** The app parses `@kuerzel` prefixes and status patterns from raw Telegram messages. If the Mac-side format changes even slightly (extra space, different emoji, new status keyword), parsing breaks silently.

**Prevention:**
- Define message format parsing as a dedicated, well-tested module with comprehensive unit tests covering edge cases.
- Use regex with named groups, not string splitting.
- Log unparseable messages rather than silently dropping them.
- Include a "raw message" view for debugging.

**Phase:** Phase 1 (message handling).

**Confidence:** MEDIUM — depends on how stable the zellij-claude message format is.

---

### Pitfall 12: Model Download and Storage on First Launch

**What goes wrong:** If the Whisper model is not bundled in the APK but downloaded at runtime, the first voice interaction requires a network download, which may fail, timeout, or confuse the user.

**Prevention:**
- Bundle the quantized base model (~18MB) directly in the APK. This is small enough to be acceptable.
- If offering larger models as optional upgrades, download in background with progress indication and resume-on-failure.
- Store models in app-internal storage, not external (avoids storage permission issues).

**Phase:** Phase 2 (voice input).

**Confidence:** HIGH — standard mobile UX concern.

---

### Pitfall 13: Notification Channel Misconfiguration on Android 8+

**What goes wrong:** Push notifications for permission requests don't appear because the notification channel wasn't created, or it was created with low importance, or the user dismissed it and Android remembers the dismissal.

**Prevention:**
- Create a HIGH importance notification channel for permission requests at app startup.
- Create a separate DEFAULT importance channel for session status updates.
- Use `NotificationManager.areNotificationsEnabled()` to detect if notifications are blocked and prompt the user.
- For the foreground service notification (long polling), use a MINIMAL importance channel to avoid being intrusive.

**Phase:** Phase 1 (notification infrastructure).

**Confidence:** HIGH — standard Android notification requirement since API 26.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|---|---|---|
| Transport layer (Phase 1) | 409 conflict with existing bot polling | Resolve single-consumer architecture before writing any polling code |
| Transport layer (Phase 1) | Background polling killed by Doze | Foreground service with `dataSync` type from the start |
| Transport layer (Phase 1) | Rate limiting under load | Build token-bucket rate limiter into API client layer |
| UI foundation (Phase 1) | LazyColumn recomposition storm | Stable keys, immutable data classes, per-session state flows |
| Voice input (Phase 2) | Whisper model too slow/large | Prototype with quantized base model on target device early |
| Voice input (Phase 2) | JNI native crashes | Use established wrapper, set up NDK crash reporting |
| TTS (Phase 3) | Init race condition | Async-safe TTS wrapper in ViewModel scope |
| Notifications (Phase 1) | Missing/silent notifications | Channel creation at startup, importance levels per category |

## Sources

- [Telegram Bot API Documentation](https://core.telegram.org/bots/api)
- [Telegram Bot API Changelog (API 8.0)](https://core.telegram.org/bots/api-changelog)
- [Telegram Polling Errors and Resolution](https://medium.com/@ratulkhan.jhenidah/telegram-polling-errors-and-resolution-4726d5eae895)
- [getUpdates 409 Conflict Discussion](https://community.home-assistant.io/t/help-on-telegram-extension-error-while-getting-updates-conflict-terminated-by-other-getupdates-request-make-sure-that-only-one-bot-instance-is-running-409/177544)
- [whisper.cpp GitHub](https://github.com/ggml-org/whisper.cpp)
- [WhisperKit Android](https://github.com/argmaxinc/WhisperKitAndroid)
- [Running Transcription Models on the Edge](https://www.ionio.ai/blog/running-transcription-models-on-the-edge-a-practical-guide-for-devices)
- [Building LocalMind: Whisper to Android via JNI](https://medium.com/@mohammedrazachandwala/building-localmind-how-i-ported-openais-whisper-to-android-using-jni-and-kotlin-575dddd38fdc)
- [Android whisper.cpp Inference Speed Issue #1070](https://github.com/ggml-org/whisper.cpp/issues/1070)
- [Jetpack Compose Performance Best Practices](https://developer.android.com/develop/ui/compose/performance/bestpractices)
- [Jetchat Scroll Position Issue #696](https://github.com/android/compose-samples/issues/696)
- [LazyColumn Recomposition Fixes](https://dev.to/theplebdev/the-refactors-i-did-to-stop-my-jetpack-compose-lazycolumn-from-constantly-recomposing-57l0)
- [Android Doze and App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)
- [Android Foreground Service Types](https://developer.android.com/develop/background-work/background-tasks/persistent/how-to/long-running)
- [Android TextToSpeech API Reference](https://developer.android.com/reference/android/speech/tts/TextToSpeech)
- [Android Streaming TTS Tutorial](https://picovoice.ai/blog/android-streaming-text-to-speech/)
- [Common TTS Issues in Android](https://javanexus.com/blog/common-tts-issues-android-fixes)
