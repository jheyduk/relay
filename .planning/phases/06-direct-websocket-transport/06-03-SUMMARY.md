---
phase: 06-direct-websocket-transport
plan: 03
subsystem: android
tags: [websocket, nsd, mdns, foreground-service, koin, datastore]

# Dependency graph
requires:
  - phase: 06-direct-websocket-transport plan 02
    provides: WebSocketClient, ConnectionState, RelayRepository in shared module
provides:
  - WebSocketService foreground service with WebSocket connection
  - NsdDiscovery for _relay._tcp mDNS service discovery
  - Rewritten setup screen (server secret + WireGuard IP)
  - Telegram-free Android module (no bot tokens, no OffsetProvider)
affects: [06-04-integration-testing]

# Tech tracking
tech-stack:
  added: [NsdManager, mDNS discovery]
  patterns: [mDNS-first with WireGuard fallback, callbackFlow for NSD callbacks]

key-files:
  created:
    - androidApp/src/main/java/dev/heyduk/relay/service/NsdDiscovery.kt
    - androidApp/src/main/java/dev/heyduk/relay/service/WebSocketService.kt
  modified:
    - androidApp/src/main/java/dev/heyduk/relay/util/PollingServiceLauncher.kt
    - androidApp/src/main/AndroidManifest.xml
    - androidApp/src/main/java/dev/heyduk/relay/presentation/setup/SetupViewModel.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/setup/SetupScreen.kt
    - androidApp/src/main/java/dev/heyduk/relay/di/AndroidModule.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/session/SessionListViewModel.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/status/StatusViewModel.kt
    - androidApp/src/main/java/dev/heyduk/relay/MainActivity.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/status/StatusScreen.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/navigation/RelayNavGraph.kt

key-decisions:
  - "mDNS discovery with 5s timeout, WireGuard IP as fallback for remote access"
  - "No network validation during setup -- server may not be running yet"
  - "PollingService.kt and DataStoreOffsetProvider.kt removed as Telegram cleanup"
  - "isConfigured check in MainActivity uses server_secret instead of bot tokens"

patterns-established:
  - "NsdDiscovery callbackFlow pattern for NSD → coroutines bridge"
  - "WebSocketService reads config from DataStore at start, same as old PollingService pattern"

requirements-completed: [R-06-05, R-06-06]

# Metrics
duration: 5min
completed: 2026-04-03
---

# Phase 06 Plan 03: Android WebSocket Layer Summary

**WebSocketService replaces PollingService with mDNS discovery, simplified setup screen (shared secret only), and full Telegram code removal from Android layer**

## Performance

- **Duration:** 5 min
- **Started:** 2026-04-03T09:30:30Z
- **Completed:** 2026-04-03T09:35:39Z
- **Tasks:** 2
- **Files modified:** 14

## Accomplishments
- WebSocketService foreground service connects to relay-server via WebSocket with mDNS discovery and WireGuard fallback
- NsdDiscovery wraps Android NsdManager in callbackFlow for coroutine-safe _relay._tcp service discovery
- Setup screen simplified to two fields: server secret (required) and WireGuard IP (optional)
- All Telegram-specific code removed from Android layer (bot tokens, PollingService, DataStoreOffsetProvider, OffsetProvider)
- ViewModels migrated from TelegramRepository to RelayRepository

## Task Commits

Each task was committed atomically:

1. **Task 1: Create NsdDiscovery and WebSocketService** - `d67062a` (feat)
2. **Task 2: Rewrite setup screen, update AndroidModule, remove Telegram code** - `a54d73e` (feat)

## Files Created/Modified
- `androidApp/src/main/java/dev/heyduk/relay/service/NsdDiscovery.kt` - mDNS discovery via NsdManager with callbackFlow
- `androidApp/src/main/java/dev/heyduk/relay/service/WebSocketService.kt` - Foreground service maintaining WebSocket connection
- `androidApp/src/main/java/dev/heyduk/relay/util/PollingServiceLauncher.kt` - Updated to launch WebSocketService
- `androidApp/src/main/AndroidManifest.xml` - Registered WebSocketService instead of PollingService
- `androidApp/src/main/java/dev/heyduk/relay/presentation/setup/SetupViewModel.kt` - Server secret + WireGuard IP fields
- `androidApp/src/main/java/dev/heyduk/relay/presentation/setup/SetupScreen.kt` - Two-field setup UI
- `androidApp/src/main/java/dev/heyduk/relay/di/AndroidModule.kt` - NsdDiscovery added, Telegram singletons removed
- `androidApp/src/main/java/dev/heyduk/relay/presentation/session/SessionListViewModel.kt` - Uses RelayRepository
- `androidApp/src/main/java/dev/heyduk/relay/presentation/status/StatusViewModel.kt` - Uses RelayRepository
- `androidApp/src/main/java/dev/heyduk/relay/MainActivity.kt` - isConfigured checks server_secret, auto-starts WebSocketService
- `androidApp/src/main/java/dev/heyduk/relay/presentation/status/StatusScreen.kt` - References WebSocketService
- `androidApp/src/main/java/dev/heyduk/relay/presentation/navigation/RelayNavGraph.kt` - Calls startWebSocketService

## Decisions Made
- mDNS discovery with 5-second timeout before falling back to WireGuard IP -- balances fast local discovery with reliable remote access
- No network validation during setup save -- server may not be running when user configures the app
- Removed PollingService.kt and DataStoreOffsetProvider.kt entirely since this plan is the designated cleanup point
- Updated MainActivity isConfigured check from 3 Telegram tokens to single server_secret

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated all callers of renamed functions**
- **Found during:** Task 1
- **Issue:** StatusScreen, RelayNavGraph, and MainActivity referenced old startPollingService/RequestNotificationPermissionAndStartPolling functions
- **Fix:** Updated all imports and call sites to use startWebSocketService/RequestNotificationPermissionAndStartConnection
- **Files modified:** StatusScreen.kt, RelayNavGraph.kt, MainActivity.kt
- **Verification:** grep confirms no remaining references to old function names
- **Committed in:** d67062a (Task 1 commit)

**2. [Rule 2 - Missing Critical] Updated isConfigured check in MainActivity**
- **Found during:** Task 1
- **Issue:** MainActivity checked for 3 Telegram tokens to determine isConfigured, would never match after Telegram removal
- **Fix:** Changed isConfigured to check for server_secret only
- **Files modified:** MainActivity.kt
- **Verification:** Reviewed DataStore key matches SetupViewModel.SERVER_SECRET_KEY
- **Committed in:** d67062a (Task 1 commit)

**3. [Rule 2 - Missing Critical] Removed PollingService.kt and DataStoreOffsetProvider.kt**
- **Found during:** Task 2
- **Issue:** Old Telegram files would cause verification failure (grep for PollingService should return 0 hits)
- **Fix:** git rm both files
- **Files modified:** PollingService.kt (deleted), DataStoreOffsetProvider.kt (deleted)
- **Verification:** grep -r "PollingService" returns only comment reference to file name
- **Committed in:** a54d73e (Task 2 commit)

---

**Total deviations:** 3 auto-fixed (2 missing critical, 1 blocking)
**Impact on plan:** All auto-fixes necessary for correctness. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all data sources are wired to real implementations.

## Next Phase Readiness
- Android layer fully migrated to WebSocket transport
- Ready for Plan 04 integration testing across Mac server + Android client
- SessionRepositoryImpl in shared module still references TelegramRepository (shared module concern, not Android layer)

## Self-Check: PASSED

All created files verified on disk. All commit hashes found in git log.

---
*Phase: 06-direct-websocket-transport*
*Completed: 2026-04-03*
