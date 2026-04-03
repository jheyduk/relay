---
phase: 06
slug: direct-websocket-transport
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-03
---

# Phase 06 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Kotlin Test + JUnit 4 + Turbine (coroutines) / Node.js `--test` |
| **Config file** | shared/build.gradle.kts (commonTest dependencies) |
| **Quick run command** | `./gradlew :shared:jvmTest --tests "dev.heyduk.relay.*"` |
| **Full suite command** | `./gradlew :shared:allTests` |
| **Estimated runtime** | ~30 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :shared:jvmTest` + `node --test test/` (in zellij-claude)
- **After every plan wave:** Run `./gradlew :shared:allTests`
- **Before `/gsd:verify-work`:** Full suite must be green
- **Max feedback latency:** 30 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|-----------|-------------------|-------------|--------|
| 06-01-01 | 01 | 1 | R-06-01 | integration (Node.js) | `node --test test/relay-server.test.js` | ❌ W0 | ⬜ pending |
| 06-01-02 | 01 | 1 | R-06-02 | manual | Verify with `dns-sd -B _relay._tcp local` | N/A | ⬜ pending |
| 06-01-03 | 01 | 1 | R-06-04 | unit (Node.js) | `node --test test/telegram-helper.test.js` | ❌ W0 | ⬜ pending |
| 06-01-04 | 01 | 1 | R-06-07 | unit (Node.js) | `node --test test/relay-server.test.js` | ❌ W0 | ⬜ pending |
| 06-02-01 | 02 | 1 | R-06-05 | unit | `./gradlew :shared:jvmTest --tests "*NsdDiscoveryTest*"` | ❌ W0 | ⬜ pending |
| 06-02-02 | 02 | 1 | R-06-06 | unit | `./gradlew :shared:jvmTest --tests "*WebSocketClientTest*"` | ❌ W0 | ⬜ pending |
| 06-02-03 | 02 | 1 | R-06-03 | unit | `./gradlew :shared:jvmTest --tests "*WebSocketClientTest*"` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `shared/src/commonTest/kotlin/dev/heyduk/relay/data/remote/WebSocketClientTest.kt` — stubs for R-06-03, R-06-05, R-06-06
- [ ] `test/relay-server.test.js` (in zellij-claude repo) — stubs for R-06-01, R-06-07
- [ ] `test/telegram-helper.test.js` (in zellij-claude repo) — stubs for R-06-04

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| mDNS service advertised on server start | R-06-02 | Requires macOS network stack | 1. Start relay server 2. Run `dns-sd -B _relay._tcp local` 3. Verify service appears |
| App discovers Mac via NSD on local network | R-06-05 | Requires Android device on same network | 1. Start relay server on Mac 2. Open app 3. Verify auto-connection |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 30s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
