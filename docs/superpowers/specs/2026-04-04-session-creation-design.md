# Session Creation from App + Smart Response Handling

**Date:** 2026-04-04
**Status:** Approved

## Overview

Add the ability to create new Claude Code sessions directly from the Relay Android app, with FZF-style fuzzy directory search and a confirmation dialog. Additionally, replace the legacy Telegram truncation logic with smarter response size handling.

## Requirements

### Session Creation
1. FAB (+) button on SessionListScreen + NavigationDrawer menu entry as entry points
2. Fullscreen dialog with FZF-style fuzzy search over project directories
3. Server scans configurable project roots (2 levels deep) and returns full directory list
4. App filters locally for instant fuzzy-match feedback (no per-keystroke roundtrip)
5. Confirmation dialog before session creation with editable kuerzel, path display, and flags toggle
6. Custom path input option for directories not under configured roots (creates directory if needed)
7. Server-side config file for project roots, default flags, and scan depth

### Smart Response Handling
8. Remove hard 2000-char truncation from session-stop.cjs
9. If lastResponse <= 4KB: send complete (both responses, count=2)
10. If lastResponse > 4KB: send only last response (count=1), untruncated
11. If single response > 16KB: truncate with marker

## Architecture

### Entry Points (Android App)

**SessionListScreen:**
- FloatingActionButton (+) next to existing refresh FAB
- NavigationDrawer menu item "New Session"
- Both open the same CreateSessionDialog

### CreateSessionDialog (Fullscreen Compose Dialog)

**Phase 1 — Directory Selection:**
- On open: sends `list_directories` WebSocket action to server
- Shows loading indicator while waiting for response
- Search TextField at top with immediate local fuzzy filtering
- LazyColumn of matching directories showing name + full path
- "Custom Path..." option at bottom of list for manual entry
- Tap on directory → Phase 2

**Phase 2 — Confirmation:**
- Editable TextField for kuerzel (pre-filled from directory basename)
- Read-only path display
- Switch/Toggle for `--dangerously-skip-permissions` (default from server config)
- "Create" and "Cancel" buttons
- On "Create": sends `create_session` WebSocket action
- Shows progress indicator, then navigates to new session on success
- Shows error snackbar on failure

### Fuzzy Matching (App-side)

Simple scoring algorithm matching input characters against directory name:
- Consecutive character matches score higher
- Start-of-word matches score higher
- Case-insensitive
- Sort by score descending

No external library needed — straightforward Kotlin implementation sufficient for <500 directories.

### Server Config

**File:** `~/.config/relay/project-roots.json`

```json
{
  "roots": ["~/prj", "~/projects"],
  "defaultFlags": "--dangerously-skip-permissions",
  "scanDepth": 2
}
```

- `roots`: Array of absolute or `~`-prefixed paths to scan
- `defaultFlags`: Default Claude CLI flags for new sessions
- `scanDepth`: How many levels deep to scan (default: 2)
- File is read on each `list_directories` request (no caching)

**Fallback:** If file doesn't exist, use `["~/prj"]` as default root with `--dangerously-skip-permissions`.

### WebSocket Protocol Extensions

#### `list_directories` (App -> Server)

Request:
```json
{ "action": "list_directories" }
```

Response:
```json
{
  "type": "directory_list",
  "directories": [
    { "path": "/Users/jheyduk/prj/relay", "name": "relay" },
    { "path": "/Users/jheyduk/prj/fischer/fischer-operations", "name": "fischer-operations" }
  ],
  "defaultFlags": "--dangerously-skip-permissions"
}
```

Server scans each root up to `scanDepth` levels, filters to directories only (skips hidden dirs, node_modules, .git), sorts alphabetically by name.

#### `create_session` (App -> Server)

Request:
```json
{
  "action": "create_session",
  "path": "/Users/jheyduk/prj/relay",
  "kuerzel": "relay",
  "flags": "--dangerously-skip-permissions"
}
```

Success response:
```json
{
  "type": "session_created",
  "kuerzel": "relay",
  "path": "/Users/jheyduk/prj/relay",
  "success": true
}
```

Error response:
```json
{
  "type": "session_created",
  "success": false,
  "error": "Directory does not exist"
}
```

### Server Logic (relay-server.cjs)

#### Directory Listing Handler

