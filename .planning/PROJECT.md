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
- ✓ Theme settings (System/Light/Dark) — v1.2
- ✓ File attachment sharing (screenshots, files) with staged send UI — v1.2
- ✓ Chat auto-scroll to latest messages — v1.2
- ✓ STATUS messages filtered from chat view — v1.2
- ✓ Session-stop hook includes last 2 responses — v1.2
- ✓ Redesigned message bubbles with elevation and better spacing — v1.2
- ✓ App icon (terminal + phone + signal waves) + notification icon — v1.2
- ✓ Live session status in chat (working/waiting/ready) with animated progress bar — v1.2
- ✓ Adaptive status polling (3s active, 30s idle) with optimistic working state — v1.2
- ✓ Minimal notification (only warns after 30s disconnect) — v1.2
- ✓ Notification tap opens app — v1.2
- ✓ README with architecture diagram — v1.2

### Active

- [ ] Create new sessions from the app with FZF-style fuzzy directory search
- [ ] Server-side config for project roots, default flags, scan depth
- [ ] Confirmation dialog with editable kuerzel, path, and flags toggle
- [ ] Custom path input for directories not under configured roots
- [ ] Smart response handling (replace legacy Telegram truncation)

## Current Milestone: v1.3 Session Management

**Goal:** Create new Claude Code sessions from the app and improve response handling

**Target features:**
- FZF-style fuzzy directory search for session creation
- Confirmation dialog with editable kuerzel and flags toggle
- Server-side config (`~/.config/relay/project-roots.json`)
- Smart response handling (size-aware, no hard truncation)

### Out of Scope

- Web version — native mobile focus
- Multi-user support — single developer tool
- Cloud-based voice — Mac-local whisper.cpp only
- FCM push — WebSocket is the transport
- mDNS auto-discovery — removed, IP-based connection instead

## Context

- **Shipped v1.0** — 6 phases, 18 plans, 96 commits in 2 days
- **Shipped v1.1** — 3 phases (server migration, interactive controls, mac-side voice)
- **Shipped v1.2** — Post-release polish: theme, attachments, status, bubbles, icon, adaptive polling, favorites
- **Architecture**: KMP shared module (Ktor WebSocket, SQLDelight, Koin DI) + Android Compose UI + Node.js relay-server
- **Transport**: Direct WebSocket to relay-server in `server/`
- **Voice**: whisper.cpp medium model on Mac (~5s transcription, German default)
- **Single repo**: App + Server both in relay repo
- **GitHub**: https://github.com/jheyduk/relay

## Constraints

- **Transport**: Direct WebSocket to relay-server on Mac
- **Platform**: Kotlin Multiplatform (KMP) — shared business logic, Compose for Android UI
- **Voice**: Mac-side whisper.cpp transcription only
- **Protocol**: JSON message format (`{type, session, message, status, tool_details, timestamp}`)
- **Single user**: App is for the developer only, shared secret auth

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Direct WebSocket transport | Telegram fundamentally broken for relay | ✓ Working |
| Kotlin Multiplatform (KMP) | Shared business logic across platforms | ✓ Good |
| Koin over Hilt for DI | Hilt is Android-only, Koin supports KMP | ✓ Good |
| SQLDelight over Room | Room not KMP-compatible | ✓ Good |
| Mac-side Whisper | Fast transcription, medium model | ✓ Good — ~5s, German support |
| relay-server in relay repo | Independent from zellij-claude | ✓ Good |
| mDNS discovery | Zero-config local network | ✗ Removed — unreliable |
| zellij write-chars + write 13 | Direct pane input for commands | ✓ Working |
| Adaptive status polling | 3s when active, 30s when idle | ✓ Good — low overhead, fast feedback |
| Staged attachments | Pick file → preview → send with message | ✓ Good UX |

## Evolution

This document evolves at phase transitions and milestone boundaries.

---
*Last updated: 2026-04-04 after v1.3 milestone start*
