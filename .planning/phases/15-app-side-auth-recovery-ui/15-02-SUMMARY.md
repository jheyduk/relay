---
phase: 15-app-side-auth-recovery-ui
plan: 02
subsystem: ui
tags: [compose, material3, auth-recovery, websocket, intent]

requires:
  - phase: 15-01
    provides: "sendAuthCode in ChatRepository, AUTH_REQUIRED/AUTH_URL/AUTH_TIMEOUT message types"
  - phase: 14-01
    provides: "OAuth URL extraction and forwarding via AUTH_URL messages"
  - phase: 13-01
    provides: "Auth error detection, /login dispatch, AUTH_REQUIRED signaling"
provides:
  - "AuthRecoveryCard composable with 5 lifecycle phases"
  - "Auth state management in ChatViewModel (observe, send code, retry)"
  - "Persistent auth recovery card in ChatScreen bottom bar"
affects: []

tech-stack:
  added: []
  patterns: ["Persistent overlay card in bottom bar for transient ViewModel state"]

key-files:
  created:
    - androidApp/src/main/java/dev/heyduk/relay/presentation/chat/AuthRecoveryCard.kt
  modified:
    - androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatViewModel.kt
    - androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatScreen.kt

key-decisions:
  - "Auth card as persistent bottom-bar overlay rather than inline chat message (auth types are DB-skipped)"
  - "AuthPhase enum in presentation layer only (not shared module)"

patterns-established:
  - "Persistent overlay pattern: transient ViewModel state rendered above input in bottomBar Column"

requirements-completed: [AUTH-05, AUTH-07]

duration: 2min
completed: 2026-04-08
---

# Phase 15 Plan 02: Auth Recovery UI Summary

**AuthRecoveryCard composable with 5-phase lifecycle (required/open-url/enter-code/recovered/timeout), wired into ChatScreen as persistent bottom-bar overlay with Intent.ACTION_VIEW browser launch and code dispatch**

## Performance

- **Duration:** 2 min
- **Started:** 2026-04-08T12:30:39Z
- **Completed:** 2026-04-08T12:32:30Z
- **Tasks:** 3 (2 auto + 1 checkpoint auto-approved)
- **Files modified:** 3

## Accomplishments
- AuthRecoveryCard composable renders all 5 auth lifecycle phases with amber-themed Material 3 Surface
- ChatViewModel observes AUTH_REQUIRED/AUTH_URL/AUTH_TIMEOUT from relayUpdates flow and provides openAuthUrl/sendAuthCode/retryAuth methods
- ChatScreen shows persistent auth recovery card above the input bar when auth flow is active
- Browser launch via Intent.ACTION_VIEW for OAuth URL
- Code input with disable-after-send UX and CircularProgressIndicator during send
- Auto-dismiss after 5 seconds on successful recovery
- Retry button on timeout

## Task Commits

Each task was committed atomically:

1. **Task 1: Create AuthRecoveryCard composable and add auth state to ChatViewModel** - `89f8ef5` (feat)
2. **Task 2: Wire AuthRecoveryCard into ChatScreen message rendering** - `7a158df` (feat)
3. **Task 3: Visual verification of auth recovery flow** - auto-approved (checkpoint)

## Files Created/Modified
- `androidApp/.../chat/AuthRecoveryCard.kt` - AuthPhase enum + AuthRecoveryCard composable with 5 lifecycle phases
- `androidApp/.../chat/ChatViewModel.kt` - Auth state in LocalState/ChatUiState, relay observer, openAuthUrl/sendAuthCode/retryAuth methods
- `androidApp/.../chat/ChatScreen.kt` - Persistent AuthRecoveryCard in bottomBar above input

## Decisions Made
- Auth card rendered as persistent bottom-bar overlay (not inline in messages) because AUTH_REQUIRED/AUTH_URL/AUTH_TIMEOUT are DB-skipped non-chat messages
- AuthPhase enum defined in AuthRecoveryCard.kt (presentation-only, not in shared module)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Auth recovery UI complete end-to-end
- Full chain from server auth detection (Phase 13) -> URL extraction (Phase 14) -> code transport (15-01) -> UI (15-02) is wired
- Ready for live testing with actual OAuth flow

---
*Phase: 15-app-side-auth-recovery-ui*
*Completed: 2026-04-08*
