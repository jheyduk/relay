---
phase: quick
plan: 260403-d2e
subsystem: android-app
tags: [polling, auto-start, service, ux]
dependency_graph:
  requires: [PollingService, DataStore, setup-flow]
  provides: [auto-start-polling]
  affects: [MainActivity, RelayNavGraph, StatusScreen]
tech_stack:
  added: []
  patterns: [shared-utility-extraction, invisible-composable-side-effect]
key_files:
  created:
    - androidApp/src/main/java/dev/heyduk/relay/util/PollingServiceLauncher.kt
  modified:
    - androidApp/src/main/java/dev/heyduk/relay/MainActivity.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/navigation/RelayNavGraph.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/status/StatusScreen.kt
decisions:
  - "RequestNotificationPermissionAndStartPolling as invisible composable for permission+service launch"
  - "Start service regardless of notification permission grant (service works, notification just hidden)"
  - "Explicit startPollingService in onConfigured callback for immediate start without waiting for recomposition"
metrics:
  duration: 2min
  completed: 2026-04-03
---

# Quick Plan 260403-d2e: Auto-start PollingService After Setup Summary

Shared PollingServiceLauncher utility with invisible composable for permission request + service start on both entry paths (fresh setup and app relaunch).

## What Was Done

### Task 1: Extract startPollingService to shared utility and auto-start on app launch
**Commit:** `3146275`

- Created `PollingServiceLauncher.kt` with reusable `startPollingService(Context)` function
- Created `RequestNotificationPermissionAndStartPolling` composable that handles POST_NOTIFICATIONS permission on Android 13+ and starts the service
- Added conditional composable in `MainActivity.kt` that fires when `isConfigured` is true
- Replaced StatusScreen's private helper with the shared utility import
- Removed unused ContextCompat import from StatusScreen

### Task 2: Auto-start PollingService after setup completion
**Commit:** `6b647f9`

- Added `startPollingService(context)` call in RelayNavGraph's `onConfigured` callback, before navigation to sessions
- This ensures immediate polling start without waiting for DataStore recomposition in MainActivity

## Deviations from Plan

None -- plan executed exactly as written.

## Known Stubs

None.

## Self-Check: PASSED
