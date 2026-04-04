# Requirements: Relay

**Defined:** 2026-04-04
**Core Value:** Remote session control with per-session separation — see all Claude Code sessions at a glance, interact with any of them, and never miss a permission request or completion notification.

## v1.3 Requirements

Requirements for Session Management milestone. Each maps to roadmap phases.

### Session Creation

- [ ] **SESS-01**: User can open a session creation dialog via FAB (+) on SessionListScreen
- [ ] **SESS-02**: User can open a session creation dialog via NavigationDrawer menu entry
- [ ] **SESS-03**: User can fuzzy-search project directories with instant local filtering
- [ ] **SESS-04**: User can select a directory and see a confirmation dialog with editable kuerzel
- [ ] **SESS-05**: User can toggle `--dangerously-skip-permissions` flag before creating
- [ ] **SESS-06**: User can enter a custom path not under configured roots
- [ ] **SESS-07**: User can create a new Claude Code session from the confirmation dialog

### Server Config

- [ ] **CONF-01**: Server reads project roots from `~/.config/relay/project-roots.json`
- [ ] **CONF-02**: Server scans configured roots 2 levels deep and returns directory list via WebSocket
- [ ] **CONF-03**: Server handles `create_session` action (validate, deduplicate kuerzel, create zellij tab)

### Response Handling

- [ ] **RESP-01**: Session-stop hook uses smart size-aware response handling instead of hard truncation

## Future Requirements

### iOS
- **IOS-01**: SwiftUI frontend using KMP shared module

### Session Commands
- **CMD-01**: /goto, /rename, /last session commands via relay-server

## Out of Scope

| Feature | Reason |
|---------|--------|
| LLM-based directory suggestions | Unnecessary complexity — FZF-style search is sufficient |
| Directory caching on server | Scan on request is fast enough for <500 directories |
| iOS session creation | iOS UI not yet built |
| Session deletion from app | Session lifecycle managed by Claude Code |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SESS-01 | — | Pending |
| SESS-02 | — | Pending |
| SESS-03 | — | Pending |
| SESS-04 | — | Pending |
| SESS-05 | — | Pending |
| SESS-06 | — | Pending |
| SESS-07 | — | Pending |
| CONF-01 | — | Pending |
| CONF-02 | — | Pending |
| CONF-03 | — | Pending |
| RESP-01 | — | Pending |

**Coverage:**
- v1.3 requirements: 11 total
- Mapped to phases: 0
- Unmapped: 11

---
*Requirements defined: 2026-04-04*
*Last updated: 2026-04-04 after initial definition*
