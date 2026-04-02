# Phase 2: Session Discovery & Status - Research

**Researched:** 2026-04-02
**Domain:** Android Compose UI — session list, drawer navigation, Material 3 theming, command input
**Confidence:** HIGH

## Summary

Phase 2 transforms the basic status screen from Phase 1 into a full session-aware UI. The core work is: (1) a new `RelayTheme` composable with Material 3 Dynamic Color and system dark mode, (2) a `ModalNavigationDrawer` with session list for navigation, (3) a session list screen with `Card`-based items showing status `Chip`s, (4) `PullToRefreshBox` and FAB for `/ls` refresh, (5) a persistent bottom command input with auto `@kuerzel` prefix, and (6) inline expandable `/last` output within session cards.

The existing codebase provides all transport primitives: `TelegramRepository.sendRawCommand()` for `/ls`, `/last`, `/open`, `/goto`, `/rename`; `getRecentMessages()` and `getMessagesForSession()` for data; `SessionStatus` enum already maps to Working/Waiting/Ready/Shell. The SQLDelight schema already has `getLastMessageForSession` query. The main work is UI construction and a new domain layer for session list parsing.

**Primary recommendation:** Build a `SessionRepository` in the shared module that parses `/ls` responses into a `List<Session>` domain model, then build the Compose UI layer in androidApp consuming it via a new `SessionListViewModel`.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Vertical list with Material 3 Cards — each session as a Card showing kuerzel, status badge, last activity
- Status visualization via color-coded Chips: Working=Blue (animated), Waiting=Orange, Ready=Green, Shell=Gray
- Pull-to-refresh AND a FAB with refresh icon as fallback for session list refresh
- Drawer navigation (hamburger menu) with session list — scales better with many sessions than tabs
- Text input field at bottom bar (persistent, like a chat input) for slash commands
- App auto-prefixes `@kuerzel` when a session is selected — user only types the command/text
- `/last` output displayed as inline expandable section within the session card
- Follow system dark mode setting via Material 3 `isSystemInDarkTheme()` — no manual toggle
- Material 3 Dynamic Color (Android 12+) — derive colors from wallpaper, fallback to custom theme

### Claude's Discretion
- Exact Card layout and spacing
- Animation details for "working" status
- Drawer menu implementation details
- Error state presentation

### Deferred Ideas (OUT OF SCOPE)
- Multi-session dashboard with grid/card view (v2 — SESS-06)
- Session-aware auto-routing without manual selection (v2 — SESS-07)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SESS-01 | User can view list of all active zellij-claude sessions with status | Session list parsing from `/ls` response, Card-based UI with LazyColumn |
| SESS-02 | User can see session status with visual indicator | Color-coded Chips (Working=Blue, Waiting=Orange, Ready=Green, Shell=Gray), animated indicator for Working |
| SESS-03 | User can execute `/ls` to refresh session list | PullToRefreshBox + FAB, sendRawCommand("/ls"), parse response |
| SESS-04 | User can execute `/last @kuerzel` to see last response | Bottom command input with auto-prefix, inline expandable in Card |
| SESS-05 | User can execute `/open`, `/goto`, `/rename` session commands | Bottom command input, sendCommand(kuerzel, command) |
| UI-01 | App supports dark mode via Material 3 dynamic theming | RelayTheme composable with dynamicDarkColorScheme/dynamicLightColorScheme + fallback |
</phase_requirements>

## Standard Stack

### Core (already in project)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Compose Material 3 | via BOM 2026.03.00 | UI components (Cards, Chips, Drawer, FAB, PullToRefreshBox) | All needed components are in M3 |
| Compose Material Icons Extended | via BOM | Icons for menu, refresh, send | Already added in Phase 1 |
| Navigation Compose | 2.9.7 | Screen routing | Already in version catalog |
| Koin Compose | 4.0.4 | ViewModel injection via koinViewModel() | Already established pattern |
| SQLDelight | 2.0.2 | Session data persistence | Already has message queries |
| Ktor | 3.4.2 | Telegram Bot API communication | Already wired |

### No New Dependencies Required

