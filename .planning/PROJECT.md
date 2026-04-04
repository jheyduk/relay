# Relay

## What This Is

A mobile companion app for Claude Code sessions. Relay provides a session-aware UI where each zellij-claude session gets its own visual space, with text and voice input in a single conversation stream. Built with Kotlin Multiplatform (KMP) for Android. Communicates directly via WebSocket to a lightweight relay-server on the Mac — no cloud backend required.

## Core Value

Remote session control with per-session separation — see all Claude Code sessions at a glance, interact with any of them, and never miss a permission request or completion notification.

## Requirements

### Validated

- ✓ Direct WebSocket transport with relay-server, shared secret auth — v1.0
- ✓ Session-aware UI with Material 3 Dynamic Color, dark mode, session status chips — v1.0
- ✓ Per-session text messaging with persistent history (SQLDelight) — v1.0
- ✓ Native Allow/Deny permission UI with callback routing — v1.0
- ✓ Two-channel push notifications (permission HIGH, completion DEFAULT) with deep links — v1.0
- ✓ Bidirectional real-time messaging Mac ↔ App via WebSocket — v1.0
- ✓ Standalone relay-server in `server/` with direct Zellij dispatch — v1.1
- ✓ AskUserQuestion keystroke mapping (single, multi, free text, multi-question auto-submit) — v1.1
- ✓ Session sync on reconnect — v1.1
- ✓ Mac-side Whisper transcription (medium model, German + English) — v1.1
- ✓ Theme settings (System/Light/Dark) — post-v1.1
- ✓ File attachment sharing (screenshots, files) — post-v1.1
- ✓ Chat auto-scroll to latest messages — post-v1.1
- ✓ STATUS messages filtered from chat view — post-v1.1
- ✓ Session-stop hook includes last 2 responses — post-v1.1

### Active

- [ ] iOS UI (SwiftUI frontend using KMP shared module)
- [ ] Session commands via relay-server (/open, /goto, /rename, /last)
- [ ] App icon for notification (custom drawable instead of system default)

### Out of Scope

- Web version — native mobile focus
- Multi-user support — single developer tool
- Cloud-based voice — Mac-local whisper.cpp only
- FCM push — WebSocket is the transport
- mDNS auto-discovery — removed, IP-based connection instead

## Context

- **Shipped v1.0** — 6 phases, 18 plans, 96 commits in 2 days
- **Shipped v1.1** — 3 phases (server migration, interactive controls, mac-side voice)
- **Post-v1.1 fixes** — theme settings, file attachments, auto-scroll, status filtering, message bubbles redesign
- **Architecture**: KMP shared module (Ktor WebSocket, SQLDelight, Koin DI) + Android Compose UI + Node.js relay-server
- **Transport**: Direct WebSocket to relay-server in `server/`
- **Voice**: whisper.cpp medium model on Mac (~5s transcription, German default)
- **Single repo**: App + Server both in relay repo, published on GitHub (public)
- **GitHub**: https://github.com/jheyduk/relay

## Constraints

- **Transport**: Direct WebSocket to relay-server on Mac
- **Platform**: Kotlin Multiplatform (KMP) — shared business logic, Compose for Android UI
- **Voice**: Mac-side whisper.cpp transcription only (on-device Whisper removed in v1.1)
- **Protocol**: JSON message format (`{type, session, message, status, tool_details, timestamp}`)
- **Single user**: App is for the developer only, shared secret auth

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Telegram Bot API as transport | Reuses existing infrastructure | ⚠️ Replaced in v1.0 Phase 6 |
| Direct WebSocket transport | Telegram fundamentally broken for relay | ✓ Working |
| Kotlin Multiplatform (KMP) | Shared business logic across platforms | ✓ Good |
| Koin over Hilt for DI | Hilt is Android-only, Koin supports KMP | ✓ Good |
| SQLDelight over Room | Room not KMP-compatible | ✓ Good |
| On-device Whisper | No server dependency for voice | ⚠️ Replaced in v1.1 — too slow on device |
| Mac-side Whisper | Fast transcription, medium model | ✓ Good — ~5s, German support |
| relay-server in relay repo | Independent from zellij-claude | ✓ Good — moved in v1.1 |
| mDNS discovery | Zero-config local network | ✗ Removed — unreliable, IP-based instead |
| zellij write-chars + write 13 | Direct pane input for commands | ✓ Working — never use \n in write-chars |

## Evolution

This document evolves at phase transitions and milestone boundaries.

---
*Last updated: 2026-04-04 after v1.1 post-release fixes*
