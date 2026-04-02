---
phase: 04-permissions-notifications
verified: 2026-04-02T21:30:00Z
status: human_needed
score: 6/6 must-haves verified
human_verification:
  - test: "Permission card renders with tool details and Allow/Deny buttons on a live device"
    expected: "Permission card shows tool name, command/filePath, Allow (green) and Deny (red) buttons; tapping Allow sends callback and shows Allowed state"
    why_human: "Compose rendering and visual correctness cannot be verified without running the app on a device"
  - test: "Question card renders option chips and records selection"
    expected: "QuestionCard parses newline-separated options, renders as FilterChips; tapping an option highlights it and sends the text to the session"
    why_human: "Visual rendering and chip interaction require device testing"
  - test: "High-priority permission notification fires when app is backgrounded"
    expected: "Notification appears with sound/vibration as heads-up; title shows 'Permission: @{kuerzel}'; content shows tool details"
    why_human: "Notification heads-up behaviour and audio/vibration require a physical device with notification permissions granted"
  - test: "Completion notification fires at normal priority"
    expected: "Notification appears without sound/vibration (badge only); title shows 'Complete: @{kuerzel}'"
    why_human: "Same as above -- notification priority behaviour requires device testing"
  - test: "Tapping a notification deep-links to the correct session chat screen"
    expected: "App opens (or foregrounds) directly to ChatScreen for the session in the notification, not the session list"
    why_human: "Intent routing and NavHost navigation from PendingIntent require a running app on device"
---

# Phase 4: Permissions & Notifications Verification Report

