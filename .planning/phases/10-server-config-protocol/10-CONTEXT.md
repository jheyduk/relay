# Phase 10: Server Config & Protocol - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped)

<domain>
## Phase Boundary

Server can read project root configuration from `~/.config/relay/project-roots.json`, scan directories 2 levels deep, and create new Claude Code sessions on request from the app via WebSocket protocol extensions (`list_directories`, `create_session`).

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — pure infrastructure phase. Use ROADMAP phase goal, success criteria, design spec (`docs/superpowers/specs/2026-04-04-session-creation-design.md`), and codebase conventions to guide decisions.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `relay-server.cjs` — existing WebSocket message handler pattern (line ~656-695 for action dispatch)
- `session-start.cjs` — tab file pattern (`/tmp/zellij-claude-tab-*`) for kuerzel deduplication
- `zellij action new-tab` — existing tab creation pattern used in zellij-claude

### Established Patterns
- WebSocket actions: `{ action: "command" | "raw_command" | "answer" | "attachment", ... }`
- Server responses: `{ type: "session_list" | "status" | "completion", ... }`
- Config files: JSON with sensible defaults on missing file

### Integration Points
- WebSocket message handler in relay-server.cjs (add new action cases)
- Existing `listSessions()` function for session list pattern
- `findSessionForKuerzel()` for kuerzel lookup pattern

</code_context>

<specifics>
## Specific Ideas

- Config file at `~/.config/relay/project-roots.json` with roots array, defaultFlags, scanDepth
- Skip hidden dirs, node_modules, .git, __pycache__ during scan
- Deduplicate kuerzel by appending -2, -3, etc. (matches zellij-claude pattern)
- Default roots: `["~/prj"]` if config file missing

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
