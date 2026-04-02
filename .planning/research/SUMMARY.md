# Project Research Summary

**Project:** Relay
**Domain:** Native Android companion app for CLI session management via Telegram Bot API
**Researched:** 2026-04-02
**Confidence:** HIGH

## Executive Summary

Relay is a native Android companion app that replaces the current Telegram chat interface for managing multiple Claude Code sessions running in zellij-claude. The established approach for this type of app is Kotlin with Jetpack Compose, MVVM with unidirectional data flow, and a clean architecture layering (presentation, domain, data). The Telegram Bot API serves as the transport layer -- no custom backend is needed. On-device speech is handled by whisper.cpp (JNI) for input and Android's built-in TTS for output. The stack is modern, all versions are current and compatible, and every major component has high-confidence documentation.

The recommended approach is a phased build following the architecture's natural dependency chain: transport layer and polling infrastructure first, then session UI, then permissions and notifications, and finally the voice pipeline. This order ensures each phase delivers testable, usable functionality. The multi-session dashboard is Relay's primary differentiator -- no competitor (Claude RC, Happy Coder, Nimbalyst, Tactic Remote, or Claude Channels) manages multiple concurrent zellij sessions as first-class citizens.

The single most critical risk is the Telegram `getUpdates` 409 conflict: only one client may poll a bot token at a time. Since zellij-claude already polls, Relay cannot simply start a second polling loop. This must be resolved architecturally before any transport code is written. Secondary risks include Android killing the polling service in background (mitigated by foreground service with `dataSync` type) and whisper.cpp JNI integration complexity (mitigated by using the official Android example as foundation and deferring voice to a later phase).

## Key Findings

### Recommended Stack

The stack is a standard modern Android setup with one notable addition: whisper.cpp via JNI for on-device speech recognition. All versions are current stable releases with verified compatibility.

**Core technologies:**
- **Kotlin 2.3.20 + AGP 9.1.0**: Latest stable, required for Compose BOM 2026.03.00. KSP replaces KAPT entirely.
- **Jetpack Compose (BOM 2026.03.00)**: Material 3 UI toolkit. Pure Compose, no XML mixing.
- **Ktor Client 3.4.2**: HTTP client for Telegram Bot API. Pure Kotlin, coroutine-native. No Telegram wrapper library needed -- the API surface is just 3 endpoints.
- **Hilt 2.56**: Compile-time DI. Catches errors at build time, integrates with ViewModel, Navigation, WorkManager.
- **Room 2.8.4**: Local database for sessions and messages. Stick with 2.x (3.0 shipped March 2026, too fresh).
- **whisper.cpp 1.8.3**: On-device ASR via JNI. Ship quantized base.en model (~18MB with INT8). Official Android example provides the JNI bridge.
- **Android TTS**: Built-in platform API, zero dependencies, works offline.

**Key stack decisions:** No Telegram bot library (unnecessary server abstractions), no Navigation3 (too new), no Room 3.0 (too new), no KAPT (deprecated). See STACK.md for full rationale and alternatives considered.

### Expected Features

**Must have (table stakes):**
- Session list with color-coded status (working, waiting, ready, shell)
- Text messaging to sessions via `@kuerzel` prefix
- Push notifications for permission requests and completions
- Native Allow/Deny permission UI (replacing Telegram inline keyboards)
- Session commands (`/ls`, `/last`, `/open`, `/goto`, `/rename`)
- Conversation history per session
- Dark mode (Material 3 provides this nearly free)

**Should have (differentiators):**
- Multi-session dashboard -- the primary differentiator over all competitors
- On-device Whisper transcription -- independence from cloud, works offline
- Voice-to-session routing ("Hey @infra, deploy staging")
- TTS response playback -- hear Claude while walking
- Unified chat stream (text + voice in one timeline)
- Zero infrastructure requirement -- Telegram Bot API is the only dependency

**Defer (v2+):**
- Conversation history persistence (start in-memory, add Room later)
- Status-driven notification priority (polish)
- FCM push notifications (long polling is sufficient for v1)

**Anti-features (explicitly do NOT build):** Code editor, terminal emulator, custom relay server, multi-user support, iOS version, git operations, cloud voice transcription.

### Architecture Approach

The architecture follows standard Android Clean Architecture with MVVM and unidirectional data flow. A foreground service runs continuous Telegram long-polling, broadcasting updates via SharedFlow to repositories that maintain session, message, and permission state. The domain layer is pure Kotlin with no Android dependencies. A dedicated MessageProtocol adapter translates between Telegram wire format (`@kuerzel` prefixes, callback formats) and domain models, fully decoupling the app from the transport layer.

