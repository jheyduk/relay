---
phase: 01-transport-foundation
plan: 01
subsystem: foundation
tags: [kmp, kotlin-multiplatform, gradle, sqldelight, ktor, koin, kotlinx-serialization, compose]

# Dependency graph
requires: []
provides:
  - KMP multi-module project structure (:shared, :androidApp)
  - Gradle version catalog with all dependency versions
  - Domain models (SessionStatus, RelayMessageType, RelayUpdate)
  - Telegram API DTOs with kotlinx.serialization
  - Relay JSON protocol models with snake_case SerialName mappings
  - SQLDelight messages schema with dedup and session queries
  - Koin DI bootstrap in Application class
affects: [01-02-PLAN, 01-03-PLAN, 02-session-ui, 03-messaging]

# Tech tracking
tech-stack:
  added: [Kotlin 2.3.20, AGP 9.1.0, Gradle 9.4.1, Ktor 3.4.2, SQLDelight 2.0.2, Koin 4.0.4, KSP 2.3.6, Compose BOM 2026.03.00, kotlinx-serialization 1.10.0, coroutines 1.10.2, Timber 5.0.1]
  patterns: [KMP shared module with commonMain/androidMain source sets, version catalog for dependency management, SQLDelight for cross-platform persistence]

key-files:
  created:
    - settings.gradle.kts
    - build.gradle.kts
    - gradle/libs.versions.toml
    - shared/build.gradle.kts
    - androidApp/build.gradle.kts
    - shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/SessionStatus.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayMessageType.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayUpdate.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/TelegramUpdate.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/TelegramResponse.kt
    - shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/RelayMessage.kt
    - shared/src/commonMain/sqldelight/dev/heyduk/relay/Messages.sq
    - androidApp/src/main/AndroidManifest.xml
    - androidApp/src/main/java/dev/heyduk/relay/RelayApp.kt
  modified: []

key-decisions:
  - "Upgraded Gradle 8.14 to 9.4.1 for JDK 25 compatibility (system has only JDK 25)"
  - "Added android.builtInKotlin=false and android.newDsl=false for AGP 9 + KMP traditional setup compatibility"
  - "KSP version changed from 2.3.20-1.0.3 to 2.3.6 (new simplified versioning scheme)"
  - "Used Koin (not Hilt) and SQLDelight (not Room) per KMP architecture decision"

patterns-established:
  - "Package structure: dev.heyduk.relay.domain.model for domain, dev.heyduk.relay.data.remote.dto for DTOs"
  - "SQLDelight .sq files in shared/src/commonMain/sqldelight/dev/heyduk/relay/"
  - "Version catalog in gradle/libs.versions.toml with libs.* accessor pattern"
  - "KMP source sets: commonMain for shared code, androidMain for Android-specific implementations"

requirements-completed: [TRNS-01, TRNS-02, TRNS-04]

# Metrics
duration: 10min
completed: 2026-04-02
---

# Phase 01 Plan 01: KMP Project Foundation Summary

**KMP multi-module project with Ktor/SQLDelight/Koin, Telegram API DTOs, Relay JSON protocol models, and SQLDelight message schema**

## Performance

- **Duration:** 10 min
- **Started:** 2026-04-02T16:04:00Z
- **Completed:** 2026-04-02T16:14:21Z
- **Tasks:** 2
- **Files modified:** 14

## Accomplishments
- Compilable KMP project with :shared and :androidApp modules using Gradle 9.4.1 and AGP 9.1.0
- Complete type system: domain models, Telegram API DTOs with @Serializable, and Relay JSON protocol models
- SQLDelight messages schema with INSERT OR IGNORE dedup and session-based queries
- Koin DI bootstrapped in RelayApp Application class with Timber logging

## Task Commits

Each task was committed atomically:

1. **Task 1: Create KMP project structure with Gradle build files and version catalog** - `46a65c0` (feat)
2. **Task 2: Define domain models, Telegram DTOs, Relay protocol models, and SQLDelight schema** - `0fbc1be` (feat)