All UI components needed (`ModalNavigationDrawer`, `PullToRefreshBox`, `Card`, `AssistChip`/`SuggestionChip`, `FloatingActionButton`, `BottomAppBar`) are part of `androidx.compose.material3` which is already included via the Compose BOM. No new library additions are needed for Phase 2.

**Verification:** `PullToRefreshBox` was added to Material 3 Compose in stable release. It lives in `androidx.compose.material3.pulltorefresh` package.

## Architecture Patterns

### Recommended Project Structure (new files for Phase 2)
```
shared/src/commonMain/kotlin/dev/heyduk/relay/
├── domain/model/
│   └── Session.kt                    # NEW: Session domain model (kuerzel, status, lastActivity)
├── domain/parser/
│   └── SessionListParser.kt          # NEW: Parse /ls response text into List<Session>
└── data/repository/
    └── SessionRepository.kt          # NEW: Interface for session state management

androidApp/src/main/java/dev/heyduk/relay/
├── presentation/
│   ├── theme/
│   │   ├── RelayTheme.kt             # NEW: M3 Dynamic Color + dark mode theme
│   │   └── Color.kt                  # NEW: Fallback color definitions
│   ├── session/
│   │   ├── SessionListScreen.kt      # NEW: Main session list with PullToRefresh
│   │   ├── SessionListViewModel.kt   # NEW: Session list state management
│   │   ├── SessionCard.kt            # NEW: Individual session card composable
│   │   └── SessionStatusChip.kt      # NEW: Color-coded status chip composable
│   ├── components/
│   │   └── CommandInput.kt           # NEW: Bottom bar command input with auto-prefix
│   └── navigation/
│       └── RelayNavGraph.kt          # MODIFY: Add drawer wrapping, session routes
├── MainActivity.kt                    # MODIFY: Wrap in RelayTheme
└── di/
    └── AndroidModule.kt              # MODIFY: Register new ViewModels
```

### Pattern 1: Session Domain Model
**What:** A `Session` data class representing a parsed session from `/ls` output
**When to use:** Anywhere session list data is needed
**Example:**
```kotlin
// shared/src/commonMain/kotlin/.../domain/model/Session.kt
data class Session(
    val kuerzel: String,
    val status: SessionStatus,
    val lastActivity: String? = null,  // from /ls output e.g. "(active)"
    val isActive: Boolean = false
)
```

### Pattern 2: /ls Response Parsing
**What:** Parse the text response from `/ls` command into structured session list
**When to use:** After receiving a message that is a response to `/ls`
**Example:**
```kotlin
// The /ls command response from zellij-claude returns lines like:
// @abc  working  (active)
// @xyz  ready
// Parse each line with regex: @(\S+)\s+(\w+)\s*(?:\((\w+)\))?
object SessionListParser {
    private val linePattern = Regex("""@(\S+)\s+(\w+)\s*(?:\((\w+)\))?""")

    fun parse(responseText: String): List<Session> {
        return responseText.lines()
            .mapNotNull { line ->
                linePattern.find(line.trim())?.let { match ->
                    val (kuerzel, statusStr, extra) = match.destructured
                    Session(
                        kuerzel = kuerzel,
                        status = SessionStatus.valueOf(statusStr.uppercase()),
                        lastActivity = extra.ifBlank { null },
                        isActive = extra == "active"
                    )
                }
            }
    }
}
```

### Pattern 3: Material 3 Dynamic Color Theme
**What:** App-wide theme with Dynamic Color on Android 12+ and dark mode support
**When to use:** Root of the app composition
**Example:**
```kotlin
// Source: https://developer.android.com/develop/ui/compose/designsystems/material3
@Composable
fun RelayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme ->
            dynamicDarkColorScheme(LocalContext.current)
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme ->
            dynamicLightColorScheme(LocalContext.current)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
```

