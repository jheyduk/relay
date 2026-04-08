---
phase: 15-app-side-auth-recovery-ui
verified: 2026-04-08T13:00:00Z
status: human_needed
score: 4/4 must-haves verified
re_verification: false
human_verification:
  - test: "Trigger auth error and verify AuthRecoveryCard appears above input bar with amber styling"
    expected: "Amber card shows 'Auth Required' status chip, then transitions to 'Open URL' when OAuth URL arrives"
    why_human: "Visual rendering, color, and layout cannot be verified programmatically"
  - test: "Tap 'Open in Browser' and verify phone browser opens with OAuth URL"
    expected: "Default browser launches with the OAuth URL, card transitions to 'Enter Authorization Code' phase"
    why_human: "Intent.ACTION_VIEW and browser launch require device interaction"
  - test: "Paste auth code and tap 'Send Code', verify code dispatched to terminal"
    expected: "Button disables after tap, shows spinner, card transitions to 'Recovered', auto-dismisses after 5s"
    why_human: "End-to-end flow through WebSocket to terminal requires live server and session"
  - test: "Verify timeout scenario shows retry card"
    expected: "After auth_timeout message, card shows 'Auth Recovery Timed Out' with Retry button"
    why_human: "Requires triggering timeout condition on server"
---

# Phase 15: App-Side Auth Recovery UI Verification Report

**Phase Goal:** User can complete the entire OAuth re-authentication flow from their phone -- open the URL, get the code, paste it back, and see the session recover
**Verified:** 2026-04-08T13:00:00Z
**Status:** human_needed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can tap the OAuth URL in the app and it opens in their phone's browser | VERIFIED | ChatScreen.kt line 244-249: Intent.ACTION_VIEW with Uri.parse(url) launches browser, AuthRecoveryCard OPEN_URL phase has "Open in Browser" filled button |
| 2 | User can paste the authorization code into the app and it gets dispatched to the correct Claude Code terminal session | VERIFIED | AuthRecoveryCard ENTER_CODE phase has OutlinedTextField + "Send Code" button -> ChatViewModel.sendAuthCode -> ChatRepository.sendAuthCode -> RelayRepository -> WebSocketClient sends {action: "auth_code", kuerzel, code} -> server dispatchCommand(kuerzel, code) |
| 3 | App shows a status indicator tracking the auth recovery lifecycle: detected -> login triggered -> waiting for code -> recovered | VERIFIED | AuthPhase enum: AUTH_REQUIRED, OPEN_URL, ENTER_CODE, RECOVERED, TIMED_OUT. ChatViewModel observes AUTH_REQUIRED/AUTH_URL/AUTH_TIMEOUT from relayUpdates. Status chip with colored circle for each phase. |
| 4 | After successful recovery, the session returns to normal operating state in the app | VERIFIED | ChatViewModel.sendAuthCode transitions to RECOVERED, then after 5s delay sets authPhase=null/authUrl=null, dismissing the card and returning to normal chat input |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `androidApp/.../AuthRecoveryCard.kt` | Auth recovery composable with URL button, code input, status chip, timeout retry | VERIFIED | 213 lines, AuthPhase enum, 5 phase branches, Material 3 Surface with amber theme |
| `androidApp/.../ChatViewModel.kt` | Auth recovery state management | VERIFIED | authPhase/authUrl/isSendingAuthCode in LocalState+ChatUiState, observes AUTH_REQUIRED/AUTH_URL/AUTH_TIMEOUT, openAuthUrl/sendAuthCode/retryAuth methods |
| `androidApp/.../ChatScreen.kt` | AUTH_REQUIRED/AUTH_URL/AUTH_TIMEOUT rendering | VERIFIED | Persistent AuthRecoveryCard in bottomBar Column when authPhase != null, Intent.ACTION_VIEW wired |
| `server/relay-server.cjs` | auth_code action handler | VERIFIED | Line 1131: `msg.action === 'auth_code'`, validates kuerzel+code, calls dispatchCommand |
| `shared/.../WebSocketClient.kt` | sendAuthCode method | VERIFIED | Line 144: sends {action: "auth_code", kuerzel, code} JSON |
| `shared/.../RelayRepository.kt` | sendAuthCode interface | VERIFIED | Line 29: interface method |
| `shared/.../RelayRepositoryImpl.kt` | sendAuthCode delegation | VERIFIED | Lines 48-49: delegates to webSocketClient.sendAuthCode |
| `shared/.../ChatRepository.kt` | sendAuthCode interface | VERIFIED | Line 32: interface method |
| `shared/.../ChatRepositoryImpl.kt` | sendAuthCode delegation | VERIFIED | Lines 87-88: delegates to relayRepository.sendAuthCode |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| ChatScreen.kt | AuthRecoveryCard.kt | Persistent card in bottomBar when authPhase != null | WIRED | Lines 237-260 in ChatScreen |
| AuthRecoveryCard onSendCode | ChatViewModel.sendAuthCode | Lambda callback `{ code -> viewModel.sendAuthCode(code) }` | WIRED | Line 253 in ChatScreen |
| ChatViewModel.sendAuthCode | ChatRepository.sendAuthCode | Repository call `chatRepository.sendAuthCode(kuerzel, code)` | WIRED | Line 159 in ChatViewModel |
| AuthRecoveryCard onOpenUrl | Intent.ACTION_VIEW | Android Intent with Uri.parse(url) | WIRED | Lines 244-249 in ChatScreen |
| WebSocketClient.sendAuthCode | server relay-server.cjs | WebSocket JSON {action: 'auth_code', kuerzel, code} | WIRED | Payload matches server handler |
| server auth_code handler | dispatchCommand | dispatchCommand(msg.kuerzel, msg.code) | WIRED | Line 1138 in relay-server.cjs |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| ChatViewModel | authPhase/authUrl | relayUpdates flow filtered by session | Auth messages from WebSocket (AUTH_REQUIRED/AUTH_URL/AUTH_TIMEOUT) | FLOWING |
| AuthRecoveryCard | authPhase, authUrl | ChatUiState via collectAsStateWithLifecycle | ViewModel state from relayUpdates | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED (requires running Android app on device -- no runnable entry point for automated check)

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-----------|-------------|--------|----------|
| AUTH-05 | 15-02 | User can open the OAuth URL in their phone browser directly from the app | SATISFIED | Intent.ACTION_VIEW in ChatScreen + "Open in Browser" button in AuthRecoveryCard OPEN_URL phase |
| AUTH-06 | 15-01 | User can paste the authorization code in the app and it gets dispatched to the Claude Code terminal | SATISFIED | Full transport chain: AuthRecoveryCard -> ChatViewModel -> ChatRepository -> RelayRepository -> WebSocketClient -> server auth_code handler -> dispatchCommand |
| AUTH-07 | 15-02 | App shows auth recovery status feedback (detected -> login triggered -> waiting for confirmation -> recovered) | SATISFIED | AuthPhase enum with 5 states, colored status chips, ChatViewModel observes relay updates for state transitions |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | - | - | - | - |

