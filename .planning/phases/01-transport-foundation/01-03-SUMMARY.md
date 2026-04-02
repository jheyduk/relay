---
phase: 01-transport-foundation
plan: 03
subsystem: android
tags: [foreground-service, compose, datastore, koin, navigation, polling]

requires:
  - phase: 01-transport-foundation/01-02
    provides: "TelegramApi, TelegramPoller, RelayMessageParser, TelegramRepository, SharedModule, SQLDelight schema"
provides:
  - "Foreground service with dataSync type hosting Telegram long-polling loop"
  - "NetworkMonitor wrapping ConnectivityManager as Flow<Boolean>"
  - "DataStoreOffsetProvider persisting polling offset"
  - "Android Koin module with DataStore, OffsetProvider, NetworkMonitor, SQLDelight bindings"
  - "Setup screen for bot token configuration with validation"
  - "Status screen with connection indicator, message list, and test command input"
  - "Navigation graph routing between setup and status screens"
  - "Debug APK ready for device testing"
affects: [02-session-ui, 03-messaging, 04-permissions, 05-voice]

tech-stack:
  added: [material-icons-extended, ktor-client-core (androidApp), sqldelight-android-driver (androidApp), datastore-preferences (androidApp), koin-android]
  patterns: [foreground-service-token-flow, datastore-preferences-delegate, koin-viewmodel-injection, compose-navigation-conditional-start]

key-files:
  created:
    - androidApp/src/main/java/dev/heyduk/relay/service/PollingService.kt
    - androidApp/src/main/java/dev/heyduk/relay/service/NetworkMonitor.kt
    - androidApp/src/main/java/dev/heyduk/relay/data/DataStoreOffsetProvider.kt
    - androidApp/src/main/java/dev/heyduk/relay/di/AndroidModule.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/setup/SetupScreen.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/setup/SetupViewModel.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/status/StatusScreen.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/status/StatusViewModel.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/navigation/RelayNavGraph.kt
    - androidApp/src/main/java/dev/heyduk/relay/MainActivity.kt
  modified:
    - androidApp/build.gradle.kts
    - androidApp/src/main/AndroidManifest.xml
    - androidApp/src/main/java/dev/heyduk/relay/RelayApp.kt

key-decisions:
  - "Tokens read from DataStore at service start, not injected via Koin singletons -- avoids unresolvable Koin deps at app startup"
  - "PollingService inserts updates into SQLDelight directly -- decouples service from SharedModule's TelegramRepository poller dependency"
  - "preferencesDataStore delegate on Context extension for single-instance DataStore pattern"
  - "Manifest theme changed from Material3 style resource to android:Theme.Material.Light.NoActionBar -- Compose manages theming programmatically"

patterns-established:
  - "Foreground service token flow: read DataStore -> construct API -> guard with stopSelf() on missing config"
  - "Network-aware polling: awaitConnected() before pollLoop(), notification updates on connectivity changes"
  - "Koin ViewModel registration with viewModel {} DSL in androidModule"
  - "Conditional navigation start: DataStore check determines setup vs status as initial destination"

requirements-completed: [TRNS-01, TRNS-03, TRNS-05]

duration: 6min
completed: 2026-04-02
---

# Phase 1 Plan 3: Android Platform Layer Summary

**Foreground polling service with network recovery, DataStore token storage, Compose setup/status screens, and conditional navigation producing a 72MB debug APK**

## Performance

- **Duration:** 6 min
- **Started:** 2026-04-02T16:28:50Z
- **Completed:** 2026-04-02T16:34:35Z
- **Tasks:** 3 (2 auto + 1 checkpoint auto-approved)
- **Files modified:** 13

## Accomplishments
- Foreground service reads bot tokens from DataStore, constructs TelegramApi/TelegramPoller with real tokens, and guards against missing config
- NetworkMonitor wraps ConnectivityManager.NetworkCallback into Flow<Boolean> with awaitConnected() suspend function
- DataStoreOffsetProvider persists polling offset across app restarts
- Setup screen accepts relay bot token, command bot token, and chat ID with Telegram API validation
- Status screen shows connection indicator, recent messages from SQLDelight, and test command input
- Navigation conditionally routes to setup or status based on DataStore configuration state
- Debug APK assembles successfully, all 16 shared tests still pass

## Task Commits

Each task was committed atomically:

1. **Task 1: PollingService, NetworkMonitor, DataStoreOffsetProvider, AndroidModule** - `8fdb277` (feat)
2. **Task 2: Setup screen, Status screen, navigation, MainActivity** - `0c1834c` (feat)
3. **Task 3: E2E verification checkpoint** - auto-approved (auto_advance=true)

