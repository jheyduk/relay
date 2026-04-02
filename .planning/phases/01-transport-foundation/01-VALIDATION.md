---
phase: 1
slug: transport-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-04-02
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + kotlinx-coroutines-test + Turbine (Flow testing) |
| **Config file** | None yet — Wave 0 creates test infrastructure |
| **Quick run command** | `./gradlew :shared:allTests -x lint` |
| **Full suite command** | `./gradlew :shared:allTests :androidApp:testDebugUnitTest` |
| **Estimated runtime** | ~15 seconds |

---

## Sampling Rate

- **After every task commit:** Run `./gradlew :shared:allTests -x lint`
- **After every wave merge:** Run `./gradlew :shared:allTests`
- **Phase gate:** Full unit test suite green before `/gsd:verify-work`

---

## Requirements -> Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| TRNS-01 | getUpdates returns parsed updates | unit | `./gradlew :shared:allTests --tests "*.TelegramApiTest"` | No — Plan 01-02 |
| TRNS-01 | Polling loop processes updates and persists offset | unit | `./gradlew :shared:allTests --tests "*.TelegramPollerTest"` | No — Plan 01-02 |
| TRNS-02 | Two-bot architecture (separate tokens, no 409) | unit | `./gradlew :shared:allTests --tests "*.TwoTokenConfigTest"` | No — Plan 01-02 |
| TRNS-03 | sendMessage formats `@kuerzel message` correctly | unit | `./gradlew :shared:allTests --tests "*.MessageProtocolTest"` | No — Plan 01-02 |
| TRNS-04 | JSON relay messages parsed into domain models | unit | `./gradlew :shared:allTests --tests "*.RelayMessageParserTest"` | No — Plan 01-02 |
| TRNS-05 | Backoff increases on error, resets on success | unit | `./gradlew :shared:allTests --tests "*.BackoffStrategyTest"` | No — Plan 01-02 |
| TRNS-05 | Network monitor emits connectivity changes | unit | `./gradlew :androidApp:testDebugUnitTest --tests "*.NetworkMonitorTest"` | No — Plan 01-03 |

---

## Wave 0 Gaps

Test dependencies and infrastructure created within Plan 01-01 (build configuration):
- [ ] `shared/src/commonTest/` directory structure
- [ ] Test dependencies in `shared/build.gradle.kts`: JUnit 5, kotlinx-coroutines-test, Turbine, MockK
- [ ] Ktor mock engine dependency: `ktor-client-mock`
- [ ] SQLDelight in-memory driver for testing

Test files created within Plan 01-02 (TDD approach):
- [ ] `shared/src/commonTest/kotlin/.../TelegramApiTest.kt` — TRNS-01
- [ ] `shared/src/commonTest/kotlin/.../TelegramPollerTest.kt` — TRNS-01, TRNS-05
- [ ] `shared/src/commonTest/kotlin/.../RelayMessageParserTest.kt` — TRNS-04
- [ ] `shared/src/commonTest/kotlin/.../MessageProtocolTest.kt` — TRNS-03