1. Read `~/.config/relay/project-roots.json` (or use defaults)
2. Expand `~` to home directory
3. For each root, recursively list directories up to `scanDepth` levels
4. Skip: hidden directories (`.`-prefix), `node_modules`, `.git`, `__pycache__`
5. Return sorted list with `{ path, name }` objects
6. Include `defaultFlags` from config

#### Session Creation Handler

1. Validate path exists and is a directory
2. If path doesn't exist and was explicitly requested (custom path): create it with `mkdirSync(path, { recursive: true })`
3. Derive kuerzel from provided value or path basename
4. Deduplicate kuerzel: query existing `@`-tabs via `zellij action list-tabs`, if `@{kuerzel}` exists, try `kuerzel-2`, `kuerzel-3`, etc.
5. Execute: `zellij --session ${ZELLIJ_SESSION_NAME} action new-tab --name @${kuerzel} --cwd ${path} -- claude ${flags}`
6. Send success/error response to app
7. The existing session-start hook will fire automatically, registering the tab file and sending status updates

### Smart Response Handling (session-stop.cjs)

**Current behavior:**
```javascript
truncate(lastResponse, 2000)  // Hard limit from Telegram era
```

**New behavior:**
```javascript
function getSmartResponse(kuerzel) {
  const full = getLastResponse(kuerzel, 2);  // Try last 2 responses
  if (!full) return 'Task complete';

  const bytes = Buffer.byteLength(full, 'utf8');

  if (bytes <= 4096) {
    return full;  // Both responses fit, send complete
  }

  // Too large with 2 responses, try just the last one
  const single = getLastResponse(kuerzel, 1);
  if (!single) return 'Task complete';

  const singleBytes = Buffer.byteLength(single, 'utf8');

  if (singleBytes <= 16384) {
    return single;  // Single response fits
  }

  // Even single response is huge, truncate
  return single.slice(0, 16384) + '\n...(truncated)';
}
```

Remove the old `truncate()` function entirely.

## Data Flow

```
User taps (+) FAB
  -> CreateSessionDialog opens
  -> App sends { action: "list_directories" } via WebSocket
  -> Server reads project-roots.json, scans directories
  -> Server sends { type: "directory_list", directories: [...] }
  -> App displays list, user types fuzzy search
  -> App filters locally, user selects directory
  -> Confirmation dialog: kuerzel (editable), path, flags toggle
  -> User taps "Create"
  -> App sends { action: "create_session", path, kuerzel, flags }
  -> Server validates, deduplicates kuerzel, runs zellij new-tab
  -> Server sends { type: "session_created", success: true }
  -> App navigates to new session (or shows error)
  -> session-start hook fires automatically -> status updates flow
```

## Files to Create/Modify

### New Files
- `~/.config/relay/project-roots.json` — server config (created manually or with defaults)
- `androidApp/.../presentation/session/CreateSessionDialog.kt` — fullscreen dialog composable
- `androidApp/.../presentation/session/CreateSessionViewModel.kt` — or extend SessionListViewModel
- `shared/.../domain/model/DirectoryEntry.kt` — data class for directory list items
- `shared/.../util/FuzzyMatch.kt` — fuzzy matching algorithm

### Modified Files
- `server/relay-server.cjs` — add `list_directories` and `create_session` handlers, config reading
- `server/hooks/session-stop.cjs` — replace truncate() with smart response sizing
- `shared/.../data/remote/WebSocketClient.kt` — add sendListDirectories(), sendCreateSession()
- `shared/.../data/remote/RelayMessageParser.kt` — parse directory_list and session_created messages
- `shared/.../domain/model/RelayUpdate.kt` — add DirectoryList and SessionCreated update types
- `androidApp/.../presentation/session/SessionListScreen.kt` — add FAB, drawer entry
- `androidApp/.../presentation/session/SessionListViewModel.kt` — add create session actions

## Edge Cases

- **No project-roots.json:** Use `["~/prj"]` as default, log warning
- **Root doesn't exist:** Skip silently, log warning
- **Empty directory list:** Show message "No directories found. Configure project roots in ~/.config/relay/project-roots.json"
- **Kuerzel collision:** Auto-deduplicate with `-2`, `-3` suffix, show final name in confirmation
- **Custom path creation fails:** Return error with message (permissions, invalid path)
- **Zellij session not found:** Return error — relay-server must be running inside a zellij session
- **WebSocket disconnected during creation:** App shows error, user can retry
