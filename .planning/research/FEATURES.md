# Feature Landscape

**Domain:** Mobile companion app for remote AI coding session management
**Researched:** 2026-04-02
**Confidence:** MEDIUM-HIGH

## Critical Context: Competitive Landscape Shift

Since Relay was conceived, the ecosystem has changed significantly. Claude Code now has:

1. **Remote Control** (Feb 2026) -- official mobile session control via Claude iOS/Android app
2. **Channels** (research preview) -- official Telegram plugin that bridges Telegram messages into Claude Code sessions with permission relay
3. **Dispatch** -- mobile-to-desktop task delegation via Claude app

Third-party competitors also emerged:
- **Happy Coder** -- open-source mobile Claude Code client (iOS + Android), E2E encrypted, voice, push notifications
- **Nimbalyst** -- visual workspace with iOS companion, kanban board, multi-agent support
- **Tactic Remote** -- terminal streaming, file browser, Live Activities, iPad optimization

**Relay's unique position**: Multi-session management via zellij-claude with session-aware UI. No competitor manages multiple concurrent zellij sessions as first-class citizens. Claude Code Remote Control supports one session per process (unless server mode), and none of the third-party apps understand zellij-claude's kuerzel-based session protocol.

## Table Stakes

Features users expect. Missing = product feels incomplete.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Session list with status | Every competitor shows session state; Claude RC shows online/offline | Low | Map zellij-claude states: working, waiting, ready, shell |
| Text messaging to sessions | Core interaction model; all competitors support this | Low | `@kuerzel message` format via Telegram Bot API |
| Push notifications | Happy, Nimbalyst, Tactic, Claude RC all have them | Medium | Permission requests, completions, errors. FCM or long polling |
| Permission Allow/Deny | Claude RC, Happy, Tactic all support remote approval | Medium | Native UI replacing Telegram inline keyboards. Must be fast and reliable |
| Session commands | Basic session management expected from a session-aware app | Low | `/ls`, `/last`, `/open`, `/goto`, `/rename` -- pass through to Telegram bot |
| Conversation history | Happy and Claude RC both show full history | Medium | Per-session message history with kuerzel separation |
| Reliable message delivery | All competitors handle reconnection gracefully | Medium | Telegram long polling is inherently reliable; handle network transitions |
| Dark mode | Android standard; all competitor apps support it | Low | Material 3 dynamic theming handles this |

## Differentiators

Features that set Relay apart. Not expected from generic tools, but high value for the zellij-claude workflow.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Multi-session dashboard | **Primary differentiator.** See all zellij-claude sessions at a glance with color-coded status. No competitor does this for zellij sessions | Medium | Grid or list view showing kuerzel, status, last activity. Nimbalyst has kanban but not for zellij |
| Session-aware routing | Messages automatically routed to correct session via `@kuerzel` prefix. No manual session switching needed | Low | Built into the existing Telegram protocol |
| On-device Whisper transcription | Independence from server. Happy uses cloud STT, Tactic uses cloud STT. On-device = works on airplane, no latency, no cost | High | whisper.cpp or similar on-device model. Significant integration effort |
| Voice-to-session pipeline | Speak a command, it gets transcribed and sent to the right session. Unique workflow for mobile-first dev control | High | Combines Whisper + session routing. "Hey @infra, deploy the staging branch" |
| TTS response playback | Hear Claude's responses while walking. Competitive: Happy has voice but via Eleven Labs (cloud). On-device TTS is free | Medium | Android TTS engine is built-in and decent. Low effort, high convenience |
| Unified chat stream | Text and voice in one timeline per session. Voice shows transcript + playback button | Medium | Differentiates from pure-text competitors |
| Zero infrastructure | No custom server, no relay service, no CLI wrapper to install. Telegram Bot API is the only dependency | Low | Happy requires CLI install + relay server. Tactic requires Mac menu bar app. Claude RC requires subscription |
| Status-driven notification priority | Permission requests get high-priority notifications; completions get normal. Status-aware triage | Low | Leverage Android notification channels for priority levels |

## Anti-Features

Features to explicitly NOT build.

