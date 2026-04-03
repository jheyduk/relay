# Phase 7: Server Migration - Context

**Gathered:** 2026-04-03
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped)

<domain>
## Phase Boundary

relay-server is a standalone Node.js component in the relay repo that dispatches directly to Zellij -- no dependency on zellij-claude for anything except Claude Code itself.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — pure infrastructure phase. Use ROADMAP phase goal, success criteria, and codebase conventions to guide decisions.

Key context from v1.0 testing:
- relay-server.cjs currently lives in /Users/jheyduk/prj/zellij-claude/hooks/relay-server.cjs
- Hooks (session-start, session-stop, permission-notify, ask-notify) also in zellij-claude/hooks/
- Server currently dispatches via `npx zellij-claude send` — needs to change to `zellij action write-chars`
- telegram-helper.cjs stays in zellij-claude (Telegram is their concern)
- Hook IPC protocol unchanged (Unix socket at /tmp/zellij-claude-relay.sock)

</decisions>

<code_context>
## Existing Code Insights

Codebase context will be gathered during plan-phase research.

</code_context>

<specifics>
## Specific Ideas

No specific requirements — infrastructure phase. Refer to ROADMAP phase description and success criteria.

</specifics>

<deferred>
## Deferred Ideas

None — discuss phase skipped.

</deferred>