## Files Created/Modified
- `settings.gradle.kts` - Multi-module project definition with :shared and :androidApp
- `build.gradle.kts` - Root build file with plugin declarations
- `gradle/libs.versions.toml` - Version catalog with all dependency versions
- `gradle.properties` - Gradle/Kotlin/Android build properties
- `shared/build.gradle.kts` - KMP shared module with commonMain/androidMain source sets
- `androidApp/build.gradle.kts` - Android app module with Compose and Koin
- `androidApp/src/main/AndroidManifest.xml` - Permissions for INTERNET, FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS
- `androidApp/src/main/java/dev/heyduk/relay/RelayApp.kt` - Application class with Koin and Timber init
- `shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/SessionStatus.kt` - Session status enum
- `shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayMessageType.kt` - Message type enum
- `shared/src/commonMain/kotlin/dev/heyduk/relay/domain/model/RelayUpdate.kt` - Domain update model
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/TelegramResponse.kt` - Generic Telegram API response wrapper
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/TelegramUpdate.kt` - Telegram Update/Message/User/Chat/CallbackQuery DTOs
- `shared/src/commonMain/kotlin/dev/heyduk/relay/data/remote/dto/RelayMessage.kt` - Relay JSON protocol models with ToolDetails
- `shared/src/commonMain/sqldelight/dev/heyduk/relay/Messages.sq` - SQLDelight message table schema

## Decisions Made
- **Gradle 9.4.1 instead of 8.14:** System JDK is 25.0.2, which Gradle 8.14.x does not support. Upgraded to 9.4.1 which runs on JDK 25.
- **android.builtInKotlin=false:** AGP 9.0+ has built-in Kotlin support that conflicts with `kotlin-android` and `kotlin-multiplatform` plugins. Disabled built-in Kotlin to use traditional KMP setup.
- **KSP 2.3.6:** The planned version 2.3.20-1.0.3 does not exist. KSP adopted simplified versioning (2.3.x). Used 2.3.6 which is compatible with Kotlin 2.3.20.
- **Koin over Hilt, SQLDelight over Room:** Per CONTEXT.md KMP architecture decision. Research recommended Hilt/Room but those are Android-only.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Gradle version upgrade from 8.14 to 9.4.1**
- **Found during:** Task 1 (project structure creation)
- **Issue:** Gradle 8.14 and 8.14.4 fail with error "25.0.2" on JDK 25 -- no Gradle 8.x version supports JDK 25
- **Fix:** Upgraded to Gradle 9.4.1 which supports JDK 25
- **Files modified:** gradle/wrapper/gradle-wrapper.properties
- **Verification:** ./gradlew --version reports 9.4.1, builds succeed
- **Committed in:** 46a65c0

**2. [Rule 3 - Blocking] KSP version changed to 2.3.6**
- **Found during:** Task 1 (project structure creation)
- **Issue:** KSP 2.3.20-1.0.3 does not exist on Maven Central. KSP moved to simplified versioning.
- **Fix:** Changed to KSP 2.3.6 which is available and compatible with Kotlin 2.3.20
- **Files modified:** gradle/libs.versions.toml
- **Verification:** Plugin resolves, dry-run succeeds
- **Committed in:** 46a65c0

**3. [Rule 3 - Blocking] Added android.builtInKotlin=false for AGP 9 + KMP compat**
- **Found during:** Task 1 (project structure creation)
- **Issue:** AGP 9.0 has built-in Kotlin support that is incompatible with kotlin-multiplatform plugin
- **Fix:** Added android.builtInKotlin=false and android.newDsl=false to gradle.properties
- **Files modified:** gradle.properties
- **Verification:** Both modules compile successfully
- **Committed in:** 46a65c0

**4. [Rule 3 - Blocking] compilerOptions DSL replaces kotlinOptions**
- **Found during:** Task 1 (project structure creation)
- **Issue:** kotlinOptions {} block is removed in Kotlin 2.3.x, causes build error
- **Fix:** Replaced with kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
- **Files modified:** androidApp/build.gradle.kts
- **Verification:** Build succeeds without deprecation errors
- **Committed in:** 46a65c0

---

**Total deviations:** 4 auto-fixed (4 blocking issues)
**Impact on plan:** All auto-fixes were necessary to resolve version incompatibilities between JDK 25, Gradle, AGP 9, and KMP. No scope creep. Final project structure matches plan intent.

## Issues Encountered
- Android SDK platform 36 was not installed locally. Installed via `sdkmanager "platforms;android-36" "build-tools;36.0.0"`.
- AGP 9 deprecation warnings about KMP/android-library compatibility are expected and non-blocking. Future migration to `com.android.kotlin.multiplatform.library` plugin recommended.

## User Setup Required
None - no external service configuration required.

## Known Stubs
None - all models are fully defined, no placeholder data.

## Next Phase Readiness
- Project structure and all type definitions are in place for Plan 02 (Telegram API client implementation)
- SQLDelight schema generates DAO code via `generateCommonMainRelayDatabaseInterface` task
- Koin DI framework is bootstrapped and ready for module registration
- All Telegram API DTOs and Relay protocol models are available in the shared module's commonMain source set

## Self-Check: PASSED

All 14 created files verified present. Both task commits (46a65c0, 0fbc1be) verified in git log.

---
*Phase: 01-transport-foundation*
*Completed: 2026-04-02*