### Pattern 4: ModalNavigationDrawer with Session List
**What:** Drawer containing session list items, controlled via hamburger menu
**Example:**
```kotlin
// Source: https://developer.android.com/develop/ui/compose/components/drawer
val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
val scope = rememberCoroutineScope()

ModalNavigationDrawer(
    drawerContent = {
        ModalDrawerSheet {
            Text("Sessions", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
            HorizontalDivider()
            sessions.forEach { session ->
                NavigationDrawerItem(
                    label = { Text("@${session.kuerzel}") },
                    selected = session.kuerzel == selectedKuerzel,
                    badge = { SessionStatusChip(session.status) },
                    onClick = {
                        onSessionSelected(session.kuerzel)
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    },
    drawerState = drawerState
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Relay") },
                navigationIcon = {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, contentDescription = "Menu")
                    }
                }
            )
        }
    ) { /* content */ }
}
```

### Pattern 5: PullToRefreshBox
**What:** Pull-to-refresh wrapping the session list LazyColumn
**Example:**
```kotlin
// Source: https://developer.android.com/develop/ui/compose/components/pull-to-refresh
PullToRefreshBox(
    isRefreshing = uiState.isRefreshing,
    onRefresh = { viewModel.refreshSessions() }  // sends /ls
) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(uiState.sessions) { session ->
            SessionCard(session = session, ...)
        }
    }
}
```

### Pattern 6: Color-Coded Status Chips
**What:** Composable chips with status-specific colors
**Example:**
```kotlin
@Composable
fun SessionStatusChip(status: SessionStatus) {
    val (label, color) = when (status) {
        SessionStatus.WORKING -> "Working" to Color(0xFF2196F3)  // Blue
        SessionStatus.WAITING -> "Waiting" to Color(0xFFFF9800)  // Orange
        SessionStatus.READY -> "Ready" to Color(0xFF4CAF50)      // Green
        SessionStatus.SHELL -> "Shell" to Color(0xFF9E9E9E)      // Gray
    }
    AssistChip(
        onClick = {},
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = color.copy(alpha = 0.15f),
            labelColor = color
        )
    )
}
```

### Pattern 7: Bottom Command Input with Auto-Prefix
**What:** Persistent text input at the bottom that auto-prefixes `@kuerzel`
**Example:**
```kotlin
@Composable
fun CommandInput(
    selectedKuerzel: String?,
    onSendCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }

    Row(modifier = modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (selectedKuerzel != null) {
            Text(
                text = "@$selectedKuerzel ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Type command...") },
            singleLine = true
        )
        IconButton(onClick = {
            if (text.isNotBlank()) {
                onSendCommand(text)
                text = ""
            }
        }) {
            Icon(Icons.AutoMirrored.Default.Send, contentDescription = "Send")
        }
    }
}
```

### Anti-Patterns to Avoid
- **Parsing /ls in the UI layer:** Parse in shared module so it is testable and reusable
- **Hardcoded colors for status:** Use theme-aware color definitions so they work in both light and dark modes
- **Blocking the main thread for /ls:** Always use coroutines; sendRawCommand is already suspend
- **Mixing Scaffold with ModalNavigationDrawer incorrectly:** ModalNavigationDrawer must be the outermost container, with Scaffold inside its content slot (not the other way around)

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Pull-to-refresh gesture | Custom scroll detection | `PullToRefreshBox` from material3.pulltorefresh | Handles nested scrolling, indicator animation, accessibility |
| Navigation drawer | Custom side panel | `ModalNavigationDrawer` + `ModalDrawerSheet` | Handles gestures, scrim, accessibility, proper Material animation |
| Dynamic theming | Manual color switching | `dynamicDarkColorScheme()` / `dynamicLightColorScheme()` | Automatically derives full palette from wallpaper |
| Status chip styling | Custom colored boxes | `AssistChip` / `SuggestionChip` with `AssistChipDefaults` | Correct elevation, shape, padding per Material spec |
| Animated indicator | Custom infinite animation | `InfiniteTransition` + `animateFloat` | Framework handles lifecycle, pause in background |

## Common Pitfalls

### Pitfall 1: ModalNavigationDrawer must wrap Scaffold
**What goes wrong:** If Scaffold wraps ModalNavigationDrawer, the drawer renders inside the scaffold content area instead of overlaying the entire screen
**Why it happens:** Common misconception about composable nesting order
**How to avoid:** Always use `ModalNavigationDrawer { Scaffold { ... } }` not `Scaffold { ModalNavigationDrawer { ... } }`
**Warning signs:** Drawer appears clipped or doesn't overlay the top bar

