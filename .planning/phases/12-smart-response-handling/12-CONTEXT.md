# Phase 12: Smart Response Handling - Context

**Gathered:** 2026-04-04
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — discuss skipped)

<domain>
## Phase Boundary

Replace the legacy `truncate(2000)` in `session-stop.cjs` with tiered size-aware response logic. Three tiers: ≤4KB both responses, ≤16KB single response, >16KB truncated with marker.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
All implementation choices are at Claude's discretion — pure infrastructure phase. The exact logic is specified in the approved design spec at `docs/superpowers/specs/2026-04-04-session-creation-design.md` (Smart Response Handling section).

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `server/hooks/session-stop.cjs` — existing `getLastResponse()` and `truncate()` functions
- `getSmartResponse()` algorithm specified in design spec

### Established Patterns
- `getLastResponse(kuerzel, count)` calls `npx zellij-claude last @kuerzel count`
- `Buffer.byteLength(text, 'utf8')` for size measurement

### Integration Points
- `notify()` function in session-stop.cjs calls truncate() — replace with getSmartResponse()

</code_context>

<specifics>
## Specific Ideas

- Tiered logic: 4KB threshold for both responses, 16KB for single, truncate beyond
- Remove old `truncate()` function entirely
- Use `Buffer.byteLength()` not `text.length` for accurate byte measurement

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