**Major components:**
1. **TelegramPoller + PollingService** -- Foreground service running long-poll loop, emitting updates via SharedFlow
2. **MessageProtocol** -- Adapter translating between Telegram Bot API format and domain models
3. **SessionRepository** -- Aggregates session state from updates, exposes Flow of sessions
4. **MessageRepository** -- Per-session message storage and retrieval via Room
5. **PermissionRepository** -- Tracks pending Allow/Deny requests, triggers system notifications
6. **WhisperEngine** -- Singleton JNI wrapper for whisper.cpp with explicit lifecycle management
7. **Presentation layer** -- Feature-based Compose screens (SessionList, Chat, Settings) with ViewModels

### Critical Pitfalls

1. **Telegram 409 Conflict** -- Only one client may call `getUpdates` per bot token. Since zellij-claude already polls, a coordination mechanism is required (e.g., Relay claims exclusive polling when active). This is an architectural blocker for Phase 1.
2. **getUpdates Offset Mismanagement** -- Crash between receive and acknowledge causes lost or duplicate messages. Persist offset to local storage before acknowledging. Use at-least-once delivery with deduplication.
3. **Android Kills Background Polling** -- Doze mode and App Standby kill network connections. Use a foreground service with `dataSync` type and persistent notification from day one.
4. **Whisper Model Size/Speed Tradeoff** -- Wrong model choice causes OOM or unusable latency. Use quantized base.en (~18MB) bundled in APK. Implement hard timeout with text fallback.
5. **Whisper JNI Crashes and Memory Leaks** -- Native crashes bypass Java exception handling. Use the official Android example wrapper, tie lifecycle to Application (not Activity), set up NDK crash reporting.

## Implications for Roadmap

Based on research, suggested phase structure:

### Phase 1: Transport and Foundation
**Rationale:** Everything depends on the Telegram connection working correctly. The 409 conflict and offset management must be solved first. The foreground service pattern must be established before any UI can display live data.
**Delivers:** Working Telegram polling with reliable message receipt, Room database schema, domain models, Hilt DI setup, project scaffolding.
**Addresses:** Telegram Bot API connection, reliable message delivery, notification infrastructure.
**Avoids:** Pitfall 1 (409 conflict -- resolve single-consumer architecture), Pitfall 2 (offset management), Pitfall 4 (background polling killed), Pitfall 9 (rate limiting built into API client).

### Phase 2: Session UI
**Rationale:** With transport working, build the primary differentiator: the multi-session dashboard. Session discovery (`/ls`), status tracking, and the session list screen are the first user-visible deliverables.
**Delivers:** Session list screen with status indicators, session detail/chat screen (text only), session discovery via `/ls`, session status parsing from message content.
**Addresses:** Session list with status, text messaging, session commands, conversation history (in-memory).
**Avoids:** Pitfall 6 (LazyColumn recomposition -- stable keys and immutable data classes from the start), Pitfall 7 (scroll position jumps), Pitfall 11 (message format parsing -- dedicated tested module).

### Phase 3: Permissions and Notifications
**Rationale:** Permission handling is the most time-critical mobile action and a core table-stakes feature. It depends on the session context from Phase 2 and the transport layer from Phase 1.
**Delivers:** Native Allow/Deny permission cards in chat, system notifications for permission requests with high-priority channel, foreground service notification polish, settings screen (bot token configuration via EncryptedSharedPreferences).
**Addresses:** Permission Allow/Deny, push notifications, status-driven notification priority, settings.
**Avoids:** Pitfall 13 (notification channel misconfiguration -- create channels at startup with correct importance levels).

### Phase 4: Voice Pipeline
**Rationale:** Voice is the major differentiator but also the heaviest native integration. It has no dependencies on Phases 2-3 beyond the send-message path, but deferring it reduces risk on the core experience. Early prototyping of Whisper performance on target devices is recommended.
**Delivers:** On-device Whisper transcription, hold-to-record voice UI, voice-to-session routing, TTS playback for responses, unified chat stream (text + voice).
**Addresses:** On-device Whisper, voice-to-session pipeline, TTS playback, unified chat stream.
**Avoids:** Pitfall 3 (model too large/slow -- quantized base.en, bundled in APK, hard timeout), Pitfall 5 (JNI crashes -- official wrapper, Application-scoped lifecycle, NDK crash reporting), Pitfall 8 (TTS init race -- async-safe wrapper in ViewModel scope), Pitfall 10 (audio permissions -- request on first voice tap, graceful fallback).

### Phase 5: Polish and Hardening
**Rationale:** Refinements that improve daily usability but are not critical for a working product.
**Delivers:** Conversation history persistence (Room), message retention policy, dark mode testing, FCM wake-up signal (optional), battery optimization tuning.
**Addresses:** Persistent history, notification priority refinement, battery life optimization.