### Pitfall 2: PullToRefreshBox needs ExperimentalMaterial3Api
**What goes wrong:** Compilation error without the opt-in annotation
**Why it happens:** PullToRefreshBox may still be marked experimental in some BOM versions
**How to avoid:** Add `@OptIn(ExperimentalMaterial3Api::class)` on composables using PullToRefreshBox
**Warning signs:** Unresolved reference or opt-in requirement error

### Pitfall 3: Dynamic Color fallback on pre-Android 12
**What goes wrong:** App crashes on Android 11 or below if dynamicDarkColorScheme is called
**Why it happens:** Dynamic color APIs are only available on SDK 31+
**How to avoid:** Always guard with `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` check
**Warning signs:** Runtime crash on older devices

### Pitfall 4: /ls response format is not guaranteed
**What goes wrong:** Parser breaks on unexpected format
**Why it happens:** zellij-claude format may vary slightly or include additional text
**How to avoid:** Use lenient regex parsing that skips unparseable lines, log warnings for unmatched lines
**Warning signs:** Empty session list despite known active sessions

### Pitfall 5: DrawerState survives configuration changes but not process death
**What goes wrong:** Drawer state lost on process recreation
**Why it happens:** rememberDrawerState uses remember, not rememberSaveable
**How to avoid:** This is acceptable for a drawer (it should start closed). But selected session should be saved via ViewModel StateFlow (survives config changes) or SavedStateHandle (survives process death)
**Warning signs:** Selected session resets unexpectedly

### Pitfall 6: Auto-prefix collides with raw slash commands
**What goes wrong:** User types `/ls` but it gets sent as `@kuerzel /ls` which is wrong
**Why it happens:** Auto-prefix applied to all input indiscriminately
**How to avoid:** If input starts with `/` (slash command), send raw without prefix. Only prefix plain text messages and commands like `/last` that need a target
**Warning signs:** `/ls` returns nothing, `/open` fails

## Code Examples

### Expandable /last Response in Session Card
```kotlin
@Composable
fun SessionCard(
    session: Session,
    lastResponse: String?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@${session.kuerzel}",
                    style = MaterialTheme.typography.titleMedium
                )
                SessionStatusChip(session.status)
            }
            if (session.lastActivity != null) {
                Text(
                    text = session.lastActivity,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Expandable /last section
            if (lastResponse != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onToggleExpand) {
                    Text(if (isExpanded) "Hide last response" else "Show last response")
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null
                    )
                }
                AnimatedVisibility(visible = isExpanded) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = lastResponse,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}
```

### Working Status Animation
```kotlin
@Composable
fun AnimatedWorkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "working")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Color(0xFF2196F3).copy(alpha = alpha))
    )
}
```

