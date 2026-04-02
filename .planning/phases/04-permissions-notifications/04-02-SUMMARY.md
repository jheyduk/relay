---
phase: 04-permissions-notifications
plan: 02
subsystem: notifications
tags: [android-notifications, deep-link, pendingintent, notification-channels, koin]

requires:
  - phase: 01-transport-foundation
    provides: PollingService, TelegramPoller, RelayUpdate model
  - phase: 03-messaging-chat
    provides: ChatScreen route, NavGraph, RelayMessageType.TEXT

provides:
  - NotificationHelper with two priority channels (permissions HIGH, updates DEFAULT)
  - Notification triggering in PollingService for PERMISSION and COMPLETION messages
  - Deep-link navigation from notification taps via relay://chat/{kuerzel}
  - singleTop launch mode and onNewIntent handling for running activity

affects: [04-permissions-notifications, 05-voice-pipeline]

tech-stack:
  added: [NotificationCompat, NotificationChannel, PendingIntent, navDeepLink]
  patterns: [notification-channel-separation, deep-link-via-launchedeffect, singleTop-onNewIntent]

key-files:
  created:
    - androidApp/src/main/java/dev/heyduk/relay/service/NotificationHelper.kt
  modified:
    - androidApp/src/main/java/dev/heyduk/relay/service/PollingService.kt
    - androidApp/src/main/java/dev/heyduk/relay/MainActivity.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/navigation/RelayNavGraph.kt
    - androidApp/src/main/AndroidManifest.xml
    - androidApp/src/main/java/dev/heyduk/relay/di/AndroidModule.kt

key-decisions:
  - "LaunchedEffect with mutableStateOf for deep-link navigation -- avoids navigating before NavHost is composed"
  - "extractKuerzelFromIntent parses relay://chat/{kuerzel} with scheme+host check, path segment extraction"
  - "PendingIntent uses session.hashCode() as requestCode for unique intents per session"

patterns-established:
  - "Deep-link via LaunchedEffect: store pending kuerzel in mutableStateOf, observe in Compose, navigate then null-out"
  - "Notification channel separation: HIGH for user-action-required, DEFAULT for informational"

requirements-completed: [NOTF-01, NOTF-02, NOTF-03, NOTF-04]

duration: 3min
completed: 2026-04-02
---

# Phase 04 Plan 02: Notifications Summary

**Two-channel notification system with deep-link navigation for permission requests (HIGH priority) and session completions (DEFAULT priority)**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-02T20:48:55Z
- **Completed:** 2026-04-02T20:52:26Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- NotificationHelper with relay_permissions (HIGH, sound+vibration) and relay_updates (DEFAULT, badge-only) channels
- PollingService triggers notifications on PERMISSION and COMPLETION message types after DB insert
- Deep-link from notification tap navigates to correct session chat screen via relay://chat/{kuerzel}
- singleTop + onNewIntent ensures notification taps reuse existing activity

## Task Commits

Each task was committed atomically:

1. **Task 1: NotificationHelper with two priority channels** - `1bc3d54` (feat)
2. **Task 2: PollingService notification triggers and deep-link navigation** - `2f9c128` (feat)

## Files Created/Modified
- `androidApp/src/main/java/dev/heyduk/relay/service/NotificationHelper.kt` - Notification channels and emission for permission/completion messages
- `androidApp/src/main/java/dev/heyduk/relay/service/PollingService.kt` - Notification triggering in update collector
- `androidApp/src/main/java/dev/heyduk/relay/MainActivity.kt` - Deep-link handling via LaunchedEffect and onNewIntent
- `androidApp/src/main/java/dev/heyduk/relay/presentation/navigation/RelayNavGraph.kt` - navDeepLink on chat route
- `androidApp/src/main/AndroidManifest.xml` - singleTop launch mode and deep-link intent filter
- `androidApp/src/main/java/dev/heyduk/relay/di/AndroidModule.kt` - NotificationHelper Koin registration
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/TelegramRepositoryImpl.kt` - Added callback_response param to insertOrIgnore

## Decisions Made
- Used LaunchedEffect with mutableStateOf for deep-link navigation to avoid navigating before NavHost is composed
- PendingIntent uses session.hashCode() as requestCode to ensure unique per-session intents
- extractKuerzelFromIntent parses URI via scheme/host check rather than regex for clarity

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed insertOrIgnore calls missing callback_response parameter**
- **Found during:** Task 1 (compilation verification)
- **Issue:** Plan 04-01 added callback_response column to Messages.sq schema but did not update the insertOrIgnore callers in TelegramRepositoryImpl and PollingService
- **Fix:** Added `callback_response = null` parameter to both insertOrIgnore call sites
- **Files modified:** TelegramRepositoryImpl.kt, PollingService.kt
- **Verification:** compileDebugKotlin passes
- **Committed in:** 1bc3d54 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Fix was necessary for compilation. No scope creep.

## Issues Encountered
None beyond the deviation above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Notification infrastructure complete, ready for plan 04-03 (permission action UI)
- Deep-link navigation enables seamless flow from notification to session chat

---
*Phase: 04-permissions-notifications*
*Completed: 2026-04-02*

## Self-Check: PASSED
- All 6 key files verified present
- Both task commits (1bc3d54, 2f9c128) verified in git log