### Phase Ordering Rationale

- Phases follow the architecture's natural dependency chain: transport -> data display -> interaction -> advanced features -> polish.
- The 409 conflict is an absolute blocker -- no code should be written before this is resolved. Phase 1 exists to tackle this head-on.
- Voice is deferred to Phase 4 because it is independent of the core chat experience and carries the highest integration risk (JNI, native memory, device-specific performance).
- Permissions are separated from basic UI (Phase 3 vs Phase 2) because they require notification infrastructure and have distinct testing requirements.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1:** The 409 conflict resolution needs concrete investigation of zellij-claude's current polling mechanism. How does it handle being "replaced"? Is there a `/claim` command? Can the Mac-side gracefully yield polling to Relay?
- **Phase 4:** Whisper performance on target devices needs early prototyping. The quantized base.en model should be tested on the actual phone before committing to the voice architecture.

Phases with standard patterns (skip research-phase):
- **Phase 2:** Standard Compose MVVM with LazyColumn. Well-documented patterns, Google samples available.
- **Phase 3:** Standard Android notification channels + callback handling. Established patterns.
- **Phase 5:** Standard Room persistence and polish work.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All versions verified against official release notes. Compatibility matrix confirmed. |
| Features | MEDIUM-HIGH | Competitive landscape well-researched. Feature prioritization is sound. Slight uncertainty on whether Claude Channels will evolve to cover Relay's niche. |
| Architecture | HIGH | Standard Android Clean Architecture with well-documented patterns. Telegram Bot API is simple REST. |
| Pitfalls | HIGH | Critical pitfalls (409 conflict, Doze mode) are well-documented platform limitations. Whisper pitfalls are MEDIUM-HIGH (device-dependent). |

**Overall confidence:** HIGH

### Gaps to Address

- **409 conflict resolution mechanism:** The exact coordination protocol between Relay and zellij-claude's existing Telegram polling is undefined. This must be designed and potentially prototyped before Phase 1 implementation begins.
- **Whisper performance on target device:** Benchmarks are from community reports. Actual transcription latency on the specific target phone needs validation. Build a minimal Whisper prototype early.
- **zellij-claude message format stability:** The MessageProtocol adapter depends on the `@kuerzel` format and status patterns remaining stable. Any planned format changes on the Mac side should be identified.
- **Permission callback format:** The exact `callback:allow:kuerzel` / `callback:deny:kuerzel` format needs verification against current zellij-claude source code. The research assumes this format but it should be confirmed.
- **Claude Channels evolution:** If Anthropic's official Channels feature adds session-aware UI or push notifications, Relay's competitive position changes. Monitor this during development.

## Sources

### Primary (HIGH confidence)
- [Telegram Bot API Documentation](https://core.telegram.org/bots/api) -- polling, sendMessage, callback queries, rate limits
- [Android Developer Documentation](https://developer.android.com/) -- foreground services, Doze mode, notifications, TTS
- [whisper.cpp GitHub](https://github.com/ggml-org/whisper.cpp) -- Android example, model sizes, JNI approach
- [Kotlin 2.3.20 Release](https://blog.jetbrains.com/kotlin/2026/03/kotlin-2-3-20-released/) -- version compatibility
- [AGP 9.1.0 Release Notes](https://developer.android.com/build/releases/agp-9-1-0-release-notes) -- build system requirements
- [Compose BOM](https://developer.android.com/develop/ui/compose/bom) -- version alignment
- [Claude Code Remote Control docs](https://code.claude.com/docs/en/remote-control) -- competitive landscape
- [Claude Code Channels docs](https://code.claude.com/docs/en/channels) -- competitive landscape

### Secondary (MEDIUM confidence)
- [Happy Coder](https://happy.engineering/docs/features/) -- competitor features
- [Nimbalyst](https://nimbalyst.com/blog/best-mobile-apps-for-claude-code-2026/) -- competitor comparison
- [Tactic Remote](https://tacticremote.com/) -- competitor features
- [ionio.ai Edge Deployment Guide](https://www.ionio.ai/blog/running-transcription-models-on-the-edge-a-practical-guide-for-devices) -- Whisper performance benchmarks
- [Whisper Model Sizes](https://openwhispr.com/blog/whisper-model-sizes-explained) -- model selection rationale

### Tertiary (LOW confidence)
- WhisperKit Android as alternative to whisper.cpp JNI -- newer, less battle-tested, needs validation if considered

---
*Research completed: 2026-04-02*
*Ready for roadmap: yes*
