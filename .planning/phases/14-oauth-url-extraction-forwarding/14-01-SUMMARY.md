---
phase: 14-oauth-url-extraction-forwarding
plan: 01
subsystem: auth
tags: [oauth, websocket, url-extraction, auth-recovery]

# Dependency graph
requires:
  - phase: 13-auth-error-detection-login-dispatch
    provides: auth recovery state machine, /login dispatch, AUTH_REQUIRED message type
provides:
  - OAuth URL extraction from terminal output after /login dispatch
  - auth_url WebSocket message with session and URL for app consumption
  - auth_timeout WebSocket message when 5-min recovery window expires
  - App protocol extended with AUTH_URL and AUTH_TIMEOUT message types
  - RelayUpdate.authUrl field for Phase 15 UI consumption
affects: [15-auth-recovery-ui]

# Tech tracking
tech-stack:
  added: []
  patterns: [oauth-url-scanning, auth-recovery-timeout-notification]

key-files:
  created: []
  modified:
    - server/relay-server.cjs
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/RelayMessage.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayMessageType.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/RelayMessageParser.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayUpdate.kt
    - androidApp/src/main/java/dev/heyduk/relay/service/WebSocketService.kt

key-decisions:
  - "3s URL scan interval for responsive OAuth URL detection without excessive polling"
  - "url_sent recovery state to prevent duplicate auth_url broadcasts"

patterns-established:
  - "OAuth URL scanning: separate function with own cooldown map, only active during login_sent state"
  - "Timeout notification: auth_timeout sent to app before clearing recovery state"

requirements-completed: [AUTH-03, AUTH-04]

# Metrics
duration: 3min
completed: 2026-04-08
---

# Phase 14 Plan 01: OAuth URL Extraction & Forwarding Summary

**Server-side OAuth URL extraction with regex scanning during login_sent state, auth_url/auth_timeout WebSocket broadcast, and 4-place app protocol extension**

## Performance

- **Duration:** 3 min
- **Started:** 2026-04-08T08:39:11Z
- **Completed:** 2026-04-08T08:42:14Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- Server scans terminal output for Anthropic OAuth URLs every 3s during login_sent recovery state
- OAuth URL match triggers auth_url WebSocket message with session and extracted URL
- 5-min recovery timeout triggers auth_timeout WebSocket message before clearing state
- App protocol extended with AUTH_URL and AUTH_TIMEOUT in all 4 convention places (DTO, domain, parser, DB-skip)
- RelayUpdate domain model carries authUrl field ready for Phase 15 UI consumption

## Task Commits

Each task was committed atomically:

1. **Task 1: Add OAuth URL scanning and auth_url/auth_timeout broadcast to relay-server** - `0ed92ee` (feat)
2. **Task 2: Add AUTH_URL and AUTH_TIMEOUT message types to app protocol** - `57e4582` (feat)

## Files Created/Modified
- `server/relay-server.cjs` - OAuth URL pattern, scanForOAuthUrl(), auth_timeout broadcast, URL scan map
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/RelayMessage.kt` - AUTH_URL/AUTH_TIMEOUT DTO enums, url field
- `shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayMessageType.kt` - AUTH_URL/AUTH_TIMEOUT domain enums
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/RelayMessageParser.kt` - toDomain() mappings, authUrl pass-through
- `shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayUpdate.kt` - authUrl field added
- `androidApp/src/main/java/dev/heyduk/relay/service/WebSocketService.kt` - AUTH_URL/AUTH_TIMEOUT in DB-skip list

## Decisions Made
- 3s URL scan interval (URL_SCAN_INTERVAL) for responsive detection without excessive polling
- Introduced 'url_sent' recovery state to prevent duplicate auth_url broadcasts after URL is found
- url field on RelayMessage DTO maps to authUrl on RelayUpdate domain model (distinct naming for clarity)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Auth URL extraction and forwarding complete, ready for Phase 15 auth recovery UI
- App receives auth_url with OAuth URL and auth_timeout on expiry
- RelayUpdate.authUrl field available for UI consumption
- Server restarts cleanly with all new functionality

---
*Phase: 14-oauth-url-extraction-forwarding*
*Completed: 2026-04-08*