No TODOs, FIXMEs, placeholders, or stub patterns found in any phase 15 artifacts.

### Human Verification Required

### 1. Visual Auth Recovery Card Rendering

**Test:** Trigger an auth error on a Claude Code session and verify the amber auth recovery card appears above the chat input bar.
**Expected:** Card shows with amber background, session label, status chip with colored circle, and phase-appropriate content.
**Why human:** Visual styling (colors, layout, spacing) cannot be verified programmatically.

### 2. Browser Launch via OAuth URL

**Test:** When auth card shows "Open URL to Authenticate", tap the "Open in Browser" button.
**Expected:** Default phone browser opens with the OAuth URL. Card transitions to "Enter Authorization Code" phase.
**Why human:** Intent.ACTION_VIEW and browser interaction require device testing.

### 3. Auth Code Paste-Back End-to-End

**Test:** After browser auth, paste the authorization code into the text field and tap "Send Code".
**Expected:** Button disables, spinner shows, card transitions to "Recovered", auto-dismisses after 5 seconds. Code appears in terminal session.
**Why human:** Full end-to-end flow requires live server, WebSocket, and terminal session.

### 4. Timeout and Retry

**Test:** Trigger an auth_timeout condition and verify retry card appears.
**Expected:** Card shows "Auth Recovery Timed Out" with red status chip and "Retry" outlined button. Tapping Retry resets to AUTH_REQUIRED.
**Why human:** Requires triggering timeout scenario on server side.

### Gaps Summary

No automated gaps found. All artifacts exist, are substantive (no stubs), are wired together through the full chain, and data flows from WebSocket through ViewModel to UI. All three requirements (AUTH-05, AUTH-06, AUTH-07) are satisfied.

The phase requires human verification to confirm visual rendering and end-to-end device behavior, as these cannot be tested programmatically.

---

_Verified: 2026-04-08T13:00:00Z_
_Verifier: Claude (gsd-verifier)_
