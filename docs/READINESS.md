# SDK Readiness

This document tracks how ready `putio-sdk-kotlin` is for autonomous agent work and public package maintenance.

## Overall Status

- deterministic unit verification exists under `src/test/kotlin`
- opt-in live verification exists under `src/liveTest/kotlin`
- `./gradlew verify` now enforces compile, unit-test, jar, and a `90%` line coverage floor
- local first-party Android proof covers minSdk 26 and an R8-minified release build through the composite SDK; continuous CI enforcement is pending
- typed exceptions now feed a user-facing localization layer for recovery guidance
- `./gradlew liveTest` remains the real API verification lane

## Current Confidence

| Area | Status | Notes |
| --- | --- | --- |
| Package boot | `good` | `./gradlew verify` is stable locally and in CI once Java `21` is available |
| Unit verification | `good` | request shaping, parsing, unknown-value preservation, typed API errors, and localized recovery guidance have deterministic coverage across current domains with a `90%` line floor |
| Live verification | `medium` | account, auth, config, grants, routes, files/trash, playback-adjacent file helpers, history, and safe transfer read paths are live-covered when credentials are configured; destructive account and IFTTT mutation paths stay deterministic-only for now |
| Android consumption | `medium` | local `putio-android` proof passes at minSdk 26 and assembles an unsigned, minified mobile release with no SDK-specific keep rules; the continuous CI gate is pending putdotio/putio-android#85 and its putdotio/putio-android#75 prerequisite |
| Release readiness | `medium` | local Maven publishing exists, but external package publishing is still intentionally deferred |

## Highest-Value Next Gaps

1. decide whether IFTTT should grow beyond playback events into a generic event surface
2. deepen deterministic coverage for more conditional payload branches and transport failure paths so the `90%` floor stays comfortable as the surface grows
3. expand live verification as new safe namespaces land, following the shared-account rules
