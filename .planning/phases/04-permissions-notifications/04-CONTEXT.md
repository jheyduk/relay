# Phase 4: Permissions & Notifications - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning

<domain>
## Phase Boundary

Add native Allow/Deny permission handling, AskUserQuestion option buttons, and push notifications with priority channels. Users should never miss a permission request or session completion — these are the time-critical interactions that make Relay essential.

</domain>

<decisions>
## Implementation Decisions

### Permission UI
- Full-width Permission Cards in chat — tool details (command, file path) displayed prominently
- Two prominent buttons: Allow (green, filled) and Deny (red, outlined)
- Callback uses existing format: `callback:allow:{kürzel}` / `callback:deny:{kürzel}` sent to command bot
- After tap: button immediately disabled + spinner, then confirmation icon (✅/❌)

### AskUserQuestion UI
- Question displayed as a Card with question text
- Options as tappable Chips/Buttons below the question
- Support multiselect where the question requires it
- Selected option sent as text response to the session

### Notifications
- Two notification channels: "Permissions" (IMPORTANCE_HIGH, sound+vibration) and "Updates" (IMPORTANCE_DEFAULT, badge only)
- Permission notifications: "🔐 @infra: Bash: git push" format
- Completion notifications: "✅ @hub: Task complete" format
- Tapping notification deep-links to chat/{kürzel} route

### Claude's Discretion
- Exact notification channel IDs and display names
- Permission card animation details
- Notification icon design
- How to detect permission vs completion messages from the incoming stream

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RelayMessageType` enum — already has PERMISSION, QUESTION, COMPLETION types
- `RelayUpdate` — has toolDetails field for permission messages
- `ChatScreen.kt` — permission cards render as special message types in the chat
- `TelegramRepository.sendCommand()` — for sending callback responses
- `PollingService.kt` — foreground service, ideal place to trigger notifications

### Established Patterns
- Message-type-specific rendering in chat via when(message.type) pattern
- Koin DI for all components
- StateFlow for reactive UI updates

### Integration Points
- `PollingService` — emit notifications when permission/completion messages arrive
- `ChatScreen` — render PermissionCard and QuestionCard composables for special message types
- `AndroidManifest.xml` — POST_NOTIFICATIONS permission
- `RelayNavGraph` — deep link handling for notification taps

</code_context>

<specifics>
## Specific Ideas

- Permission cards should show: session kürzel, tool name, tool details (truncated), Allow/Deny buttons
- After Allow/Deny, the card should update to show the decision (not just disable buttons)
- Notifications should be grouped per session using notification group feature
- Consider using NotificationCompat.BigTextStyle for long tool details

</specifics>

<deferred>
## Deferred Ideas

- FCM push for real-time when app backgrounded (v2 — NOTF-05)
- Configurable notification sounds per session (v2 — UI-02)

</deferred>
