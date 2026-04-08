# Phase 16: Last-Response Dedup - Context

**Gathered:** 2026-04-08
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase)

<domain>
## Phase Boundary

Server-side checksum dedup for `/last` responses. When user requests `/last` and the content is unchanged since the last request for that session, respond with "No updates" instead of resending duplicate content.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — straightforward infrastructure phase. Use ROADMAP phase goal, success criteria, and codebase conventions to guide decisions.

Key guidance:
- Use crypto.createHash('md5') or similar for checksum — fast, collision risk irrelevant for this use case
- Store checksums in a per-session Map (in-memory, not persisted)
- "No updates" should be sent as a `last_response` type with `message: "No updates"` and a flag like `no_change: true`
- Clear checksum on session removal or server restart (natural Map behavior)

</decisions>

<code_context>
## Existing Code Insights

The `get_last` action handler in `relay-server.cjs` calls `getLastResponses(kuerzel, count)` which returns a string. The response is sent as `{type: 'last_response', session, message, success: true}`.

</code_context>

<specifics>
## Specific Ideas

No specific requirements — infrastructure phase. Refer to ROADMAP phase description and success criteria.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