## Files Created/Modified
- `androidApp/src/main/java/dev/heyduk/relay/service/PollingService.kt` - Foreground service hosting polling coroutine with token-from-DataStore flow
- `androidApp/src/main/java/dev/heyduk/relay/service/NetworkMonitor.kt` - ConnectivityManager wrapper as Flow<Boolean>
- `androidApp/src/main/java/dev/heyduk/relay/data/DataStoreOffsetProvider.kt` - OffsetProvider using DataStore Preferences
- `androidApp/src/main/java/dev/heyduk/relay/di/AndroidModule.kt` - Koin module with DataStore, OffsetProvider, NetworkMonitor, SQLDelight, ViewModels
- `androidApp/src/main/java/dev/heyduk/relay/presentation/setup/SetupScreen.kt` - Bot token configuration UI
- `androidApp/src/main/java/dev/heyduk/relay/presentation/setup/SetupViewModel.kt` - Token persistence and validation logic
- `androidApp/src/main/java/dev/heyduk/relay/presentation/status/StatusScreen.kt` - Connection status, message list, command input
- `androidApp/src/main/java/dev/heyduk/relay/presentation/status/StatusViewModel.kt` - Combines network state and recent messages
- `androidApp/src/main/java/dev/heyduk/relay/presentation/navigation/RelayNavGraph.kt` - Conditional setup/status navigation
- `androidApp/src/main/java/dev/heyduk/relay/MainActivity.kt` - Entry point with DataStore config check
- `androidApp/build.gradle.kts` - Added koin-android, datastore-preferences, sqldelight-android-driver, ktor-client-core, material-icons-extended
- `androidApp/src/main/AndroidManifest.xml` - Added PollingService with foregroundServiceType=dataSync, fixed theme
- `androidApp/src/main/java/dev/heyduk/relay/RelayApp.kt` - Loads sharedModule and androidModule

## Decisions Made
- Tokens read from DataStore at service start time, not injected via Koin -- avoids unresolvable dependencies at app startup when tokens aren't yet configured
- PollingService inserts updates into SQLDelight directly rather than via TelegramRepository -- decouples the service from the SharedModule's poller-dependent repository singleton
- Used preferencesDataStore Context extension delegate for guaranteed single-instance DataStore
- Changed manifest theme from non-existent Material3 style resource to android:Theme.Material.Light.NoActionBar since Compose manages theming programmatically

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added missing Ktor client dependency to androidApp**
- **Found during:** Task 1 (PollingService compilation)
- **Issue:** PollingService injects HttpClient from Koin but ktor-client-core was only in shared module
- **Fix:** Added `implementation(libs.ktor.client.core)` to androidApp/build.gradle.kts
- **Files modified:** androidApp/build.gradle.kts
- **Verification:** compileDebugKotlin passes
- **Committed in:** 8fdb277 (Task 1 commit)

**2. [Rule 3 - Blocking] Fixed non-existent Material3 theme resource in AndroidManifest**
- **Found during:** Task 2 (assembleDebug)
- **Issue:** Theme.Material3.DayNight.NoActionBar doesn't exist as an Android resource -- it's a Compose-only concept
- **Fix:** Changed to android:Theme.Material.Light.NoActionBar (platform theme)
- **Files modified:** androidApp/src/main/AndroidManifest.xml
- **Verification:** assembleDebug produces APK
- **Committed in:** 0c1834c (Task 2 commit)

**3. [Rule 3 - Blocking] Added material-icons-extended for Settings icon**
- **Found during:** Task 2 (compileDebugKotlin)
- **Issue:** Icons.Default.Settings requires material-icons-extended which isn't included by default in Compose BOM
- **Fix:** Added `implementation("androidx.compose.material:material-icons-extended")` to androidApp/build.gradle.kts
- **Files modified:** androidApp/build.gradle.kts
- **Verification:** compileDebugKotlin passes
- **Committed in:** 0c1834c (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (3 blocking)
**Impact on plan:** All auto-fixes necessary for compilation. No scope creep.

## Issues Encountered
None beyond the blocking issues documented above.

## User Setup Required

The E2E device verification checkpoint was auto-approved. When ready for device testing:
1. Create a new Telegram bot via @BotFather for Relay
2. Copy existing zellij-claude bot token from ~/.config/zellij-claude/telegram.json
3. Install APK: `adb install androidApp/build/outputs/apk/debug/androidApp-debug.apk`
4. Enter tokens in Setup screen, tap Validate & Save
5. Start Polling on Status screen

## Known Stubs
None -- all UI components are wired to real data sources (DataStore for tokens, SQLDelight for messages, NetworkMonitor for connectivity).

## Next Phase Readiness
- Phase 1 transport foundation complete: KMP project structure, domain models, Telegram API client, polling with backoff, SQLDelight persistence, foreground service, setup/status UI
- Ready for Phase 2 (Session UI): TelegramRepository.getMessagesForSession() and getRecentMessages() are available
- Ready for Phase 3 (Messaging): TelegramRepository.sendCommand() and sendRawCommand() are wired
- Note: E2E device verification was auto-approved and should be manually tested before starting Phase 2

## Self-Check: PASSED

All 11 created files verified present. Both task commits (8fdb277, 0c1834c) verified in git log. APK exists at 72MB.

---
*Phase: 01-transport-foundation*
*Completed: 2026-04-02*