### Slash Command Routing Logic
```kotlin
// In SessionListViewModel
fun handleCommandInput(input: String, selectedKuerzel: String?) {
    viewModelScope.launch {
        when {
            // Global commands - send raw, no prefix
            input.startsWith("/ls") || input.startsWith("/help") ->
                repository.sendRawCommand(input)
            // Session-targeted commands - need @kuerzel prefix
            input.startsWith("/last") || input.startsWith("/open") ||
            input.startsWith("/goto") || input.startsWith("/rename") -> {
                val kuerzel = selectedKuerzel ?: return@launch
                repository.sendRawCommand("$input @$kuerzel")
            }
            // Plain text message - prefix with @kuerzel
            else -> {
                val kuerzel = selectedKuerzel ?: return@launch
                repository.sendCommand(kuerzel, input)
            }
        }
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `PullRefreshState` + `pullRefresh` modifier | `PullToRefreshBox` composable | Material 3 Compose stable 2025 | Simpler API, handles nested scroll automatically |
| `rememberPullRefreshState` | `PullToRefreshBox(isRefreshing, onRefresh)` | Material 3 1.3+ | State management simplified |
| Custom drawer composable | `ModalNavigationDrawer` | Material 3 from inception | Standard component with proper Material animations |
| Manual color scheme switching | `dynamicDarkColorScheme` / `dynamicLightColorScheme` | Android 12 (API 31) | Automatic wallpaper-derived colors |

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit 4.13.2 + kotlinx-coroutines-test 1.10.2 + Turbine 1.2.0 |
| Config file | shared/build.gradle.kts (commonTest dependencies) |
| Quick run command | `./gradlew :shared:allTests --tests "*SessionListParser*" -x lint` |
| Full suite command | `./gradlew :shared:allTests -x lint` |

### Phase Requirements to Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SESS-01 | Session list parsed from /ls response | unit | `./gradlew :shared:allTests --tests "*SessionListParserTest*" -x lint` | Wave 0 |
| SESS-02 | SessionStatus maps to correct color/label | unit | `./gradlew :shared:allTests --tests "*SessionStatusTest*" -x lint` | Wave 0 |
| SESS-03 | /ls command triggers refresh flow | unit | `./gradlew :shared:allTests --tests "*SessionRepositoryTest*" -x lint` | Wave 0 |
| SESS-04 | /last command routed correctly with @kuerzel | unit | `./gradlew :shared:allTests --tests "*CommandRoutingTest*" -x lint` | Wave 0 |
| SESS-05 | /open, /goto, /rename routed correctly | unit | `./gradlew :shared:allTests --tests "*CommandRoutingTest*" -x lint` | Wave 0 |
| UI-01 | Theme composable produces correct color scheme | manual-only | Visual inspection on device | N/A |

### Sampling Rate
- **Per task commit:** `./gradlew :shared:allTests -x lint`
- **Per wave merge:** `./gradlew :shared:allTests -x lint && ./gradlew :androidApp:assembleDebug`
- **Phase gate:** Full suite green + APK assembles + visual inspection

### Wave 0 Gaps
- [ ] `shared/src/commonTest/kotlin/.../domain/parser/SessionListParserTest.kt` -- covers SESS-01
- [ ] `shared/src/commonTest/kotlin/.../domain/CommandRoutingTest.kt` -- covers SESS-04, SESS-05

## Open Questions

1. **Exact /ls response format**
   - What we know: CONTEXT.md says it returns lines like `@kuerzel status (active)`
   - What's unclear: Exact whitespace, separators, edge cases (sessions with no status?)
   - Recommendation: Build lenient parser that skips unparseable lines, add logging for unmatched lines. Test with multiple format variants.

2. **Command format for /last, /open, /goto, /rename**
   - What we know: `/last @kuerzel` retrieves last response; `/open`, `/goto`, `/rename` are session commands
   - What's unclear: Does `/last` use `@kuerzel` prefix or suffix? What arguments does `/rename` take?
   - Recommendation: Implement as raw command passthrough initially. The auto-prefix handles `@kuerzel` insertion. Can be refined after device testing.

3. **Session list refresh frequency**
   - What we know: Manual refresh via pull-to-refresh and FAB
   - What's unclear: Should there be periodic auto-refresh?
   - Recommendation: Start with manual-only (pull-to-refresh + FAB). Auto-refresh is Phase 2 scope creep.

## Sources

### Primary (HIGH confidence)
- [Material Design 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) - Dynamic color, theme setup, color scheme APIs
- [Navigation Drawer](https://developer.android.com/develop/ui/compose/components/drawer) - ModalNavigationDrawer, ModalDrawerSheet, NavigationDrawerItem
- [Pull to Refresh](https://developer.android.com/develop/ui/compose/components/pull-to-refresh) - PullToRefreshBox API and usage pattern

### Secondary (MEDIUM confidence)
- [composables.com PullToRefreshBox](https://composables.com/material3/pulltorefreshbox) - Parameter documentation
- [composables.com ModalNavigationDrawer](https://composables.com/material3/modalnavigationdrawer) - Component API reference

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH - All libraries already in project from Phase 1, no new deps needed
- Architecture: HIGH - Follows established MVVM + StateFlow pattern from Phase 1, standard Compose component usage
- Pitfalls: HIGH - Well-documented Material 3 component behaviors from official docs
- /ls parsing: MEDIUM - Exact response format not formally documented, but regex approach is robust

**Research date:** 2026-04-02
**Valid until:** 2026-05-02 (stable libraries, no fast-moving dependencies)
