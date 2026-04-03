# Relay

## What This Is

A mobile companion app for Claude Code sessions. Relay provides a session-aware UI where each zellij-claude session gets its own visual space, with text and voice input in a single conversation stream. Built with Kotlin Multiplatform (KMP) for Android. Communicates directly via WebSocket to a lightweight relay-server on the Mac — no cloud backend required.

## Core Value

Remote session control with per-session separation — see all Claude Code sessions at a glance, interact with any of them, and never miss a permission request or completion notification.

## Requirements

### Validated

- ✓ Direct WebSocket transport with relay-server, mDNS auto-discovery, shared secret auth — v1.0
- ✓ Session-aware UI with Material 3 Dynamic Color, dark mode, session status chips — v1.0
- ✓ Session discovery and management commands (/ls, /last, /open, /goto, /rename) — v1.0
- ✓ Per-session text messaging with persistent history (SQLDelight) — v1.0
- ✓ Native Allow/Deny permission UI with callback routing — v1.0
- ✓ Two-channel push notifications (permission HIGH, completion DEFAULT) with deep links — v1.0
- ✓ On-device Whisper transcription with tap-to-toggle recording — v1.0
- ✓ TTS playback for Claude responses — v1.0
- ✓ Bidirectional real-time messaging Mac ↔ App via WebSocket — v1.0

### Active

- [ ] AskUserQuestion interactive keystroke mapping (single choice, multiple choice, free text)
- [ ] Session sync on reconnect (existing sessions appear after app restart)
- [ ] Move relay-server from zellij-claude to relay repo (`server/` directory)
- [ ] Mac-side Whisper transcription (offload from device to Mac for speed)

### Out of Scope

- iOS UI — Android only for v1, KMP shared logic ready for future iOS
- Web version — native mobile focus
- Multi-user support — single developer tool
- Cloud-based voice — on-device or Mac-local only
- FCM push — WebSocket is the transport, Telegram fallback for offline notifications

## Context

- **Shipped v1.0** with 5,484 LOC Kotlin, 96 commits, 6 phases, 18 plans in 2 days
- **Architecture**: KMP shared module (Ktor WebSocket, SQLDelight, Koin DI) + Android Compose UI + Node.js relay-server
- **Transport**: Direct WebSocket (replaced Telegram Bot API which was fundamentally broken for inter-process relay)
- **Discovery**: mDNS/Bonjour on local network, WireGuard IPv6 for remote
- **Voice**: whisper.cpp v1.8.3 on-device (141 MB model, ~45s transcription on Galaxy S25 Ultra — Mac offload planned for v1.1)
- **Two repos affected**: `relay` (Android app) and `zellij-claude` (hooks + relay-server currently)

## Constraints

- **Transport**: Direct WebSocket to relay-server on Mac — Telegram only as notification fallback
- **Platform**: Kotlin Multiplatform (KMP) — shared business logic, Compose for Android UI
- **Voice**: On-device Whisper or Mac-side transcription — must work without cloud
- **Protocol**: zellij-claude JSON message format (`{type, session, message, status, tool_details, timestamp, __relay}`)
- **Single user**: App is for the developer only, shared secret auth

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Telegram Bot API as transport | Reuses existing infrastructure | ⚠️ Replaced — bot can't receive own messages |
| Direct WebSocket transport | Telegram fundamentally broken for relay | ✓ Working — mDNS + WireGuard fallback |
| Kotlin Multiplatform (KMP) | Shared business logic across platforms | ✓ Good — Ktor/SQLDelight/Koin all KMP-native |
| Koin over Hilt for DI | Hilt is Android-only, Koin supports KMP | ✓ Good — compile-time DI not needed for single-dev |
| SQLDelight over Room | Room not KMP-compatible | ✓ Good — shared schema works cleanly |
| On-device Whisper | No server dependency for voice | ⚠️ Revisit — slow on device, Mac offload planned |
| Power Mode from day one | Multi-session is primary use case | ✓ Good — drawer navigation works well |
| relay-server in zellij-claude | Quick prototyping during Phase 6 | ⚠️ Revisit — should move to relay repo |

## Evolution

This document evolves at phase transitions and milestone boundaries.

---
*Last updated: 2026-04-03 after v1.0 milestone*
