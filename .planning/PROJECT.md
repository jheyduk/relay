# Relay

## What This Is

A native Android companion app for Claude Code sessions that replaces the current Telegram-based workflow. Relay provides a session-aware UI where each zellij-claude session gets its own visual space, with text and voice input unified in a single conversation stream. It communicates directly via the Telegram Bot API — no custom backend required.

## Core Value

Remote session control with per-session separation — see all Claude Code sessions at a glance, interact with any of them, and never miss a permission request or completion notification.

## Requirements

### Validated

(None yet — ship to validate)

### Active

- [ ] Session-aware UI with visual separation per zellij-claude session
- [ ] Session discovery via `/ls` command through Telegram Bot API
- [ ] Session status display (working, waiting, ready, shell)
- [ ] Text messaging to specific sessions (`@kürzel message` format)
- [ ] Native Allow/Deny permission UI (replaces Telegram inline keyboard callbacks)
- [ ] Push notifications for permission requests, completions, and questions
- [ ] Voice input with on-device Whisper transcription
- [ ] TTS playback for Claude responses
- [ ] Unified chat stream — text input shows as text, voice input shows transcript + audio
- [ ] Session commands: `/ls`, `/last`, `/open`, `/goto`, `/rename`

### Out of Scope

- Custom backend server — uses Telegram Bot API directly as transport
- iOS version — Android only for v1
- Web version — native mobile focus
- Multi-user support — single user (the developer) only
- UI navigation pattern (tabs vs drawer) — deferred to design phase

## Context

- **Existing system**: zellij-claude provides CLI + MCP + hooks for managing multiple Claude Code sessions in Zellij terminal multiplexer. Telegram integration already handles message routing, permission callbacks, voice transcription, and session notifications.
- **Transport layer**: The Telegram Bot API is the transport. The app sends the same commands the current Telegram chat does (`/ls`, `@kürzel message`, `callback:allow:kürzel`). Messages from Claude Code sessions arrive as Telegram bot messages with `@kürzel` prefix formatting.
- **Session identification**: Sessions are identified by "kürzel" — short names derived from directory basenames (e.g., `@infra`, `@hub`). Status is derived from pane title patterns (spinner = working, permission keywords = waiting, etc.).
- **Permission flow**: Non-main sessions send Allow/Deny buttons via Telegram. Currently processed by @main session via patched callback forwarding. The app would handle these natively.
- **Voice pipeline**: Currently uses server-side Whisper transcription via `transcribe.sh`. Relay will use on-device Whisper for independence from the Mac.
- **Secret sanitization**: zellij-claude already sanitizes credentials before sending to Telegram — this protection carries over.

## Constraints

- **Transport**: Telegram Bot API only — no custom server, no WebSocket, no direct connection to the Mac
- **Platform**: Android (Kotlin/Jetpack Compose) — native, no cross-platform framework
- **Voice**: On-device Whisper model for transcription — must work offline
- **Protocol**: Must speak the existing zellij-claude message format — the Mac-side should need zero changes
- **Single user**: App is for the developer only, no auth/account system needed

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Telegram Bot API as transport | Reuses existing infrastructure, zero server ops | — Pending |
| Kotlin/Jetpack Compose | Native Android performance, best platform API access | — Pending |
| On-device Whisper | No server dependency for voice, works anywhere | — Pending |
| Power Mode from day one | Zellij multi-session is the primary use case, Single Mode is a subset | — Pending |
| UI navigation pattern TBD | Tabs vs drawer vs other — needs design exploration | — Pending |
| Long polling for updates | Simplest approach, FCM can be added later if latency is an issue | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd:transition`):
1. Requirements invalidated? -> Move to Out of Scope with reason
2. Requirements validated? -> Move to Validated with phase reference
3. New requirements emerged? -> Add to Active
4. Decisions to log? -> Add to Key Decisions
5. "What This Is" still accurate? -> Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-04-02 after initialization*