**Phase Goal:** Users never miss a permission request or session completion -- native Allow/Deny buttons replace Telegram inline keyboards, with push notifications surfacing time-critical events
**Verified:** 2026-04-02T21:30:00Z
**Status:** human_needed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths (from ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User sees native Allow/Deny buttons on permission request messages, showing tool details (command, file path) | ✓ VERIFIED | `PermissionCard.kt` (169 lines): renders `@session`, `toolName`, `command ?: filePath`, `message.content`, and Allow/Deny buttons with green/red tints; handled answered state and spinner |
| 2 | Tapping Allow or Deny sends the correct callback to Telegram and the permission is processed on the Mac side | ✓ VERIFIED | `ChatScreen.kt` lines 111-116 call `viewModel.answerCallback(id, "allow"/"deny")`; `ChatViewModel.answerCallback` delegates to `chatRepository.answerCallback`; `ChatRepositoryImpl.answerCallback` calls `markAnswered` on DB and `telegramRepository.sendCommand(kuerzel, "callback:$response:$kuerzel")` |
| 3 | User can answer AskUserQuestion prompts by tapping native option buttons (multiple choice) | ✓ VERIFIED | `QuestionCard.kt` (109 lines): parses options via `parseQuestionAndOptions`, renders `FilterChip` per option; `ChatScreen.kt` line 117-119 dispatches to `QuestionCard` for `QUESTION` type and calls `viewModel.answerQuestion` |
| 4 | User receives a high-priority push notification when any session requests permission, even with the app backgrounded | ✓ VERIFIED (code) / ? HUMAN NEEDED (runtime) | `NotificationHelper.showPermissionNotification` uses `CHANNEL_PERMISSIONS` (`IMPORTANCE_HIGH`), `PRIORITY_HIGH`, `CATEGORY_ALARM`, vibration enabled; `PollingService` calls this after DB insert for `PERMISSION` type |
| 5 | User receives a normal-priority notification when a session completes its task | ✓ VERIFIED (code) / ? HUMAN NEEDED (runtime) | `NotificationHelper.showCompletionNotification` uses `CHANNEL_UPDATES` (`IMPORTANCE_DEFAULT`), `PRIORITY_DEFAULT`, vibration disabled; `PollingService` calls this for `COMPLETION` type |
| 6 | Tapping a notification opens the app directly to the relevant session | ✓ VERIFIED (code) / ? HUMAN NEEDED (runtime) | `NotificationHelper.buildPendingIntent` creates a `PendingIntent` with `relay://chat/{session}` URI; `MainActivity` handles this in `onCreate` and `onNewIntent` via `LaunchedEffect`; `RelayNavGraph` chat route declares `deepLinks = listOf(navDeepLink { uriPattern = "relay://chat/{kuerzel}" })` |

**Score:** 6/6 truths verified at code level; 5 require human runtime confirmation

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/PermissionCard.kt` | Native Allow/Deny permission card composable | ✓ VERIFIED | 169 lines; substantive implementation with tool details, spinner, answered-state rendering |
| `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/QuestionCard.kt` | AskUserQuestion card with option chips | ✓ VERIFIED | 109 lines; substantive implementation with `parseQuestionAndOptions`, `FilterChip` rendering, and selected-state highlighting |
| `shared/src/commonMain/kotlin/dev/heyduk/relay/data/repository/ChatRepository.kt` | answerCallback method for permission responses | ✓ VERIFIED | Exports `answerCallback(messageId: Long, kuerzel: String, response: String)` |
| `androidApp/src/main/java/dev/heyduk/relay/service/NotificationHelper.kt` | Notification channel creation and notification emission | ✓ VERIFIED | 122 lines; `relay_permissions` (HIGH) and `relay_updates` (DEFAULT) channels; two emission methods with `PendingIntent` deep-links |
| `androidApp/src/main/java/dev/heyduk/relay/service/PollingService.kt` | Notification triggering on permission/completion messages | ✓ VERIFIED | Contains `notificationHelper` injected via Koin; `when(update.type)` block triggers `showPermissionNotification`/`showCompletionNotification` after DB insert |
| `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatScreen.kt` | Message-type-aware rendering with PermissionCard and QuestionCard | ✓ VERIFIED | Lines 110-122: `when(message.type)` dispatch to `PermissionCard`, `QuestionCard`, or `MessageBubble` |
| `androidApp/src/main/java/dev/heyduk/relay/presentation/chat/ChatViewModel.kt` | answerCallback and answerQuestion actions | ✓ VERIFIED | Both methods present (lines 74-99); `_sendingCallbacks: MutableStateFlow<Set<Long>>` tracks per-message state; combined into `uiState.sendingCallbackIds` |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `PermissionCard.kt` | `ChatRepository.answerCallback` | onAllow/onDeny lambdas | ✓ WIRED | `ChatScreen.kt` passes `{ viewModel.answerCallback(message.id, "allow") }` and `"deny"` to `PermissionCard`; `ChatViewModel.answerCallback` calls `chatRepository.answerCallback` |
| `QuestionCard.kt` | `ChatRepository.sendMessage` | onOptionSelected lambda | ✓ WIRED | `ChatScreen.kt` passes `{ option -> viewModel.answerQuestion(message.id, option) }`; `ChatViewModel.answerQuestion` calls both `chatRepository.answerCallback` and `chatRepository.sendMessage` |
| `Messages.sq` | `ChatRepositoryImpl` | markAnswered query | ✓ WIRED | `Messages.sq` line 35-36 defines `markAnswered: UPDATE messages SET callback_response = ? WHERE update_id = ?;`; `ChatRepositoryImpl.answerCallback` (line 51) calls `database.messagesQueries.markAnswered(response, messageId)` |
| `PollingService.kt` | `NotificationHelper` | showPermissionNotification / showCompletionNotification calls | ✓ WIRED | `PollingService.kt` line 51: `val notificationHelper: NotificationHelper by inject()`; lines 104-108: `when(update.type)` block calls both methods |
| `NotificationHelper.kt` | `MainActivity` | PendingIntent with relay://chat/{kuerzel} URI | ✓ WIRED | `NotificationHelper.buildPendingIntent` creates `Intent(context, MainActivity::class.java)` with `data = Uri.parse("relay://chat/$session")`; `FLAG_IMMUTABLE` set |
| `MainActivity.kt` | `RelayNavGraph` | Intent URI parsing and navigation to chat/{kuerzel} | ✓ WIRED | `MainActivity.extractKuerzelFromIntent` parses URI; `LaunchedEffect(kuerzel)` calls `navController.navigate("chat/$kuerzel")`; `onNewIntent` also delegates to same extraction |
| `ChatScreen.kt` | `PermissionCard.kt` | when(message.type) branch for PERMISSION | ✓ WIRED | Line 111: `RelayMessageType.PERMISSION -> PermissionCard(...)` |
| `ChatScreen.kt` | `QuestionCard.kt` | when(message.type) branch for QUESTION | ✓ WIRED | Line 117: `RelayMessageType.QUESTION -> QuestionCard(...)` |
| `ChatViewModel.kt` | `ChatRepository.answerCallback` | answerCallback method delegation | ✓ WIRED | Line 81: `chatRepository.answerCallback(messageId, kuerzel, response)` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| `PermissionCard.kt` | `message.callbackResponse`, `message.toolName`, `message.command`, `message.filePath` | `ChatRepositoryImpl.toChatMessage()` maps from `Messages` DB row; `markAnswered` updates `callback_response` column | Yes -- DB columns populated from `insertOrIgnore` (incoming) and `markAnswered` (user action) | ✓ FLOWING |
| `QuestionCard.kt` | `message.callbackResponse`, `message.content` | Same `toChatMessage()` mapper | Yes -- `message TEXT NOT NULL` column from DB | ✓ FLOWING |
| `ChatScreen.kt` | `uiState.messages`, `uiState.sendingCallbackIds` | `chatRepository.messagesForSession` using SQLDelight `asFlow().mapToList()`; `_sendingCallbacks` MutableStateFlow | Yes -- reactive DB query; set tracked in ViewModel | ✓ FLOWING |
| `NotificationHelper.kt` | `update.session`, `update.toolName`, `update.command`, `update.filePath`, `update.message` | `RelayUpdate` received from `TelegramPoller.updates` flow | Yes -- parsed from live Telegram Bot API responses | ✓ FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED -- no runnable entry points without a device or emulator. The project requires Android build/install for runtime verification.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| MSG-03 | 04-01, 04-03 | User can tap Allow or Deny on permission requests with native buttons | ✓ SATISFIED | `PermissionCard.kt` allow/deny buttons; `ChatScreen.kt` dispatch; `ChatRepositoryImpl.answerCallback` sends `callback:allow/deny:{kuerzel}` |
| MSG-04 | 04-01, 04-03 | Permission requests show tool details (command, file path) | ✓ SATISFIED | `PermissionCard.kt` renders `message.toolName`, `message.command ?: message.filePath`; `ChatMessage` extended with `toolName`, `command`, `filePath`; `toChatMessage()` maps from DB |
| MSG-06 | 04-01, 04-03 | User can answer AskUserQuestion prompts with native option buttons (multiple choice) | ✓ SATISFIED | `QuestionCard.kt` with `FilterChip` per option; `parseQuestionAndOptions` heuristic; `ChatViewModel.answerQuestion` sends option as callback and text |
| NOTF-01 | 04-02 | User receives push notification when a session needs permission | ✓ SATISFIED | `PollingService` triggers `notificationHelper.showPermissionNotification` on `PERMISSION` type; `POST_NOTIFICATIONS` permission declared in `AndroidManifest.xml` |
| NOTF-02 | 04-02 | User receives push notification when a session completes | ✓ SATISFIED | `PollingService` triggers `notificationHelper.showCompletionNotification` on `COMPLETION` type |
| NOTF-03 | 04-02 | Permission notifications use high-priority channel | ✓ SATISFIED | `relay_permissions` channel: `IMPORTANCE_HIGH`, vibration enabled, default sound; `PRIORITY_HIGH`, `CATEGORY_ALARM` on notification |
| NOTF-04 | 04-02 | Completion/info notifications use normal-priority channel | ✓ SATISFIED | `relay_updates` channel: `IMPORTANCE_DEFAULT`, vibration disabled, `setSound(null, null)`; `PRIORITY_DEFAULT` on notification |

**Orphaned requirements check:** REQUIREMENTS.md traceability table maps MSG-03, MSG-04, MSG-06, NOTF-01, NOTF-02, NOTF-03, NOTF-04 to Phase 4. All 7 are claimed by the plans and verified above. No orphaned requirements.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | - | No stubs, TODO/FIXME markers, placeholder returns, or hardcoded empty data found | - | - |

Checked all 9 phase files for `TODO`, `FIXME`, `placeholder`, `return null`, `return []`, `return {}`, hardcoded empty collections. None found in production code paths.

Note: `PermissionCard.kt` uses `FilledTonalButton` instead of `FilledButton` as specified in the plan (plan said `FilledButton` with green tint). The implementation uses `FilledTonalButton` with `containerColor = AllowGreen.copy(alpha = 0.15f)` achieving a tinted appearance. This is a minor visual deviation from the plan's spec but not a functional defect -- the button is visible, coloured, and functional.

### Human Verification Required

#### 1. Permission card renders correctly with tool details

**Test:** Build and install: `./gradlew :androidApp:installDebug`. Navigate to a session, trigger a permission request from Claude Code on Mac (e.g., run a command that touches the filesystem).
**Expected:** A card appears in the chat showing `@{kuerzel}` label, tool name (e.g. "Bash"), command or file path, the request text, and clearly styled Allow (green) and Deny (red) buttons.
**Why human:** Compose visual rendering and Material 3 layout cannot be verified by grep.

#### 2. Allow/Deny buttons transition to answered state

**Test:** Tap "Allow" on a permission card. Observe the card.
**Expected:** Buttons disable immediately, a spinner shows briefly, then the card shows a checkmark icon and "Allowed" text in green. No buttons remain active. DB state persists across app restart.
**Why human:** State transition animation and UI responsiveness require live interaction.

#### 3. High-priority notification fires when app is backgrounded

**Test:** Background the app, trigger a new permission request from Mac.
**Expected:** A heads-up notification appears with sound and vibration. Title: "Permission: @{kuerzel}". Content: tool name and command/path. Tapping it opens the app directly to that session's chat.
**Why human:** Notification heads-up display, audio, and vibration require a device with notification permission granted.

#### 4. Completion notification fires at normal priority

**Test:** Let a Claude Code session complete its task with the app backgrounded.
**Expected:** A notification appears without sound/vibration. Title: "Complete: @{kuerzel}". Content: last message text (up to 80 chars).
**Why human:** Same as above.

#### 5. Deep-link from notification opens correct session

**Test:** With the app open on a different session, tap a notification for a different session.
**Expected:** App navigates directly to the notified session's chat screen, not the session list.
**Why human:** Navigation stack and singleTop intent routing require a running app to verify.

### Gaps Summary

No code-level gaps found. All 7 requirements are fully implemented and wired end-to-end:

- `PermissionCard.kt` and `QuestionCard.kt` are substantive composables (169 and 109 lines), not stubs
- `ChatRepository.answerCallback` is implemented with DB persistence + Telegram callback send
- `NotificationHelper` creates two properly configured channels and emits typed notifications
- `PollingService` triggers notifications for PERMISSION and COMPLETION after DB insert
- `MainActivity` handles deep-links in `onCreate` and `onNewIntent` via `LaunchedEffect`
- `RelayNavGraph` declares the `navDeepLink` on the chat route
- `AndroidManifest.xml` has `singleTop`, `POST_NOTIFICATIONS` permission, and the deep-link intent filter
- `NotificationHelper` is registered in Koin `androidModule`

The phase status is `human_needed` because notifications, animation, and deep-link navigation from taps require device runtime verification. All automated checks pass.

---

_Verified: 2026-04-02T21:30:00Z_
_Verifier: Claude (gsd-verifier)_