| Anti-Feature | Why Avoid | What to Do Instead |
|--------------|-----------|-------------------|
| Code editor / file browser | Relay is a control surface, not an IDE. Tactic Remote tried this and it's a maintenance nightmare on mobile. The Mac has the IDE | View-only message display. No file editing |
| Terminal emulator | Raw terminal streaming is what Tactic does. It's complex and adds little value when you have session-level messaging | Structured message display with syntax highlighting for code blocks |
| Custom relay server / backend | Happy Coder built an encrypted relay. Major ops burden. Telegram Bot API already handles message routing, persistence, and delivery | Use Telegram Bot API directly. It's proven infrastructure |
| Multi-user / team features | Single developer tool. Adding auth, sharing, roles = scope explosion for zero value | Single-user, no auth needed. The Telegram bot token is the auth |
| iOS version | Scope control. Kotlin/Compose only. If needed later, evaluate KMP | Android only for v1 |
| Git operations | Nimbalyst does visual diffs and git worktrees. That's their niche, not ours | Session commands only. Git happens through Claude Code on the Mac |
| Cloud-based voice transcription | Adds dependency, cost, latency, privacy concerns. On-device Whisper is the differentiator | On-device Whisper only |
| Claude Code direct integration | Would require reimplementing the protocol. zellij-claude already handles this via Telegram | Speak the existing Telegram message format. Mac-side needs zero changes |
| Kanban / project management | Nimbalyst's territory. Relay is a control surface, not a project manager | Simple session list with status indicators |

## Feature Dependencies

```
Session List (core) --> Session Detail View --> Conversation History
Session List (core) --> Push Notifications (need session context for routing)
Session List (core) --> Permission Allow/Deny (need session context)
Text Input --> Session-aware Routing (@kuerzel prefix)
Voice Input --> On-device Whisper --> Session-aware Routing
Voice Input --> TTS Playback (shared audio subsystem)
Telegram Bot API Connection --> Session List (via /ls)
Telegram Bot API Connection --> Text Input
Telegram Bot API Connection --> Push Notifications
Push Notifications --> Status-driven Priority (notification channels)
```

## MVP Recommendation

### Phase 1: Core Control Surface
Prioritize what makes Relay immediately useful as a Telegram replacement:

1. **Telegram Bot API connection** -- foundation for everything
2. **Session list with status** -- the primary differentiator view
3. **Text messaging to sessions** -- core interaction
4. **Permission Allow/Deny** -- the most time-critical mobile action
5. **Push notifications** -- the reason to have a native app at all
6. **Session commands** -- `/ls`, `/last`, basic management

This alone replaces the Telegram chat workflow with a session-aware UI.

### Phase 2: Voice Pipeline
7. **On-device Whisper transcription** -- the major differentiator
8. **Voice-to-session routing** -- speak to a specific session
9. **TTS response playback** -- complete the voice loop
10. **Unified chat stream** -- text + voice in one timeline

### Defer
- **Conversation history persistence** -- can start with in-memory, add Room DB later
- **Status-driven notification priority** -- nice polish, not critical for launch
- **Dark mode** -- Material 3 gives this nearly free, but explicit testing deferred

## Competitive Positioning

| Competitor | Relay's Advantage | Competitor's Advantage |
|------------|-------------------|----------------------|
| Claude RC | Multi-session, no subscription needed, works with zellij-claude | Official Anthropic product, deeper integration, web + mobile |
| Happy Coder | No CLI install, no relay server, multi-session native | E2E encryption, cross-platform, open source, larger community |
| Nimbalyst | Lighter weight, voice-first, no desktop app needed | Visual diffs, kanban, multi-agent, richer desktop experience |
| Tactic Remote | No Mac server app needed, voice input | Terminal streaming, file browser, iPad optimization |
| Claude Channels (Telegram) | Session-aware UI, push notifications, voice | Official support, zero mobile app needed |

**Key insight**: Claude Code Channels with the Telegram plugin is Relay's closest "competitor" because it uses the same transport. But Channels just bridges messages -- it has no session-aware UI, no push notifications, no voice. Relay adds the native mobile experience layer on top of the same Telegram infrastructure.

## Sources

- [Claude Code Remote Control docs](https://code.claude.com/docs/en/remote-control) -- HIGH confidence
- [Claude Code Channels docs](https://code.claude.com/docs/en/channels) -- HIGH confidence
- [Happy Coder features](https://happy.engineering/docs/features/) -- MEDIUM confidence
- [Nimbalyst mobile apps comparison](https://nimbalyst.com/blog/best-mobile-apps-for-claude-code-2026/) -- MEDIUM confidence
- [Tactic Remote](https://tacticremote.com/) -- MEDIUM confidence
- [Claude Code Remote Control issue #29319](https://github.com/anthropics/claude-code/issues/29319) -- HIGH confidence
